# afsk-java

Lightweight Java library for **Bell 202 / AFSK-1200** demodulation and AX.25 frame decoding. Designed to run on desktop JVMs **and** Android (no native deps).

[![Maven Central](https://img.shields.io/maven-central/v/io.github.dkaukov/afsk-java.svg)](https://mvnrepository.com/artifact/io.github.dkaukov/afsk-java)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](#license)

## Why?

Most AFSK/AX.25 stacks are tied to native code or heavyweight DSP toolkits. **afsk-java** is pure-Java, fast enough for phones, and easy to embed in apps, services, and tests.

## Features

* 🎧 **Demodulation pipeline**: band-limit ➜ decimate ➜ complex/quadrature FM discriminator ➜ soft-slicer PLL ➜ NRZI/HDLC ➜ AX.25 frames
* 🧪 **Optional CRC bit-flip recovery** (1–2 bit search) — off by default; toggleable via constructor flag
* 🎚️ Works at arbitrary sample rates (you pass the rate); accepts **mono float** samples in $-1..1$
* 📦 Pure Java (no JNI), small footprint, Android-friendly
* 🔧 Pluggable callbacks for decoded frames; easy to wire to audio, WAV, or SDR frontends
* 🧰 Utilities for test vectors and WAV decoding (see `src/test`)

> Artifact coordinates: `io.github.dkaukov:afsk-java` (available on Maven Central). ([mvnrepository.com][1])

---

## Quick start

### 1) Add dependency

**Gradle (Kotlin DSL)**

```kts
dependencies {
  implementation("io.github.dkaukov:afsk-java:1.9")
}
```

**Maven**

```xml
<dependency>
  <groupId>io.github.dkaukov</groupId>
  <artifactId>afsk-java</artifactId>
  <version>1.9</version>
</dependency>
```

(See Maven Central for the latest version.) ([mvnrepository.com][1])

### 2) Minimal usage (streaming samples)

```java
import io.github.dkaukov.afsk.Afsk1200Demodulator;
import io.github.dkaukov.afsk.Ax25Frame;
import java.nio.FloatBuffer;

public class Demo {
  public static void main(String[] args) {
    final int sampleRate = 48000;        // use your real input rate
    final boolean enableCrcBitRepair = false; // optional; defaults may differ

    Afsk1200Demodulator demod = new Afsk1200Demodulator(sampleRate, enableCrcBitRepair,
        frame -> {
          // Called for each good AX.25 frame
          System.out.println(frame.toString());  // pretty print or hex dump
        },
        err -> {
          // Optional: receive detailed decode stats/errors if you want
          // e.g., symbol clock drift, CRC failures, recovered bits, etc.
        });

    // Feed mono float samples [-1..1] as they arrive:
    while (hasMoreAudio()) {
      FloatBuffer chunk = nextAudioChunk();
      demod.addSamples(chunk);
    }

    demod.close();
  }
}
```

### 3) Decode a WAV file (example)

```java
try (WavReader wav = WavReader.open("input.wav")) {      // any simple WAV reader
  Afsk1200Demodulator demod = new Afsk1200Demodulator(
      wav.getSampleRate(), /*enableCrcBitRepair=*/false,
      (Ax25Frame f) -> System.out.println("AX25: " + f),
      null
  );

  float[] buf = new float[4096];
  int n;
  while ((n = wav.read(buf)) > 0) {
    demod.addSamples(buf, 0, n);
  }
  demod.close();
}
```

> Notes
>
> * The demodulator is **sample-rate agnostic**; pass the actual rate used by your audio source.
> * Input must be **mono**. If you have stereo, mix down (`(L+R)/2`).
> * For Android, capture audio via `AudioRecord` and feed the float buffer directly.

---

## API overview

Key types (names shown for orientation; see Javadoc):

* `Afsk1200Demodulator` — high-level façade that handles the full AFSK→AX.25 pipeline
* `Afsk1200Modulator` — modulator

> The API is evolving; minor changes may occur between releases. Check the Javadoc and tests for current usage patterns.

---

## Performance tips

* Prefer **48 kHz** or **44.1 kHz** capture. Lower rates work; higher rates add CPU cost with little benefit.
* Keep audio levels conservative (avoid clipping). AFSK hates hard limiting.
* If you see frequent CRC fails but clear eye-diagrams, try enabling the **CRC bit-flip** search (at the cost of a little CPU).

---

## Examples & integrations

* Android: wire `AudioRecord` ➜ `Afsk1200Demodulator` ➜ app UI/DB
* Desktop: WAV/ALSA/CoreAudio capture ➜ demod ➜ print/IGate/TNC-KISS bridge
* Testing: run demod over FLAC/WAV vectors in CI to catch regressions

---

## Roadmap

* Optional **AFSK modulator** (for loopback tests)
* Soft-decision output for external Viterbi/EC experiments
* Tools for AFC and pre/de-emphasis compensation

---

## Contributing

PRs and issues welcome! Please include:

* Short description and a failing test (if bug)
* Before/after CPU and decode stats (if perf/quality)
* Audio snippets (if RF/decoding issue)

---

## License

GNU **GPL-3.0**. See [`LICENSE`](./LICENSE) for details. 



