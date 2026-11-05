package com.handson.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JSON body for POST /api/auth/register.
 * The annotations are checked by @Valid in the controller before any of our code runs.
 */
public record RegisterRequest(

		@NotBlank(message = "Full name is required")
		@Size(max = 100, message = "Full name must be at most 100 characters")
		String fullName,

		@NotBlank(message = "Email is required")
		@Email(message = "Email must be a valid email address")
		@Size(max = 255, message = "Email must be at most 255 characters")
		String email,

		// BCrypt only uses the first 72 bytes of a password, so longer ones are rejected.
		@NotBlank(message = "Password is required")
		@Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters")
		String password) {
}
