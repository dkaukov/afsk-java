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

import java.io.ByteArrayOutputStream;

import io.github.dkaukov.afsk.util.BitBuffer;

public class HdlcDeframer {

  private static final byte FLAG = 0x7E; // 0b01111110
  private static final int  ABORT_ONES = 7;        // HDLC abort threshold

  // Sliding 8-bit window to find flags.
  private byte flagWindow = 0;

  // Frame assembly state.
  private boolean inFrame = false;
  private int oneRun = 0;        // count of consecutive '1' bits
  private int bitPos = 0;        // 0..7 (LSB-first)
  private int currentByte = 0;
  private final ByteArrayOutputStream frame = new ByteArrayOutputStream(512);

  public interface FrameListener {
    void onFrame(byte[] frame);
  }

  public void processBits(BitBuffer bits, FrameListener listener) {
    for (int bit : bits) {
      flagWindow = (byte) ((flagWindow << 1) | bit);
      if (flagWindow == FLAG) {
        if (inFrame && bitPos == 7 && frame.size() >= 9) {
          listener.onFrame(frame.toByteArray());
        }
        startNewFrame();
        continue;
      }
      if (inFrame) {
        // Bit de-stuffing: after 5 ones, skip next 0
        if (bit == 1) {
          if (++oneRun >= ABORT_ONES) {
            dropFrame();
            continue;
          }
        } else if (oneRun == 5) {
          oneRun = 0;
          continue;
        } else {
          oneRun = 0;
        }
        addBit(bit);
      }
    }
  }

  private void addBit(int bit) {
    currentByte |= (bit << bitPos);
    bitPos++;
    if (bitPos == 8) {
      frame.write((byte) currentByte);
      currentByte = 0;
      bitPos = 0;
    }
  }

  public void reset() {
    flagWindow = 0;
    inFrame = false;
    oneRun = 0;
    bitPos = 0;
    currentByte = 0;
    frame.reset();
  }

  private void startNewFrame() {
    inFrame = true;
    oneRun = 0;
    bitPos = 0;
    currentByte = 0;
    frame.reset();
  }

  private void dropFrame() {
    inFrame = false;
    oneRun = 0;
    bitPos = 0;
    currentByte = 0;
    frame.reset();
  }
}
