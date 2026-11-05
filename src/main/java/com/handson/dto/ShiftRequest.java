package com.handson.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** JSON body for creating or editing a shift. Times look like "2026-12-05T08:00". */
public record ShiftRequest(

		@NotBlank(message = "Title is required")
		@Size(max = 80, message = "Title must be at most 80 characters")
		String title,

		@NotNull(message = "Start time is required")
		@Future(message = "Start time must be in the future")
		LocalDateTime startTime,

		@NotNull(message = "End time is required")
		LocalDateTime endTime,

		@NotNull(message = "Capacity is required")
		@Min(value = 1, message = "Capacity must be at least 1")
		@Max(value = 500, message = "Capacity must be at most 500")
		Integer capacity) {

	/**
	 * A rule that needs two fields at once. Validation calls this method; if it returns false,
	 * the error is reported under the name "endAfterStart".
	 * Missing times are reported by @NotNull above, so they count as "fine" here.
	 */
	@AssertTrue(message = "End time must be after start time")
	public boolean isEndAfterStart() {
		if (startTime == null || endTime == null) {
			return true;
		}
		return endTime.isAfter(startTime);
	}

}
