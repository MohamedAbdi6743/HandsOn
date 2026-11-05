package com.handson.dto;

import java.time.LocalDateTime;

import com.handson.entity.SignupStatus;

/** One row of a volunteer's "my shifts" list: the sign-up plus the shift and event it belongs to. */
public record MySignupResponse(
		Long signupId,
		SignupStatus status,
		Integer waitlistPosition,
		Long shiftId,
		String shiftTitle,
		LocalDateTime startTime,
		LocalDateTime endTime,
		Long eventId,
		String eventTitle,
		String location) {
}
