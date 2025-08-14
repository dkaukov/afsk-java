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

import java.util.Arrays;

/**
 * Transposed direct-form FIR (“FastFIR”).
 * - O(M) per sample, no modulo, cache-friendly
 * - b[] are taps, z[] is state (length = M-1)
 *
 * y[n] = b0*x[n] + z0
 * z0'  = z1 + b1*x[n]
 * z1'  = z2 + b2*x[n]
 * ...
 * zM-2'= b(M-1)*x[n]
 */
public final class FastFIR {
  private final float[] b; // taps
  private final float[] z; // state (M-1)

  public FastFIR(float[] taps) {
    if (taps == null || taps.length == 0) {
      throw new IllegalArgumentException("taps must be non-empty");
    }
    this.b = taps.clone();
    this.z = new float[Math.max(0, b.length - 1)];
  }

  /** Filter one sample. */
  public float filter(float x) {
    float y = b[0] * x + (z.length > 0 ? z[0] : 0f);
    for (int k = 1; k < b.length - 1; k++) {
      z[k - 1] = z[k] + b[k] * x;
    }
    if (b.length > 1) {
      z[b.length - 2] = b[b.length - 1] * x;
    }
    return y;
  }

  /** Clear internal state. */
  public void reset() {
    Arrays.fill(z, 0f);
  }

  public int length() {
    return b.length;
  }

  public float[] taps() {
    return b.clone();
  }
}
