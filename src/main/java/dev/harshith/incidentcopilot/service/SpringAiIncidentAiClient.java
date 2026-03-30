package dev.harshith.incidentcopilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClient;

public class SpringAiIncidentAiClient implements IncidentAiClient {

	private final ChatClient chatClient;
	private final ObjectMapper objectMapper = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	public SpringAiIncidentAiClient(ChatClient chatClient) {
		this.chatClient = chatClient;
	}

	@Override
	public Optional<AiIncidentDraft> analyze(AiAnalysisContext context) {
		try {
			String raw = chatClient.prompt()
					.system(systemPrompt())
					.user(userPrompt(context))
					.call()
					.content();
			if (raw == null || raw.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(objectMapper.readValue(sanitize(raw), AiIncidentDraft.class));
		}
		catch (RuntimeException | JsonProcessingException exception) {
			return Optional.empty();
		}
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
}
