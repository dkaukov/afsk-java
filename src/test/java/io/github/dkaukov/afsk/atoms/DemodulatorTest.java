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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class DemodulatorTest {

  /**
   * Tests whether the demodulator can detect transitions between
   * 1200 Hz and 2200 Hz tones with known timing.
   * Used to confirm base phase discriminator logic.
   */
  @Test
  @DisplayName("Alternating Tones – Detect Zero Crossings")
  void testProcessChunkWithAlternatingTones() {
    int sampleRate = 48000;
    int toneDurationMs = 10_000; // each tone lasts 10ms
    int repeats = 10;        // total length = 10 * 20ms = 200ms
    int toneSamples = sampleRate * toneDurationMs / 1000;
    float[] signal = generateAfskAlternatingTones(sampleRate, toneDurationMs, repeats);
    Demodulator demod = new Demodulator(sampleRate, 1200, 2200, 1200);
    float[] deltaQ = demod.processChunk(signal, signal.length);
    // Only print first 100 values for debug
    log.trace("deltaQ: {}", Arrays.toString(Arrays.copyOf(deltaQ, 100)));
    // Check that deltaQ crosses zero periodically (i.e., tone changes)
    int zeroCrossings = 0;
    for (int i = 1; i < deltaQ.length; i++) {
      if ((deltaQ[i - 1] > 0 && deltaQ[i] < 0) || (deltaQ[i - 1] < 0 && deltaQ[i] > 0)) {
        zeroCrossings++;
      }
    }
    log.info("Zero crossings: {}",  zeroCrossings);
    assertTrue(zeroCrossings >= repeats - 2, "Expected at least " + (repeats - 2) + " zero crossings");
  }

  /**
   * Tests demodulator sensitivity to increasingly short tone durations.
   * Helps ensure tone transitions are still detectable for brief bursts.
   *
   * @param toneDurationMs duration of each tone segment in microseconds
   */
  @ParameterizedTest(name = "Short Tones – {0}us Duration")
  @DisplayName("Short Tone Durations – Stability at High Transition Rates")
  @ValueSource(ints = {10_000, 5_000, 2_000, 1_000 /*833, 832, 820*/})  // durations in microseconds
  void testShortToneDurations(int toneDurationMs) {
    int sampleRate = 48000;
    int repeats = 100;
    float[] signal = generateAfskAlternatingTones(sampleRate, toneDurationMs, repeats);
    Demodulator demod = new Demodulator(sampleRate, 1200, 2200, 1200);
    float[] deltaQ = demod.processChunk(signal, signal.length);
    int zeroCrossings = 0;
    for (int i = 1; i < deltaQ.length; i++) {
      if ((deltaQ[i - 1] > 0 && deltaQ[i] < 0) || (deltaQ[i - 1] < 0 && deltaQ[i] > 0)) {
        zeroCrossings++;
      }
    }
    log.debug("Tone {}us: found {} ZC (expected {})", toneDurationMs, zeroCrossings, repeats - 1);
    assertTrue(zeroCrossings >= repeats - 2, "Too few zero crossings");
  }

  @Test
  @DisplayName("1200 Hz → -1, 2200 Hz → +1 after normalization")
  void testTonePolarityAndMagnitude() {
    int sampleRate = 48000;
    Demodulator demod = new Demodulator(sampleRate, 1200, 2200, 1200);

    float[] tone1200 = generateAfskTone(sampleRate, 1200, 0.1f);
    float[] tone2200 = generateAfskTone(sampleRate, 2200, 0.1f);

    float mean1200 = mean(demod.processChunk(tone1200, tone1200.length));
    float mean2200 = mean(demod.processChunk(tone2200, tone2200.length));

    log.info("mean demod @1200Hz = {}", mean1200);
    log.info("mean demod @2200Hz = {}", mean2200);

    assertTrue(mean1200 < -0.8, "1200 Hz tone should demodulate near -1");
    assertTrue(mean2200 >  0.8, "2200 Hz tone should demodulate near +1");
  }

  @Test
  @DisplayName("Dead-zone silences near-center tone (≈1700 Hz)")
  void deadZoneSilencesCenterTone() {
    int fs = 48_000;
    Demodulator demod = new Demodulator(fs, 1200, 2200, 1200);
    //demod.setDeadZone(0.03f); // ≈ 3% of full-scale after normalization
    // center tone (mark/space mid), discriminator ≈ 0
    float[] x = generateAfskTone(fs, (1200+2200)/2, 0.2f);
    float[] y = demod.processChunk(x, x.length);
    double frac = mean(y);
    log.info("center-tone non-zero fraction = {}", frac);
    assertTrue(frac < 0.00001, "Too many non-zero samples on center tone");
  }

  @Test
  @DisplayName("Silence → near-zero mean and low RMS")
  void silenceProducesNearZero() {
    int fs = 48_000;
    Demodulator demod = new Demodulator(fs, 1200, 2200, 1200);
    float[] z = new float[8192];
    float[] y = demod.processChunk(z, z.length);

    int skip = 256; // clear FIR warmup
    double sum = 0, e2 = 0; int n = 0;
    for (int i = skip; i < y.length; i++) { sum += y[i]; e2 += y[i]*y[i]; n++; }
    float mean = (float)(sum / n);
    float rms  = (float)Math.sqrt(e2 / n);

    assertEquals(0.0f, mean, 5e-3f, "Silence mean should be ~0");
    assertTrue(rms < 0.02f, "Silence RMS should be small (got " + rms + ")");
  }

  @Test
  @DisplayName("Amplitude invariance: 0.25× vs 0.9× input → same demod mean")
  void amplitudeInvariance() {
    int fs = 48_000;
    Demodulator d1 = new Demodulator(fs, 1200, 2200, 1200);
    Demodulator d2 = new Demodulator(fs, 1200, 2200, 1200);

    float[] base = generateAfskTone(fs, 2200, 0.2f);
    float[] a025 = new float[base.length];
    float[] a090 = new float[base.length];
    for (int i = 0; i < base.length; i++) { a025[i] = 0.25f * base[i]; a090[i] = 0.90f * base[i]; }

    float m1 = mean(d1.processChunk(a025, a025.length));
    float m2 = mean(d2.processChunk(a090, a090.length));

    assertEquals(m2, m1, 0.05f, "Demod mean should be ~amplitude-invariant");
    assertTrue(m1 > 0.8f && m2 > 0.8f, "Should still be near +1");
  }

  @Test
  @DisplayName("Small frequency error (±30 Hz) still normalizes correctly")
  void smallFreqOffsetTolerance() {
    int fs = 48_000;
    Demodulator demod = new Demodulator(fs, 1200, 2200, 1200);
    float[] hi = generateAfskTone(fs, 2200 + 30, 0.2f);
    float[] lo = generateAfskTone(fs, 1200 - 30, 0.2f);

    float mHi = mean(demod.processChunk(hi, hi.length));
    demod = new Demodulator(fs, 1200, 2200, 1200);
    float mLo = mean(demod.processChunk(lo, lo.length));

    assertTrue(mHi > 0.7f, "Slightly high space tone should still demod near +1");
    assertTrue(mLo < -0.7f, "Slightly low mark tone should still demod near -1");
  }

  @Test
  @DisplayName("Input DC offset is rejected by pre‑BPF")
  void dcOffsetRejected() {
    int fs = 48_000;
    Demodulator demod = new Demodulator(fs, 1200, 2200, 1200);
    float[] t = generateAfskTone(fs, 2200, 0.2f);
    for (int i = 0; i < t.length; i++) t[i] += 0.1f; // add DC
    float[] y = demod.processChunk(t, t.length);
    int skip = 256;
    double sum = 0; int n = 0;
    for (int i = skip; i < y.length; i++) { sum += y[i]; n++; }
    float mu = (float)(sum / n);
    assertTrue(mu > 0.75f, "DC should not reduce normalized demod below ~0.75");
  }

  @Test
  @DisplayName("Transition delay ≈ FIR group delay")
  void transitionDelayMatchesGroupDelay() {
    int fs = 48_000;
    int toneUs = 10_000; // 10 ms per segment
    int repeats = 4;
    float[] x = generateAfskAlternatingTones(fs, toneUs, repeats);
    Demodulator demod = new Demodulator(fs, 1200, 2200, 1200);
    float[] y = demod.processChunk(x, x.length);
    // Find the first transition sample in input (10 ms) and when demod crosses zero after that.
    int transition = fs * toneUs / 1_000_000; // ~480 samples
    int zc = -1;
    for (int i = transition; i < y.length; i++) {
      if (Math.signum(y[i - 1]) != Math.signum(y[i])) {
        zc = i;
        break;
      }
    }
    assertTrue(zc > 0, "Need a zero crossing after the tone switch");
    // Expected delay ~ ((57-1)/2 + (35-1)/2)/fs = 45/fs ≈ 0.94ms at 48 kHz → ~45 samples
    int expectedSamples = 45;
    int measured = zc - transition;

    assertTrue(measured >= expectedSamples - 12 && measured <= expectedSamples + 20,
      "Measured transition delay (" + measured + " samples) should be near ~" + expectedSamples);
  }

  @Test
  @DisplayName("Mark/space detection holds with moderate white noise (SNR ≈ 10 dB)")
  void noisyTonesStayCorrectSign() {
    int fs = 48_000;
    float[] tMark = generateAfskTone(fs, 1200, 0.25f);
    float[] tSpace = generateAfskTone(fs, 2200, 0.25f);
    addWhiteNoise(tMark, 0.3f, 12345);   // ~10–12 dB SNR depending on window
    addWhiteNoise(tSpace, 0.3f, 12345);
    Demodulator d = new Demodulator(fs, 1200, 2200, 1200);
    float mMark  = mean(d.processChunk(tMark, tMark.length));
    d = new Demodulator(fs, 1200, 2200, 1200);
    float mSpace = mean(d.processChunk(tSpace, tSpace.length));
    assertTrue(mMark < -0.5f,  "Mark should remain negative on average under noise");
    assertTrue(mSpace >  0.5f, "Space should remain positive on average under noise");
  }

  /**
   * Generate a synthetic AFSK signal of alternating 1200/2200 Hz tones.
   *
   * @param sampleRate sample rate in Hz
   * @param toneDurationUs tone duration in microseconds
   * @param repeats number of tone transitions
   * @return signal buffer
   */
  private float[] generateAfskAlternatingTones(int sampleRate, int toneDurationUs, int repeats) {
    int samplesPerTone = sampleRate * toneDurationUs / 1_000_000;
    int totalSamples = samplesPerTone * repeats;
    float[] signal = new float[totalSamples];
    for (int i = 0; i < totalSamples; i++) {
      int toneIndex = i / samplesPerTone;
      int freq = (toneIndex % 2 == 0) ? 1200 : 2200;
      signal[i] = (float) Math.sin(2 * Math.PI * freq * i / sampleRate);
    }
    return signal;
  }

  private float[] generateAfskTone(int sampleRate, int freq, float seconds) {
    int n = (int)(sampleRate * seconds);
    float[] signal = new float[n];
    for (int i = 0; i < n; i++) {
      signal[i] = (float) Math.sin(2 * Math.PI * freq * i / sampleRate);
    }
    return signal;
  }

  private float mean(float[] x) {
    double sum = 0;
    for (float v : x) sum += v;
    return (float) (sum / x.length);
  }

  private static void addWhiteNoise(float[] x, float noiseRms, long seed) {
    java.util.Random rnd = new java.util.Random(seed);
    for (int i = 0; i < x.length; i++) {
      // Gaussian via Box–Muller
      double u1 = Math.max(1e-12, rnd.nextDouble());
      double u2 = rnd.nextDouble();
      double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
      x[i] += (float)(noiseRms * z);
    }
  }
}
