package dev.harshith.incidentcopilot.model;

import java.util.List;

public record IncidentAnalysisResponse(
		String summary,
		List<String> possibleCauses,
		List<String> suggestedChecks,
		double confidenceScore,
		AnalysisMode analysisMode,
		boolean safeFallbackApplied,
		IncidentAnalysisAudit audit
) {
}
