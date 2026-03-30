package dev.harshith.incidentcopilot.model;

import java.time.Instant;

public record StoredOpenAiCredentialResponse(
		String userId,
		String provider,
		boolean credentialStored,
		String defaultModel,
		Instant updatedAt
) {
}
