package dev.harshith.incidentcopilot.web;

import dev.harshith.incidentcopilot.model.IncidentAnalysisRequest;
import dev.harshith.incidentcopilot.model.IncidentAnalysisResponse;
import dev.harshith.incidentcopilot.service.IncidentAnalysisService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/incidents")
public class IncidentAnalysisController {

	private final IncidentAnalysisService incidentAnalysisService;

	public IncidentAnalysisController(IncidentAnalysisService incidentAnalysisService) {
		this.incidentAnalysisService = incidentAnalysisService;
	}

	@PostMapping("/analyze")
	@ResponseStatus(HttpStatus.OK)
	public IncidentAnalysisResponse analyze(
			@Valid @RequestBody IncidentAnalysisRequest request,
			@RequestHeader(value = "X-User-Id", required = false) String userId
	) {
		return incidentAnalysisService.analyze(request, userId);
	}
}
