package dev.harshith.incidentcopilot.web;

import java.net.URI;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException exception,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request
	) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		detail.setTitle("Invalid incident analysis request");
		detail.setType(URI.create("https://incident-copilot.dev/problems/invalid-request"));
		detail.setDetail("One or more request fields failed validation.");
		detail.setProperty(
				"fieldErrors",
				exception.getBindingResult().getFieldErrors().stream()
						.collect(Collectors.toMap(
								fieldError -> fieldError.getField(),
								fieldError -> fieldError.getDefaultMessage() == null ? "Invalid value" : fieldError.getDefaultMessage(),
								(left, right) -> left,
								java.util.LinkedHashMap::new
						))
		);
		return ResponseEntity.badRequest().body(detail);
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception exception) {
		ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		detail.setTitle("Incident analysis failed");
		detail.setType(URI.create("https://incident-copilot.dev/problems/internal-error"));
		detail.setDetail("The service could not complete the analysis request.");
		detail.setProperty("errorClass", exception.getClass().getSimpleName());
		return detail;
	}
}
