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
package io.github.dkaukov.afsk.dsp;

import lombok.Getter;

/**
 * Simple one-pole low-pass IIR filter (exponential moving average form).
 * y[n] = y[n-1] + α * (x[n] - y[n-1])
 */
public class SinglePoleIIRLpf {

  @Getter
  private final float alpha;
  private float y;

  /**
   * Create an IIR low-pass filter from sample rate and cutoff frequency.
   *
   * @param sampleRate Sampling frequency in Hz
   * @param cutoffHz   Cutoff frequency (-3dB point) in Hz
   */
  public SinglePoleIIRLpf(float sampleRate, float cutoffHz) {
    this.alpha = 1f - (float) Math.exp(-2.0 * Math.PI * cutoffHz / sampleRate);
    this.y = 0f;
  }

  public SinglePoleIIRLpf(float alpha) {
    this.alpha = alpha;
    this.y = 0f;
  }

  /**
   * Filter one sample.
   *
   * @param x Input sample
   * @return Filtered sample
   */
  public float filter(float x) {
    y += alpha * (x - y);
    return y;
  }

  /**
   * Reset internal state.
   */
  public void reset() {
    y = 0f;
  }

}

