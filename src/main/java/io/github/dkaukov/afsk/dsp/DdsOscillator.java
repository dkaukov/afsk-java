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

/**
 * Direct Digital Synthesis (DDS) oscillator for generating sine and cosine waveforms.
 *
 * This class uses a precomputed sine table to efficiently generate samples at a specified frequency.
 * The oscillator can be reset and advances its phase on each call to `next()`.
 */
public class DdsOscillator {

  private static final int PHASE_BITS = 32;
  private static final int TABLE_BITS = 10;
  private static final int TABLE_SIZE = 1 << TABLE_BITS;
  private static final int TABLE_MASK = TABLE_SIZE - 1;
  private static final int COS_SHIFT = TABLE_SIZE / 4; // 90° phase shift

  private final float[] sineTable = new float[TABLE_SIZE];
  private int phase = 0;
  private int index = 0;
  private final int phaseStep;

  public DdsOscillator(float sampleRate, float freq) {
    // Precompute sine table
    for (int i = 0; i < TABLE_SIZE; i++) {
      sineTable[i] = (float) Math.sin(2.0 * Math.PI * i / TABLE_SIZE);
    }
    // Fixed-point step
    this.phaseStep = (int) ((freq * (1L << PHASE_BITS)) / sampleRate);
  }

  public void reset() {
    phase = 0;
    index = 0;
  }

  /**
   * Advance the oscillator.
   */
  public void next() {
    phase += phaseStep;
    index = (phase >>> (PHASE_BITS - TABLE_BITS)) & TABLE_MASK;
  }

  public float sin() {
    return sineTable[index];
  }

  public float cos() {
    return sineTable[(index + COS_SHIFT) & TABLE_MASK];
  }
}
