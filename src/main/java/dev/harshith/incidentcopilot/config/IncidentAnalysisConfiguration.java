package dev.harshith.incidentcopilot.config;

import dev.harshith.incidentcopilot.service.IncidentAiClient;
import dev.harshith.incidentcopilot.service.NoOpIncidentAiClient;
import dev.harshith.incidentcopilot.service.OpenAiIncidentAiClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(IncidentAnalysisProperties.class)
public class IncidentAnalysisConfiguration {

	@Bean
	RestClient incidentAiRestClient(RestClient.Builder restClientBuilder, IncidentAnalysisProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.aiTimeout());
		requestFactory.setReadTimeout(properties.aiTimeout());

		return restClientBuilder
				.baseUrl(properties.openaiBaseUrl())
				.requestFactory(requestFactory)
				.build();
	}

	@Bean
	IncidentAiClient incidentAiClient(RestClient incidentAiRestClient, IncidentAnalysisProperties properties) {
		return new OpenAiIncidentAiClient(incidentAiRestClient, properties);
	}

	@Bean
	NoOpIncidentAiClient noOpIncidentAiClient() {
		return new NoOpIncidentAiClient();
	}
}
