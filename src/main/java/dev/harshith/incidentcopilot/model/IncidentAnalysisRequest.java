package dev.harshith.incidentcopilot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IncidentAnalysisRequest(
		@NotBlank @Size(max = 120) String serviceName,
		@NotBlank @Size(max = 20000) String errorLog,
		@NotBlank @Size(max = 80) String environment,
		boolean recentDeploy,
		@Size(max = 8000) String previousIncidentNotes
) {
}
