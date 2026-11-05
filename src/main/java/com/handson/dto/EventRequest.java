package com.handson.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** JSON body for creating or editing an event. */
public record EventRequest(

		@NotBlank(message = "Title is required")
		@Size(max = 120, message = "Title must be at most 120 characters")
		String title,

		@Size(max = 2000, message = "Description must be at most 2000 characters")
		String description,

		@NotBlank(message = "Location is required")
		@Size(max = 200, message = "Location must be at most 200 characters")
		String location) {
}
