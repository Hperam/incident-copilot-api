package dev.harshith.incidentcopilot.config;

import dev.harshith.incidentcopilot.service.IncidentAiClient;
import dev.harshith.incidentcopilot.service.NoOpIncidentAiClient;
import dev.harshith.incidentcopilot.service.SpringAiIncidentAiClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IncidentAnalysisProperties.class)
public class IncidentAnalysisConfiguration {

	@Bean
	@ConditionalOnBean(ChatClient.Builder.class)
	IncidentAiClient incidentAiClient(ChatClient.Builder chatClientBuilder) {
		return new SpringAiIncidentAiClient(chatClientBuilder.build());
	}

	@Bean
	@ConditionalOnMissingBean(IncidentAiClient.class)
	IncidentAiClient noOpIncidentAiClient() {
		return new NoOpIncidentAiClient();
	}
}
