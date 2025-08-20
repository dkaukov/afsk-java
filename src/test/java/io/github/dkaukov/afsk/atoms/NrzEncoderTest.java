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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("NRZ ↔ NRZI Symmetry Tests")
public class NrzEncoderTest {

  private void roundTrip(BitBuffer original) {
    NrzEncoder encoder = new NrzEncoder();
    NrziDecoder decoder = new NrziDecoder();
    BitBuffer encoded = encoder.encode(original.copy()); // encoding is in-place, so copy
    BitBuffer decoded = decoder.decode(encoded.copy());  // decoding is also in-place
    assertEquals(original, decoded, "NRZ → NRZI → NRZ did not preserve the bitstream");
  }

  @Test
  @DisplayName("Alternating bit pattern")
  public void testAlternatingBits() {
    BitBuffer bits = new BitBuffer(16);
    for (int i = 0; i < 16; i++) {
      bits.addBit(i % 2);
    }
    roundTrip(bits);
  }

  @Test
  @DisplayName("All ones")
  public void testAllOnes() {
    BitBuffer bits = new BitBuffer(32);
    for (int i = 0; i < 32; i++) {
      bits.addBit(1);
    }
    roundTrip(bits);
  }

  @Test
  @DisplayName("All zeros")
  public void testAllZeros() {
    BitBuffer bits = new BitBuffer(32);
    for (int i = 0; i < 32; i++) {
      bits.addBit(0);
    }
    roundTrip(bits);
  }

  @Test
  @DisplayName("Random bitstream")
  public void testRandomBits() {
    BitBuffer bits = new BitBuffer(128);
    Random rnd = new Random(42);
    for (int i = 0; i < 128; i++) {
      bits.addBit(rnd.nextBoolean() ? 1 : 0);
    }
    roundTrip(bits);
  }

  @Test
  @DisplayName("Empty input")
  public void testEmpty() {
    BitBuffer bits = new BitBuffer(0);
    roundTrip(bits);
  }
}
