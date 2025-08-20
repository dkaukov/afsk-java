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

import java.util.function.BiConsumer;

import io.github.dkaukov.afsk.dsp.DdsOscillator;
import io.github.dkaukov.afsk.util.BitBuffer;

/**
 * AFSK modulator using a single DDS oscillator with phase continuity
 * and callback-based chunk emission. Supports non-integer samplesPerBit.
 */
public class Modulator {

  private final float samplesPerBit;
  private final DdsOscillator osc;
  private final float markFreq;
  private final float spaceFreq;

  /**
   * Create a new modulator instance.
   *
   * @param sampleRate Sampling rate in Hz
   * @param markFreq MARK tone frequency in Hz
   * @param spaceFreq SPACE tone frequency in Hz
   * @param baudRate Bit rate (e.g., 1200 for Bell 202)
   */
  public Modulator(float sampleRate, float markFreq, float spaceFreq, float baudRate) {
    this.samplesPerBit = sampleRate / baudRate;
    this.markFreq = markFreq;
    this.spaceFreq = spaceFreq;
    this.osc = new DdsOscillator(sampleRate, markFreq); // start with MARK
  }

  /**
   * Modulate the given bits into audio samples.
   *
   * @param bits BitBuffer containing bits to modulate
   * @param buffer Audio buffer to reuse for output chunks
   * @param chunkSize Number of samples per chunk to emit
   * @param callback Called with (buffer, validLength) after each filled chunk
   */
  public int modulate(BitBuffer bits, float[] buffer, int chunkSize, int chunkIndex,  BiConsumer<float[], Integer> callback) {
    float bitPhase = 0f;
    float bitEnd = 0f;
    for (int bit: bits) {
      osc.setFrequency(bit != 0 ? markFreq : spaceFreq);
      bitEnd += samplesPerBit;
      while (bitPhase < bitEnd) {
        osc.next(); bitPhase += 1.0f;
        buffer[chunkIndex++] = osc.cos();
        if (chunkIndex % chunkSize == 0) {
          callback.accept(buffer, chunkIndex);
          chunkIndex = 0;
        }
      }
    }
    return chunkIndex;
  }

  public void reset() {
    osc.reset();
  }
}
