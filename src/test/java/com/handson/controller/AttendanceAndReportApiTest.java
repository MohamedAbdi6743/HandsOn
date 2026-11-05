package com.handson.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.handson.TestcontainersConfiguration;
import com.handson.entity.AppUser;
import com.handson.entity.Event;
import com.handson.entity.Role;
import com.handson.entity.Shift;
import com.handson.entity.Signup;
import com.handson.entity.SignupStatus;
import com.handson.repository.EventRepository;
import com.handson.repository.ShiftRepository;
import com.handson.repository.SignupRepository;
import com.handson.service.UserService;

/**
 * Integration tests for recording attendance and for the hours reports.
 * Past shifts and sign-ups are saved directly, since the API (correctly) won't create them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AttendanceAndReportApiTest {

	private static final RequestPostProcessor COORDINATOR = user("coordinator@handson.test").roles("COORDINATOR");
	private static final RequestPostProcessor SAM = user("sam@example.com").roles("VOLUNTEER");

	// Fixed dates in the past, so the report numbers are always the same.
	private static final LocalDateTime JUNE_10_9AM = LocalDateTime.of(2025, 6, 10, 9, 0);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private ShiftRepository shiftRepository;

	@Autowired
	private SignupRepository signupRepository;

	private AppUser sam;
	private AppUser priya;
	private Event event;

	@BeforeEach
	void setUp() {
		sam = userService.createUser("Sam Ortiz", "sam@example.com", "correct-horse-42", Role.VOLUNTEER);
		priya = userService.createUser("Priya Shah", "priya@example.com", "correct-horse-42", Role.VOLUNTEER);
		event = eventRepository.save(new Event("Food Drive", null, "Community Center",
				userService.getByEmail("coordinator@handson.test")));
	}

	// ---------- attendance ----------

	@Test
	void coordinatorCanMarkVolunteerAsAttendedAfterShiftStarts() throws Exception {
		Signup signup = signup(sam, shift(JUNE_10_9AM, 2.0), SignupStatus.CONFIRMED);

		markAttendance(signup.getId(), true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ATTENDED"));
	}

	@Test
	void attendedVolunteersStillCountAsFillingTheShift() throws Exception {
		Signup signup = signup(sam, shift(JUNE_10_9AM, 2.0), SignupStatus.CONFIRMED);
		markAttendance(signup.getId(), true);

		mockMvc.perform(get("/api/events/" + event.getId()).with(COORDINATOR))
				.andExpect(jsonPath("$.shifts[0].confirmedCount").value(1))
				.andExpect(jsonPath("$.shifts[0].spotsLeft").value(4));
	}

	@Test
	void attendanceCanBeCorrected() throws Exception {
		Signup signup = signup(sam, shift(JUNE_10_9AM, 2.0), SignupStatus.NO_SHOW);

		markAttendance(signup.getId(), true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ATTENDED"));
	}

	@Test
	void attendanceCannotBeRecordedBeforeShiftStarts() throws Exception {
		Signup signup = signup(sam, shift(LocalDateTime.now().plusDays(2), 2.0), SignupStatus.CONFIRMED);

		markAttendance(signup.getId(), true)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Attendance can only be recorded once the shift has started."));
	}

	@Test
	void waitlistedVolunteerCannotBeMarked() throws Exception {
		Signup signup = signup(sam, shift(JUNE_10_9AM, 2.0), SignupStatus.WAITLISTED);

		markAttendance(signup.getId(), true)
				.andExpect(status().isConflict());
	}

	@Test
	void attendanceRequiresTrueOrFalse() throws Exception {
		Signup signup = signup(sam, shift(JUNE_10_9AM, 2.0), SignupStatus.CONFIRMED);

		mockMvc.perform(put("/api/signups/" + signup.getId() + "/attendance").with(COORDINATOR).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.attended").exists());
	}

	@Test
	void volunteerCannotRecordAttendance() throws Exception {
		Signup signup = signup(sam, shift(JUNE_10_9AM, 2.0), SignupStatus.CONFIRMED);

		mockMvc.perform(put("/api/signups/" + signup.getId() + "/attendance").with(SAM).with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content("{ \"attended\": true }"))
				.andExpect(status().isForbidden());
	}

	@Test
	void unknownSignupReturns404() throws Exception {
		markAttendance(999999L, true).andExpect(status().isNotFound());
	}

	// ---------- reports ----------

	@Test
	void hoursReportCountsOnlyAttendedShiftsInTheDateRange() throws Exception {
		signup(sam, shift(JUNE_10_9AM, 2.0), SignupStatus.ATTENDED);                  // 2 h
		signup(sam, shift(JUNE_10_9AM.plusDays(1), 3.0), SignupStatus.ATTENDED);      // 3 h
		signup(sam, shift(JUNE_10_9AM.plusDays(2), 2.0), SignupStatus.NO_SHOW);       // not counted
		signup(priya, shift(JUNE_10_9AM.plusDays(3), 1.5), SignupStatus.ATTENDED);    // 1.5 h
		signup(priya, shift(JUNE_10_9AM.plusMonths(1), 4.0), SignupStatus.ATTENDED);  // July: outside range

		mockMvc.perform(get("/api/reports/volunteer-hours?from=2025-06-01&to=2025-06-30").with(COORDINATOR))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].fullName").value("Sam Ortiz"))
				.andExpect(jsonPath("$[0].shiftsAttended").value(2))
				.andExpect(jsonPath("$[0].totalHours").value(5.0))
				.andExpect(jsonPath("$[1].fullName").value("Priya Shah"))
				.andExpect(jsonPath("$[1].totalHours").value(1.5));
	}

	@Test
	void hoursReportIncludesShiftsOnTheLastDay() throws Exception {
		signup(sam, shift(LocalDateTime.of(2025, 6, 30, 18, 0), 2.0), SignupStatus.ATTENDED);

		mockMvc.perform(get("/api/reports/volunteer-hours?from=2025-06-01&to=2025-06-30").with(COORDINATOR))
				.andExpect(jsonPath("$[0].totalHours").value(2.0));
	}

	@Test
	void hoursReportRejectsFromAfterTo() throws Exception {
		mockMvc.perform(get("/api/reports/volunteer-hours?from=2025-07-01&to=2025-06-01").with(COORDINATOR))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("'from' must be on or before 'to'."));
	}

	@Test
	void hoursReportRejectsBadDate() throws Exception {
		mockMvc.perform(get("/api/reports/volunteer-hours?from=June").with(COORDINATOR))
				.andExpect(status().isBadRequest());
	}

	@Test
	void volunteerCannotSeeHoursReport() throws Exception {
		mockMvc.perform(get("/api/reports/volunteer-hours").with(SAM))
				.andExpect(status().isForbidden());
	}

	@Test
	void volunteerSeesTheirOwnTotalHours() throws Exception {
		signup(sam, shift(JUNE_10_9AM, 2.0), SignupStatus.ATTENDED);
		signup(sam, shift(JUNE_10_9AM.plusDays(1), 1.5), SignupStatus.ATTENDED);
		signup(sam, shift(JUNE_10_9AM.plusDays(2), 2.0), SignupStatus.NO_SHOW);

		mockMvc.perform(get("/api/me/hours").with(SAM))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.shiftsAttended").value(2))
				.andExpect(jsonPath("$.totalHours").value(3.5));
	}

	@Test
	void newVolunteerHasZeroHours() throws Exception {
		mockMvc.perform(get("/api/me/hours").with(SAM))
				.andExpect(jsonPath("$.shiftsAttended").value(0))
				.andExpect(jsonPath("$.totalHours").value(0.0));
	}

	// ---------- helpers ----------

	private Shift shift(LocalDateTime start, double hours) {
		LocalDateTime end = start.plusMinutes((long) (hours * 60));
		return shiftRepository.save(new Shift(event, "Shift", start, end, 5));
	}

	private Signup signup(AppUser volunteer, Shift shift, SignupStatus status) {
		return signupRepository.save(new Signup(shift, volunteer, status, shift.getStartTime().minusDays(3)));
	}

	private ResultActions markAttendance(Long signupId, boolean attended) throws Exception {
		return mockMvc.perform(put("/api/signups/" + signupId + "/attendance").with(COORDINATOR).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{ \"attended\": " + attended + " }"));
	}

}
