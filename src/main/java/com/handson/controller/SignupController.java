package com.handson.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.handson.dto.AttendanceRequest;
import com.handson.dto.MySignupResponse;
import com.handson.dto.RosterEntryResponse;
import com.handson.dto.SignupResponse;
import com.handson.service.SignupService;

import jakarta.validation.Valid;

/**
 * Sign-up endpoints. Volunteers sign up, cancel and see their own shifts;
 * coordinators see a shift's roster (see SecurityConfig for the role rules).
 */
@RestController
public class SignupController {

	private final SignupService signupService;

	public SignupController(SignupService signupService) {
		this.signupService = signupService;
	}

	// No request body: the volunteer is whoever is logged in.
	@PostMapping("/api/shifts/{shiftId}/signups")
	@ResponseStatus(HttpStatus.CREATED)
	public SignupResponse signUp(@PathVariable Long shiftId, Authentication authentication) {
		return signupService.signUp(shiftId, authentication.getName());
	}

	// POST, not DELETE: the row is kept (status CANCELLED) as history.
	@PostMapping("/api/signups/{signupId}/cancel")
	public SignupResponse cancel(@PathVariable Long signupId, Authentication authentication) {
		return signupService.cancel(signupId, authentication.getName());
	}

	@GetMapping("/api/me/signups")
	public List<MySignupResponse> mySignups(Authentication authentication) {
		return signupService.listMySignups(authentication.getName());
	}

	@GetMapping("/api/shifts/{shiftId}/signups")
	public List<RosterEntryResponse> roster(@PathVariable Long shiftId) {
		return signupService.getRoster(shiftId);
	}

	@PutMapping("/api/signups/{signupId}/attendance")
	public SignupResponse recordAttendance(@PathVariable Long signupId, @Valid @RequestBody AttendanceRequest request) {
		return signupService.recordAttendance(signupId, request.attended());
	}

}
