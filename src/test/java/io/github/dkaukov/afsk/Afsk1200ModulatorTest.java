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
package io.github.dkaukov.afsk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import net.ab0oo.aprs.parser.APRSPacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Parameterized tests for Afsk1200Modulator with end-to-end loopback decoding.
 */
public class Afsk1200ModulatorTest {

  static Stream<Integer> sampleRates() {
    return Stream.of(11025, 22050, 44100, 48000);
  }

  @ParameterizedTest(name = "SampleRate = {0}")
  @MethodSource("sampleRates")
  @DisplayName("Loopback: Basic payload")
  public void testLoopbackEncodingDecoding(int sampleRate) {
    byte[] payload = new APRSPacket("TEST", "CQ", null, "Hello AFSK 1200".getBytes()).toAX25Frame();
    List<byte[]> decodedFrames = new ArrayList<>();
    Afsk1200Demodulator demodulator = new Afsk1200Demodulator(sampleRate, decodedFrames::add);
    Afsk1200Modulator modulator = new Afsk1200Modulator(sampleRate);
    float[] buffer = new float[64];
    modulator.modulate(payload, buffer, buffer.length, Duration.ofMillis(0), Duration.ofMillis(1), demodulator::addSamples);
    assertEquals(1, decodedFrames.size(), "Expected exactly one decoded frame");
    assertArrayEquals(payload, decodedFrames.get(0), "Decoded frame must match original payload");
  }

  @ParameterizedTest(name = "SampleRate = {0}")
  @MethodSource("sampleRates")
  @DisplayName("Loopback: Multiple frames in sequence")
  public void testMultipleFrames(int sampleRate) {
    List<byte[]> decodedFrames = new ArrayList<>();
    Afsk1200Demodulator demodulator = new Afsk1200Demodulator(sampleRate, decodedFrames::add);
    Afsk1200Modulator modulator = new Afsk1200Modulator(sampleRate);
    float[] buffer = new float[128];
    byte[] msg1 = new APRSPacket("TEST", "CQ", null, "First Frame".getBytes()).toAX25Frame();
    byte[] msg2 = new APRSPacket("TEST", "CQ", null, "Second Frame".getBytes()).toAX25Frame();
    modulator.modulate(msg1, buffer, buffer.length, Duration.ofMillis(1), Duration.ofMillis(2), demodulator::addSamples);
    modulator.modulate(msg2, buffer, buffer.length, Duration.ofMillis(1), Duration.ofMillis(2), demodulator::addSamples);
    assertEquals(2, decodedFrames.size(), "Expected two frames");
    assertArrayEquals(msg1, decodedFrames.get(0), "First frame mismatch");
    assertArrayEquals(msg2, decodedFrames.get(1), "Second frame mismatch");
  }

  @ParameterizedTest(name = "SampleRate = {0}")
  @MethodSource("sampleRates")
  @DisplayName("Loopback: Alternating bit pattern payload")
  public void testEdgePatternPayload(int sampleRate) {
    List<byte[]> decodedFrames = new ArrayList<>();
    Afsk1200Demodulator demodulator = new Afsk1200Demodulator(sampleRate, decodedFrames::add);
    Afsk1200Modulator modulator = new Afsk1200Modulator(sampleRate);
    float[] buffer = new float[128];
    byte[] pattern = new APRSPacket("TEST", "CQ", null, new byte[] {
      (byte) 0b01010101, (byte) 0b10101010, (byte) 0b11110000, (byte) 0b00001111,
      (byte) 0b01010101, (byte) 0b10101010, (byte) 0b11110000, (byte) 0b00001111,
    }).toAX25Frame();
    modulator.modulate(pattern, buffer, buffer.length, Duration.ofMillis(0), Duration.ofMillis(1), demodulator::addSamples);
    assertEquals(1, decodedFrames.size(), "Expected one decoded frame");
    assertArrayEquals(pattern, decodedFrames.get(0), "Pattern mismatch");
  }

  @ParameterizedTest(name = "SampleRate = {0}")
  @MethodSource("sampleRates")
  @DisplayName("Loopback: Empty payload (1-byte APRS)")
  public void testEmptyAprsPayload(int sampleRate) {
    byte[] payload = new APRSPacket("NOCALL", "CQ", null, new byte[] { 0 }).toAX25Frame();
    List<byte[]> decodedFrames = new ArrayList<>();
    Afsk1200Demodulator demodulator = new Afsk1200Demodulator(sampleRate, decodedFrames::add);
    Afsk1200Modulator modulator = new Afsk1200Modulator(sampleRate);
    float[] buffer = new float[64];
    modulator.modulate(payload, buffer, buffer.length, Duration.ofMillis(0), Duration.ofMillis(1), demodulator::addSamples);
    assertEquals(1, decodedFrames.size(), "Should decode empty APRS frame");
    assertArrayEquals(payload, decodedFrames.get(0), "Empty APRS frame mismatch");
  }
}
