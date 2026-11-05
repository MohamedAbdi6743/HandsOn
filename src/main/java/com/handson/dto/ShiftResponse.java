package com.handson.dto;

import java.time.LocalDateTime;

import com.handson.entity.Shift;

/**
 * A shift as the API shows it, including how full it is.
 * mySignup is the viewer's own sign-up on this shift, or null if they haven't signed up.
 */
public record ShiftResponse(
		Long id,
		Long eventId,
		String title,
		LocalDateTime startTime,
		LocalDateTime endTime,
		int capacity,
		long confirmedCount,
		long waitlistCount,
		long spotsLeft,
		SignupResponse mySignup) {

	public static ShiftResponse from(Shift shift, ShiftCounts counts, SignupResponse mySignup) {
		long spotsLeft = Math.max(0, shift.getCapacity() - counts.confirmed());
		return new ShiftResponse(
				shift.getId(),
				shift.getEvent().getId(),
				shift.getTitle(),
				shift.getStartTime(),
				shift.getEndTime(),
				shift.getCapacity(),
				counts.confirmed(),
				counts.waitlisted(),
				spotsLeft,
				mySignup);
	}

}
