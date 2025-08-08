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

import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import io.github.dkaukov.afsk.atoms.Ax25Fcs.BitCorrectionUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FrameVerifier {

  public byte[] verifyAndStrip(byte[] frameWithFcs) {
    if (frameWithFcs == null || frameWithFcs.length < 5) {
      return null;
    }
    byte[] frame = Arrays.copyOf(frameWithFcs, frameWithFcs.length - 2);
    int actualCrc = Ax25Fcs.calculateFcs(frame);
    int expectedCrc = (frameWithFcs[frameWithFcs.length - 2] & 0xFF) |
      ((frameWithFcs[frameWithFcs.length - 1] & 0xFF) << 8);
    if (actualCrc == expectedCrc) {
      return frame;
    }
    if (tryRecoverByBitFlips(frame, expectedCrc)) {
      return frame;
    }
    return null;
  }

  private void fix(byte[] frame,  Map<Integer, Byte> corrections) {
    for (Entry<Integer, Byte> corr : corrections.entrySet()) {
      frame[corr.getKey()] ^= corr.getValue();
    }
  }

  private boolean tryRecoverByBitFlips(byte[] frame, int expectedCrc) {
    int bitCount = frame.length * 8;
    // 1-bit flips (fast, can stay single-threaded)
    for (int bit = bitCount - 1; bit >= 0; bit--) {
      Map<Integer, Byte> corr = BitCorrectionUtil.fromBits(bit);
      if (Ax25Fcs.calculateFcs(frame, corr) == expectedCrc) {
        fix(frame, corr);
        return true;
      }
    }
    // 2-bit flips (parallel)
    var result = new AtomicReference<Map<Integer, Byte>>(null);
    IntStream.range(0, bitCount).parallel().forEach(bit1 -> {
      for (int bit2 = bit1; bit2 < bitCount; bit2++) {
        if (result.get() != null) {
          return; // already found
        }
        if (bit1 == bit2) {
          continue;
        }
        Map<Integer, Byte> corr = BitCorrectionUtil.fromBits(bit1, bit2);
        if (Ax25Fcs.calculateFcs(frame, corr) == expectedCrc) {
          result.compareAndSet(null, corr);
          return;
        }
      }
    });
    if (result.get() != null) {
      fix(frame, result.get());
      return true;
    }
    // 3-bit flips (expensive, also parallelized)
    result.set(null);
    IntStream.range(0, bitCount).parallel().forEach(bit1 -> {
      for (int bit2 = bit1; bit2 < bitCount; bit2++) {
        for (int bit3 = bit2; bit3 < bitCount; bit3++) {
          if (result.get() != null) {
            return;
          }
          if (bit1 == bit2 || bit2 == bit3) {
            continue;
          }
          Map<Integer, Byte> corr = BitCorrectionUtil.fromBits(bit1, bit2, bit3);
          if (Ax25Fcs.calculateFcs(frame, corr) == expectedCrc) {
            result.compareAndSet(null, corr);
            return;
          }
        }
      }
    });
    if (result.get() != null) {
      fix(frame, result.get());
      return true;
    }
    return false;
  }
}


