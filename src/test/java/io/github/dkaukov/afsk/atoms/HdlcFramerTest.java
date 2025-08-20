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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.dkaukov.afsk.util.BitBuffer;

@DisplayName("HDLC Framer/Deframer Symmetry Tests")
public class HdlcFramerTest {

  /**
   * Helper to perform round-trip encoding and decoding,
   * asserting that the decoded payload matches the original.
   */
  private void roundTrip(byte[] payload) {
    HdlcFramer framer = new HdlcFramer(1, 1);
    HdlcDeframer deFramer = new HdlcDeframer();
    BitBuffer encoded = framer.frame(payload);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    deFramer.processBits(encoded, frameWithFcs -> {
      try {
        out.write(Arrays.copyOf(frameWithFcs, frameWithFcs.length - 2));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    assertArrayEquals(payload, out.toByteArray(), "Framer/Deframer round-trip failed");
  }

  @Test
  @DisplayName("Simple payload round-trip")
  public void testSimplePayload() {
    roundTrip("0123456789: Hello".getBytes());
  }

  @Test
  @DisplayName("Payload with long run of 1s triggers bit stuffing")
  public void testBitStuffingPayload() {
    roundTrip(new byte[] {
      (byte) 0b11111111, (byte) 0b11111000,
      (byte) 0b11111111, (byte) 0b11111000,
      (byte) 0b11111111, (byte) 0b11111000,
      (byte) 0b11111111, (byte) 0b11111000,
      (byte) 0b11111111, (byte) 0b11111000,
    });
  }

  @Test
  @DisplayName("Payload contains 0x7E flag byte")
  public void testPayloadWithFlagByte() {
    roundTrip(new byte[] {
      0x7E, 0x01, 0x7E,
      0x7E, 0x01, 0x7E,
      0x7E, 0x01, 0x7E,
    });
  }

  @Test
  @DisplayName("Payload ends with five consecutive 1s")
  public void testTrailingStuffedBit() {
    roundTrip(new byte[] {
      (byte) 0b11111000,
      (byte) 0b11111000,
      (byte) 0b11111000,
      (byte) 0b11111000,
      (byte) 0b11111000,
      (byte) 0b11111000,
      (byte) 0b11111000,
      (byte) 0b11111000,
      (byte) 0b11111000,
      (byte) 0b11111000,
    });
  }

  @Test
  @DisplayName("All-zero payload")
  public void testAllZeroPayload() {
    roundTrip(new byte[] {
      0x00, 0x00, 0x00,
      0x00, 0x00, 0x00,
      0x00, 0x00, 0x00,
    });
  }

  @Test
  @DisplayName("Alternating bits payload")
  public void testAlternatingBitsPayload() {
    roundTrip(new byte[] {
      (byte) 0b10101010, (byte) 0b01010101,
      (byte) 0b10101010, (byte) 0b01010101,
      (byte) 0b10101010, (byte) 0b01010101,
      (byte) 0b10101010, (byte) 0b01010101,
      (byte) 0b10101010, (byte) 0b01010101,
      (byte) 0b10101010, (byte) 0b01010101,
      (byte) 0b10101010, (byte) 0b01010101,
    });
  }

  @Test
  @DisplayName("Random 256-byte payload")
  public void testRandomPayload() {
    byte[] data = new byte[256];
    new Random(42).nextBytes(data);
    roundTrip(data);
  }
}
