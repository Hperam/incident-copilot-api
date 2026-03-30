package dev.harshith.incidentcopilot.service;

import dev.harshith.incidentcopilot.config.IncidentAnalysisProperties;
import dev.harshith.incidentcopilot.model.AiRequestOptions;
import dev.harshith.incidentcopilot.model.AnalysisMode;
import dev.harshith.incidentcopilot.model.IncidentAnalysisAudit;
import dev.harshith.incidentcopilot.model.IncidentAnalysisRequest;
import dev.harshith.incidentcopilot.model.IncidentAnalysisResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IncidentAnalysisService {

	private static final Logger logger = LoggerFactory.getLogger(IncidentAnalysisService.class);

	private final RuleBasedIncidentAnalyzer ruleBasedIncidentAnalyzer;
	private final RunbookRetrievalService runbookRetrievalService;
	private final IncidentAiClient incidentAiClient;
	private final NoOpIncidentAiClient noOpIncidentAiClient;
	private final UserCredentialService userCredentialService;
	private final IncidentAnalysisProperties properties;
	private final MeterRegistry meterRegistry;

	public IncidentAnalysisService(
			RuleBasedIncidentAnalyzer ruleBasedIncidentAnalyzer,
			RunbookRetrievalService runbookRetrievalService,
			IncidentAiClient incidentAiClient,
			NoOpIncidentAiClient noOpIncidentAiClient,
			UserCredentialService userCredentialService,
			IncidentAnalysisProperties properties,
			MeterRegistry meterRegistry
	) {
		this.ruleBasedIncidentAnalyzer = ruleBasedIncidentAnalyzer;
		this.runbookRetrievalService = runbookRetrievalService;
		this.incidentAiClient = incidentAiClient;
		this.noOpIncidentAiClient = noOpIncidentAiClient;
		this.userCredentialService = userCredentialService;
		this.properties = properties;
		this.meterRegistry = meterRegistry;
	}

	@Observed(name = "incident.analysis")
	public IncidentAnalysisResponse analyze(IncidentAnalysisRequest request, String userId) {
		Timer.Sample timer = Timer.start(meterRegistry);
		List<RuleMatch> matchedRules = ruleBasedIncidentAnalyzer.analyze(request);
		List<RunbookSnippet> runbookSnippets = runbookRetrievalService.retrieve(request);
		AiRequestOptions aiRequestOptions = resolveAiRequestOptions(userId);
		IncidentAiClient aiClient = properties.aiEnabled() && aiRequestOptions.hasApiKey() ? incidentAiClient : noOpIncidentAiClient;
		AiAnalysisContext context = new AiAnalysisContext(request, aiRequestOptions, properties.promptVersion(), matchedRules, runbookSnippets);

		boolean aiAttempted = properties.aiEnabled() && aiRequestOptions.hasApiKey() && !(aiClient instanceof NoOpIncidentAiClient);
		String fallbackReason = "NONE";
		IncidentAnalysisResponse response;

		Optional<AiIncidentDraft> aiDraft = aiClient.analyze(context);
		if (aiDraft.isPresent() && isUsable(aiDraft.get())) {
			AiIncidentDraft draft = aiDraft.get();
			response = new IncidentAnalysisResponse(
					draft.summary(),
					trimList(draft.possibleCauses()),
					trimList(draft.suggestedChecks()),
					draft.confidenceScore(),
					AnalysisMode.AI_ASSISTED,
					false,
					audit(0L, aiAttempted, fallbackReason, matchedRules, runbookSnippets)
			);
		}
		else {
			fallbackReason = fallbackReason(userId, aiRequestOptions);
			response = buildFallbackResponse(request, matchedRules, runbookSnippets, aiAttempted, fallbackReason, 0L);
		}

		long latencyMs = TimeUnit.NANOSECONDS.toMillis(
				timer.stop(Timer.builder("incident.analysis.latency").register(meterRegistry))
		);
		IncidentAnalysisAudit audit = audit(latencyMs, aiAttempted, fallbackReason, matchedRules, runbookSnippets);
		IncidentAnalysisResponse completed = new IncidentAnalysisResponse(
				response.summary(),
				response.possibleCauses(),
				response.suggestedChecks(),
				response.confidenceScore(),
				response.analysisMode(),
				response.safeFallbackApplied(),
				audit
		);

		Counter.builder("incident.analysis.requests")
				.tag("mode", completed.analysisMode().name())
				.tag("fallbackReason", audit.fallbackReason())
				.register(meterRegistry)
				.increment();

		logger.info(
				"Incident analyzed service={} env={} mode={} fallbackReason={} matchedRules={} runbooks={} latencyMs={}",
				request.serviceName(),
				request.environment(),
				completed.analysisMode(),
				audit.fallbackReason(),
				audit.matchedRules(),
				audit.retrievedRunbooks(),
				audit.latencyMs()
		);

		return completed;
	}

	private AiRequestOptions resolveAiRequestOptions(String userId) {
		if (userId == null || userId.isBlank()) {
			return new AiRequestOptions(null, null);
		}

		OpenAiCredentialStore.StoredOpenAiCredential credential = userCredentialService.resolveOpenAiCredential(userId);
		if (credential == null) {
			return new AiRequestOptions(null, null);
		}

		return new AiRequestOptions(credential.apiKey(), credential.defaultModel());
	}

	private String fallbackReason(String userId, AiRequestOptions aiRequestOptions) {
		if (!properties.aiEnabled()) {
			return "AI_DISABLED";
		}
		if (userId == null || userId.isBlank()) {
			return "USER_ID_MISSING";
		}
		if (!aiRequestOptions.hasApiKey()) {
			return "USER_API_KEY_MISSING";
		}
		return "LOW_CONFIDENCE_OR_UNAVAILABLE";
	}

	private boolean isUsable(AiIncidentDraft draft) {
		return draft.summary() != null
				&& !draft.summary().isBlank()
				&& draft.possibleCauses() != null
				&& !draft.possibleCauses().isEmpty()
				&& draft.suggestedChecks() != null
				&& !draft.suggestedChecks().isEmpty()
				&& draft.confidenceScore() >= properties.confidenceThreshold()
				&& draft.confidenceScore() <= 1.0;
	}

	private IncidentAnalysisResponse buildFallbackResponse(
			IncidentAnalysisRequest request,
			List<RuleMatch> matchedRules,
			List<RunbookSnippet> runbookSnippets,
			boolean aiAttempted,
			String fallbackReason,
			long latencyMs
	) {
		RuleMatch strongestRule = matchedRules.stream()
				.max(Comparator.comparingDouble(RuleMatch::confidenceScore))
				.orElse(null);

		Set<String> causes = new LinkedHashSet<>();
		Set<String> checks = new LinkedHashSet<>();

		if (strongestRule != null) {
			causes.addAll(strongestRule.causes());
			checks.addAll(strongestRule.checks());
		}

		for (RunbookSnippet snippet : runbookSnippets) {
			checks.add("Review guidance in runbook " + snippet.fileName() + ".");
		}

		if (causes.isEmpty()) {
			causes.add("There is not enough trustworthy evidence for a model-led diagnosis, so the response stays intentionally conservative.");
			causes.add("The incident may be tied to a recent change in dependencies, infrastructure, or configuration.");
		}

		if (checks.isEmpty()) {
			checks.add("Check the last successful deploy, service health, and dependency status before taking corrective action.");
			checks.add("Correlate logs, traces, and metrics around the first observed failure timestamp.");
		}

		String summary = strongestRule != null
				? strongestRule.summaryHint()
				: "The service has an active incident, but the system does not have enough high-confidence evidence to trust an AI-generated diagnosis.";

		return new IncidentAnalysisResponse(
				summary + " Rule-based fallback is being returned to keep the response safe.",
				new ArrayList<>(causes),
				new ArrayList<>(checks),
				strongestRule != null ? strongestRule.confidenceScore() : 0.35,
				AnalysisMode.RULE_BASED_FALLBACK,
				true,
				audit(latencyMs, aiAttempted, fallbackReason, matchedRules, runbookSnippets)
		);
	}

	private IncidentAnalysisAudit audit(
			long latencyMs,
			boolean aiAttempted,
			String fallbackReason,
			List<RuleMatch> matchedRules,
			List<RunbookSnippet> runbookSnippets
	) {
		return new IncidentAnalysisAudit(
				latencyMs,
				properties.promptVersion(),
				aiAttempted,
				fallbackReason,
				matchedRules.stream().map(RuleMatch::id).toList(),
				runbookSnippets.stream().map(RunbookSnippet::fileName).toList()
		);
	}

	private List<String> trimList(List<String> values) {
		return values.stream()
				.filter(value -> value != null && !value.isBlank())
				.limit(4)
				.toList();
	}
}
