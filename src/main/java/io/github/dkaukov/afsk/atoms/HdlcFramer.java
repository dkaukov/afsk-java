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
 * HDLC bitstream framer for AX.25-like protocols.
 * Encodes data with bit-stuffing and appends CRC and flag sequences.
 */
public class HdlcFramer {

  public static final byte FLAG = 0x7E; // HDLC frame delimiter
  private int oneCount;
  private final int leadFlagsCount;
  private final int tailFlagsCount;

  public HdlcFramer(int leadFlagsCount, int tailFlagsCount) {
    this.leadFlagsCount = leadFlagsCount;
    this.tailFlagsCount = tailFlagsCount;
  }

  /**
   * Frames a payload by adding CRC, bit-stuffing, and flag bytes.
   *
   * @param data Raw data (e.g., AX.25 frame without flags or CRC)
   * @return BitBuffer containing stuffed bits and surrounding flags
   */
  public BitBuffer frame(byte[] data) {
    // Preallocate buffer with estimated overhead
    BitBuffer bits = new BitBuffer((data.length + leadFlagsCount + tailFlagsCount + 2) * 10);
    oneCount = 0;
    // Start flag(s) (not stuffed)
    for (int i = 0; i < leadFlagsCount; i++) {
      writeRawByte(FLAG, bits);
    }
    // Payload with bit-stuffing
    for (byte b : data) {
      stuffByte(b, bits);
    }
    // Append CRC (little-endian)
    int crc = Ax25Fcs.calculateFcs(data) ^ 0xffff;
    stuffByte((byte) (crc & 0xFF), bits);
    stuffByte((byte) ((crc >> 8) & 0xFF), bits);
    // End flag(s) (not stuffed)
    for (int i = 0; i < tailFlagsCount; i++) {
      writeRawByte(FLAG, bits);
    }
    return bits;
  }

  /**
   * Writes a byte to the bitstream with HDLC bit-stuffing (LSB-first).
   *
   * @param b Byte to encode
   * @param out Output bit buffer
   */
  private void stuffByte(byte b, BitBuffer out) {
    for (int i = 0; i < 8; i++) {
      int bit = (b >> i) & 1;
      out.addBit(bit);
      if (bit == 1) {
        oneCount++;
        if (oneCount == 5) {
          out.addBit(0); // Stuff a 0 after five 1s
          oneCount = 0;
        }
      } else {
        oneCount = 0;
      }
    }
  }

  private void writeRawByte(byte b, BitBuffer out) {
    for (int i = 0; i < 8; i++) {
      int bit = (b >> i) & 1;
      out.addBit(bit);
    }
  }
}
