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

import java.util.ArrayList;
import java.util.List;

public class HdlcDeframer {

  private static final int FLAG_PATTERN = 0b01111110;

  private int bitBuffer = 0;
  private int bitCount = 0;

  private final List<Integer> currentBits = new ArrayList<>();
  private int oneCount = 0;
  private boolean inFrame = false;

  public interface FrameListener {
    void onFrame(byte[] frame);
  }

  public void processBits(int[] bits, FrameListener listener) {
    for (int bit : bits) {
      // Shift in each bit (MSB first)
      bitBuffer = ((bitBuffer << 1) | bit) & 0xFF;
      bitCount++;
      // Check for 0x7E flag
      if (bitCount >= 8 && bitBuffer == FLAG_PATTERN) {
        if (inFrame && !currentBits.isEmpty()) {
          // Frame complete
          byte[] frame = bitsToBytes(currentBits);
          listener.onFrame(frame);
        }
        // Reset for next frame
        inFrame = true;
        currentBits.clear();
        oneCount = 0;
        continue;
      }
      if (!inFrame) {
        continue;
      }
      // Bit de-stuffing: after 5 ones, skip next 0
      if (bit == 1) {
        oneCount++;
      } else {
        if (oneCount == 5) {
          // Stuffed 0 — skip it
          oneCount = 0;
          continue;
        }
        oneCount = 0;
      }
      currentBits.add(bit);
    }
  }

  private byte[] bitsToBytes(List<Integer> bits) {
    List<Byte> result = new ArrayList<>();
    int currentByte = 0;
    int bitIndex = 0;
    for (int bit : bits) {
      currentByte |= (bit << bitIndex++);
      if (bitIndex == 8) {
        result.add((byte) currentByte);
        currentByte = 0;
        bitIndex = 0;
      }
    }
    byte[] out = new byte[result.size()];
    for (int i = 0; i < result.size(); i++) {
      out[i] = result.get(i);
    }
    return out;
  }

  public void reset() {
    bitBuffer = 0;
    bitCount = 0;
    oneCount = 0;
    inFrame = false;
    currentBits.clear();
  }
}

