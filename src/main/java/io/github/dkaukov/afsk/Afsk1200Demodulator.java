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

import io.github.dkaukov.afsk.atoms.HdlcDeframer;
import io.github.dkaukov.afsk.atoms.HdlcDeframer.FrameListener;
import io.github.dkaukov.afsk.atoms.Demodulator;
import io.github.dkaukov.afsk.atoms.SymbolSlicerPll;
import io.github.dkaukov.afsk.atoms.FrameVerifier;
import io.github.dkaukov.afsk.atoms.NrziDecoder;

public class Afsk1200Demodulator {

  private final Demodulator demodulator;
  private final SymbolSlicerPll slicer;
  private final NrziDecoder nrziDecoder;
  private final HdlcDeframer framer;
  private final FrameListener onFrame;
  private final FrameVerifier frameVerifier = new FrameVerifier();

  public Afsk1200Demodulator(int sampleRate, FrameListener onFrame) {
    this.onFrame = onFrame;
    this.nrziDecoder = new NrziDecoder();
    this.framer = new HdlcDeframer();
    this.demodulator = new Demodulator(sampleRate, 1200, 2200);
    this.slicer = new SymbolSlicerPll(sampleRate, 1200);
  }

  public void processChunk(float[] samples) {
    framer.processBits(
      nrziDecoder.decode(
        slicer.slice(
          demodulator.processChunk(samples)))
      , f -> onFrame.onFrame(frameVerifier.verifyAndStrip(f)));
  }

}
