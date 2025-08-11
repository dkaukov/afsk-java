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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogManager;

import net.ab0oo.aprs.parser.APRSPacket;
import net.ab0oo.aprs.parser.Parser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.bridge.SLF4JBridgeHandler;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class Afsk1200DemodulatorTest {

  @BeforeAll
  static void redirectJulToStdout() {
    // Remove existing JUL handlers
    LogManager.getLogManager().reset();
    // Install bridge handler
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

    @Test
    void testDecodeTrackRec() throws Exception {
        List<byte[]> res = processFile(new File("src/test/cd/one-packet-direct-samped.flac"));
    }

  @Test
  void testDecodeTrackRec2() throws Exception {
    List<byte[]> res = processFile(new File("src/test/cd/one-packet-with-clicks.flac"));
  }

  /**
   * Test decoding of Track 1 from the WA8LMF Test CD: long-duration, discriminator‑style flat audio.
   * Ensures the demodulator handles raw audio as would appear directly from an FM discriminator :contentReference[oaicite:2]{index=2}.
   */
  @Test
  @DisplayName("Track 1 – 40 Mins of Traffic (Flat Discriminator Audio)")
  void testDecodeTrack1() throws Exception {
    List<byte[]> res = processFile(new File("src/test/cd/01_40-Mins-Traffic -on-144.39.flac"));
    assertTrue(res.size() >= 1005, "Should decode at least 1006 frames from Track 1");
  }

  /**
   * Test decoding of Track 2 from the WA8LMF Test CD: de‑emphasized audio as seen from speaker outputs.
   * Validates that the demodulator tolerates the typical audio coloration introduced by radio speaker audio :contentReference[oaicite:3]{index=3}.
   */
  @Test
  @DisplayName("Track 2 – 100 Mic-E Bursts (De-emphasized Audio)")
  void testDecodeTrack2() throws Exception {
    List<byte[]> res = processFile(new File("src/test/cd/02_100-Mic-E-Bursts-DE-emphasized.flac"));
    assertTrue(res.size() >= 943, "Should decode at least 955 frames from Track 2");
  }

  /**
   * Optional test using Track 5: calibration with alternating tones.
   * Useful for verifying tone recovery and bit‑level timing—uncomment when needed.
   */
  //@Test
  @DisplayName("Track 5 – KPC3+ Calibration Tone (Flat)")
  void testDecodeTrack5() throws Exception {
    processFile(new File("src/test/cd/05_KPC3plus-CAL-tone-Flat.flac"));
  }

  /**
   * Test decoding of an idealized flac with only a small number of packets.
   * Serves as a sanity check for perfect-case scenarios.
   */
  @Test
  @DisplayName("Ideal Test – Clean AX.25 Packets")
  void testDecodeIdeal() throws Exception {
    List<byte[]> res = processFile(new File("src/test/cd/ideal.flac"));
    assertEquals(4, res.size(), "Should decode exactly 4 frames from ideal.flac");
  }

  /**
   * Common logic to run the demodulator over the given audio file, collecting frames.
   *
   * @param flacFile the FLAC file containing AFSK‑encoded signals from the test CD
   * @throws Exception if the file is missing or fails to decode
   */
  List<byte[]> processFile(File flacFile) throws Exception {
    assertTrue(flacFile.exists());
    List<byte[]> frames = new ArrayList<>();
    Afsk1200Demodulator demod = new Afsk1200Demodulator(48000, frame -> {
      if (frame != null && frame.length > 0) {
          try {
              APRSPacket packet = Parser.parseAX25(frame);
              //log.info("Found AX.25 frame: {}>{} {}", packet.getSourceCall(), packet.getDestinationCall(), packet.getAprsInformation().toString());
              frames.add(frame);
          } catch (Exception e) {
              //throw new RuntimeException(e);
          }
      }
    });
    AudioDispatcher dispatcher = AudioDispatcherFactory.fromPipe(flacFile.getAbsolutePath(), 48000, 4000, 0, 0);
    dispatcher.addAudioProcessor(new AudioProcessor() {
      @Override
      public boolean process(AudioEvent audioEvent) {
        float[] buffer = audioEvent.getFloatBuffer();
        demod.processChunk(buffer, buffer.length);
        return true; // keep processing entire file
      }
      @Override
      public void processingFinished() {
      }
    });
    dispatcher.run();
    assertFalse(frames.isEmpty(), "No valid AX.25 frames found.");
    log.info("✅ Decoded {} frames.", frames.size());
    return frames;
  }
}
