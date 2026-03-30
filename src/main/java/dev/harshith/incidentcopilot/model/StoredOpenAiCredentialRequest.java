package dev.harshith.incidentcopilot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoredOpenAiCredentialRequest(
		@NotBlank @Size(max = 200) String apiKey,
		@Size(max = 80) String defaultModel
) {
}
