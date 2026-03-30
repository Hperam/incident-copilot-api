package dev.harshith.incidentcopilot.service;

import java.util.Optional;

public interface IncidentAiClient {

	Optional<AiIncidentDraft> analyze(AiAnalysisContext context);
}
