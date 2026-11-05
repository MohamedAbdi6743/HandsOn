package com.handson.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.handson.dto.RegisterRequest;
import com.handson.dto.UserResponse;
import com.handson.entity.AppUser;
import com.handson.service.UserService;

import jakarta.validation.Valid;

/**
 * Account endpoints. Login and logout are handled by Spring Security (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final UserService userService;

	public AuthController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse register(@Valid @RequestBody RegisterRequest request) {
		AppUser user = userService.registerVolunteer(request);
		return UserResponse.from(user);
	}

	@GetMapping("/me")
	public UserResponse currentUser(Authentication authentication) {
		return UserResponse.from(userService.getByEmail(authentication.getName()));
	}

	/**
	 * The frontend calls this once when a page loads. Reading the token makes Spring Security
	 * send the XSRF-TOKEN cookie, which the frontend then copies into a header on later requests.
	 */
	@GetMapping("/csrf")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void csrfToken(CsrfToken csrfToken) {
		csrfToken.getToken();
	}

}
