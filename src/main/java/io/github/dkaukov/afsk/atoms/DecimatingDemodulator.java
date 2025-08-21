package io.github.dkaukov.afsk.atoms;

import io.github.dkaukov.afsk.dsp.DdsOscillator;
import io.github.dkaukov.afsk.dsp.DecimatingFIR;
import io.github.dkaukov.afsk.dsp.FastFIR;
import io.github.dkaukov.afsk.dsp.FilterDesignUtils;
import io.github.dkaukov.afsk.dsp.SinglePoleIIRLpf;

/**
 * Bell 202 (AFSK 1200) demodulator using a frequency-translating FIR with decimation.
 *
 * Steps:
 *   1. Apply band-pass FIR filter.
 *   2. Decimate input stream.
 *   3. Mix with oscillator to shift to baseband.
 *   4. Use a quadrature FM discriminator to extract symbol energy.
 *   5. Output signal suitable for NRZI decoding.
 */
public class DecimatingDemodulator {

  private final int decimation;
  private final float normGain;

  private final DdsOscillator oscillator;
  private final DecimatingFIR bpf;
  private final SinglePoleIIRLpf outputLp;
  private final FastFIR iFilter;
  private final FastFIR qFilter;

  private long sampleCounter = 0;
  private float prevI = 0f;
  private float prevQ = 0f;

  /**
   * Constructs a new demodulator instance.
   *
   * @param sampleRate Input sample rate in Hz (e.g. 48000)
   * @param markFreq Frequency for 'mark' (typically 1200 Hz)
   * @param spaceFreq Frequency for 'space' (typically 2200 Hz)
   * @param baudRate Baud rate (typically 1200)
   * @param decimation Decimation factor (e.g. 2, 4)
   */
  public DecimatingDemodulator(float sampleRate, float markFreq, float spaceFreq, float baudRate, int decimation) {
    this.decimation = decimation;
    float dev = (spaceFreq - markFreq) / 2f;
    float centerFreq = (spaceFreq + markFreq) / 2f;
    float rateAfterDecim = sampleRate / decimation;
    this.oscillator = new DdsOscillator(rateAfterDecim, centerFreq);
    float[] firTaps = FilterDesignUtils.designBandPassKaiser(251, markFreq, spaceFreq, sampleRate, 60);
    this.bpf = new DecimatingFIR(firTaps);
    float[] lpf = FilterDesignUtils.designLowPassHamming(21, dev, rateAfterDecim);
    iFilter = new FastFIR(lpf);
    qFilter = new FastFIR(lpf);
    this.outputLp = new SinglePoleIIRLpf(rateAfterDecim, baudRate * 3f);
    this.normGain = 1f / (2f * (float) Math.PI * (dev / rateAfterDecim));
  }

  /**
   * Demodulates a chunk of audio samples into baseband FM data.
   *
   * @param samples PCM input (-1.0 to +1.0 floats)
   * @param length  Number of valid samples
   * @return demodulated float buffer (~±1.0 range)
   */
  public float[] processChunk(float[] samples, int length) {
    int remainder = (int)(decimation - (sampleCounter % decimation)) % decimation;
    int outLen = (length - remainder + decimation - 1) / decimation;
    float[] output = new float[outLen];
    int outIdx = 0;
    for (int i = 0; i < length; i++) {
      bpf.accept(samples[i]);
      if (sampleCounter % decimation == 0) {
        float sample = bpf.getOutput();
        // Mix to baseband
        float mixedI = iFilter.filter(sample * oscillator.cos());
        float mixedQ = qFilter.filter(-sample * oscillator.sin());
        // Phase change
        //float demod = (float)Math.atan2(mixedI * prevQ - prevI * mixedQ, prevI * mixedI + prevQ * mixedQ);
        // Phase change
        float deltaQ = mixedQ * prevI - mixedI * prevQ;
        float magSq = mixedI * mixedI + mixedQ * mixedQ;
        float demod = outputLp.filter((magSq < 1e-12f) ? 0.0f : (deltaQ / magSq) * normGain);
        // Apply dead-zone
        if (Math.abs(demod) < 0.006) {
          demod = 1e-12f;
        }
        output[outIdx++] = demod;
        prevI = mixedI;
        prevQ = mixedQ;
        // DDS phase advance
        oscillator.next();
      }
      sampleCounter++;
    }
    return output;
  }

  public void reset() {
    sampleCounter = 0;
    prevI = 0f;
    prevQ = 0f;
    oscillator.reset();
    bpf.reset();
    outputLp.reset();
  }
}
