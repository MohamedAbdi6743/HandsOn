package com.handson.dto;

import java.time.LocalDateTime;

import com.handson.entity.Shift;

/** A shift as the API shows it. Sign-up counts are added in the sign-ups milestone. */
public record ShiftResponse(
		Long id,
		Long eventId,
		String title,
		LocalDateTime startTime,
		LocalDateTime endTime,
		int capacity) {

	public static ShiftResponse from(Shift shift) {
		return new ShiftResponse(
				shift.getId(),
				shift.getEvent().getId(),
				shift.getTitle(),
				shift.getStartTime(),
				shift.getEndTime(),
				shift.getCapacity());
	}

}
