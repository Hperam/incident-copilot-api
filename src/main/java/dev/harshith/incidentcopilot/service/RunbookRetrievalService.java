package dev.harshith.incidentcopilot.service;

import dev.harshith.incidentcopilot.config.IncidentAnalysisProperties;
import dev.harshith.incidentcopilot.model.IncidentAnalysisRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class RunbookRetrievalService {

	private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-zA-Z0-9]+");

	private final IncidentAnalysisProperties properties;
	private final PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

	public RunbookRetrievalService(IncidentAnalysisProperties properties) {
		this.properties = properties;
	}

	public List<RunbookSnippet> retrieve(IncidentAnalysisRequest request) {
		Set<String> queryTokens = tokenize(request.serviceName() + " " + request.environment() + " " + request.errorLog() + " " + safe(request.previousIncidentNotes()));
		List<ScoredSnippet> externalSnippets = externalSnippets(queryTokens);
		List<ScoredSnippet> classpathSnippets = classpathSnippets(queryTokens);

		return Stream.concat(externalSnippets.stream(), classpathSnippets.stream())
				.filter(scored -> scored.score() > 0)
				.sorted(Comparator.comparingInt(ScoredSnippet::score).reversed())
				.distinct()
				.limit(properties.maxRunbookSnippets())
				.map(ScoredSnippet::snippet)
				.toList();
	}

	private ScoredSnippet toSnippet(Path path, Set<String> queryTokens) {
		try {
			String content = Files.readString(path, StandardCharsets.UTF_8);
			Set<String> docTokens = tokenize(content);
			int score = (int) queryTokens.stream().filter(docTokens::contains).count();
			return new ScoredSnippet(score, new RunbookSnippet(path.getFileName().toString(), excerpt(content)));
		}
		catch (IOException exception) {
			return new ScoredSnippet(0, new RunbookSnippet(path.getFileName().toString(), ""));
		}
	}

	private List<ScoredSnippet> externalSnippets(Set<String> queryTokens) {
		String location = properties.runbookLocation();
		if (location == null || location.startsWith("classpath:")) {
			return List.of();
		}

		Path runbookDirectory = Path.of(location);
		if (!Files.isDirectory(runbookDirectory)) {
			return List.of();
		}

		try (Stream<Path> files = Files.list(runbookDirectory)) {
			return files
					.filter(path -> path.getFileName().toString().endsWith(".md"))
					.map(path -> toSnippet(path, queryTokens))
					.toList();
		}
		catch (IOException exception) {
			return List.of();
		}
	}

	private List<ScoredSnippet> classpathSnippets(Set<String> queryTokens) {
		String location = properties.runbookLocation();
		if (location == null || !location.startsWith("classpath:")) {
			return List.of();
		}

		String pattern = location.replace("classpath:", "classpath*:") + (location.endsWith("/") ? "*.md" : "/*.md");

		try {
			Resource[] resources = resourcePatternResolver.getResources(pattern);
			return Stream.of(resources)
					.map(resource -> toSnippet(resource, queryTokens))
					.toList();
		}
		catch (IOException exception) {
			return List.of();
		}
	}

	private ScoredSnippet toSnippet(Resource resource, Set<String> queryTokens) {
		try (var inputStream = resource.getInputStream()) {
			String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
			Set<String> docTokens = tokenize(content);
			int score = (int) queryTokens.stream().filter(docTokens::contains).count();
			return new ScoredSnippet(score, new RunbookSnippet(resource.getFilename(), excerpt(content)));
		}
		catch (IOException exception) {
			return new ScoredSnippet(0, new RunbookSnippet(resource.getFilename(), ""));
		}
	}

	private Set<String> tokenize(String value) {
		return TOKEN_SPLIT.splitAsStream(value.toLowerCase(Locale.ROOT))
				.filter(token -> token.length() > 2)
				.collect(java.util.stream.Collectors.toSet());
	}

	private String excerpt(String content) {
		String normalized = content.replaceAll("\\s+", " ").trim();
		if (normalized.length() <= 320) {
			return normalized;
		}
		return normalized.substring(0, 320) + "...";
	}

	private String safe(String value) {
		return value == null ? "" : value;
	}

	private record ScoredSnippet(int score, RunbookSnippet snippet) {
	}
}
