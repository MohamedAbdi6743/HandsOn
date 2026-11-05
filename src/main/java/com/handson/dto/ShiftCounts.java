package com.handson.dto;

/** How many people are confirmed and waitlisted on a shift. */
public record ShiftCounts(long confirmed, long waitlisted) {

	public static final ShiftCounts NONE = new ShiftCounts(0, 0);

}
