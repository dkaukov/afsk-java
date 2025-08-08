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
    Demodulator demod = new Demodulator(sampleRate, 1200, 2200);
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
  @ValueSource(ints = {10_000, 5_000, 2_000, 1_000, 833, 832, 820})  // durations in microseconds
  void testShortToneDurations(int toneDurationMs) {
    int sampleRate = 48000;
    int repeats = 10;
    float[] signal = generateAfskAlternatingTones(sampleRate, toneDurationMs, repeats);
    Demodulator demod = new Demodulator(sampleRate, 1200, 2200);
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

}
