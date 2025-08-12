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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class FrameVerifierTest {

  private static final Random RNG = new Random(0xC0FFEE);

  // --------------------------- Helpers ---------------------------

  private static byte encCallChar(char c) {
    // AX.25 stores callsign chars left-shifted by 1, LSB must be 0
    return (byte)((c & 0x7F) << 1);
  }

  private static void writeAddressBlock(byte[] dst, int off, String callsign, int ssid, boolean lastEa) {
    // callsign up to 6 chars [A-Z0-9], pad with spaces
    for (int i = 0; i < 6; i++) {
      char ch = (i < callsign.length()) ? callsign.charAt(i) : ' ';
      dst[off + i] = encCallChar(ch);
    }
    // SSID octet:
    // bit0 = EA (1 if last address block)
    // bits1..4 = SSID (0..15)
    // bits5..6 = set (0b11) per spec convention (0x60)
    // bit7 = C/H bit (we'll set 0)
    int ssidOctet = 0x60 | ((ssid & 0x0F) << 1) | (lastEa ? 1 : 0);
    dst[off + 6] = (byte)ssidOctet;
  }

  /** Build a minimal AX.25 UI frame with no digipeaters:
   *  [Dest(7) | Src(7) | 0x03 | 0xF0 | INFO...] || FCS(le)  */
  private static byte[] makeValidAprsUiFrame(byte[] infoAscii) {
    // header = 14 (addresses) + 2 (UI/PID)
    byte[] frame = new byte[16 + infoAscii.length + 2 /*FCS*/];

    // Destination "APRS  " SSID-0, EA=0 (not last)
    writeAddressBlock(frame, 0, "APRS  ", 0, false);
    // Source "NOCALL" SSID-0, EA=1 (last address block)
    writeAddressBlock(frame, 7, "NOCALL", 0, true);

    // Control/PID (UI, no L3)
    frame[14] = 0x03;
    frame[15] = (byte)0xF0;

    // INFO (ASCII)
    System.arraycopy(infoAscii, 0, frame, 16, infoAscii.length);

    // Append FCS (no-xorout API -> ones-complement on wire)
    int crc = Ax25Fcs.calculateFcs(Arrays.copyOf(frame, frame.length - 2));
    int fcs = (~crc) & 0xFFFF;
    frame[frame.length - 2] = (byte)(fcs & 0xFF);
    frame[frame.length - 1] = (byte)((fcs >>> 8) & 0xFF);

    // Sanity: CRC residue matches good residue
    assertEquals(Ax25Fcs.AX25_CRC_CORRECT, Ax25Fcs.calculateFcs(frame));
    return frame;
  }

  /** Start index (bytes) of the INFO field inside payload (excludes FCS). */
  private static int infoStartIndex(byte[] frameWithFcs) {
    // 2 address blocks (14) + control(1) + pid(1)
    return 16;
  }

  /** Generate random ASCII INFO (printable). */
  private static byte[] randAsciiInfo(int len, Random rng) {
    byte[] b = new byte[len];
    for (int i = 0; i < len; i++) {
      int v = 0x20 + rng.nextInt(0x7F - 0x20); // 0x20..0x7E
      b[i] = (byte)v;
    }
    return b;
  }

  /** Flip bits but ONLY within the INFO field (LSB-first in each byte). Positions are relative to INFO start. */
  private static void flipInfoBits(byte[] frame, int... infoBitPositions) {
    int infoByteStart = infoStartIndex(frame);
    for (int infoBit : infoBitPositions) {
      int byteInInfo = infoBit >>> 3;
      int bitInByte  = infoBit & 7;
      int byteIdx    = infoByteStart + byteInInfo;
      int mask       = 1 << bitInByte;
      frame[byteIdx] = (byte)((frame[byteIdx] & 0xFF) ^ mask);
    }
  }

  /** Total number of bits available in the INFO field (excludes FCS). */
  private static int infoBitCount(byte[] frame) {
    int infoBytes = (frame.length - 2) - infoStartIndex(frame);
    return Math.max(0, infoBytes * 8);
  }

  /** Rebuild a frame from a payload-without-FCS by appending a freshly computed FCS. */
  private static byte[] appendFreshFcs(byte[] payloadNoFcs) {
    byte[] rebuilt = Arrays.copyOf(payloadNoFcs, payloadNoFcs.length + 2);
    int crc = Ax25Fcs.calculateFcs(payloadNoFcs);
    int fcs = (~crc) & 0xFFFF;
    rebuilt[rebuilt.length - 2] = (byte)(fcs & 0xFF);
    rebuilt[rebuilt.length - 1] = (byte)((fcs >>> 8) & 0xFF);
    return rebuilt;
  }

  // --------------------------- Tests ---------------------------

  @Test
  void validFramePassesAndStrips() {
    byte[] info  = randAsciiInfo(100, RNG);
    byte[] frame = makeValidAprsUiFrame(info);

    FrameVerifier vf = new FrameVerifier();
    byte[] out = vf.verifyAndStrip(frame);

    assertNotNull(out, "valid frame should verify");
    // For a clean frame (no errors), verifyAndStrip should return the original payload (frame without FCS)
    assertArrayEquals(Arrays.copyOf(frame, frame.length - 2), out);
  }

  @RepeatedTest(20)
  void recoversSingleBitFlipInInfo() {
    byte[] info  = randAsciiInfo(120, RNG);
    byte[] frame = makeValidAprsUiFrame(info);

    int infoBits = infoBitCount(frame);
    int bit = RNG.nextInt(infoBits);
    flipInfoBits(frame, bit);

    FrameVerifier vf = new FrameVerifier();
    byte[] out = vf.verifyAndStrip(frame);

    assertNotNull(out, "should recover 1-bit flip");
    // Rebuild with fresh FCS and check residue:
    byte[] rebuilt = appendFreshFcs(out);
    assertEquals(Ax25Fcs.AX25_CRC_CORRECT, Ax25Fcs.calculateFcs(rebuilt));
  }

  @RepeatedTest(20)
  void recoversTwoBitFlipInInfo() {
    byte[] info  = randAsciiInfo(150, RNG);
    byte[] frame = makeValidAprsUiFrame(info);

    int infoBits = infoBitCount(frame);
    int a = RNG.nextInt(infoBits);
    int b;
    do { b = RNG.nextInt(infoBits); } while (b == a);

    flipInfoBits(frame, a, b);

    FrameVerifier vf = new FrameVerifier();
    byte[] out = vf.verifyAndStrip(frame);

    assertNotNull(out, "should recover 2-bit flip");

    // CRC-valid with the original FCS (identity not guaranteed for 2-bit):
    byte[] rebuilt = Arrays.copyOf(out, out.length + 2);
    rebuilt[rebuilt.length - 2] = frame[frame.length - 2];
    rebuilt[rebuilt.length - 1] = frame[frame.length - 1];
    assertEquals(Ax25Fcs.AX25_CRC_CORRECT, Ax25Fcs.calculateFcs(rebuilt));
  }

  @Test
  void doesNotRecoverThreeBitFlipInInfo() {
    byte[] info  = randAsciiInfo(120, RNG);
    byte[] frame = makeValidAprsUiFrame(info);

    int infoBits = infoBitCount(frame);
    int a = RNG.nextInt(infoBits);
    int b; do { b = RNG.nextInt(infoBits); } while (b == a);
    int c; do { c = RNG.nextInt(infoBits); } while (c == a || c == b);

    flipInfoBits(frame, a, b, c);

    FrameVerifier vf = new FrameVerifier();
    byte[] out = vf.verifyAndStrip(frame);

    assertNull(out, "3-bit flip should not be corrected by 1/2-bit solver");
  }

  @Test
  void lengthSweep_catchesIndexingBugs_overInfoLengths() {
    FrameVerifier vf = new FrameVerifier();

    for (int infoLen = 5; infoLen <= 250; infoLen += 7) {
      byte[] info  = randAsciiInfo(infoLen, RNG);
      byte[] frame = makeValidAprsUiFrame(info);

      // flip first INFO bit
      flipInfoBits(frame, 0);

      byte[] out = vf.verifyAndStrip(frame);
      assertNotNull(out, "failed at infoLen=" + infoLen);

      // CRC-validity with rebuilt FCS:
      byte[] rebuilt = appendFreshFcs(out);
      assertEquals(Ax25Fcs.AX25_CRC_CORRECT, Ax25Fcs.calculateFcs(rebuilt));
    }
  }

  @Test
  void singleKnownBit_inInfo_isRecovered_andMatchesInfluence() {
    int infoLen = 10;                  // INFO = spaces (ASCII & stable)
    byte[] info = new byte[infoLen];
    Arrays.fill(info, (byte)0x20);     // ' '
    byte[] frame = makeValidAprsUiFrame(info);

    int nBits = frame.length * 8;
    int infoStartBits = infoStartIndex(frame) * 8;
    int bitInInfo = 13;
    int bit = infoStartBits + bitInInfo;

    // flip that payload bit
    frame[bit >>> 3] ^= (byte)(1 << (bit & 7));

    int R = Ax25Fcs.calculateFcs(frame) ^ Ax25Fcs.AX25_CRC_CORRECT;
    int[] S = Ax25Fcs.buildInfluenceTable(nBits);
    assertEquals(S[bit], R, "syndrome must equal influence(bit)");

    FrameVerifier vf = new FrameVerifier();
    byte[] out = vf.verifyAndStrip(frame);
    assertNotNull(out);
  }

  @RepeatedTest(50)
  void fuzz_singleAndDoubleFlip_recoverable() {
    Random rng = new Random();
    int infoLen = 10 + rng.nextInt(200);
    byte[] info  = randAsciiInfo(infoLen, rng);
    byte[] frame = makeValidAprsUiFrame(info);

    int infoBits = infoBitCount(frame);
    int a = rng.nextInt(infoBits);
    boolean two = infoBits > 1 && rng.nextBoolean();
    int b = a;
    while (two && b == a) b = rng.nextInt(infoBits);

    byte[] corrupted = frame.clone();
    flipInfoBits(corrupted, a);
    if (two) flipInfoBits(corrupted, b);

    FrameVerifier vf = new FrameVerifier();
    byte[] out = vf.verifyAndStrip(corrupted);
    assertNotNull(out); // existence; CRC-validity covered elsewhere
  }

  @Test
  void fcsOnlyFlip_notRecoverable_whenSearchingPayloadOnly() {
    byte[] info  = randAsciiInfo(80, RNG);
    byte[] frame = makeValidAprsUiFrame(info);

    int nBits = frame.length * 8;
    int fcsBit0 = nBits - 16; // first bit of FCS region (LSB-first)
    byte[] corrupted = frame.clone();
    corrupted[fcsBit0 >>> 3] ^= (byte)(1 << (fcsBit0 & 7));

    assertNotEquals(Ax25Fcs.AX25_CRC_CORRECT, Ax25Fcs.calculateFcs(corrupted));
    FrameVerifier vf = new FrameVerifier();
    assertNull(vf.verifyAndStrip(corrupted));
  }
}
