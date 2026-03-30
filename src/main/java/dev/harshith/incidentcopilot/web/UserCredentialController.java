package dev.harshith.incidentcopilot.web;

import dev.harshith.incidentcopilot.model.StoredOpenAiCredentialRequest;
import dev.harshith.incidentcopilot.model.StoredOpenAiCredentialResponse;
import dev.harshith.incidentcopilot.service.UserCredentialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/users/{userId}/credentials")
public class UserCredentialController {

	private final UserCredentialService userCredentialService;

	public UserCredentialController(UserCredentialService userCredentialService) {
		this.userCredentialService = userCredentialService;
	}

	@PutMapping("/openai")
	@ResponseStatus(HttpStatus.CREATED)
	public StoredOpenAiCredentialResponse saveOpenAiCredential(
			@PathVariable @Pattern(regexp = "[a-zA-Z0-9_-]{3,64}") String userId,
			@Valid @RequestBody StoredOpenAiCredentialRequest request
	) {
		return userCredentialService.saveOpenAiCredential(userId, request.apiKey(), request.defaultModel());
	}

	@GetMapping("/openai")
	public StoredOpenAiCredentialResponse getOpenAiCredentialStatus(
			@PathVariable @Pattern(regexp = "[a-zA-Z0-9_-]{3,64}") String userId
	) {
		return userCredentialService.getOpenAiCredentialStatus(userId);
	}

	@DeleteMapping("/openai")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteOpenAiCredential(
			@PathVariable @Pattern(regexp = "[a-zA-Z0-9_-]{3,64}") String userId
	) {
		userCredentialService.deleteOpenAiCredential(userId);
	}
}
