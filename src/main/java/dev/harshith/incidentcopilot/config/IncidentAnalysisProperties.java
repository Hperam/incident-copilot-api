package dev.harshith.incidentcopilot.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "incident.analysis")
public record IncidentAnalysisProperties(
		boolean aiEnabled,
		String promptVersion,
		@DecimalMin("0.0") @DecimalMax("1.0") double confidenceThreshold,
		@Min(1) int maxRunbookSnippets,
		String runbookLocation
) {
}
