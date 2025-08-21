package io.github.dkaukov.afsk.dsp;

import java.util.Arrays;

/**
 * Finite Impulse Response (FIR) filter with external control over decimation.
 * Supports both step-wise and single-sample usage.
 */
public class DecimatingFIR {

  private final float[] taps;
  private final float[] delayLine;
  private int delayIndex = 0;

  public DecimatingFIR(float[] taps) {
    this.taps = Arrays.copyOf(taps, taps.length);
    this.delayLine = new float[taps.length];
  }

  /**
   * Feed one new sample into the delay line.
   * @param sample new input sample
   */
  public void accept(float sample) {
    delayLine[delayIndex] = sample;
    delayIndex = (delayIndex + 1) % delayLine.length;
  }

  /**
   * Compute current output using FIR tap convolution.
   * @return filtered output
   */
  public float getOutput() {
    float acc = 0f;
    int idx = delayIndex;
    for (float tap : taps) {
      idx = (idx - 1 + delayLine.length) % delayLine.length;
      acc += delayLine[idx] * tap;
    }
    return acc;
  }

  /**
   * Convenience method: push a sample and immediately compute output.
   * @param sample input sample
   * @return filtered output
   */
  public float filter(float sample) {
    accept(sample);
    return getOutput();
  }

  /**
   * Resets the internal delay line.
   */
  public void reset() {
    Arrays.fill(delayLine, 0f);
    delayIndex = 0;
  }
}
