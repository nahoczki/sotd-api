package sotd.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
/**
 * Encrypts and decrypts long-lived Spotify refresh tokens before persistence.
 *
 * <p>The stored payload format is:
 *
 * <ul>
 *   <li>1 byte version marker
 *   <li>12 byte random GCM IV
 *   <li>ciphertext with the GCM authentication tag appended
 * </ul>
 */
public class TokenEncryptionService {

    private static final byte CURRENT_VERSION = 1;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final CryptoProperties cryptoProperties;
    private final SecureRandom secureRandom;

    @Autowired
    public TokenEncryptionService(CryptoProperties cryptoProperties) {
        this(cryptoProperties, new SecureRandom());
    }

    TokenEncryptionService(CryptoProperties cryptoProperties, SecureRandom secureRandom) {
        this.cryptoProperties = cryptoProperties;
        this.secureRandom = secureRandom;
    }

    /**
     * Encrypts a plaintext refresh token using AES-256-GCM.
     */
    public byte[] encrypt(String plaintext) {
        if (!StringUtils.hasText(plaintext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refresh token is missing.");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(1 + GCM_IV_LENGTH + encrypted.length);
            buffer.put(CURRENT_VERSION);
            buffer.put(iv);
            buffer.put(encrypted);
            return buffer.array();
        }
        catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt refresh token.", ex);
        }
    }

    /**
     * Decrypts a previously stored refresh token payload.
     */
    public String decrypt(byte[] ciphertext) {
        if (ciphertext == null || ciphertext.length <= 1 + GCM_IV_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Encrypted token payload is invalid.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(ciphertext);
        byte version = buffer.get();
        if (version != CURRENT_VERSION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Encrypted token version is not supported.");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        }
        catch (GeneralSecurityException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Encrypted token payload is invalid.", ex);
        }
    }

    private SecretKey getSecretKey() {
        if (!StringUtils.hasText(cryptoProperties.getBase64Key())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SOTD_CRYPTO_BASE64_KEY is not configured."
            );
        }

        byte[] decoded = Base64.getDecoder().decode(cryptoProperties.getBase64Key());
        if (decoded.length != 32) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SOTD_CRYPTO_BASE64_KEY must decode to exactly 32 bytes."
            );
        }

        return new SecretKeySpec(decoded, "AES");
    }
}
