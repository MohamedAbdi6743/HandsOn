package com.handson.exception;

/**
 * The thing that was asked for does not exist. GlobalExceptionHandler turns it into 404 Not Found.
 */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

}
