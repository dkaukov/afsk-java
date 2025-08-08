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

import io.github.dkaukov.afsk.dsp.DdsOscillator;
import io.github.dkaukov.afsk.dsp.FIRLowPassFilter;
import lombok.Getter;

/**
 * AFSK (Audio Frequency Shift Keying) Demodulator.
 *
 * Uses a center-frequency oscillator to mix incoming signal to baseband, followed by low-pass filtering
 * and FM demodulation via phase difference (deltaQ). Outputs are normalized to approximately ±1.
 */
public class Demodulator {

  public static final int LPF_NUM_TAPS = 70;

  @Getter
  private final float sampleRate;

  private final DdsOscillator oscillator;
  private final FIRLowPassFilter iFilter;
  private final FIRLowPassFilter qFilter;

  private float prevI = 0f;
  private float prevQ = 0f;

  public Demodulator(float sampleRate, float markFreq, float spaceFreq) {
    this.sampleRate = sampleRate;
    float centerFreq = (markFreq + spaceFreq) / 2f;
    this.oscillator = new DdsOscillator(sampleRate, centerFreq);
    //this.iFilter = new FIRLowPassFilter(LPF_NUM_TAPS, (spaceFreq - centerFreq), sampleRate);
    //this.qFilter = new FIRLowPassFilter(LPF_NUM_TAPS, (spaceFreq - centerFreq), sampleRate);
    this.iFilter = new FIRLowPassFilter(LPF_NUM_TAPS, 1.027f, 40, 0,  0); // RRC filter
    this.qFilter = new FIRLowPassFilter(LPF_NUM_TAPS, 1.027f, 40, 0, 0); // RRC filter
  }

  /**
   * Efficiently demodulate a chunk of audio samples.
   *
   * @param samples PCM audio chunk (-1.0 to +1.0 floats)
   * @return array of demodulated values (-1.0 to +1.0 approx)
   */
  public float[] processChunk(float[] samples, int length) {
    float[] output = new float[length];
    for (int i = 0; i < length; i++) {
      float sample = samples[i];
      // DDS phase advance
      oscillator.next();
      // Mix to baseband
      float mixedI = sample * oscillator.cos();
      float mixedQ = sample * oscillator.sin();
      // Low-pass filter
      float filteredI = iFilter.filter(mixedI);
      float filteredQ = qFilter.filter(mixedQ);
      // Phase change
      float deltaQ = filteredQ * prevI - filteredI * prevQ;
      float magSq = filteredI * filteredI + filteredQ * filteredQ;
      output[i] = magSq == 0.0f ? 0.0f : deltaQ / magSq;
      prevI = filteredI;
      prevQ = filteredQ;
    }
    return output;
  }

  /**
   * Optional: process a single sample at a time (less efficient).
   */
  public float processSample(float sample) {
    return processChunk(new float[]{sample}, 1)[0];
  }
}
