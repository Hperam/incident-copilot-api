package dev.harshith.incidentcopilot.web;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.harshith.incidentcopilot.service.AiIncidentDraft;
import dev.harshith.incidentcopilot.service.IncidentAiClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IncidentAnalysisControllerTest {

	private static Path tempRoot;

	@Autowired
	private MockMvc mockMvc;

	@MockBean(name = "incidentAiClient")
	private IncidentAiClient incidentAiClient;

	@BeforeAll
	static void beforeAll() throws IOException {
		tempRoot = Files.createTempDirectory("incident-copilot-controller-test");
	}

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("incident.analysis.runbook-location", () -> "classpath:/runbooks");
		registry.add("incident.analysis.credential-store-directory", () -> tempRoot.resolve("credentials").toString());
		registry.add("incident.analysis.master-key-path", () -> tempRoot.resolve("security/master.key").toString());
	}

	@Test
	void returnsFallbackWhenUserDoesNotHaveStoredCredential() throws Exception {
		mockMvc.perform(post("/incidents/analyze")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-User-Id", "missing-user")
						.content("""
								{
								  "serviceName": "payment-service",
								  "errorLog": "java.lang.OutOfMemoryError: Java heap space",
								  "environment": "production",
								  "recentDeploy": false
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.analysisMode", equalTo("RULE_BASED_FALLBACK")))
				.andExpect(jsonPath("$.audit.fallbackReason", equalTo("USER_API_KEY_MISSING")));
	}

	@Test
	void savesCredentialAndUsesStoredKeyForAnalysis() throws Exception {
		when(incidentAiClient.analyze(any())).thenReturn(Optional.of(new AiIncidentDraft(
				"Stored key was used for AI-assisted analysis.",
				java.util.List.of("A recent config change may have introduced the issue."),
				java.util.List.of("Diff the last rollout against the prior stable release."),
				0.88
		)));

		mockMvc.perform(put("/users/demo-user/credentials/openai")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "apiKey": "sk-demo-user-key",
								  "defaultModel": "gpt-4o-mini"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId", equalTo("demo-user")))
				.andExpect(jsonPath("$.credentialStored", equalTo(true)))
				.andExpect(jsonPath("$.provider", equalTo("openai")))
				.andExpect(jsonPath("$.defaultModel", equalTo("gpt-4o-mini")));

		mockMvc.perform(post("/incidents/analyze")
						.contentType(MediaType.APPLICATION_JSON)
						.header("X-User-Id", "demo-user")
						.content("""
								{
								  "serviceName": "checkout-service",
								  "errorLog": "HTTP 503 upstream connect error",
								  "environment": "staging",
								  "recentDeploy": true
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.analysisMode", equalTo("AI_ASSISTED")))
				.andExpect(jsonPath("$.audit.fallbackReason", equalTo("NONE")));

		mockMvc.perform(get("/users/demo-user/credentials/openai"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.credentialStored", equalTo(true)))
				.andExpect(jsonPath("$.provider", equalTo("openai")))
				.andExpect(jsonPath("$.defaultModel", equalTo("gpt-4o-mini")));

		mockMvc.perform(delete("/users/demo-user/credentials/openai"))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/users/demo-user/credentials/openai"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.credentialStored", equalTo(false)));
	}

	@Test
	void returnsProblemDetailForInvalidIncidentRequest() throws Exception {
		mockMvc.perform(post("/incidents/analyze")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "serviceName": "",
								  "errorLog": "",
								  "environment": ""
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title", equalTo("Invalid incident analysis request")))
				.andExpect(jsonPath("$.fieldErrors", hasKey("serviceName")))
				.andExpect(jsonPath("$.fieldErrors", hasKey("errorLog")))
				.andExpect(jsonPath("$.fieldErrors", hasKey("environment")));
	}
}
