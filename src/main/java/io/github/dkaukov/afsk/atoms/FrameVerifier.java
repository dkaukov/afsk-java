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
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import io.github.dkaukov.afsk.atoms.Ax25Fcs.BitCorrection;
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

  private void fix(byte[] frame, List<BitCorrection> corrections) {
    for (BitCorrection corr : corrections) {
      frame[corr.byteIndex()] ^= (byte) corr.xorMask();
    }
  }

  private boolean tryRecoverByBitFlips(byte[] frame, int expectedCrc) {
    int bitCount = frame.length * 8;
    // 1-bit flips (fast, can stay single-threaded)
    for (int bit = bitCount - 1; bit >= 0; bit--) {
      List<BitCorrection> corr = List.of(BitCorrection.fromBit(bit));
      if (Ax25Fcs.calculateFcs(frame, corr) == expectedCrc) {
        fix(frame, corr);
        return true;
      }
    }
    // 2-bit flips (parallel)
    var result = new AtomicReference<List<BitCorrection>>(null);
    IntStream.range(0, bitCount).parallel().forEach(bit1 -> {
      for (int bit2 = bit1; bit2 < bitCount; bit2++) {
        if (result.get() != null) {
          return; // already found
        }
        List<BitCorrection> corr = List.of(BitCorrection.fromBit(bit1), BitCorrection.fromBit(bit2));
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
          List<BitCorrection> corr = List.of(
            BitCorrection.fromBit(bit1),
            BitCorrection.fromBit(bit2),
            BitCorrection.fromBit(bit3)
          );
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


