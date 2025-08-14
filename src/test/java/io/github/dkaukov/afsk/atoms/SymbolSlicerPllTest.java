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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import io.github.dkaukov.afsk.util.BitBuffer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class SymbolSlicerPllTest {

  /**
   * Tests slicing a long stable MARK signal (-1.0f), which should yield all 0s.
   */
  @Test
  @DisplayName("Stable MARK Signal – Decode All Zeros")
  void testStableMark() {
    SymbolSlicerPll slicer = new SymbolSlicerPll(48000, 1200);
    float[] markSignal = new float[401]; // 10 bits worth at 1200 baud, 48kHz
    // MARK = -1
    Arrays.fill(markSignal, -1.0f);
    BitBuffer bits = slicer.slice(markSignal);
    assertEquals(10, bits.size());
    for (int bit : bits) assertEquals(0, bit);
  }

  /**
   * Tests slicing a long stable SPACE signal (+1.0f), which should yield all 1s.
   */
  @Test
  @DisplayName("Stable SPACE Signal – Decode All Ones")
  void testStableSpace() {
    SymbolSlicerPll slicer = new SymbolSlicerPll(48000, 1200);
    float[] spaceSignal = new float[401];
    // SPACE = +1
    Arrays.fill(spaceSignal, 1.0f);
    BitBuffer bits = slicer.slice(spaceSignal);
    assertEquals(10, bits.size());
    for (int bit : bits) assertEquals(1, bit);
  }

  /**
   * Tests slicing a clean alternating MARK/SPACE pattern.
   * Verifies PLL tracks timing and captures symbol transitions.
   */
  @Test
  @DisplayName("Alternating MARK/SPACE – Decode Flipping Bits")
  void testAlternatingSymbols() {
    SymbolSlicerPll slicer = new SymbolSlicerPll(48000, 1200);
    float[] signal = new float[400 * 2]; // 20 symbols: alternating MARK/SPACE
    for (int i = 0; i < signal.length; i++) {
      signal[i] = (i / 40) % 2 == 0 ? -1.0f : 1.0f;
    }
    BitBuffer bits = slicer.slice(signal);
    assertTrue(bits.size() >= 15, "Should decode at least 15 bits");
    // Optional: check some alternation
    assertTrue(bits.getBit(0) != bits.getBit(1) || bits.getBit(1) != bits.getBit(2)); // etc.
  }

  /**
   * Tests slicing a clean alternating MARK/SPACE pattern.
   * Verifies PLL tracks timing and captures symbol transitions.
   */
  @Test
  @DisplayName("Alternating MARK/SPACE – Decode Flipping Bits")
  void testSync() {
    SymbolSlicerPll slicer = new SymbolSlicerPll(48000, 1200);
    float[] signal = new float[400 * 2]; // 20 symbols: alternating MARK/SPACE
    for (int i = 0; i < signal.length; i++) {
      signal[i] = (i / 41) % 2 == 0 ? -1.0f : 1.0f;
    }
    BitBuffer bits = slicer.slice(signal);
    assertTrue(bits.size() >= 15, "Should decode at least 15 bits");
    // Optional: check some alternation
    assertTrue(bits.getBit(0) != bits.getBit(1) || bits.getBit(1) != bits.getBit(2)); // etc.
  }

}
