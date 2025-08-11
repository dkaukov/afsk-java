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
import io.github.dkaukov.afsk.dsp.FastFIR;
import io.github.dkaukov.afsk.dsp.FilterDesignUtils;
import lombok.Getter;

/**
 * AFSK (Audio Frequency Shift Keying) Demodulator.
 *
 * Uses a center-frequency oscillator to mix incoming signal to baseband, followed by low-pass filtering
 * and FM demodulation via phase difference (deltaQ). Outputs are normalized to approximately ±1.
 */
public class Demodulator {


  @Getter
  private final float sampleRate;

  private final DdsOscillator oscillator;
  private final FastFIR iFilter;
  private final FastFIR qFilter;
  private final FastFIR bpf;
  private final float normGain;

  private float prevI = 0f;
  private float prevQ = 0f;


  public Demodulator(float sampleRate, float markFreq, float spaceFreq) {
    this.sampleRate = sampleRate;
    float centerFreq = (markFreq + spaceFreq) / 2f;
    float dev = 0.5f * (spaceFreq - markFreq);
    this.oscillator = new DdsOscillator(sampleRate, centerFreq);
    this.bpf = new FastFIR(FilterDesignUtils.designBandPassKaiser(57, markFreq,  spaceFreq, sampleRate, 30));
    float[] lpf = FilterDesignUtils.designLowPassHamming(35, dev, sampleRate);
    iFilter = new FastFIR(lpf);
    qFilter = new FastFIR(lpf);
    this.normGain = 1.0f / (2f * (float)Math.PI * (dev / sampleRate));
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
      float sample = bpf.filter(samples[i]);
      // DDS phase advance
      oscillator.next();
      // Mix to baseband
      float mixedI = sample * oscillator.cos();
      float mixedQ = -sample * oscillator.sin();
      // Low-pass filter
      float filteredI = iFilter.filter(mixedI);
      float filteredQ = qFilter.filter(mixedQ);
      // Phase change
      float deltaQ = filteredQ * prevI - filteredI * prevQ;
      float magSq = filteredI * filteredI + filteredQ * filteredQ;
      float demod = (magSq < 0.0001f) ? 0.0f : (deltaQ / magSq) * normGain;
      // Apply dead-zone
      if (Math.abs(demod) < 0.006) {
        demod = 0.00001f;
      }
      output[i] = demod;
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
