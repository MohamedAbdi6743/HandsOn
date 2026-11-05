package com.handson.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.handson.dto.ShiftRequest;
import com.handson.dto.ShiftResponse;
import com.handson.service.ShiftService;

import jakarta.validation.Valid;

/** Shift endpoints (coordinators only, see SecurityConfig). */
@RestController
public class ShiftController {

	private final ShiftService shiftService;

	public ShiftController(ShiftService shiftService) {
		this.shiftService = shiftService;
	}

	// A new shift always belongs to an event, so the event id is part of the URL.
	@PostMapping("/api/events/{eventId}/shifts")
	@ResponseStatus(HttpStatus.CREATED)
	public ShiftResponse addShift(@PathVariable Long eventId, @Valid @RequestBody ShiftRequest request) {
		return shiftService.addShift(eventId, request);
	}

	@PutMapping("/api/shifts/{shiftId}")
	public ShiftResponse updateShift(@PathVariable Long shiftId, @Valid @RequestBody ShiftRequest request) {
		return shiftService.updateShift(shiftId, request);
	}

	@DeleteMapping("/api/shifts/{shiftId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteShift(@PathVariable Long shiftId) {
		shiftService.deleteShift(shiftId);
	}

}
