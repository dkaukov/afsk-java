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
package io.github.dkaukov.afsk.util;

import java.util.Arrays;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;

/**
 * Growable packed bit buffer (LSB-first) backed by {@code long[]} words.
 *
 * <p>Key properties:</p>
 * <ul>
 *   <li>Append-only (use {@link #clear()} to reuse).</li>
 *   <li>LSB-first bit ordering (bit 0 is the least significant bit of the first word).</li>
 *   <li>No boxing on append or primitive iteration.</li>
 *   <li>Cheap conversion to tight {@code int[]} or packed {@code byte[]}.</li>
 * </ul>
 *
 * <p>Not thread-safe.</p>
 */
public final class BitBuffer implements Iterable<Integer> {

  private long[] words;
  private int bitCount;

  /** Create with a small default capacity (512 bits). */
  public BitBuffer() {
    this(512);
  }

  /**
   * @param initialBits initial capacity in bits (rounded up to whole words)
   */
  public BitBuffer(int initialBits) {
    int w = Math.max(1, (initialBits + 63) >>> 6);
    this.words = new long[w];
    this.bitCount = 0;
  }

  /** Number of bits currently stored. */
  public int size() { return bitCount; }

  /** True if no bits stored. */
  public boolean isEmpty() { return bitCount == 0; }

  /** Remove all bits (keeps capacity). */
  public void clear() {
    // Optionally skip zeroing for speed if caller guarantees full overwrite on reuse.
    Arrays.fill(words, 0L);
    bitCount = 0;
  }

  /** Append a single bit (0 or 1). */
  public void addBit(int bit) {
    ensureCapacity(1);
    if ((bit & 1) != 0) {
      int idx = bitCount >>> 6;
      int off = bitCount & 63;
      words[idx] |= (1L << off);
    }
    bitCount++;
  }

  /**
   * Append {@code n} bits from {@code value}, LSB-first.
   * @param value source of bits (lower {@code n} bits are used)
   * @param n number of bits to append (0..32)
   */
  public void addBits(int value, int n) {
    if (n < 0 || n > 32) {
      throw new IllegalArgumentException("n must be 0..32");
    }
    ensureCapacity(n);
    int remaining = n;
    int v = value;
    while (remaining > 0) {
      int idx = bitCount >>> 6;
      int off = bitCount & 63;
      int space = 64 - off;
      int take = Math.min(space, remaining);

      long mask = (take == 64) ? -1L : ((1L << take) - 1);
      long chunk = ((long) v) & mask;
      words[idx] |= (chunk << off);

      v >>>= take;
      bitCount += take;
      remaining -= take;
    }
  }

  /** Read A bit at index (0/1). */
  public int getBit(int i) {
    if (i < 0 || i >= bitCount) {
      throw new IndexOutOfBoundsException();
    }
    int idx = i >>> 6, off = i & 63;
    return (int) ((words[idx] >>> off) & 1L);
  }

  /** Overwrite A bit at index with 0/1. */
  public void setBit(int i, int bit) {
    int idx = i >>> 6, off = i & 63;
    if (i < 0 || idx >= words.length) {
      throw new IndexOutOfBoundsException();
    }
    long mask = 1L << off;
    if ((bit & 1) != 0) {
      words[idx] |= mask;
    } else {
      words[idx] &= ~mask;
    }
  }

  /** Append all bits from another buffer (zero-copy semantics avoided). */
  public void addAll(BitBuffer other) {
    int n = other.bitCount;
    ensureCapacity(n);
    // Fast path: append whole words when aligned
    if ((bitCount & 63) == 0) {
      int dstWord = bitCount >>> 6;
      int fullWords = n >>> 6;
      // copy full words
      if (fullWords > 0) {
        ensureWordCapacity(dstWord + fullWords);
        System.arraycopy(other.words, 0, this.words, dstWord, fullWords);
      }
      bitCount += fullWords << 6;
      // tail bits
      int tail = n & 63;
      if (tail != 0) {
        long mask = (1L << tail) - 1;
        words[bitCount >>> 6] |= other.words[fullWords] & mask;
        bitCount += tail;
      }
      return;
    }
    // General path
    for (int i = 0; i < n; i++) {
      addBit(other.getBit(i));
    }
  }

  public BitBuffer copy() {
    BitBuffer copy = new BitBuffer(bitCount);
    System.arraycopy(words, 0, copy.words, 0, words.length);
    copy.bitCount = bitCount;
    return copy;
  }

  /** Emit bits to a sink (0/1) without allocating arrays. */
  public void emit(IntConsumer sink) {
    for (int i = 0; i < bitCount; i++) {
      int idx = i >>> 6, off = i & 63;
      sink.accept((int) ((words[idx] >>> off) & 1L));
    }
  }

  /** Copy out as a tight int[] of 0/1 (allocates once). */
  public int[] toIntArray() {
    int[] out = new int[bitCount];
    int k = 0;
    for (int i = 0; i < bitCount; i++) {
      int idx = i >>> 6, off = i & 63;
      out[k++] = (int) ((words[idx] >>> off) & 1L);
    }
    return out;
  }

  /**
   * Copy out as packed bytes, LSB-first within each byte.
   * The last byte is right-sized (no padding beyond {@link #size()}).
   */
  public byte[] toByteArray() {
    int nBytes = (bitCount + 7) >>> 3;
    byte[] out = new byte[nBytes];
    for (int i = 0; i < bitCount; i++) {
      int bit = getBit(i);
      if (bit != 0) {
        int byteIdx = i >>> 3;
        int bitPos = i & 7;
        out[byteIdx] |= (byte) (1 << bitPos);
      }
    }
    return out;
  }

  // ---------- Iteration & Streams ----------

  /** Primitive iterator over bits (0/1), LSB-first, no boxing. Snapshot of current size. */
  public PrimitiveIterator.OfInt bitIterator() {
    final int n = bitCount;
    return new PrimitiveIterator.OfInt() {
      int i = 0;
      @Override public boolean hasNext() { return i < n; }
      @Override public int nextInt() {
        int idx = i >>> 6, off = i & 63;
        int v = (int) ((words[idx] >>> off) & 1L);
        i++;
        return v;
      }
    };
  }

  /** Spliterator suited for IntStream over bits. */
  public Spliterator.OfInt intSpliterator() {
    final int n = bitCount;
    return new Spliterators.AbstractIntSpliterator(
      n,
      Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.IMMUTABLE | Spliterator.NONNULL
    ) {
      int i = 0;
      @Override public boolean tryAdvance(IntConsumer action) {
        if (i >= n) {
          return false;
        }
        int idx = i >>> 6, off = i & 63;
        int v = (int) ((words[idx] >>> off) & 1L);
        i++;
        action.accept(v);
        return true;
      }
    };
  }

  /** Primitive stream of bits (0/1). */
  public IntStream bitStream() {
    return StreamSupport.intStream(intSpliterator(), false);
  }

  /** Enable for-each: {@code for (int b : buffer) { ... }} (boxed Integer). */
  @Override
  public @Nonnull PrimitiveIterator.OfInt iterator() {
    return bitIterator();
  }

  // ---------- Internals ----------

  private void ensureCapacity(int bitsToAdd) {
    int needBits = bitCount + bitsToAdd;
    int needWords = (needBits + 63) >>> 6;
    ensureWordCapacity(needWords);
  }

  private void ensureWordCapacity(int needWords) {
    if (needWords > words.length) {
      int newLen = Math.max(needWords, words.length + (words.length >> 1) + 1);
      words = Arrays.copyOf(words, newLen);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof BitBuffer other)) {
      return false;
    }
    if (this.size() != other.size()) {
      return false;
    }
    for (int i = 0; i < size(); i++) {
      if (this.getBit(i) != other.getBit(i)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = 1;
    for (int i = 0; i < size(); i++) {
      result = 31 * result + getBit(i);
    }
    return result;
  }
}
