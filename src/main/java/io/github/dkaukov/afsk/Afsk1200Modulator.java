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

import java.time.Duration;
import java.util.function.BiConsumer;

import io.github.dkaukov.afsk.atoms.HdlcFramer;
import io.github.dkaukov.afsk.atoms.Modulator;
import io.github.dkaukov.afsk.atoms.NrzEncoder;
import io.github.dkaukov.afsk.util.BitBuffer;

/**
 * High-level AFSK1200 modulator for AX.25 frames.
 * Wraps HDLC framing, NRZ encoding, and waveform generation.
 */
public class Afsk1200Modulator {

  private final HdlcFramer framer;
  private final NrzEncoder nrzEncoder;
  private final Modulator modulator;
  private final int sampleRate;

  /**
   * Create a new Bell 202 (AFSK1200) modulator.
   *
   * @param sampleRate Output sample rate in Hz (e.g., 48000)
   */
  public Afsk1200Modulator(int sampleRate) {
    this.framer = new HdlcFramer();
    this.nrzEncoder = new NrzEncoder();
    this.modulator = new Modulator(sampleRate, 1200, 2200, 1200);
    this.sampleRate = sampleRate;
  }

  private void emitSilence(float[] buffer, int chunkSize, Duration duration, BiConsumer<float[], Integer> callback) {
    int samples = (int) (duration.toNanos() * 1e-9 * sampleRate);
    int chunkIndex = 0;
    for (int i = 0; i < samples; i++) {
      buffer[chunkIndex++] = 0f;
      if (chunkIndex >= chunkSize) {
        callback.accept(buffer, chunkIndex);
        chunkIndex = 0;
      }
    }
    if (chunkIndex > 0) {
      callback.accept(buffer, chunkIndex);
    }
  }

  /**
   * Modulate the given AX.25 payload into AFSK audio samples.
   * CRC, flags, bit-stuffing, and NRZ encoding are applied automatically.
   *
   * @param payload AX.25 frame (without flags or CRC)
   * @param buffer Audio buffer to reuse
   * @param chunkSize Max number of samples per callback
   * @param callback Called with (buffer, validLength) after each filled chunk
   */
  public void modulate(byte[] payload, float[] buffer, int chunkSize, BiConsumer<float[], Integer> callback) {
    nrzEncoder.reset();
    modulator.reset();
    BitBuffer stuffed = framer.frame(payload);
    BitBuffer nrz = nrzEncoder.encode(stuffed);
    modulator.modulate(nrz, buffer, chunkSize, callback);
  }

  /**
   * Modulate the given AX.25 payload into AFSK audio samples,
   * adding silence at the beginning and end.
   *
   * @param payload AX.25 frame (without flags or CRC)
   * @param buffer Audio buffer to reuse
   * @param chunkSize Max number of samples per callback
   * @param leadSilence Number of silence samples to emit before transmission
   * @param tailSilence Number of silence samples to emit after transmission
   * @param callback Called with (buffer, validLength) after each filled chunk
   */
  public void modulate(byte[] payload, float[] buffer, int chunkSize, Duration leadSilence, Duration tailSilence, BiConsumer<float[], Integer> callback) {
    emitSilence(buffer, chunkSize, leadSilence, callback);
    modulate(payload, buffer, chunkSize, callback);
    emitSilence(buffer, chunkSize, tailSilence, callback);
  }
}
