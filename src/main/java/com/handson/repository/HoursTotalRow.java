package com.handson.repository;

import java.math.BigDecimal;

/** A single volunteer's totals. */
public interface HoursTotalRow {

	Long getShiftsAttended();

	BigDecimal getTotalHours();

}
