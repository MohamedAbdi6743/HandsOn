package com.handson.repository;

import java.math.BigDecimal;

/**
 * One row of the hours report. Spring Data fills these getters from the SQL column
 * aliases with the same names ("userId", "fullName", ...).
 */
public interface VolunteerHoursRow {

	Long getUserId();

	String getFullName();

	String getEmail();

	Long getShiftsAttended();

	BigDecimal getTotalHours();

}
