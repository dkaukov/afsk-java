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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * AFSK Slicer with PLL (Phase-Locked Loop) for demodulating AFSK signals.
 * This class slices demodulated samples into bits using a PLL to maintain synchronization.
 * It adjusts the slicing phase based on the detected symbol transitions.
 */
@Slf4j
public class SymbolSlicerPll {

  private float pllStep;          // Nominal increment per sample
  @Getter
  private float pllPhase = 0.5f;
  private final float searchingErrorGain;   // Bigger correction when not yet locked
  private int prevSymbol = 0;
  @Getter
  private boolean locked = false;
  private int consecutiveGoodTransitions = 0;
  private float pllErrorIntegral = 0.0f;
  private final float pllStepMin;
  private final float pllStepMax;

  public SymbolSlicerPll(float sampleRate, float baudRate) {
    this.pllStep = baudRate / sampleRate;
    this.pllStepMin = pllStep * 0.99f; // Minimum step size for PLL
    this.pllStepMax = pllStep * 1.01f; // Maximum step
    this.searchingErrorGain = 0.22f;   // More aggressive for acquiring signal
  }

  /**
   * Feed a single demodulated sample to the PLL. Calls listener when a bit is ready.
   */
  void process(float sample, Consumer<Integer> listener) {
    // Slice: convert float sample to symbol (0 = MARK, 1 = SPACE)
    int currentSymbol = (sample > 0) ? 1 : 0;
    // Advance phase
    pllPhase += pllStep;
    if (pllPhase >= 1.0f) {
      pllPhase -= 1.0f;
      listener.accept(currentSymbol); // Sample at bit center
    }
    // If a symbol transition, apply error correction to sync timing
    if (currentSymbol != prevSymbol) {
      float error = pllPhase - 0.5f; // Error from ideal phase (0.5 is mid-symbol)
      if (Math.abs(error) < 0.3f) {
        consecutiveGoodTransitions++;
        if (consecutiveGoodTransitions > 5)  {
          locked = true;
        }
      } else {
        consecutiveGoodTransitions = 0;
        locked = false;
      }
      log.trace("PLL phase: {}, step: {}, error: {}, locked: {}", pllPhase, pllStep, error, locked);
      pllStep = pllStep - (error * 0.0001f) - (pllErrorIntegral * 0.000008f);
      if (pllStep < pllStepMin) {
        pllStep = pllStepMin; // Prevent too small step
      } else if (pllStep > pllStepMax) {
        pllStep = pllStepMax; // Prevent too large step
      } else {
        pllErrorIntegral += error; // Integral for PLL
      }
      pllPhase -= error * searchingErrorGain; // Adjust phase based on error
    }
    prevSymbol = currentSymbol;
  }

  /**
   * Slice an entire chunk of demodulated samples into bits.
   * Returns an array of decoded bits (0 or 1).
   */
  public int[] slice(float[] samples) {
    List<Integer> bits = new ArrayList<>();
    for (float sample : samples) {
      process(sample, bits::add);
    }
    return bits.stream()
      .mapToInt(Integer::intValue)
      .toArray();
  }

  public void reset() {
    pllPhase = 0.5f;
    prevSymbol = 0;
    locked = false;
    consecutiveGoodTransitions = 0;
  }
}
