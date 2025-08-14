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
 * NRZI (Non-Return-to-Zero Inverted) decoder.
 * Converts NRZI-encoded bits into logical bits.
 *
 * NRZI encoding uses a transition to indicate a '1' and no transition for '0'.
 * This decoder assumes the first bit is '0' if not specified.
 */
public class NrziDecoder {

  private int lastBit = 0;

  /**
   * Decode a chunk of NRZI-encoded bits.
   * @param nrziBits Input array (physical line level), 0 or 1
   * @return Decoded bitstream (logical bits), 0 or 1
   */
   public BitBuffer decode(BitBuffer nrziBits) {
    //BitBuffer result = new BitBuffer(nrziBits.size());
    for (int i = 0; i < nrziBits.size(); i++) {
      int current = nrziBits.getBit(i);
      nrziBits.setBit(i, (current == lastBit) ? 1 : 0);
      lastBit = current;
    }
    return nrziBits;
  }

  /**
   * Reset decoder state (useful between frames).
   */
  public void reset() {
    lastBit = 0;
  }
}

