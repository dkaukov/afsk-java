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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModulatorTest {

  @Test
  void testSingleBitMark() {
    float sampleRate = 48000f;
    float markFreq = 1200f;
    float spaceFreq = 2200f;
    float baudRate = 1200f;
    int chunkSize = 1024;

    BitBuffer bits = new BitBuffer();
    bits.addBit(1); // MARK

    List<float[]> outputChunks = new ArrayList<>();

    Modulator mod = new Modulator(sampleRate, markFreq, spaceFreq, baudRate);
    float[] buffer = new float[chunkSize];
    mod.modulate(bits, buffer, chunkSize, (buf, len) -> {
      float[] copy = new float[len];
      System.arraycopy(buf, 0, copy, 0, len);
      outputChunks.add(copy);
    });

    assertFalse(outputChunks.isEmpty());
    int totalSamples = outputChunks.stream().mapToInt(a -> a.length).sum();
    int expectedSamples = Math.round(sampleRate / baudRate);
    assertEquals(expectedSamples, totalSamples, 1);
  }

  @Test
  void testAlternatingBits() {
    float sampleRate = 44100f;
    float markFreq = 1200f;
    float spaceFreq = 2200f;
    float baudRate = 1200f;
    int chunkSize = 64;

    BitBuffer bits = new BitBuffer();
    for (int i = 0; i < 10; i++) {
      bits.addBit(i % 2); // alternate MARK/SPACE
    }

    List<Float> samples = new ArrayList<>();

    Modulator mod = new Modulator(sampleRate, markFreq, spaceFreq, baudRate);
    float[] buffer = new float[chunkSize];
    mod.modulate(bits, buffer, chunkSize, (buf, len) -> {
      for (int i = 0; i < len; i++) {
        samples.add(buf[i]);
      }
    });

    int expectedSamples = Math.round(bits.size() * sampleRate / baudRate);
    assertEquals(expectedSamples, samples.size(), 1.0, "Mismatch in expected number of output samples");
  }

  @Test
  void testPhaseContinuity() {
    float sampleRate = 48000f;
    float markFreq = 1200f;
    float spaceFreq = 2200f;
    float baudRate = 1200f;
    int chunkSize = 256;

    BitBuffer bits = new BitBuffer();
    bits.addBit(1); // MARK
    bits.addBit(1); // MARK again

    List<Float> samples = new ArrayList<>();

    Modulator mod = new Modulator(sampleRate, markFreq, spaceFreq, baudRate);
    float[] buffer = new float[chunkSize];
    mod.modulate(bits, buffer, chunkSize, (buf, len) -> {
      for (int i = 0; i < len; i++) {
        samples.add(buf[i]);
      }
    });

    // crude phase continuity check: no jump between last of first bit and first of second
    int spb = Math.round(sampleRate / baudRate);
    float last = samples.get(spb - 1);
    float next = samples.get(spb);
    assertTrue(Math.abs(last - next) < 0.5, "Expected phase continuity");
  }
}
