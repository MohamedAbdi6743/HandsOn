package com.handson.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
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
 * Integration tests for events and shifts: the full app with real PostgreSQL.
 * Each request runs as a coordinator, a volunteer, or nobody, to check both the
 * features and who is allowed to use them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class EventApiTest {

	// The coordinator account is created at startup from src/test/resources/application.properties.
	private static final RequestPostProcessor COORDINATOR = user("coordinator@handson.test").roles("COORDINATOR");
	private static final RequestPostProcessor VOLUNTEER = user("sam@example.com").roles("VOLUNTEER");

	// A whole hour one week from now, so these tests never go stale.
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

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// ---------- events ----------

	@Test
	void listingEventsRequiresLogin() throws Exception {
		mockMvc.perform(get("/api/events"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void coordinatorCanCreateEvent() throws Exception {
		mockMvc.perform(post("/api/events").with(COORDINATOR).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson("  Food Drive  ", "Community Center")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.title").value("Food Drive"))
				.andExpect(jsonPath("$.shifts").isEmpty());
	}

	@Test
	void volunteerCannotCreateEvent() throws Exception {
		mockMvc.perform(post("/api/events").with(VOLUNTEER).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson("Food Drive", "Community Center")))
				.andExpect(status().isForbidden());
	}

	@Test
	void creatingEventWithBlankTitleReturns400() throws Exception {
		mockMvc.perform(post("/api/events").with(COORDINATOR).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson("", "Community Center")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.title").value("Title is required"));
	}

	@Test
	void volunteerCanViewEventWithShiftsSortedByStartTime() throws Exception {
		long eventId = createEvent("Food Drive");
		addShift(eventId, "Sorting", NEXT_WEEK_8AM.plusHours(2), NEXT_WEEK_8AM.plusHours(5), 5);
		addShift(eventId, "Setup", NEXT_WEEK_8AM, NEXT_WEEK_8AM.plusHours(2), 2);

		mockMvc.perform(get("/api/events/" + eventId).with(VOLUNTEER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Food Drive"))
				.andExpect(jsonPath("$.shifts[0].title").value("Setup"))
				.andExpect(jsonPath("$.shifts[0].startTime").value(startsWith(NEXT_WEEK_8AM.toString())))
				.andExpect(jsonPath("$.shifts[1].title").value("Sorting"));
	}

	@Test
	void unknownEventReturns404() throws Exception {
		mockMvc.perform(get("/api/events/999999").with(VOLUNTEER))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("Event not found."));
	}

	@Test
	void nonNumericEventIdReturns400() throws Exception {
		mockMvc.perform(get("/api/events/abc").with(VOLUNTEER))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("Invalid value for 'eventId'."));
	}

	@Test
	void coordinatorCanUpdateEvent() throws Exception {
		long eventId = createEvent("Food Drive");

		mockMvc.perform(put("/api/events/" + eventId).with(COORDINATOR).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson("Winter Food Drive", "Hall B")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Winter Food Drive"))
				.andExpect(jsonPath("$.location").value("Hall B"));
	}

	@Test
	void coordinatorCanDeleteEvent() throws Exception {
		long eventId = createEvent("Food Drive");

		mockMvc.perform(delete("/api/events/" + eventId).with(COORDINATOR).with(csrf()))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/events/" + eventId).with(VOLUNTEER))
				.andExpect(status().isNotFound());
	}

	@Test
	void eventWithShiftsButNoSignupsCanBeDeleted() throws Exception {
		long eventId = createEvent("Food Drive");
		long shiftId = addShift(eventId, "Setup", NEXT_WEEK_8AM, NEXT_WEEK_8AM.plusHours(2), 2);

		mockMvc.perform(delete("/api/events/" + eventId).with(COORDINATOR).with(csrf()))
				.andExpect(status().isNoContent());
		mockMvc.perform(put("/api/shifts/" + shiftId).with(COORDINATOR).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(shiftJson("Setup", NEXT_WEEK_8AM, NEXT_WEEK_8AM.plusHours(2), 2)))
				.andExpect(status().isNotFound());
	}

	@Test
	void shiftWithSignupsCannotBeDeleted() throws Exception {
		long eventId = createEvent("Food Drive");
		long shiftId = addShift(eventId, "Setup", NEXT_WEEK_8AM, NEXT_WEEK_8AM.plusHours(2), 2);
		Long volunteerId = userService.createUser("Sam Ortiz", "sam@example.com", "correct-horse-42", Role.VOLUNTEER).getId();
		jdbcTemplate.update("INSERT INTO signup (shift_id, user_id, status) VALUES (?, ?, 'CONFIRMED')", shiftId, volunteerId);

		mockMvc.perform(delete("/api/shifts/" + shiftId).with(COORDINATOR).with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("This shift has volunteer sign-ups, so it can't be deleted."));
	}

	@Test
	void eventWithSignupsCannotBeDeleted() throws Exception {
		long eventId = createEvent("Food Drive");
		long shiftId = addShift(eventId, "Setup", NEXT_WEEK_8AM, NEXT_WEEK_8AM.plusHours(2), 2);
		Long volunteerId = userService.createUser("Sam Ortiz", "sam@example.com", "correct-horse-42", Role.VOLUNTEER).getId();
		// Sign-ups get their own API in the next milestone; insert one directly for now.
		jdbcTemplate.update("INSERT INTO signup (shift_id, user_id, status) VALUES (?, ?, 'CONFIRMED')", shiftId, volunteerId);

		mockMvc.perform(delete("/api/events/" + eventId).with(COORDINATOR).with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("This event has volunteer sign-ups, so it can't be deleted."));
	}

	@Test
	void upcomingListSkipsEventsWhoseShiftsAreAllInThePast() throws Exception {
		long pastEventId = createEvent("Last Month's Drive");
		Event pastEvent = eventRepository.findById(pastEventId).orElseThrow();
		// The API refuses past start times, so save this one directly through the repository.
		LocalDateTime lastMonth = LocalDateTime.now().minusMonths(1);
		shiftRepository.save(new Shift(pastEvent, "Old shift", lastMonth, lastMonth.plusHours(2), 3));

		long futureEventId = createEvent("Next Week's Drive");
		addShift(futureEventId, "Setup", NEXT_WEEK_8AM, NEXT_WEEK_8AM.plusHours(2), 2);
		long draftEventId = createEvent("Draft Event Without Shifts");

		mockMvc.perform(get("/api/events").with(VOLUNTEER))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id").value(hasItem((int) futureEventId)))
				.andExpect(jsonPath("$[*].id").value(hasItem((int) draftEventId)))
				.andExpect(jsonPath("$[*].id").value(not(hasItem((int) pastEventId))));
	}

	// ---------- shifts ----------

	@Test
	void shiftEndingBeforeItStartsReturns400() throws Exception {
		long eventId = createEvent("Food Drive");

		mockMvc.perform(post("/api/events/" + eventId + "/shifts").with(COORDINATOR).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(shiftJson("Setup", NEXT_WEEK_8AM, NEXT_WEEK_8AM.minusHours(1), 2)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.endAfterStart").value("End time must be after start time"));
	}

	@Test
	void shiftStartingInThePastReturns400() throws Exception {
		long eventId = createEvent("Food Drive");
		LocalDateTime yesterday = LocalDateTime.now().minusDays(1);

		mockMvc.perform(post("/api/events/" + eventId + "/shifts").with(COORDINATOR).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(shiftJson("Setup", yesterday, yesterday.plusHours(2), 2)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.startTime").value("Start time must be in the future"));
	}

	@Test
	void shiftWithZeroCapacityReturns400() throws Exception {
		long eventId = createEvent("Food Drive");

		mockMvc.perform(post("/api/events/" + eventId + "/shifts").with(COORDINATOR).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(shiftJson("Setup", NEXT_WEEK_8AM, NEXT_WEEK_8AM.plusHours(2), 0)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.capacity").value("Capacity must be at least 1"));
	}

	@Test
	void addingShiftToUnknownEventReturns404() throws Exception {
		mockMvc.perform(post("/api/events/999999/shifts").with(COORDINATOR).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(shiftJson("Setup", NEXT_WEEK_8AM, NEXT_WEEK_8AM.plusHours(2), 2)))
				.andExpect(status().isNotFound());
	}

	@Test
	void volunteerCannotAddShift() throws Exception {
		long eventId = createEvent("Food Drive");

		mockMvc.perform(post("/api/events/" + eventId + "/shifts").with(VOLUNTEER).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(shiftJson("Setup", NEXT_WEEK_8AM, NEXT_WEEK_8AM.plusHours(2), 2)))
				.andExpect(status().isForbidden());
	}

	@Test
	void coordinatorCanUpdateShift() throws Exception {
		long eventId = createEvent("Food Drive");
		long shiftId = addShift(eventId, "Setup", NEXT_WEEK_8AM, NEXT_WEEK_8AM.plusHours(2), 2);

		mockMvc.perform(put("/api/shifts/" + shiftId).with(COORDINATOR).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(shiftJson("Setup crew", NEXT_WEEK_8AM, NEXT_WEEK_8AM.plusHours(3), 4)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Setup crew"))
				.andExpect(jsonPath("$.capacity").value(4));
	}

	@Test
	void coordinatorCanDeleteShift() throws Exception {
		long eventId = createEvent("Food Drive");
		long shiftId = addShift(eventId, "Setup", NEXT_WEEK_8AM, NEXT_WEEK_8AM.plusHours(2), 2);

		mockMvc.perform(delete("/api/shifts/" + shiftId).with(COORDINATOR).with(csrf()))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/events/" + eventId).with(VOLUNTEER))
				.andExpect(jsonPath("$.shifts").isEmpty());
	}

	// ---------- helpers ----------

	private long createEvent(String title) throws Exception {
		String body = mockMvc.perform(post("/api/events").with(COORDINATOR).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(eventJson(title, "Community Center")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private long addShift(long eventId, String title, LocalDateTime start, LocalDateTime end, int capacity) throws Exception {
		String body = mockMvc.perform(post("/api/events/" + eventId + "/shifts").with(COORDINATOR).with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(shiftJson(title, start, end, capacity)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private static String eventJson(String title, String location) {
		return """
				{ "title": "%s", "description": "Sorting donated food.", "location": "%s" }
				""".formatted(title, location);
	}

	private static String shiftJson(String title, LocalDateTime start, LocalDateTime end, int capacity) {
		return """
				{ "title": "%s", "startTime": "%s", "endTime": "%s", "capacity": %d }
				""".formatted(title, start, end, capacity);
	}

}
