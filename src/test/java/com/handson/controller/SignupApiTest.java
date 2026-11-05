package com.handson.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.handson.entity.Event;
import com.handson.entity.Role;
import com.handson.entity.Shift;
import com.handson.repository.EventRepository;
import com.handson.repository.ShiftRepository;
import com.handson.service.UserService;
import com.jayway.jsonpath.JsonPath;

/**
 * Integration tests for the sign-up rules, through the HTTP API with real PostgreSQL.
 * Events and shifts are saved straight through the repositories so tests can use any
 * time, including shifts that already started or start in a few hours.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class SignupApiTest {

	private static final RequestPostProcessor COORDINATOR = user("coordinator@handson.test").roles("COORDINATOR");
	private static final RequestPostProcessor SAM = user("sam@example.com").roles("VOLUNTEER");
	private static final RequestPostProcessor PRIYA = user("priya@example.com").roles("VOLUNTEER");
	private static final RequestPostProcessor LEE = user("lee@example.com").roles("VOLUNTEER");

	private static final LocalDateTime NEXT_WEEK_8AM = LocalDateTime.now().plusDays(7)
			.withHour(8).withMinute(0).withSecond(0).withNano(0);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private ShiftRepository shiftRepository;

	private Event event;

	@BeforeEach
	void createVolunteersAndEvent() {
		userService.createUser("Sam Ortiz", "sam@example.com", "correct-horse-42", Role.VOLUNTEER);
		userService.createUser("Priya Shah", "priya@example.com", "correct-horse-42", Role.VOLUNTEER);
		userService.createUser("Lee Park", "lee@example.com", "correct-horse-42", Role.VOLUNTEER);
		event = eventRepository.save(new Event("Food Drive", null, "Community Center",
				userService.getByEmail("coordinator@handson.test")));
	}

	// ---------- signing up ----------

	@Test
	void signingUpForShiftWithFreeSpotIsConfirmed() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 2);

		signUp(shiftId, SAM)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.waitlistPosition").doesNotExist());
	}

	@Test
	void signingUpForFullShiftJoinsWaitlistInOrder() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 1);
		signUp(shiftId, SAM).andExpect(jsonPath("$.status").value("CONFIRMED"));

		signUp(shiftId, PRIYA)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("WAITLISTED"))
				.andExpect(jsonPath("$.waitlistPosition").value(1));
		signUp(shiftId, LEE)
				.andExpect(jsonPath("$.waitlistPosition").value(2));
	}

	@Test
	void signingUpTwiceIsRejected() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 2);
		signUp(shiftId, SAM);

		signUp(shiftId, SAM)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("You're already signed up for this shift."));
	}

	@Test
	void overlappingShiftIsRejected() throws Exception {
		Long setupId = shift("Setup", NEXT_WEEK_8AM, 2);                       // 08:00-10:00
		Long overlappingId = shift("Greeting", NEXT_WEEK_8AM.plusHours(1), 2); // 09:00-11:00
		signUp(setupId, SAM);

		signUp(overlappingId, SAM)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString("overlaps with your 'Setup' shift")));
	}

	@Test
	void backToBackShiftsAreAllowed() throws Exception {
		Long setupId = shift("Setup", NEXT_WEEK_8AM, 2);                     // 08:00-10:00
		Long sortingId = shift("Sorting", NEXT_WEEK_8AM.plusHours(2), 2);    // 10:00-12:00
		signUp(setupId, SAM);

		signUp(sortingId, SAM).andExpect(status().isCreated());
	}

	@Test
	void shiftThatAlreadyStartedIsRejected() throws Exception {
		Long shiftId = shift("Earlier today", LocalDateTime.now().minusHours(1), 2);

		signUp(shiftId, SAM)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("This shift has already started."));
	}

	@Test
	void coordinatorCannotSignUp() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 2);

		mockMvc.perform(post("/api/shifts/" + shiftId + "/signups").with(COORDINATOR).with(csrf()))
				.andExpect(status().isForbidden());
	}

	// ---------- cancelling ----------

	@Test
	void cancellingConfirmedSpotPromotesFirstPersonOnWaitlist() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 1);
		long samSignup = signUpAndGetId(shiftId, SAM);
		signUp(shiftId, PRIYA);   // waitlist #1
		signUp(shiftId, LEE);     // waitlist #2

		cancel(samSignup, SAM)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		mockMvc.perform(get("/api/me/signups").with(PRIYA))
				.andExpect(jsonPath("$[0].status").value("CONFIRMED"));
		mockMvc.perform(get("/api/me/signups").with(LEE))
				.andExpect(jsonPath("$[0].status").value("WAITLISTED"))
				.andExpect(jsonPath("$[0].waitlistPosition").value(1));
	}

	@Test
	void confirmedSpotCannotBeCancelledWithinTwelveHours() throws Exception {
		Long shiftId = shift("Tonight", LocalDateTime.now().plusHours(6), 2);
		long samSignup = signUpAndGetId(shiftId, SAM);

		cancel(samSignup, SAM)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString("less than 12 hours")));
	}

	@Test
	void waitlistedSpotCanBeCancelledAnyTime() throws Exception {
		Long shiftId = shift("Tonight", LocalDateTime.now().plusHours(6), 1);
		signUp(shiftId, SAM);
		long priyaSignup = signUpAndGetId(shiftId, PRIYA);   // waitlisted

		cancel(priyaSignup, PRIYA).andExpect(status().isOk());
	}

	@Test
	void cannotCancelSomeoneElsesSignup() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 2);
		long samSignup = signUpAndGetId(shiftId, SAM);

		cancel(samSignup, PRIYA)
				.andExpect(status().isNotFound());
	}

	@Test
	void cancellingTwiceIsRejected() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 2);
		long samSignup = signUpAndGetId(shiftId, SAM);
		cancel(samSignup, SAM);

		cancel(samSignup, SAM).andExpect(status().isConflict());
	}

	@Test
	void volunteerCanSignUpAgainAfterCancelling() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 2);
		long samSignup = signUpAndGetId(shiftId, SAM);
		cancel(samSignup, SAM);

		signUp(shiftId, SAM)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
	}

	// ---------- what people see ----------

	@Test
	void mySignupsShowsShiftAndEventDetails() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 2);
		signUp(shiftId, SAM);

		mockMvc.perform(get("/api/me/signups").with(SAM))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].shiftTitle").value("Setup"))
				.andExpect(jsonPath("$[0].eventTitle").value("Food Drive"))
				.andExpect(jsonPath("$[0].status").value("CONFIRMED"));
	}

	@Test
	void eventPageShowsCountsAndTheViewersOwnStatus() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 1);
		signUp(shiftId, SAM);
		signUp(shiftId, PRIYA);

		mockMvc.perform(get("/api/events/" + event.getId()).with(PRIYA))
				.andExpect(jsonPath("$.shifts[0].confirmedCount").value(1))
				.andExpect(jsonPath("$.shifts[0].waitlistCount").value(1))
				.andExpect(jsonPath("$.shifts[0].spotsLeft").value(0))
				.andExpect(jsonPath("$.shifts[0].mySignup.status").value("WAITLISTED"))
				.andExpect(jsonPath("$.shifts[0].mySignup.waitlistPosition").value(1));
		mockMvc.perform(get("/api/events/" + event.getId()).with(LEE))
				.andExpect(jsonPath("$.shifts[0].mySignup").doesNotExist());
	}

	@Test
	void coordinatorSeesRosterWithConfirmedFirstThenWaitlist() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 1);
		signUp(shiftId, SAM);
		signUp(shiftId, PRIYA);

		mockMvc.perform(get("/api/shifts/" + shiftId + "/signups").with(COORDINATOR))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].fullName").value("Sam Ortiz"))
				.andExpect(jsonPath("$[0].status").value("CONFIRMED"))
				.andExpect(jsonPath("$[1].fullName").value("Priya Shah"))
				.andExpect(jsonPath("$[1].waitlistPosition").value(1));
	}

	@Test
	void volunteerCannotSeeRoster() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 1);

		mockMvc.perform(get("/api/shifts/" + shiftId + "/signups").with(SAM))
				.andExpect(status().isForbidden());
	}

	// ---------- capacity changes and deleting ----------

	@Test
	void capacityCannotDropBelowConfirmedCount() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 2);
		signUp(shiftId, SAM);
		signUp(shiftId, PRIYA);

		updateCapacity(shiftId, 1)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Capacity can't be lower than the 2 volunteers already confirmed."));
	}

	@Test
	void raisingCapacityPromotesPeopleFromWaitlist() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 1);
		signUp(shiftId, SAM);
		signUp(shiftId, PRIYA);   // waitlisted

		updateCapacity(shiftId, 2)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.confirmedCount").value(2))
				.andExpect(jsonPath("$.waitlistCount").value(0));
	}

	@Test
	void shiftWithOnlyCancelledSignupsCanBeDeleted() throws Exception {
		Long shiftId = shift("Setup", NEXT_WEEK_8AM, 2);
		long samSignup = signUpAndGetId(shiftId, SAM);
		cancel(samSignup, SAM);

		mockMvc.perform(delete("/api/shifts/" + shiftId).with(COORDINATOR).with(csrf()))
				.andExpect(status().isNoContent());
	}

	// ---------- helpers ----------

	/** A two-hour shift in the test event. */
	private Long shift(String title, LocalDateTime start, int capacity) {
		return shiftRepository.save(new Shift(event, title, start, start.plusHours(2), capacity)).getId();
	}

	private ResultActions signUp(Long shiftId, RequestPostProcessor volunteer) throws Exception {
		return mockMvc.perform(post("/api/shifts/" + shiftId + "/signups").with(volunteer).with(csrf()));
	}

	private long signUpAndGetId(Long shiftId, RequestPostProcessor volunteer) throws Exception {
		String body = signUp(shiftId, volunteer)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private ResultActions cancel(long signupId, RequestPostProcessor volunteer) throws Exception {
		return mockMvc.perform(post("/api/signups/" + signupId + "/cancel").with(volunteer).with(csrf()));
	}

	private ResultActions updateCapacity(Long shiftId, int capacity) throws Exception {
		String json = """
				{ "title": "Setup", "startTime": "%s", "endTime": "%s", "capacity": %d }
				""".formatted(NEXT_WEEK_8AM, NEXT_WEEK_8AM.plusHours(2), capacity);
		return mockMvc.perform(put("/api/shifts/" + shiftId).with(COORDINATOR).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(json));
	}

}
