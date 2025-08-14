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
import io.github.dkaukov.afsk.opus.OpusUtils.OpusDecoderWrapper;
import io.github.dkaukov.afsk.opus.OpusUtils.OpusEncoderWrapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class Afsk1200DemodulatorOpusTest {

  @BeforeAll
  static void redirectJulToStdout() {
    // Remove existing JUL handlers
    LogManager.getLogManager().reset();
    // Install bridge handler
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  @Test
  @DisplayName("One Packet directly sampled from radio")
  void testDecodeTrackRec() throws Exception {
    List<byte[]> res = processFile(new File("src/test/cd/one-packet-direct-samped.flac"));
  }

  @Test
  @DisplayName("One Packet witch Clicks from RSSI queries.")
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
    assertTrue(res.size() >= 1003, "Should decode at least 1003 frames from Track 1");
  }

  /**
   * Test decoding of Track 2 from the WA8LMF Test CD: de‑emphasized audio as seen from speaker outputs.
   * Validates that the demodulator tolerates the typical audio coloration introduced by radio speaker audio :contentReference[oaicite:3]{index=3}.
   */
  @Test
  @DisplayName("Track 2 – 100 Mic-E Bursts (De-emphasized Audio)")
  void testDecodeTrack2() throws Exception {
    List<byte[]> res = processFile(new File("src/test/cd/02_100-Mic-E-Bursts-DE-emphasized.flac"));
    assertTrue(res.size() >= 949, "Should decode at least 949 frames from Track 2");
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
    Afsk1200Demodulator demod = new Afsk1200Demodulator(48000, false, frame -> {
      if (frame != null && frame.length > 0) {
          try {
              APRSPacket packet = Parser.parseAX25(frame);
              log.trace("Found AX.25 frame: {}>{} {}", packet.getSourceCall(), packet.getDestinationCall(), packet.getAprsInformation().toString());
              frames.add(frame);
          } catch (Exception e) {
              //throw new RuntimeException(e);
          }
      }
    });
    OpusDecoderWrapper opusDecoder = new OpusDecoderWrapper(48000, 1920);
    OpusEncoderWrapper opusEncoder = new OpusEncoderWrapper(48000, 1920);
    AudioDispatcher dispatcher = AudioDispatcherFactory.fromPipe(flacFile.getAbsolutePath(), 48000, 1920, 0, 0);
    dispatcher.addAudioProcessor(new AudioProcessor() {
      @Override
      public boolean process(AudioEvent audioEvent) {
        float[] buffer = audioEvent.getFloatBuffer();
        byte[] opusFrame = new byte[1920];
        int encoded = opusEncoder.encode(buffer, opusFrame);
        float[] pcmFloat = new float[1920];
        int decoded = opusDecoder.decode(opusFrame, encoded, pcmFloat);
        demod.addSamples(pcmFloat,decoded);
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
