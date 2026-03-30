package dev.harshith.incidentcopilot.service;

import dev.harshith.incidentcopilot.model.IncidentAnalysisRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedIncidentAnalyzer {

	public List<RuleMatch> analyze(IncidentAnalysisRequest request) {
		String content = (request.errorLog() + "\n" + safe(request.previousIncidentNotes())).toLowerCase(Locale.ROOT);
		List<RuleMatch> matches = new ArrayList<>();

		if (request.recentDeploy()) {
			matches.add(new RuleMatch(
					"recent-deploy",
					0.62,
					"Symptoms started near a deployment, so a rollout regression is a plausible first suspect.",
					List.of("A recent code or config change introduced a regression.", "A dependency, schema, or feature flag changed during deploy."),
					List.of("Compare the failing version against the last known good deploy.", "Check deployment events, feature-flag flips, and rollout percentage changes.")
			));
		}

		if (containsAny(content, "hikari", "jdbc", "sqltransientconnectionexception", "connection refused", "too many connections", "password authentication failed")) {
			matches.add(new RuleMatch(
					"database-connectivity",
					0.83,
					"Database connectivity or pool exhaustion appears in the error details.",
					List.of("The service cannot reach the database or is exhausting its connection pool.", "Database credentials, network policy, or pool sizing may be incorrect."),
					List.of("Verify database health, credentials, and recent secret rotations.", "Inspect connection pool saturation and active connection counts.")
			));
		}

		if (containsAny(content, "outofmemoryerror", "gc overhead limit exceeded", "java heap space", "killed process out of memory")) {
			matches.add(new RuleMatch(
					"memory-pressure",
					0.88,
					"The failure pattern strongly suggests JVM or container memory pressure.",
					List.of("The service exceeded heap or container memory limits.", "A recent code path may be allocating unexpectedly large objects."),
					List.of("Check container restart history and memory graphs.", "Capture a heap dump or inspect allocation-heavy code paths from the latest release.")
			));
		}

		if (containsAny(content, "read timed out", "sockettimeoutexception", "503", "upstream connect error", "connection reset by peer", "no healthy upstream")) {
			matches.add(new RuleMatch(
					"downstream-dependency",
					0.77,
					"Requests to a downstream service look unhealthy or unavailable.",
					List.of("An upstream dependency is timing out or rejecting traffic.", "Network routing, service discovery, or rate limiting may be involved."),
					List.of("Check the health and latency of downstream dependencies.", "Inspect retry bursts, circuit breakers, and recent infrastructure changes.")
			));
		}

		if (containsAny(content, "401", "403", "invalid token", "signatureexception", "access denied", "permission denied")) {
			matches.add(new RuleMatch(
					"authz-authn",
					0.74,
					"Authentication or authorization failures are visible in the incident details.",
					List.of("A token, certificate, or secret may be expired or invalid.", "An authorization policy or role binding may have changed."),
					List.of("Verify recent IAM, secret, or certificate updates.", "Replay one failing request and inspect the auth chain end to end.")
			));
		}

		return matches;
	}

	private boolean containsAny(String content, String... terms) {
		for (String term : terms) {
			if (content.contains(term)) {
				return true;
			}
		}
		return false;
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}
}
