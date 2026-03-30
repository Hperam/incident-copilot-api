package dev.harshith.incidentcopilot.web;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"incident.analysis.runbook-location=classpath:/runbooks"
})
@AutoConfigureMockMvc
class IncidentAnalysisControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void returnsFallbackAnalysisForValidRequest() throws Exception {
		mockMvc.perform(post("/incidents/analyze")
						.contentType(MediaType.APPLICATION_JSON)
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
				.andExpect(jsonPath("$.audit.fallbackReason", equalTo("REQUEST_API_KEY_MISSING")));
	}

	@Test
	void returnsProblemDetailForInvalidRequest() throws Exception {
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
