package com.handson.dto;

import jakarta.validation.constraints.NotNull;

/** JSON body for PUT /api/signups/{id}/attendance, e.g. { "attended": true }. */
public record AttendanceRequest(

		@NotNull(message = "attended must be true or false")
		Boolean attended) {
}
