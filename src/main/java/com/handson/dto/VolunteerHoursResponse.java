package com.handson.dto;

/** One volunteer's line in the coordinator's hours report. */
public record VolunteerHoursResponse(Long userId, String fullName, String email, long shiftsAttended, double totalHours) {
}
