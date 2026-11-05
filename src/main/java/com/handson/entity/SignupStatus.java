package com.handson.entity;

/**
 * Where a sign-up is in its life:
 *
 *   sign up (spot free) -> CONFIRMED ---- shift happens ----> ATTENDED or NO_SHOW
 *   sign up (full)      -> WAITLISTED --- someone cancels --> CONFIRMED
 *   CONFIRMED or WAITLISTED --- cancel ---> CANCELLED
 */
public enum SignupStatus {
	CONFIRMED,
	WAITLISTED,
	CANCELLED,
	ATTENDED,
	NO_SHOW
}
