package dev.harshith.incidentcopilot.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.harshith.incidentcopilot.config.IncidentAnalysisProperties;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class OpenAiIncidentAiClient implements IncidentAiClient {

	private final RestClient restClient;
	private final IncidentAnalysisProperties properties;
	private final ObjectMapper objectMapper = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	public OpenAiIncidentAiClient(RestClient restClient, IncidentAnalysisProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public Optional<AiIncidentDraft> analyze(AiAnalysisContext context) {
		if (!context.aiRequestOptions().hasApiKey()) {
			return Optional.empty();
		}

		try {
			OpenAiChatResponse response = restClient.post()
					.uri("/v1/chat/completions")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + context.aiRequestOptions().openAiApiKey())
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.body(new OpenAiChatRequest(
							resolveModel(context),
							0.2,
							List.of(
									new OpenAiMessage("system", systemPrompt()),
									new OpenAiMessage("user", userPrompt(context))
							),
							new ResponseFormat("json_object")
					))
					.retrieve()
					.body(OpenAiChatResponse.class);

			if (response == null || response.choices() == null || response.choices().isEmpty()) {
				return Optional.empty();
			}

			String raw = response.choices().getFirst().message().content();
			if (raw == null || raw.isBlank()) {
				return Optional.empty();
			}

			return Optional.of(objectMapper.readValue(sanitize(raw), AiIncidentDraft.class));
		}
		catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException exception) {
			return Optional.empty();
		}
	}

	private String resolveModel(AiAnalysisContext context) {
		String requestedModel = context.aiRequestOptions().openAiModel();
		if (requestedModel != null && !requestedModel.isBlank()) {
			return requestedModel;
		}
		return properties.defaultOpenaiModel();
	}

	private String systemPrompt() {
		return """
				You are an incident triage assistant for backend engineers.
				Return strict JSON with keys: summary, possibleCauses, suggestedChecks, confidenceScore.
				Keep the summary to 1-2 sentences.
				Possible causes and suggested checks must be arrays of concise strings.
				Set confidenceScore between 0.0 and 1.0.
				If evidence is weak, lower the confidence rather than sounding certain.
				Do not invent metrics, stack frames, or deployment facts.
				""";
	}

	private String userPrompt(AiAnalysisContext context) {
		return """
				Prompt version: %s

				Incident request:
				- serviceName: %s
				- environment: %s
				- recentDeploy: %s
				- errorLog:
				%s
				- previousIncidentNotes:
				%s

				Rule matches:
				%s

				Runbook snippets:
				%s

				Generate a short incident triage response.
				""".formatted(
				context.promptVersion(),
				context.request().serviceName(),
				context.request().environment(),
				context.request().recentDeploy(),
				context.request().errorLog(),
				safe(context.request().previousIncidentNotes()),
				context.matchedRules().stream()
						.map(rule -> "- " + rule.id() + ": " + rule.summaryHint())
						.reduce("", (left, right) -> left + right + "\n"),
				context.runbookSnippets().stream()
						.map(snippet -> "- " + snippet.fileName() + ": " + snippet.excerpt())
						.reduce("", (left, right) -> left + right + "\n")
		);
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private String sanitize(String raw) {
		return raw.replace("```json", "").replace("```", "").trim();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record OpenAiChatResponse(List<OpenAiChoice> choices) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record OpenAiChoice(OpenAiResponseMessage message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record OpenAiResponseMessage(String content) {
	}

	private record OpenAiChatRequest(
			String model,
			double temperature,
			List<OpenAiMessage> messages,
			@JsonProperty("response_format") ResponseFormat responseFormat
	) {
	}

	private record OpenAiMessage(String role, String content) {
	}

	private record ResponseFormat(String type) {
	}
}
