package dev.harshith.incidentcopilot.service;

import dev.harshith.incidentcopilot.config.IncidentAnalysisProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class CredentialEncryptionService {

	private static final String AES = "AES";
	private static final String AES_GCM = "AES/GCM/NoPadding";
	private static final int IV_LENGTH = 12;
	private static final int TAG_LENGTH = 128;

	private final IncidentAnalysisProperties properties;
	private final SecureRandom secureRandom = new SecureRandom();
	private final SecretKey secretKey;

	public CredentialEncryptionService(IncidentAnalysisProperties properties) {
		this.properties = properties;
		this.secretKey = loadOrCreateSecretKey();
	}

	public EncryptedPayload encrypt(String plaintext) {
		try {
			byte[] iv = new byte[IV_LENGTH];
			secureRandom.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(AES_GCM);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

			return new EncryptedPayload(
					Base64.getEncoder().encodeToString(iv),
					Base64.getEncoder().encodeToString(ciphertext)
			);
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Could not encrypt the OpenAI credential.", exception);
		}
	}

	public String decrypt(EncryptedPayload payload) {
		try {
			Cipher cipher = Cipher.getInstance(AES_GCM);
			cipher.init(
					Cipher.DECRYPT_MODE,
					secretKey,
					new GCMParameterSpec(TAG_LENGTH, Base64.getDecoder().decode(payload.iv()))
			);
			byte[] plaintext = cipher.doFinal(Base64.getDecoder().decode(payload.ciphertext()));
			return new String(plaintext, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Could not decrypt the stored OpenAI credential.", exception);
		}
	}

	private SecretKey loadOrCreateSecretKey() {
		try {
			if (Files.exists(properties.masterKeyPath())) {
				String encoded = Files.readString(properties.masterKeyPath(), StandardCharsets.UTF_8).trim();
				byte[] decoded = Base64.getDecoder().decode(encoded);
				return new SecretKeySpec(decoded, AES);
			}

			Files.createDirectories(properties.masterKeyPath().getParent());
			KeyGenerator keyGenerator = KeyGenerator.getInstance(AES);
			keyGenerator.init(256);
			SecretKey generated = keyGenerator.generateKey();
			String encoded = Base64.getEncoder().encodeToString(generated.getEncoded());
			Files.writeString(properties.masterKeyPath(), encoded, StandardCharsets.UTF_8);
			return generated;
		}
		catch (IOException | GeneralSecurityException exception) {
			throw new IllegalStateException("Could not initialize the encryption key for stored credentials.", exception);
		}
	}

	public record EncryptedPayload(String iv, String ciphertext) {
	}
}
