package dev.harshith.incidentcopilot.service;

import dev.harshith.incidentcopilot.model.AiRequestOptions;
import dev.harshith.incidentcopilot.model.IncidentAnalysisRequest;
import java.util.List;

public record AiAnalysisContext(
		IncidentAnalysisRequest request,
		AiRequestOptions aiRequestOptions,
		String promptVersion,
		List<RuleMatch> matchedRules,
		List<RunbookSnippet> runbookSnippets
) {
}
