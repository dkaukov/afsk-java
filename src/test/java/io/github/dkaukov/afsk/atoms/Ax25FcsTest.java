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

import static io.github.dkaukov.afsk.atoms.Ax25Fcs.AX25_CRC_CORRECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

public class Ax25FcsTest {

  private static final Random RNG = new Random(0xBEEF);

  /** Helper: make a valid frame: payload || FCS(le), using no-xorout CRC. */
  private static byte[] makeValidFrame(byte[] payload) {
    int crc = Ax25Fcs.calculateFcs(payload);          // no xorout
    int fcs = (~crc) & 0xFFFF;                        // on-air (little-endian)
    byte[] frame = new byte[payload.length + 2];
    System.arraycopy(payload, 0, frame, 0, payload.length);
    frame[frame.length - 2] = (byte) (fcs & 0xFF);
    frame[frame.length - 1] = (byte) ((fcs >>> 8) & 0xFF);
    // sanity: full-frame residue is the AX.25/PPP good residue
    assertEquals(AX25_CRC_CORRECT, Ax25Fcs.calculateFcs(frame));
    return frame;
  }

  /** Flip a single bit (LSB-first within each byte). */
  private static void flipBit(byte[] buf, int bitIndex) {
    int byteIdx = bitIndex >>> 3;
    int mask = 1 << (bitIndex & 7);
    buf[byteIdx] = (byte) ((buf[byteIdx] & 0xFF) ^ mask);
  }

  @Test
  void goodResidue_onValidFrame() {
    byte[] payload = new byte[100];
    RNG.nextBytes(payload);
    byte[] frame = makeValidFrame(payload);
    assertEquals(AX25_CRC_CORRECT, Ax25Fcs.calculateFcs(frame));
  }

  @Test
  void influence_matches_singleBitSyndrome_onZeroPayload() {
    int payloadLen = 40; // bytes
    byte[] payload = new byte[payloadLen]; // all zeros
    byte[] frame = makeValidFrame(payload);
    int nBits = frame.length * 8;
    int payloadBits = nBits - 16;
    int[] S = Ax25Fcs.buildInfluenceTable(nBits);
    // pick a few deterministic payload bit positions
    int[] bits = new int[] { 0, 1, 7, 8, 13, payloadBits - 1 };
    for (int bit : bits) {
      assertTrue(bit >= 0 && bit < payloadBits, "bit inside payload");
      byte[] corrupted = frame.clone();
      flipBit(corrupted, bit);
      int syndrome = Ax25Fcs.calculateFcs(corrupted) ^ AX25_CRC_CORRECT; // zero when valid
      assertEquals(S[bit], syndrome,
        "syndrome must equal influence(bit) for bit=" + bit);
    }
  }

  @Test
  void influenceXor_matches_twoBitSyndrome_onZeroPayload() {
    int payloadLen = 60; // bytes
    byte[] payload = new byte[payloadLen]; // all zeros
    byte[] frame = makeValidFrame(payload);

    int nBits = frame.length * 8;
    int payloadBits = nBits - 16;
    int[] S = Ax25Fcs.buildInfluenceTable(nBits);

    int a = 7, b = 77;
    assertTrue(a < payloadBits && b < payloadBits);

    byte[] corrupted = frame.clone();
    flipBit(corrupted, a);
    flipBit(corrupted, b);

    int syndrome = Ax25Fcs.calculateFcs(corrupted) ^ AX25_CRC_CORRECT;
    assertEquals(S[a] ^ S[b], syndrome, "syndrome must equal S[a]^S[b]");
  }

  @Test
  void sweep_lengths_and_positions_singleBit() {
    for (int payloadLen = 1; payloadLen <= 64; payloadLen += 7) {
      byte[] payload = new byte[payloadLen]; // zeros for clarity
      byte[] frame = makeValidFrame(payload);

      int nBits = frame.length * 8;
      int payloadBits = nBits - 16;
      int[] S = Ax25Fcs.buildInfluenceTable(nBits);

      // try first, middle-ish, and last payload bit
      int[] testBits = new int[] {
        0,
        Math.max(0, payloadBits / 2),
        payloadBits - 1
      };

      for (int bit : testBits) {
        byte[] corrupted = frame.clone();
        flipBit(corrupted, bit);

        int syndrome = Ax25Fcs.calculateFcs(corrupted) ^ AX25_CRC_CORRECT;
        assertEquals(S[bit], syndrome,
          "len=" + payloadLen + "B, bit=" + bit + " influence mismatch");
      }
    }
  }

  @Test
  void residueAndOnAirFcs_roundtrip() {
    byte[] payload = new byte[123];
    new Random(1).nextBytes(payload);

    int crcNoXor = Ax25Fcs.calculateFcs(payload);
    int fcsOnAir = (~crcNoXor) & 0xFFFF;

    byte[] frame = new byte[payload.length + 2];
    System.arraycopy(payload, 0, frame, 0, payload.length);
    frame[frame.length - 2] = (byte)(fcsOnAir & 0xFF);
    frame[frame.length - 1] = (byte)((fcsOnAir >>> 8) & 0xFF);

    assertEquals(Ax25Fcs.AX25_CRC_CORRECT, Ax25Fcs.calculateFcs(frame));
  }

  @Test
  void linearityProperty_singleVsXor() {
    int payloadLen = 40;
    byte[] payload = new byte[payloadLen];
    byte[] frame = makeValidFrame(payload);

    int nBits = frame.length * 8, payloadBits = nBits - 16;
    int[] S = Ax25Fcs.buildInfluenceTable(nBits);

    int a = 5, b = 29;
    assertTrue(a < payloadBits && b < payloadBits);

    byte[] fA = frame.clone(); flipBit(fA, a);
    byte[] fB = frame.clone(); flipBit(fB, b);
    byte[] fAB = frame.clone(); flipBit(fAB, a); flipBit(fAB, b);

    int syndA  = Ax25Fcs.calculateFcs(fA)  ^ Ax25Fcs.AX25_CRC_CORRECT;
    int syndB  = Ax25Fcs.calculateFcs(fB)  ^ Ax25Fcs.AX25_CRC_CORRECT;
    int syndAB = Ax25Fcs.calculateFcs(fAB) ^ Ax25Fcs.AX25_CRC_CORRECT;

    assertEquals(S[a], syndA);
    assertEquals(S[b], syndB);
    assertEquals(syndA ^ syndB, syndAB);
    assertEquals(S[a] ^ S[b], syndAB);
  }

  @Test
  void prebuiltMaxTable_sliceMatchesPerN() {
    // Suppose production prebuilds at MAX_BYTES=360
    final int MAX_BITS = 360 * 8;
    final int[] prebuilt = Ax25Fcs.buildInfluenceTable(MAX_BITS);

    for (int payloadLen = 1; payloadLen <= 64; payloadLen += 5) {
      byte[] frame = makeValidFrame(new byte[payloadLen]);
      int nBits = frame.length * 8;
      int delta = MAX_BITS - nBits;

      int[] perN = Ax25Fcs.buildInfluenceTable(nBits);
      for (int i = 0; i < nBits - 16; i++) { // payload bits only
        assertEquals(perN[i], prebuilt[delta + i], "mismatch at len=" + payloadLen + " i=" + i);
      }
    }
  }

  @Test
  void zeroAndTinyPayloads_work() {
    for (int len : new int[]{0,1,2}) {
      byte[] payload = new byte[len];
      byte[] frame = makeValidFrame(payload);
      assertEquals(Ax25Fcs.AX25_CRC_CORRECT, Ax25Fcs.calculateFcs(frame));

      // flip one payload bit if any payload bits exist
      if (len > 0) {
        byte[] corrupted = frame.clone();
        flipBit(corrupted, 0);
        int synd = Ax25Fcs.calculateFcs(corrupted) ^ Ax25Fcs.AX25_CRC_CORRECT;
        int[] S = Ax25Fcs.buildInfluenceTable(corrupted.length*8);
        assertEquals(S[0], synd);
      }
    }
  }

}
