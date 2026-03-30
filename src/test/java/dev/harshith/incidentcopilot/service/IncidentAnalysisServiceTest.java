package dev.harshith.incidentcopilot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.harshith.incidentcopilot.config.IncidentAnalysisProperties;
import dev.harshith.incidentcopilot.model.AnalysisMode;
import dev.harshith.incidentcopilot.model.IncidentAnalysisRequest;
import dev.harshith.incidentcopilot.model.IncidentAnalysisResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IncidentAnalysisServiceTest {

	@TempDir
	Path tempDir;

	@Test
	void fallsBackSafelyWhenAiIsDisabled() throws IOException {
		writeRunbook("db.md", "# Database\nCheck Hikari pool, credentials, and connection refused symptoms.");

		UserCredentialService userCredentialService = userCredentialService(properties(false));
		userCredentialService.saveOpenAiCredential("billing-user", "sk-test", "gpt-4o-mini");

		IncidentAnalysisService service = new IncidentAnalysisService(
				new RuleBasedIncidentAnalyzer(),
				new RunbookRetrievalService(properties(false)),
				new NoOpIncidentAiClient(),
				new NoOpIncidentAiClient(),
				userCredentialService,
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
				"billing-user"
		);

		assertEquals(AnalysisMode.RULE_BASED_FALLBACK, response.analysisMode());
		assertTrue(response.safeFallbackApplied());
		assertTrue(response.audit().matchedRules().contains("database-connectivity"));
		assertTrue(response.audit().retrievedRunbooks().contains("db.md"));
		assertEquals("AI_DISABLED", response.audit().fallbackReason());
	}

	@Test
	void returnsAiAssistedResultWhenStoredCredentialExistsAndConfidenceIsHigh() throws IOException {
		writeRunbook("deploy.md", "# Rollout\nCompare current deploy against last stable revision.");

		UserCredentialService userCredentialService = userCredentialService(properties(true));
		userCredentialService.saveOpenAiCredential("checkout-user", "sk-test", "gpt-4o-mini");

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
				userCredentialService,
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
				"checkout-user"
		);

		assertEquals(AnalysisMode.AI_ASSISTED, response.analysisMode());
		assertFalse(response.safeFallbackApplied());
		assertEquals("NONE", response.audit().fallbackReason());
		assertTrue(response.audit().aiAttempted());
	}

	@Test
	void fallsBackWhenAiReturnsLowConfidence() throws IOException {
		writeRunbook("memory.md", "# Memory\nCheck OOMKilled events, heap usage, and GC pressure.");

		UserCredentialService userCredentialService = userCredentialService(properties(true));
		userCredentialService.saveOpenAiCredential("reporting-user", "sk-test", "gpt-4o-mini");

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
				userCredentialService,
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
				"reporting-user"
		);

		assertEquals(AnalysisMode.RULE_BASED_FALLBACK, response.analysisMode());
		assertTrue(response.safeFallbackApplied());
		assertEquals("LOW_CONFIDENCE_OR_UNAVAILABLE", response.audit().fallbackReason());
	}

	@Test
	void fallsBackWhenUserHasNoStoredCredential() throws IOException {
		writeRunbook("deploy.md", "# Rollout\nCompare current deploy against last stable revision.");

		UserCredentialService userCredentialService = userCredentialService(properties(true));

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
				userCredentialService,
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
				"missing-user"
		);

		assertEquals(AnalysisMode.RULE_BASED_FALLBACK, response.analysisMode());
		assertEquals("USER_API_KEY_MISSING", response.audit().fallbackReason());
		assertFalse(response.audit().aiAttempted());
	}

	@Test
	void fallsBackWhenUserIdHeaderIsMissing() throws IOException {
		writeRunbook("deploy.md", "# Rollout\nCompare current deploy against last stable revision.");

		UserCredentialService userCredentialService = userCredentialService(properties(true));

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
				userCredentialService,
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
				null
		);

		assertEquals(AnalysisMode.RULE_BASED_FALLBACK, response.analysisMode());
		assertEquals("USER_ID_MISSING", response.audit().fallbackReason());
		assertFalse(response.audit().aiAttempted());
	}

	private UserCredentialService userCredentialService(IncidentAnalysisProperties properties) {
		return new UserCredentialService(
				new OpenAiCredentialStore(
						properties,
						new CredentialEncryptionService(properties)
				)
		);
	}

	private IncidentAnalysisProperties properties(boolean aiEnabled) {
		return new IncidentAnalysisProperties(
				aiEnabled,
				"test-v1",
				0.65,
				2,
				tempDir.resolve("runbooks").toString(),
				"https://api.openai.com",
				"gpt-4o-mini",
				Duration.ofSeconds(5),
				tempDir.resolve("credentials"),
				tempDir.resolve("security/master.key")
		);
	}

	private void writeRunbook(String fileName, String content) throws IOException {
		Files.createDirectories(tempDir.resolve("runbooks"));
		Files.writeString(tempDir.resolve("runbooks").resolve(fileName), content);
	}
}
