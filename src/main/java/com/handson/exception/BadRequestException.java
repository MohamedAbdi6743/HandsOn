package com.handson.exception;

/**
 * The request's values don't make sense together, e.g. a report "from" date after its "to" date.
 * GlobalExceptionHandler turns it into 400 Bad Request.
 */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}

}
