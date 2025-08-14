/*
 * This file is licensed under the GNU General Public License v3.0.
 *
 * You may obtain a copy of the License at
 * https://www.gnu.org/licenses/gpl-3.0.html
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */
package io.github.dkaukov.afsk.atoms;

import java.util.function.IntConsumer;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.dkaukov.afsk.util.BitBuffer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * AFSK symbol slicer with a simple second-order (PI) timing PLL.
 * - Emits one bit whenever the internal phase wraps [0..1).
 * - Retimes on symbol transitions (zero-crossings) using a phase error at 0.5 UI.
 */
@Slf4j
public class SymbolSlicerPll {

  // ---- Nominal parameters (derived) ----
  private final float nominalStep;     // UI advance per sample = baud / sampleRate
  private final float stepMin, stepMax;

  // ---- Loop gains (configurable) ----
  private final float kp;              // proportional gain on frequency loop (step adjust)
  private final float ki;              // integral gain on frequency loop (step adjust)
  private final float phaseGainAcq;    // stronger phase pull during acquisition

  // ---- Lock detection ----
  private final float lockErrorWindow;   // |error| < window -> good transition (units of UI)
  private final float unlockErrorWindow; // hysteresis (slightly larger than lock window)
  private final int lockConsecutive;     // N good transitions to declare lock
  private final int unlockBadLimit;      // N bad transitions to declare unlock

  // ---- State ----
  private float step;                  // current UI advance per sample
  @Getter private float phase = 0.5f;  // [0..1)
  private float integ = 0f;            // integral for PI
  private final float integClamp;      // anti-windup clamp
  private int prevSymbol = 0;          // 0 or 1
  @Getter private boolean locked = false;
  private int goodTrans = 0;
  private int badTrans = 0;
  private final BitBuffer buf = new BitBuffer(512 * 8);

  /**
   * @param sampleRate Hz
   * @param baudRate   symbols per second
   * @param stepPpmWindow Allowed frequency error window in ppm (e.g. 1000 = ±0.1%)
   * @param kp         P term for step control (e.g. 1e-4)
   * @param ki         I term for step control (e.g. 8e-6)
   * @param phaseGainAcq     phase pull when acquiring (e.g. 0.22)
   * @param lockErrorWindow  UI window to count a "good" transition (e.g. 0.30)
   * @param lockConsecutive  count of good transitions to declare lock (e.g. 6)
   */
  public SymbolSlicerPll(
    float sampleRate,
    float baudRate,
    float stepPpmWindow,
    float kp,
    float ki,
    float phaseGainAcq,
    float lockErrorWindow,
    int lockConsecutive
  ) {
    this.nominalStep = baudRate / sampleRate;
    float ppm = stepPpmWindow * 1e-6f;
    this.stepMin = nominalStep * (1f - ppm);
    this.stepMax = nominalStep * (1f + ppm);

    this.kp = kp;
    this.ki = ki;
    this.phaseGainAcq = phaseGainAcq;

    this.lockErrorWindow = lockErrorWindow;
    // a touch larger for unlock hysteresis:
    this.unlockErrorWindow = Math.min(0.49f, lockErrorWindow * 1.5f);
    this.lockConsecutive = lockConsecutive;
    this.unlockBadLimit = Math.max(2, lockConsecutive / 2);

    this.step = nominalStep;
    this.integClamp = 1_000f; // generous; practically limited by step clamping
  }

  /** Convenience constructor with sensible defaults for 1200 baud AFSK. */
  public SymbolSlicerPll(float sampleRate, float baudRate) {
    this(
      sampleRate, baudRate,
      5100,      // ±1.0% step window in ppm
      1.0e-4f,        // kp
      8.0e-6f,        // ki
      0.22f - 0.002f, // phase gain during acquisition
      0.30f,          // lock window (UI)
      6               // transitions to lock
    );
  }

  /**
   * Feed one demodulated sample. Emits 0/1 via listener whenever a bit boundary is reached.
   * @param sample   demod sample; sign determines symbol (<=0 => 0, >0 => 1)
   * @param listener callback for emitted bits
   */
  public void process(float sample, IntConsumer listener) {
    final int symbol = (sample > 0f) ? 1 : 0;
    // Advance phase and emit on wrap. Use while to be robust if step slightly > 1 (shouldn’t).
    phase += step;
    while (phase >= 1f) {
      phase -= 1f;
      listener.accept(symbol);
    }
    // On symbol transition, retime and adjust frequency (step).
    if (symbol != prevSymbol) {
      float error = phase - 0.5f;                // desired sampling point at mid‑UI
      error = clamp(error, -0.5f, 0.5f);  // numeric safety
      // Lock tracking with hysteresis
      if (Math.abs(error) <= lockErrorWindow) {
        goodTrans++;
        badTrans = 0;
        if (!locked && goodTrans >= lockConsecutive) {
          locked = true;
          // tighten integrator when we acquire
          integ *= 0.5f;
        }
      } else {
        goodTrans = 0;
        badTrans++;
        if (locked && Math.abs(error) >= unlockErrorWindow && badTrans >= unlockBadLimit) {
          locked = false;
          integ = 0f; // dump integrator on unlock to speed reacquisition
        }
      }
      // Frequency loop (adjust step via PI)
      // step <- step - (kp*e + ki*∫e)
      integ = clamp(integ + error, -integClamp, +integClamp);
      float delta = kp * error + ki * integ;
      step = clamp(step - delta, stepMin, stepMax);
      // Timing loop (phase pull toward mid‑UI). Stronger when acquiring.
      float g = phaseGainAcq;
      phase -= g * error;
      if (log.isTraceEnabled()) {
        log.trace("PLL phase={}, step={}, err={}, locked={}", phase, step, error, locked);
      }
    }
    prevSymbol = symbol;
  }

  /**
   * Slice a buffer of samples into bits, returning an int[] of 0/1.
   * Uses a growable array to avoid boxing/GC.
   */
  @SuppressFBWarnings("EI_EXPOSE_REP")
  public BitBuffer slice(float[] samples) {
    buf.clear();
    for (float s : samples) {
      process(s, buf::addBit);
    }
    return buf;
  }

  /** Reset to nominal (keeps configured gains). */
  public void reset() {
    phase = 0.5f;
    step = nominalStep;
    integ = 0f;
    prevSymbol = 0;
    locked = false;
    goodTrans = 0;
    badTrans = 0;
  }

  private static float clamp(float v, float lo, float hi) {
    return (v < lo) ? lo : Math.min(v, hi);
  }
}

