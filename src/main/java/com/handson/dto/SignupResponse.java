package com.handson.dto;

import com.handson.entity.SignupStatus;

/**
 * The result of signing up or cancelling.
 * waitlistPosition is 1 for the front of the line, and null unless the status is WAITLISTED.
 */
public record SignupResponse(Long id, Long shiftId, SignupStatus status, Integer waitlistPosition) {
}
