package dev.harshith.incidentcopilot.model;

public record AiRequestOptions(
		String openAiApiKey,
		String openAiModel
) {

	public boolean hasApiKey() {
		return openAiApiKey != null && !openAiApiKey.isBlank();
	}
}
