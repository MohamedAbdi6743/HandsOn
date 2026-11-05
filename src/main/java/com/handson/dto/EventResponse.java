package com.handson.dto;

import java.util.List;

import com.handson.entity.Event;

/** An event with its shifts, sorted by start time. */
public record EventResponse(
		Long id,
		String title,
		String description,
		String location,
		List<ShiftResponse> shifts) {

	public static EventResponse from(Event event, List<ShiftResponse> shifts) {
		return new EventResponse(event.getId(), event.getTitle(), event.getDescription(), event.getLocation(), shifts);
	}

}
