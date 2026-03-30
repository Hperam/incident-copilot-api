package dev.harshith.incidentcopilot.service;

import java.util.List;

public record RuleMatch(
		String id,
		double confidenceScore,
		String summaryHint,
		List<String> causes,
		List<String> checks
) {
}
