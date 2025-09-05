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
 * Single-pole high-pass (de-tilt) filter.
 * y[n] = α * (y[n-1] + x[n] - x[n-1])
 */
public class SinglePoleIIRHpf {
  @Getter
  private final float alpha;
  private float y, xPrev;

  public SinglePoleIIRHpf(float sampleRate, float cutoffHz) {
    this.alpha = (float) (Math.exp(-2.0 * Math.PI * cutoffHz / sampleRate));
    this.y = 0f;
    this.xPrev = 0f;
  }

  public float filter(float x) {
    float out = alpha * (y + x - xPrev);
    xPrev = x;
    y = out;
    return out;
  }

  public void reset() {
    y = 0f;
    xPrev = 0f;
  }
}
