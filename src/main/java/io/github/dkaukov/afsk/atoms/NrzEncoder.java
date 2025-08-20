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

import io.github.dkaukov.afsk.util.BitBuffer;

/**
 * NRZ (Non-Return-to-Zero) encoder.
 * <p>
 * Encodes a logical bitstream (0 = no change, 1 = toggle) into
 * physical line levels (0 or 1), in-place using BitBuffer.
 */
public class NrzEncoder {

  private int lastBit = 0;

  /**
   * Encodes NRZ bits into NRZI line-encoded bits (in-place).
   * Each logical 1 causes a toggle in output.
   * Each logical 0 maintains previous output level.
   */
  public BitBuffer encode(BitBuffer bits) {
    for (int i = 0; i < bits.size(); i++) {
      int bit = bits.getBit(i);
      if (bit == 0) {
        lastBit ^= 1;  // toggle
      }
      bits.setBit(i, lastBit);  // output current level
    }
    return bits;
  }


  /**
   * Reset encoder state (useful between frames).
   */
  public void reset() {
    lastBit = 0;
  }
}
