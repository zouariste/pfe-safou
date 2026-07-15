package de.uni_passau.fim.se2.application.service;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Argon2id password hashing service.
 * Parameters aligned with OWASP recommendations.
 */
@Service
public class PasswordHasherService {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int MEMORY = 65536;   // 64 MB
    private static final int ITERATIONS = 3;
    private static final int PARALLELISM = 1;

    private final SecureRandom secureRandom = new SecureRandom();

    public String hashPassword(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);

        byte[] hash = generateArgon2Hash(password, salt);

        // Format: $argon2id$salt$hash (both Base64 encoded)
        return "$argon2id$" +
                Base64.getEncoder().encodeToString(salt) + "$" +
                Base64.getEncoder().encodeToString(hash);
    }

    public boolean verifyPassword(String password, String storedHash) {
        if (storedHash == null || !storedHash.startsWith("$argon2id$")) {
            return false;
        }

        String[] parts = storedHash.split("\\$");
        // parts: ["", "argon2id", salt, hash]
        if (parts.length != 4) return false;

        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
        byte[] actualHash = generateArgon2Hash(password, salt);

        return constantTimeEquals(expectedHash, actualHash);
    }

    private byte[] generateArgon2Hash(String password, byte[] salt) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(MEMORY)
                .withIterations(ITERATIONS)
                .withParallelism(PARALLELISM)
                .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);

        byte[] hash = new byte[HASH_LENGTH];
        generator.generateBytes(password.toCharArray(), hash);
        return hash;
    }

    private boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
