package dev.harshith.incidentcopilot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.harshith.incidentcopilot.config.IncidentAnalysisProperties;
import dev.harshith.incidentcopilot.model.AiRequestOptions;
import dev.harshith.incidentcopilot.model.AnalysisMode;
import dev.harshith.incidentcopilot.model.IncidentAnalysisRequest;
import dev.harshith.incidentcopilot.model.IncidentAnalysisResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncidentAnalysisServiceTest {

	@TempDir
	Path tempDir;

	@Test
	void fallsBackSafelyWhenAiIsDisabled() throws IOException {
		writeRunbook("db.md", "# Database\nCheck Hikari pool, credentials, and connection refused symptoms.");

		IncidentAnalysisService service = new IncidentAnalysisService(
				new RuleBasedIncidentAnalyzer(),
				new RunbookRetrievalService(properties(false)),
				new NoOpIncidentAiClient(),
				new NoOpIncidentAiClient(),
				properties(false),
				new SimpleMeterRegistry()
		);

		IncidentAnalysisResponse response = service.analyze(
				new IncidentAnalysisRequest(
						"billing-service",
						"org.postgresql.util.PSQLException: Connection refused. HikariPool-1 timeout",
						"production",
						true,
						"Previous incident involved rotated DB credentials."
				),
				new AiRequestOptions("sk-test", null)
		);

		assertEquals(AnalysisMode.RULE_BASED_FALLBACK, response.analysisMode());
		assertTrue(response.safeFallbackApplied());
		assertTrue(response.audit().matchedRules().contains("database-connectivity"));
		assertTrue(response.audit().retrievedRunbooks().contains("db.md"));
		assertEquals("AI_DISABLED", response.audit().fallbackReason());
	}

	@Test
	void returnsAiAssistedResultWhenConfidenceIsHigh() throws IOException {
		writeRunbook("deploy.md", "# Rollout\nCompare current deploy against last stable revision.");

		IncidentAiClient aiClient = context -> Optional.of(new AiIncidentDraft(
				"A recent deploy likely introduced a config regression in checkout-service.",
				java.util.List.of("A feature flag or config value changed during rollout."),
				java.util.List.of("Compare the new deploy manifest to the previous version."),
				0.84
		));

		IncidentAnalysisService service = new IncidentAnalysisService(
				new RuleBasedIncidentAnalyzer(),
				new RunbookRetrievalService(properties(true)),
				aiClient,
				new NoOpIncidentAiClient(),
				properties(true),
				new SimpleMeterRegistry()
		);

		IncidentAnalysisResponse response = service.analyze(
				new IncidentAnalysisRequest(
						"checkout-service",
						"HTTP 503 upstream connect error",
						"staging",
						true,
						null
				),
				new AiRequestOptions("sk-test", "gpt-4o-mini")
		);

		assertEquals(AnalysisMode.AI_ASSISTED, response.analysisMode());
		assertFalse(response.safeFallbackApplied());
		assertEquals("NONE", response.audit().fallbackReason());
		assertTrue(response.audit().aiAttempted());
	}

	@Test
	void fallsBackWhenAiReturnsLowConfidence() throws IOException {
		writeRunbook("memory.md", "# Memory\nCheck OOMKilled events, heap usage, and GC pressure.");

		IncidentAiClient aiClient = context -> Optional.of(new AiIncidentDraft(
				"Maybe memory pressure is involved.",
				java.util.List.of("Possible heap growth."),
				java.util.List.of("Check memory usage."),
				0.42
		));

		IncidentAnalysisService service = new IncidentAnalysisService(
				new RuleBasedIncidentAnalyzer(),
				new RunbookRetrievalService(properties(true)),
				aiClient,
				new NoOpIncidentAiClient(),
				properties(true),
				new SimpleMeterRegistry()
		);

		IncidentAnalysisResponse response = service.analyze(
				new IncidentAnalysisRequest(
						"reporting-service",
						"java.lang.OutOfMemoryError: Java heap space",
						"production",
						false,
						null
				),
				new AiRequestOptions("sk-test", null)
		);

		assertEquals(AnalysisMode.RULE_BASED_FALLBACK, response.analysisMode());
		assertTrue(response.safeFallbackApplied());
		assertEquals("LOW_CONFIDENCE_OR_UNAVAILABLE", response.audit().fallbackReason());
	}

	@Test
	void fallsBackWhenRequestDoesNotBringItsOwnApiKey() throws IOException {
		writeRunbook("deploy.md", "# Rollout\nCompare current deploy against last stable revision.");

		IncidentAnalysisService service = new IncidentAnalysisService(
				new RuleBasedIncidentAnalyzer(),
				new RunbookRetrievalService(properties(true)),
				context -> Optional.of(new AiIncidentDraft(
						"Should not be used.",
						java.util.List.of("unused"),
						java.util.List.of("unused"),
						0.99
				)),
				new NoOpIncidentAiClient(),
				properties(true),
				new SimpleMeterRegistry()
		);

		IncidentAnalysisResponse response = service.analyze(
				new IncidentAnalysisRequest(
						"checkout-service",
						"HTTP 503 upstream connect error",
						"staging",
						true,
						null
				),
				new AiRequestOptions(null, null)
		);

		assertEquals(AnalysisMode.RULE_BASED_FALLBACK, response.analysisMode());
		assertEquals("REQUEST_API_KEY_MISSING", response.audit().fallbackReason());
		assertFalse(response.audit().aiAttempted());
	}

	private IncidentAnalysisProperties properties(boolean aiEnabled) {
		return new IncidentAnalysisProperties(
				aiEnabled,
				"test-v1",
				0.65,
				2,
				tempDir.toString(),
				"https://api.openai.com",
				"gpt-4o-mini",
				java.time.Duration.ofSeconds(20)
		);
	}

	private void writeRunbook(String fileName, String content) throws IOException {
		Files.writeString(tempDir.resolve(fileName), content);
	}
}
