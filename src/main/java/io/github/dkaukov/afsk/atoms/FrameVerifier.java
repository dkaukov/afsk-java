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

import java.util.Arrays;
import java.util.HashMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FrameVerifier {

  private final boolean requireAprsUi;
  private final boolean bitRecovery;

  private static final int MAX_BYTES = 360;
  private static final int[] INFLUENCE_TABLE = Ax25Fcs.buildInfluenceTable(MAX_BYTES * 8);

  public FrameVerifier(boolean requireAprsUi, boolean bitRecovery) {
    this.requireAprsUi = requireAprsUi;
    this.bitRecovery = bitRecovery;
  }

  public FrameVerifier() {
    this.requireAprsUi = true;
    this.bitRecovery = true;
  }

  public byte[] verifyAndStrip(byte[] frameWithFcs) {
    if (frameWithFcs == null || frameWithFcs.length < 9 || frameWithFcs.length > MAX_BYTES) {
      return null;
    }
    if (Ax25Fcs.calculateFcs(frameWithFcs) == AX25_CRC_CORRECT && passesAx25Sanity(frameWithFcs)) {
      return Arrays.copyOf(frameWithFcs, frameWithFcs.length - 2);
    }
    if (!bitRecovery) {
      return null; // no recovery requested
    }
    byte[] fixed = recover1or2(frameWithFcs);
    if (fixed != null && Ax25Fcs.calculateFcs(fixed) == AX25_CRC_CORRECT) {
      return Arrays.copyOf(fixed, fixed.length - 2);
    }
    return null;
  }

  /**
   * Calculates the syndrome of the given frame.
   * The syndrome is the XOR of the calculated FCS and the correct AX.25 CRC.
   *
   * @param frameWithFcs The frame including the FCS.
   * @return The syndrome value (0 if the frame is valid).
   */
  private int syndrome(byte[] frameWithFcs) {
    return Ax25Fcs.calculateFcs(frameWithFcs) ^ AX25_CRC_CORRECT; // zero when valid
  }

  /**
   * Flips a specific bit in the given frame.
   *
   * @param f   The frame to modify.
   * @param bit The index of the bit to flip.
   */
  private void flipBit(byte[] f, int bit) {
    int i = bit >>> 3, m = 1 << (bit & 7);
    f[i] = (byte)((f[i] & 0xFF) ^ m);          // LSB-first within byte
  }

  /**
   * Attempts to recover a frame with up to two bit errors.
   *
   * @param frameWithFcs The frame including the FCS.
   * @return The corrected frame if recoverable, or `null` if not.
   */
  private byte[] recover1or2(byte[] frameWithFcs) {
    final int nBits = frameWithFcs.length * 8;
    final int payloadBits = nBits - 16;      // exclude FCS
    if (payloadBits <= 0) {
      return null;
    }
    final int R = syndrome(frameWithFcs);
    if (R == 0) {
      return frameWithFcs;
    }
    // *** critical: slice offset into the prebuilt MAX table ***
    final int delta = (MAX_BYTES * 8) - nBits;
    if (delta < 0) {
      return null;              // frame longer than MAX (already guarded earlier)
    }
    // 1-bit
    for (int i = 0; i < payloadBits; i++) {
      if (INFLUENCE_TABLE[delta + i] == R) {
        byte[] out = frameWithFcs.clone();
        flipBit(out, i);
        if (passesAx25Sanity(out)) {
          return out;
        }
      }
    }
    // 2-bit
    HashMap<Integer, Integer> inv = new HashMap<>(payloadBits * 2);
    for (int j = 0; j < payloadBits; j++) {
      inv.putIfAbsent(INFLUENCE_TABLE[delta + j], j);
    }
    for (int i = 0; i < payloadBits; i++) {
      Integer j = inv.get(R ^ INFLUENCE_TABLE[delta + i]);
      if (j != null && j != i) {
        byte[] out = frameWithFcs.clone();
        flipBit(out, i);
        flipBit(out, j);
        if (passesAx25Sanity(out)) {
          return out;
        }
      }
    }
    return null;
  }

  /**
   * Checks if the given frame passes AX.25 sanity checks.
   * This includes validating addresses and optionally checking for APRS UI.
   *
   * @param frameWithFcs The frame including the FCS.
   * @return `true` if the frame passes sanity checks, `false` otherwise.
   */
  private boolean passesAx25Sanity(byte[] frameWithFcs) {
    if (frameWithFcs.length < 2) {
      return false;
    }
    int end = frameWithFcs.length - 2;
    int i = validateAx25Addresses(frameWithFcs, end);
    if (i < 0) {
      return false;
    }
    if (requireAprsUi) {
      if (i + 2 > end) {
        return false;
      }
      int control = frameWithFcs[i] & 0xFF;
      int pid = frameWithFcs[i + 1] & 0xFF;
      return control == 0x03 && pid == 0xF0;
    }
    return true;
  }

  /**
   * Validates the structure of the AX.25 address field in the frame.
   *
   * @param buf The full frame bytes.
   * @param end The index of the last byte to consider (typically frame.length-2 to ignore FCS).
   * @return The index of the first byte after the address field (start of Control field),
   *         or -1 if the address field is invalid.
   */
  static int validateAx25Addresses(byte[] buf, int end) {
    int i = 0;
    int blocks = 0;
    while (true) {
      if (i + 7 > end) {
        return -1; // truncated
      }
      // Callsign bytes: 6 chars <<1, LSB must be 0; after >>1 must be [A-Z0-9 ].
      for (int k = 0; k < 6; k++) {
        int b = buf[i + k] & 0xFF;
        if ((b & 0x01) != 0) {
          return -1; // LSB must be 0
        }
        int ch = (b >> 1) & 0x7F;
        boolean ok = (ch == 0x20) || (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'Z');
        if (!ok) {
          return -1;
        }
      }
      // SSID octet
      int ssid = buf[i + 6] & 0xFF;
      boolean last = (ssid & 0x01) != 0; // EA bit
      blocks++;
      i += 7;
      // MUST have at least destination + source:
      if (blocks == 1 && last) {
        return -1; // destination cannot be last
      }
      // Stop at EA=1 (last address block). Before that, enforce max total blocks.
      if (last) {
        break;
      }
      if (blocks >= 10) {
        return -1; // dest+src+8 digis max
      }
    }
    // Ensure we actually saw at least dest+src
    if (blocks < 2) {
      return -1;
    }
    // i now points to start of Control field
    return i;
  }

}


