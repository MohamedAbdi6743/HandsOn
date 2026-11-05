package com.handson.exception;

/**
 * The request is well-formed but breaks a rule, e.g. "email already taken" or "shift is full".
 * GlobalExceptionHandler turns it into 409 Conflict with this message.
 */
public class BusinessRuleException extends RuntimeException {

	public BusinessRuleException(String message) {
		super(message);
	}

}
