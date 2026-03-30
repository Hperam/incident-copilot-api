package dev.harshith.incidentcopilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.harshith.incidentcopilot.config.IncidentAnalysisProperties;
import dev.harshith.incidentcopilot.model.StoredOpenAiCredentialResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class OpenAiCredentialStore {

	private static final Pattern SAFE_USER_ID = Pattern.compile("[a-zA-Z0-9_-]{3,64}");

	private final IncidentAnalysisProperties properties;
	private final CredentialEncryptionService credentialEncryptionService;
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	public OpenAiCredentialStore(
			IncidentAnalysisProperties properties,
			CredentialEncryptionService credentialEncryptionService
	) {
		this.properties = properties;
		this.credentialEncryptionService = credentialEncryptionService;
	}

	public StoredOpenAiCredentialResponse save(String userId, String apiKey, String defaultModel) {
		String normalizedUserId = normalizeUserId(userId);
		Instant now = Instant.now();
		Path credentialPath = credentialPath(normalizedUserId);

		try {
			Files.createDirectories(properties.credentialStoreDirectory());
			StoredCredentialFile existing = readFile(credentialPath).orElse(null);
			CredentialEncryptionService.EncryptedPayload encryptedPayload = credentialEncryptionService.encrypt(apiKey);
			StoredCredentialFile updated = new StoredCredentialFile(
					"openai",
					encryptedPayload.iv(),
					encryptedPayload.ciphertext(),
					defaultModel == null || defaultModel.isBlank() ? properties.defaultOpenaiModel() : defaultModel,
					existing == null ? now : existing.createdAt(),
					now
			);
			objectMapper.writeValue(credentialPath.toFile(), updated);
			return toStatus(normalizedUserId, updated);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not persist the encrypted OpenAI credential.", exception);
		}
	}

	public Optional<StoredOpenAiCredential> find(String userId) {
		String normalizedUserId = normalizeUserId(userId);
		return readFile(credentialPath(normalizedUserId))
				.map(file -> new StoredOpenAiCredential(
						normalizedUserId,
						credentialEncryptionService.decrypt(
								new CredentialEncryptionService.EncryptedPayload(file.iv(), file.ciphertext())
						),
						file.defaultModel(),
						file.updatedAt()
				));
	}

	public StoredOpenAiCredentialResponse getStatus(String userId) {
		String normalizedUserId = normalizeUserId(userId);
		return readFile(credentialPath(normalizedUserId))
				.map(file -> toStatus(normalizedUserId, file))
				.orElse(new StoredOpenAiCredentialResponse(
						normalizedUserId,
						"openai",
						false,
						null,
						null
				));
	}

	public void delete(String userId) {
		try {
			Files.deleteIfExists(credentialPath(normalizeUserId(userId)));
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not delete the stored OpenAI credential.", exception);
		}
	}

	private Optional<StoredCredentialFile> readFile(Path credentialPath) {
		if (!Files.exists(credentialPath)) {
			return Optional.empty();
		}

		try {
			return Optional.of(objectMapper.readValue(
					Files.readString(credentialPath, StandardCharsets.UTF_8),
					StoredCredentialFile.class
			));
		}
		catch (IOException exception) {
			throw new IllegalStateException("Could not read the stored OpenAI credential.", exception);
		}
	}

	private StoredOpenAiCredentialResponse toStatus(String userId, StoredCredentialFile file) {
		return new StoredOpenAiCredentialResponse(
				userId,
				file.provider(),
				true,
				file.defaultModel(),
				file.updatedAt()
		);
	}

	private Path credentialPath(String userId) {
		return properties.credentialStoreDirectory().resolve(userId + ".json");
	}

	private String normalizeUserId(String userId) {
		if (userId == null || !SAFE_USER_ID.matcher(userId).matches()) {
			throw new IllegalArgumentException("userId must match [a-zA-Z0-9_-]{3,64}");
		}
		return userId;
	}

	record StoredCredentialFile(
			String provider,
			String iv,
			String ciphertext,
			String defaultModel,
			Instant createdAt,
			Instant updatedAt
	) {
	}

	public record StoredOpenAiCredential(
			String userId,
			String apiKey,
			String defaultModel,
			Instant updatedAt
	) {
	}
}
