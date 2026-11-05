package com.handson.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.handson.dto.MyHoursResponse;
import com.handson.dto.VolunteerHoursResponse;
import com.handson.entity.AppUser;
import com.handson.exception.BadRequestException;
import com.handson.repository.HoursTotalRow;
import com.handson.repository.SignupRepository;

/** Volunteer hours, counted from shifts marked ATTENDED. */
@Service
public class ReportService {

	private final SignupRepository signupRepository;
	private final UserService userService;
	private final Clock clock;

	public ReportService(SignupRepository signupRepository, UserService userService, Clock clock) {
		this.signupRepository = signupRepository;
		this.userService = userService;
		this.clock = clock;
	}

	/**
	 * Hours per volunteer for shifts that started between two dates, both included.
	 * Missing dates default to January 1 of this year through today.
	 */
	@Transactional(readOnly = true)
	public List<VolunteerHoursResponse> volunteerHours(LocalDate from, LocalDate to) {
		LocalDate today = LocalDate.now(clock);
		LocalDate start = (from != null) ? from : today.withDayOfYear(1);
		LocalDate end = (to != null) ? to : today;
		if (start.isAfter(end)) {
			throw new BadRequestException("'from' must be on or before 'to'.");
		}

		// "Up to and including the 'to' day" = before midnight at the start of the next day.
		return signupRepository.sumAttendedHours(start.atStartOfDay(), end.plusDays(1).atStartOfDay())
				.stream()
				.map(row -> new VolunteerHoursResponse(
						row.getUserId(),
						row.getFullName(),
						row.getEmail(),
						row.getShiftsAttended(),
						roundToTwoDecimals(row.getTotalHours())))
				.toList();
	}

	@Transactional(readOnly = true)
	public MyHoursResponse myHours(String volunteerEmail) {
		AppUser volunteer = userService.getByEmail(volunteerEmail);
		HoursTotalRow totals = signupRepository.sumAttendedHoursForUser(volunteer.getId());
		return new MyHoursResponse(totals.getShiftsAttended(), roundToTwoDecimals(totals.getTotalHours()));
	}

	private static double roundToTwoDecimals(BigDecimal hours) {
		return hours.setScale(2, RoundingMode.HALF_UP).doubleValue();
	}

}
