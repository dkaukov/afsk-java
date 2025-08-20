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
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
  private int chunkIndex;

  /**
   * Creates a new AFSK1200 modulator with a default lead-in flag duration of 250 ms.
   * This is typically sufficient for radio transmitters to stabilize (AGC, squelch, etc.).
   *
   * @param sampleRate Output sample rate in Hz (e.g., 48000)
   */
  public Afsk1200Modulator(int sampleRate) {
    this(sampleRate, Duration.ofMillis(250)); // Default lead-in flag duration
  }

  /**
   * Creates a new AFSK1200 modulator with a specified lead-in duration.
   * The lead-in is expressed in milliseconds and determines how many HDLC flags (0x7E)
   * will be transmitted before the actual frame begins. These flags are useful for
   * activating the receiver's squelch (AM) or AGC before data transmission.
   *
   * @param sampleRate Output sample rate in Hz (e.g., 48000)
   * @param leadInFlagDuration Duration of HDLC flags to prepend, typically 100–500 ms
   */
  public Afsk1200Modulator(int sampleRate, Duration leadInFlagDuration) {
    int leadFlags = Math.round(leadInFlagDuration.toMillis() * 1200f / 1000f / 8f);
    this.framer = new HdlcFramer(leadFlags, 3);
    this.nrzEncoder = new NrzEncoder();
    this.modulator = new Modulator(sampleRate, 1200, 2200, 1200);
    this.sampleRate = sampleRate;
  }

  private void emitSilence(float[] buffer, int chunkSize, Duration duration, BiConsumer<float[], Integer> callback) {
    int samples = (int) (duration.toNanos() * 1e-9 * sampleRate);
    for (int i = 0; i < samples; i++) {
      buffer[chunkIndex++] = 0f;
      if (chunkIndex >= chunkSize) {
        callback.accept(buffer, chunkIndex);
        chunkIndex = 0;
      }
    }
  }

  private void internalModulate(byte[] payload, float[] buffer, int chunkSize, BiConsumer<float[], Integer> callback) {
    nrzEncoder.reset();
    modulator.reset();
    BitBuffer stuffed = framer.frame(payload);
    BitBuffer nrz = nrzEncoder.encode(stuffed);
    chunkIndex = modulator.modulate(nrz, buffer, chunkSize,  chunkIndex, callback);
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
    chunkIndex = 0;
    internalModulate(payload, buffer, chunkSize, callback);
    if (chunkIndex > 0) {
      callback.accept(buffer, chunkIndex);
    }
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
    chunkIndex = 0;
    emitSilence(buffer, chunkSize, leadSilence, callback);
    internalModulate(payload, buffer, chunkSize, callback);
    emitSilence(buffer, chunkSize, tailSilence, callback);
    if (chunkIndex > 0) {
      callback.accept(buffer, chunkIndex);
    }
  }

  /**
   * Modulates an AX.25 payload into AFSK1200 audio with optional leading and trailing silence,
   * delivering the result in fixed-size audio chunks via the provided callback.
   * <p>
   * Each chunk passed to the callback will contain exactly {@code chunkSize} float samples.
   * The final chunk is zero-padded with silence if needed.
   * <p>
   * Useful for feeding data into audio encoders (e.g., Opus) that require fixed-size input frames.
   *
   * @param payload       the AX.25 payload to modulate
   * @param buffer        a reusable float buffer of at least {@code chunkSize} length
   * @param chunkSize     number of float samples per chunk
   * @param leadSilence   silence to prepend before the modulated signal
   * @param tailSilence   silence to append after the modulated signal
   * @param callback      called once per fully filled (or padded) chunk
   */
  public void modulateToFixedLengthChunks(byte[] payload, float[] buffer, int chunkSize, Duration leadSilence, Duration tailSilence, Consumer<float[]> callback) {
    chunkIndex = 0;
    emitSilence(buffer, chunkSize, leadSilence, (chunk, len) -> callback.accept(chunk));
    internalModulate(payload, buffer, chunkSize, (chunk, len) -> callback.accept(chunk));
    emitSilence(buffer, chunkSize, tailSilence, (chunk, len) -> callback.accept(chunk));
    if (chunkIndex > 0) {
      Arrays.fill(buffer, chunkIndex, buffer.length, 0f); // Fill remaining buffer with silence
      callback.accept(buffer);
    }
  }
}
