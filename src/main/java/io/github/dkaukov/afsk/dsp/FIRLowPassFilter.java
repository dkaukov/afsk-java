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
package io.github.dkaukov.afsk.dsp;

public class FIRLowPassFilter {

  private final float[] taps;
  private final float[] buffer;
  private int bufferIndex;

  public FIRLowPassFilter(int numTaps, float cutoffFreq, float sampleRate) {
    if (numTaps % 2 == 0) {
      numTaps++; // force odd number for symmetry
    }
    this.taps = designLowPass(numTaps, cutoffFreq, sampleRate);
    this.buffer = new float[numTaps];
    this.bufferIndex = 0;
  }

  public FIRLowPassFilter(int numTaps, float cutoffFreq, float sampleRate, float attenuationDb) {
    if (numTaps % 2 == 0) {
      numTaps++; // force odd number for symmetry
    }
    this.taps = designLowPassKaiser(numTaps, cutoffFreq, sampleRate, attenuationDb);
    this.buffer = new float[numTaps];
    this.bufferIndex = 0;
  }

  public FIRLowPassFilter(int filterTaps, float rolloff, float  samplesPerSymbol, int fake, int fake2) {
    this.taps = genRrcLowpass(filterTaps, rolloff, samplesPerSymbol);
    this.buffer = new float[taps.length];
    this.bufferIndex = 0;
  }

  // Process one sample
  public float filter(float sample) {
    buffer[bufferIndex] = sample;
    float result = 0;
    int tapIndex = 0;
    // Convolve
    for (int i = bufferIndex; i >= 0; i--) {
      result += taps[tapIndex++] * buffer[i];
    }
    for (int i = buffer.length - 1; i > bufferIndex; i--) {
      result += taps[tapIndex++] * buffer[i];
    }
    bufferIndex = (bufferIndex + 1) % buffer.length;
    return result;
  }

  // Generate LPF taps using sinc + Hamming window
  private float[] designLowPass(int numTaps, float cutoffHz, float sampleRate) {
    float[] coeffs = new float[numTaps];
    float fc = cutoffHz / sampleRate;
    int mid = numTaps / 2;
    for (int i = 0; i < numTaps; i++) {
      int n = i - mid;
      // sinc
      float sinc = (n == 0) ? 2.0f * fc : (float) (Math.sin(2.0 * Math.PI * fc * n) / (Math.PI * n));
      // Hamming window
      float window = 0.54f - 0.46f * (float) Math.cos(2.0 * Math.PI * i / (numTaps - 1));
      coeffs[i] = sinc * window;
    }
    // Normalize gain to 1.0
    float sum = 0;
    for (float c : coeffs) {
      sum += c;
    }
    for (int i = 0; i < coeffs.length; i++) {
      coeffs[i] /= sum;
    }
    return coeffs;
  }

  private float[] designLowPassKaiser(int numTaps, float cutoffHz, float sampleRate, float attenuationDb) {
    float[] coeffs = new float[numTaps];
    float fc = cutoffHz / sampleRate;
    // Compute Kaiser beta
    double beta;
    if (attenuationDb > 50) {
      beta = 0.1102 * (attenuationDb - 8.7);
    } else if (attenuationDb >= 21) {
      beta = 0.5842 * Math.pow((attenuationDb - 21), 0.4) + 0.07886 * (attenuationDb - 21);
    } else {
      beta = 0;
    }
    int mid = numTaps / 2;
    for (int i = 0; i < numTaps; i++) {
      int n = i - mid;
      double sinc = (n == 0) ? 2.0 * fc : Math.sin(2.0 * Math.PI * fc * n) / (Math.PI * n);
      double window = besselI0(beta * Math.sqrt(1 - Math.pow((2.0 * n / (numTaps - 1)), 2))) / besselI0(beta);
      coeffs[i] = (float) (sinc * window);
    }
    // Normalize
    float sum = 0f;
    for (float c : coeffs) {
      sum += c;
    }
    for (int i = 0; i < coeffs.length; i++) {
      coeffs[i] /= sum;
    }
    return coeffs;
  }

  // Approximation of zeroth-order modified Bessel function of the first kind (I₀)
  private double besselI0(double x) {
    double sum = 1.0;
    double y = x * x / 4.0;
    double term = y;
    for (int k = 1; k < 25; k++) {
      sum += term;
      term *= y / (k * k);
    }
    return sum;
  }

  public static float[] genRrcLowpass(int filterTaps, float rolloff, float samplesPerSymbol) {
    float[] pfilter = new float[filterTaps];
    // Generate filter taps
    for (int k = 0; k < filterTaps; k++) {
      float t = (k - ((filterTaps - 1.0f) / 2.0f)) / samplesPerSymbol;
      pfilter[k] = rrc(t, rolloff);
    }
    // Normalize to unity gain
    float sum = 0;
    for (float v : pfilter) {
      sum += v;
    }
    for (int k = 0; k < filterTaps; k++) {
      pfilter[k] /= sum;
    }
    return pfilter;
  }

  private static float rrc(float t, float alpha) {
    double pi = Math.PI;
    if (Math.abs(t) < 1e-8) {
      return (float) ((1 - alpha) + (4 * alpha / pi));
    }
    double edge = 1.0 / (4.0 * alpha);
    if (Math.abs(Math.abs(t) - edge) < 1e-6) {
      double piOver4a = pi / (4.0 * alpha);
      double term1 = (1 + 2 / pi) * Math.sin(piOver4a);
      double term2 = (1 - 2 / pi) * Math.cos(piOver4a);
      return (float) ((alpha / Math.sqrt(2.0)) * (term1 + term2));
    }
    double piT = pi * t;
    double fourAlphaT = 4.0 * alpha * t;
    double denom = piT * (1 - fourAlphaT * fourAlphaT);
    if (Math.abs(denom) < 1e-8) {
      return 0.0f;
    }
    double num = Math.sin(piT * (1 - alpha)) + fourAlphaT * Math.cos(piT * (1 + alpha));
    return (float) (num / denom);
  }

}

