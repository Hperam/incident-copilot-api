package dev.harshith.incidentcopilot.service;

import java.util.Optional;

public class NoOpIncidentAiClient implements IncidentAiClient {

	@Override
	public Optional<AiIncidentDraft> analyze(AiAnalysisContext context) {
		return Optional.empty();
	}
}
