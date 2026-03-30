package dev.harshith.incidentcopilot.model;

import java.util.List;

public record IncidentAnalysisAudit(
		long latencyMs,
		String promptVersion,
		boolean aiAttempted,
		String fallbackReason,
		List<String> matchedRules,
		List<String> retrievedRunbooks
) {
}
