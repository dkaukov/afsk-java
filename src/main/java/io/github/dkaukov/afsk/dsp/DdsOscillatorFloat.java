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

public class DdsOscillatorFloat {

  private static final float TWO_PI = (float) (2.0 * Math.PI);

  private final float oscPhaseStep;
  private float oscPhase = 0f;

  public DdsOscillatorFloat(float sampleRate, float frequency) {
    this.oscPhaseStep = TWO_PI * frequency / sampleRate;
  }

  public void reset() {
    oscPhase = 0f;
  }

  /**
   * Advance phase (if you want manual phase control).
   */
  public void next() {
    oscPhase += oscPhaseStep;
    if (oscPhase >= TWO_PI) {
      oscPhase -= TWO_PI;
    }
  }

  public float sin() {
    return (float) Math.sin(oscPhase);
  }

  public float cos() {
    return (float) Math.cos(oscPhase);
  }
}

