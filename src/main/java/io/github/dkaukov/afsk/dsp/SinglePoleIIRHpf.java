package io.github.dkaukov.afsk.dsp;

import lombok.Getter;

/**
 * Single-pole high-pass (de-tilt) filter.
 * y[n] = α * (y[n-1] + x[n] - x[n-1])
 */
public class SinglePoleIIRHpf {
  @Getter
  private final float alpha;
  private float y, xPrev;

  public SinglePoleIIRHpf(float sampleRate, float cutoffHz) {
    this.alpha = (float) (Math.exp(-2.0 * Math.PI * cutoffHz / sampleRate));
    this.y = 0f;
    this.xPrev = 0f;
  }

  public float filter(float x) {
    float out = alpha * (y + x - xPrev);
    xPrev = x;
    y = out;
    return out;
  }

  public void reset() {
    y = 0f;
    xPrev = 0f;
  }
}
