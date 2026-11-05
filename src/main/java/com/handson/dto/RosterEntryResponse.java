package com.handson.dto;

import com.handson.entity.SignupStatus;

/** One person on a shift's roster, as the coordinator sees it. */
public record RosterEntryResponse(
		Long signupId,
		Long userId,
		String fullName,
		String email,
		SignupStatus status,
		Integer waitlistPosition) {
}
