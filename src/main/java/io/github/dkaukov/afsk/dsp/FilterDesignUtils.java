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

/**
 * FIR tap designers: Hamming LPF, Kaiser LPF/BPF, Root-Raised-Cosine (RRC).
 * All designs return unity-gain normalized taps.
 */
public final class FilterDesignUtils {
  private FilterDesignUtils() {}

  /** Low-pass via windowed-sinc (Hamming). cutoffHz is 3dB point approx. */
  public static float[] designLowPassHamming(int numTaps, float cutoffHz, float sampleRate) {
    if ((numTaps & 1) == 0) {
      numTaps++; // force odd
    }
    float[] h = new float[numTaps];
    float fc = cutoffHz / sampleRate;
    int mid = numTaps / 2;

    for (int i = 0; i < numTaps; i++) {
      int n = i - mid;
      float sinc = (n == 0)
        ? 2f * fc
        : (float) (Math.sin(2.0 * Math.PI * fc * n) / (Math.PI * n));
      float w = 0.54f - 0.46f * (float) Math.cos(2.0 * Math.PI * i / (numTaps - 1));
      h[i] = sinc * w;
    }
    normalizeUnityGain(h);
    return h;
  }

  /** Low-pass via Kaiser window. attenuationDb controls sidelobes; 60–80dB good defaults. */
  public static float[] designLowPassKaiser(int numTaps, float cutoffHz, float sampleRate, float attenuationDb) {
    if ((numTaps & 1) == 0) {
      numTaps++;
    }
    float[] h = new float[numTaps];
    float fc = cutoffHz / sampleRate;
    int mid = numTaps / 2;
    double beta = kaiserBeta(attenuationDb);
    double denom = besselI0(beta);

    for (int i = 0; i < numTaps; i++) {
      int n = i - mid;
      double sinc = (n == 0)
        ? 2.0 * fc
        : Math.sin(2.0 * Math.PI * fc * n) / (Math.PI * n);
      double r = (2.0 * i) / (numTaps - 1) - 1.0;      // -1..+1 across the window
      double w = besselI0(beta * Math.sqrt(1.0 - r * r)) / denom;
      h[i] = (float) (sinc * w);
    }
    normalizeUnityGain(h);
    return h;
  }

  /** Band-pass via difference of two low-passes (Kaiser window). Pass (lowHz..highHz). */
  public static float[] designBandPassKaiser(int numTaps, float lowHz, float highHz, float sampleRate, float attenuationDb) {
    if ((numTaps & 1) == 0) {
      numTaps++;
    }
    float[] h = new float[numTaps];
    float fl = lowHz / sampleRate;
    float fh = highHz / sampleRate;
    int mid = numTaps / 2;
    double beta = kaiserBeta(attenuationDb);
    double denom = besselI0(beta);

    for (int i = 0; i < numTaps; i++) {
      int n = i - mid;
      double sincH = (n == 0)
        ? 2.0 * fh
        : Math.sin(2.0 * Math.PI * fh * n) / (Math.PI * n);
      double sincL = (n == 0)
        ? 2.0 * fl
        : Math.sin(2.0 * Math.PI * fl * n) / (Math.PI * n);
      double r = (2.0 * i) / (numTaps - 1) - 1.0;
      double w = besselI0(beta * Math.sqrt(1.0 - r * r)) / denom;
      h[i] = (float) ((sincH - sincL) * w);
    }
    normalizeUnityGain(h);
    return h;
  }

  /** Root-Raised-Cosine (RRC) low-pass shaping filter. rolloff α in (0,1], taps must be odd. */
  public static float[] designRrc(int numTaps, float rolloff, float samplesPerSymbol) {

    if ((numTaps & 1) == 0) {
      numTaps++;
    }
    float[] h = new float[numTaps];
    float mid = (numTaps - 1) / 2.0f;

    for (int k = 0; k < numTaps; k++) {
      float t = (k - mid) / samplesPerSymbol;   // time in symbols
      h[k] = rrcSample(t, rolloff);
    }
    normalizeUnityGain(h);
    return h;

    /*
    float[] pfilter = new float[numTaps];
    // Generate filter taps
    for (int k = 0; k < numTaps; k++) {
      float t = (k - ((numTaps - 1.0f) / 2.0f)) / samplesPerSymbol;
      pfilter[k] = rrcSample(t, rolloff);
    }
    // Normalize to unity gain
    float sum = 0;
    for (float v : pfilter) {
      sum += v;
    }
    for (int k = 0; k < numTaps; k++) {
      pfilter[k] /= sum;
    }
    return pfilter;
    */

  }

  /** Single RRC tap value. */
  public static float rrcSample(float t, float alpha) {
    final double pi = Math.PI;

    if (Math.abs(t) < 1e-8) {
      return (float) ((1.0 - alpha) + (4.0 * alpha / pi));
    }
    double edge = 1.0 / (4.0 * alpha);
    if (Math.abs(Math.abs(t) - edge) < 1e-6) {
      double piOver4a = pi / (4.0 * alpha);
      double term1 = (1.0 + 2.0 / pi) * Math.sin(piOver4a);
      double term2 = (1.0 - 2.0 / pi) * Math.cos(piOver4a);
      return (float) ((alpha / Math.sqrt(2.0)) * (term1 + term2));
    }

    double piT = pi * t;
    double a4t = 4.0 * alpha * t;
    double denom = piT * (1.0 - a4t * a4t);
    if (Math.abs(denom) < 1e-12) {
      return 0.0f;
    }

    double num = Math.sin(piT * (1.0 - alpha)) + a4t * Math.cos(piT * (1.0 + alpha));
    return (float) (num / denom);
  }

  // ---------- helpers ----------

  /** Normalize taps so DC gain ≈ 1. */
  public static void normalizeUnityGain(float[] taps) {
    float sum = 0f;
    for (float v : taps) {
      sum += v;
    }
    if (sum == 0f) {
      return;
    }
    float inv = 1f / sum;
    for (int i = 0; i < taps.length; i++) {
      taps[i] *= inv;
    }
  }

  /** Kaiser window beta from desired stopband attenuation (dB). */
  public static double kaiserBeta(double attenuationDb) {
    if (attenuationDb > 50.0) {
      return 0.1102 * (attenuationDb - 8.7);
    } else if (attenuationDb >= 21.0) {
      return 0.5842 * Math.pow(attenuationDb - 21.0, 0.4)
        + 0.07886 * (attenuationDb - 21.0);
    } else {
      return 0.0;
    }
  }

  /** Zeroth-order modified Bessel function of the first kind (I0), series approx. */
  public static double besselI0(double x) {
    double sum = 1.0;
    double y = (x * x) / 4.0;
    double term = y;
    for (int k = 1; k < 30; k++) { // 25–30 terms is plenty
      sum += term;
      term *= y / (k * k);
    }
    return sum;
  }
}
