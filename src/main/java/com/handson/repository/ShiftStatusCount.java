package com.handson.repository;

import com.handson.entity.SignupStatus;

/** One row of a "how many sign-ups per shift and status" query. */
public record ShiftStatusCount(Long shiftId, SignupStatus status, Long count) {
}
