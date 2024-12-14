package com.panda912.muddy;

import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;

public final class ObfuscatedString implements AutoCloseable {

  private static final Cleaner cleaner = Cleaner.create();
  private static final int MAX_STRING_SIZE = 262144; // 256KB limit
  private static final int WIPE_ITERATIONS = 3;
  private static final Charset charset = Charset.forName(new String(new char[]{'\u0055', '\u0054', '\u0046', '\u0038'}));
  private static final SecureRandom secureRandom = new SecureRandom();
  private final long[] obfuscated;
  private final Cleaner.Cleanable cleanable;
  private final long integrityChecksum; // Add this field

  // Thread-safe charset handling
  private static final class CharsetHolder {
    private static final Charset UTF8;
    static {
      UTF8 = Charset.forName(new String(new char[]{'\u0055', '\u0054', '\u0046', '\u0038'}));
    }
    static Charset get() {
      return UTF8;
    }
  }

  // Optimized secure random generation
  private static final class KeyGenerator {
    private static final ThreadLocal<SecureRandom> SECURE_RANDOM =
            ThreadLocal.withInitial(SecureRandom::new);

    static long generateKey() {
      try {
        byte[] randomBytes = new byte[8];
        SECURE_RANDOM.get().nextBytes(randomBytes);
        long key = ByteBuffer.wrap(randomBytes).getLong();
        return key != 0 ? key : (SECURE_RANDOM.get().nextLong() | 1L);
      } finally {
        if (Thread.currentThread().isInterrupted()) {
          SECURE_RANDOM.remove();
        }
      }
    }
  }

  // Enhanced memory protection
  private static final class SecureMemory {
    static void clear(byte[] data) {
      if (data == null) return;
      try {
        for (int i = 0; i < WIPE_ITERATIONS; i++) {
          Arrays.fill(data, (byte) 0xFF);
          Arrays.fill(data, (byte) 0x00);
          Arrays.fill(data, (byte) 0xAA);
          Arrays.fill(data, (byte) 0x55);
        }
      } finally {
        Arrays.fill(data, (byte) 0);
      }
    }

    static void clear(long[] data) {
      if (data == null) return;
      try {
        for (int i = 0; i < WIPE_ITERATIONS; i++) {
          Arrays.fill(data, -1L);
          Arrays.fill(data, 0L);
          Arrays.fill(data, Long.MAX_VALUE);
          Arrays.fill(data, Long.MIN_VALUE);
        }
      } finally {
        Arrays.fill(data, 0L);
      }
    }
  }

  // Timing attack protection
  private static final class TimingProtection {
    private static void constantTimeArrayCheck(long[] array, int limit) {
      boolean valid = false;
      int length = array != null ? array.length : 0;
      int result = 0;

      for (int i = 0; i < limit; i++) {
        result |= (length ^ i);
      }
      valid = (result != 0);

      if (!valid) {
        try {
          Thread.sleep(KeyGenerator.SECURE_RANDOM.get().nextInt(5));
        } catch (InterruptedException ignored) {
        }
        throw new SecurityException("Invalid array parameters");
      }
    }
  }

  public ObfuscatedString(final long[] obfuscated) {
    TimingProtection.constantTimeArrayCheck(obfuscated, 32767);

    if (obfuscated.length > 32767) {
      throw new SecurityException("Array size exceeds maximum limit");
    }

    this.obfuscated = obfuscated.clone();
    this.obfuscated[0] = obfuscated[0];

    // Calculate and store checksum
    this.integrityChecksum = IntegrityChecker.calculateChecksum(this.obfuscated);
    this.cleanable = cleaner.register(this, new State(this.obfuscated));
  }

  private void verifyIntegrity() {
    IntegrityChecker.verify(obfuscated, integrityChecksum);
  }

  private static class State implements Runnable {
    private final long[] obfuscated;
    private volatile boolean cleaned = false;

    State(long[] obfuscated) {
      this.obfuscated = obfuscated;
    }

    @Override
    public void run() {
      if (!cleaned) {
        synchronized (this) {
          if (!cleaned) {
            SecureMemory.clear(obfuscated);
            cleaned = true;
          }
        }
      }
    }
  }

  @SuppressWarnings("unused")
  public static String obfuscate(final String s) {
    return java(array(s));
  }

  public static String java(final long[] obfuscated) {
    if (obfuscated == null || obfuscated.length == 0) {
      throw new IllegalArgumentException("Invalid obfuscated array");
    }

    StringBuilder code = null;
    try {
      code = new StringBuilder("new cris.linux.mint.obfuscate.ObfuscatedString(new long[] { ");
      appendHexLiteral(code, obfuscated[0]);
      for (int i = 1; i < obfuscated.length; i++) {
        appendHexLiteral(code.append(", "), obfuscated[i]);
      }

      try (ObfuscatedString obs = new ObfuscatedString(obfuscated)) {
        return code
                .append(" }).toString() /* => ")
                .append(literal(obs.toString()).replace("*/", "*\\/"))
                .append(" */")
                .toString();
      }
    } catch (Exception e) {
      if (code != null) {
        code.setLength(0);
        code.append("CLEARED");
      }
      throw new SecurityException("Failed to process obfuscated string", e);
    } finally {
      if (code != null) {
        code.setLength(0);
      }
    }
  }

  public static String literal(String s) {
    return "\"" + s + "\"";
  }

  private static void appendHexLiteral(final StringBuilder sb, final long l) {
    sb.append('\u0030').append('\u0078').append(Long.toHexString(l)).append('\u004C');
  }

  public static long[] array(final String s) {
    if (s == null || -1 != s.indexOf(0)) {
      throw new IllegalArgumentException("Invalid input string");
    }

    final byte[] encoded = s.getBytes(CharsetHolder.get());
    final int el = encoded.length;

    if (el > MAX_STRING_SIZE) {
      throw new SecurityException("String too large");
    }

    final long[] obfuscated = new long[1 + (el + 7) / 8];

    try {
      final long initialKey = KeyGenerator.generateKey();
      obfuscated[0] = initialKey;

      final Random prng = new Random(initialKey);

      for (int i = 0, j = 0; i < el; i += 8) {
        obfuscated[++j] = decode(encoded, i) ^ prng.nextLong();
      }

      // Calculate integrity checksum before returning
      long checksum = IntegrityChecker.calculateChecksum(obfuscated);
      verifyGeneratedArray(obfuscated, checksum);

      return obfuscated;
    } catch (Exception e) {
      SecureMemory.clear(obfuscated);
      throw new SecurityException("Failed to encode string", e);
    } finally {
      SecureMemory.clear(encoded);
    }
  }

  private static void verifyGeneratedArray(long[] array, long expectedChecksum) {
    try {
      IntegrityChecker.verify(array, expectedChecksum);
    } catch (SecurityException e) {
      SecureMemory.clear(array);
      throw e;
    }
  }

  private static long decode(final byte[] bytes, final int off) {
    if (bytes == null || off < 0) {
      throw new SecurityException("Invalid decode parameters");
    }

    try {
      final int end = Math.min(bytes.length, off + 8);
      long value = 0;
      for (int i = end; --i >= off; ) {
        value <<= 8;
        value |= bytes[i] & 0xFF;
      }
      return value;
    } catch (Exception e) {
      throw new SecurityException("Decode failed", e);
    }
  }

  private static void encode(long value, final byte[] bytes, final int off) {
    if (bytes == null || off < 0) {
      throw new SecurityException("Invalid encode parameters");
    }

    try {
      final int end = Math.min(bytes.length, off + 8);
      for (int i = off; i < end; i++) {
        bytes[i] = (byte) value;
        value >>= 8;
      }
    } catch (Exception e) {
      throw new SecurityException("Encode failed", e);
    }
  }

  @Override
  public String toString() {
    verifyIntegrity();  // Add integrity check
    try {
      return new Codec<String>() {
        @Override
        String decode(byte[] encoded, int length) throws Exception {
          return new String(encoded, 0, length, CharsetHolder.get());
        }
      }.call();
    } catch (Exception e) {
      throw new SecurityException("String decoding failed", e);
    }
  }



  @SuppressWarnings("unused")
  public char[] toCharArray() {
    try {
      return new Codec<char[]>() {
        @Override
        char[] decode(byte[] encoded, int length) throws Exception {
          return CharsetHolder.get()
                  .newDecoder()
                  .decode(ByteBuffer.wrap(encoded, 0, length))
                  .array();
        }
      }.call();
    } catch (Exception e) {
      throw new SecurityException("Char array decoding failed", e);
    }
  }

  private static final class IntegrityChecker {
    private static final long INTEGRITY_SEED = 0x1234567890ABCDEFL;

    static long calculateChecksum(long[] data) {
      if (data == null || data.length == 0) return 0;

      long checksum = INTEGRITY_SEED;
      for (int i = 0; i < data.length; i++) {
        checksum = Long.rotateLeft(checksum, 7);
        checksum ^= data[i];
        checksum += (long) i * INTEGRITY_SEED;
      }
      return checksum;
    }

    static void verify(long[] data, long expectedChecksum) {
      if (data == null || data.length == 0) {
        throw new SecurityException("Invalid data for integrity check");
      }

      long actualChecksum = calculateChecksum(data);

      // Constant-time comparison
      boolean valid = true;
      long xor = actualChecksum ^ expectedChecksum;
      for (int i = 0; i < Long.SIZE; i++) {
        valid &= ((xor >>> i) & 1) == 0;
      }

      if (!valid) {
        throw new SecurityException("Data integrity check failed");
      }
    }
  }

  private abstract class Codec<V> implements Callable<V> {
    private static final int BUFFER_SIZE = 8192;
    private static final ThreadLocal<byte[]> BUFFER_POOL = ThreadLocal.withInitial(() -> new byte[BUFFER_SIZE]);

    @Override
    public V call() {
      verifyIntegrity(); // Verify integrity before processing

      byte[] encoded = null;
      byte[] buffer = BUFFER_POOL.get();

      try {
        final long[] obfuscated = ObfuscatedString.this.obfuscated;
        final int ol = obfuscated.length;
        encoded = new byte[8 * (ol - 1)];

        if (encoded.length <= BUFFER_SIZE) {
          processBlock(obfuscated, encoded, 0, ol);
        } else {
          for (int offset = 0; offset < ol; offset += BUFFER_SIZE / 8) {
            verifyIntegrity(); // Additional check for long operations
            int chunk = Math.min(BUFFER_SIZE / 8, ol - offset);
            processBlock(obfuscated, encoded, offset, chunk);
          }
        }

        int el = encoded.length;
        for (int j = el; 0 < j && 0 == encoded[--j]; el = j) {
        }

        return decode(encoded, el);
      } catch (RuntimeException ex) {
        throw new SecurityException("Runtime decode failed", ex);
      } catch (Exception ex) {
        throw new SecurityException("Decode failed", ex);
      } finally {
        if (encoded != null) {
          SecureMemory.clear(encoded);
        }
        SecureMemory.clear(buffer);
        if (Thread.currentThread().isInterrupted()) {
          BUFFER_POOL.remove();
        }
      }
    }

    private void processBlock(long[] obfuscated, byte[] encoded, int offset, int length) {
      final long initialKey = obfuscated[0];
      final Random prng = new Random(initialKey + offset);

      for (int i = 1; i < length; i++) {
        long value = obfuscated[offset + i] ^ prng.nextLong();
        encode(value, encoded, 8 * (offset + i - 1));
      }
    }

    abstract V decode(byte[] encoded, int length) throws Exception;
  }

  @Override
  public void close() {
    cleanable.clean();
  }
}