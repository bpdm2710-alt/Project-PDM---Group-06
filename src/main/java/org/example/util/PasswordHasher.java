package org.example.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing with PBKDF2-HMAC-SHA256 (built into the JDK, no extra library).
 *
 * Stored format (fits USER.Password_hash VARCHAR(255)):
 * <pre>pbkdf2_sha256$&lt;iterations&gt;$&lt;base64 salt&gt;$&lt;base64 hash&gt;</pre>
 *
 * Generate a hash for the seed / a new user from the command line:
 * <pre>java -cp target/classes org.example.util.PasswordHasher "MyPassword"</pre>
 */
public final class PasswordHasher {

    public static final String PREFIX = "pbkdf2_sha256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /** @return a new salted hash in the stored format. */
    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] dk = derive(password, salt, ITERATIONS);
        Base64.Encoder b64 = Base64.getEncoder().withoutPadding();
        return PREFIX + "$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(dk);
    }

    /** True when {@code stored} is in the PBKDF2 format produced by {@link #hash}. */
    public static boolean isHashed(String stored) {
        return stored != null && stored.startsWith(PREFIX + "$");
    }

    /** Constant-time check of {@code password} against a stored PBKDF2 hash. */
    public static boolean verify(String password, String stored) {
        if (password == null || !isHashed(stored)) {
            return false;
        }
        String[] p = stored.split("\\$");
        if (p.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(p[1]);
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expected = Base64.getDecoder().decode(p[3]);
            if (iterations < 1 || salt.length == 0 || expected.length == 0) {
                return false;
            }
            byte[] actual = derive(password, salt, iterations, expected.length * 8);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) { // bad number or bad base64
            return false;
        }
    }

    /** True when the stored hash uses weaker settings than the current ones and should be redone. */
    public static boolean needsRehash(String stored) {
        if (!isHashed(stored)) {
            return true;
        }
        try {
            return Integer.parseInt(stored.split("\\$")[1]) < ITERATIONS;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        return derive(password, salt, iterations, HASH_BITS);
    }

    private static byte[] derive(String password, byte[] salt, int iterations, int bits) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, bits);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is not available", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** CLI: prints one hash per argument. */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java org.example.util.PasswordHasher <password> [more passwords...]");
            System.exit(1);
        }
        for (String a : args) {
            System.out.println(hash(a));
        }
    }
}
