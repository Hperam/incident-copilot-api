package dev.harshith.incidentcopilot.service;

import java.util.List;

public record AiIncidentDraft(
		String summary,
		List<String> possibleCauses,
		List<String> suggestedChecks,
		double confidenceScore
) {
}
