package dev.harshith.incidentcopilot.service;

import dev.harshith.incidentcopilot.model.StoredOpenAiCredentialResponse;
import org.springframework.stereotype.Service;

@Service
public class UserCredentialService {

	private final OpenAiCredentialStore openAiCredentialStore;

	public UserCredentialService(OpenAiCredentialStore openAiCredentialStore) {
		this.openAiCredentialStore = openAiCredentialStore;
	}

	public StoredOpenAiCredentialResponse saveOpenAiCredential(String userId, String apiKey, String defaultModel) {
		return openAiCredentialStore.save(userId, apiKey, defaultModel);
	}

	public StoredOpenAiCredentialResponse getOpenAiCredentialStatus(String userId) {
		return openAiCredentialStore.getStatus(userId);
	}

	public void deleteOpenAiCredential(String userId) {
		openAiCredentialStore.delete(userId);
	}

	public OpenAiCredentialStore.StoredOpenAiCredential resolveOpenAiCredential(String userId) {
		return openAiCredentialStore.find(userId).orElse(null);
	}
}
