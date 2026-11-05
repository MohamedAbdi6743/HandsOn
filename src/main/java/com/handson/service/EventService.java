package com.handson.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.handson.dto.EventRequest;
import com.handson.dto.EventResponse;
import com.handson.dto.ShiftCounts;
import com.handson.dto.ShiftResponse;
import com.handson.dto.SignupResponse;
import com.handson.entity.AppUser;
import com.handson.entity.Event;
import com.handson.entity.Shift;
import com.handson.exception.BusinessRuleException;
import com.handson.exception.NotFoundException;
import com.handson.repository.EventRepository;
import com.handson.repository.ShiftRepository;

/**
 * Rules for events. Methods return DTOs, not entities: the database session closes when
 * a service method ends (open-in-view is off), so everything the response needs is
 * loaded here, inside the transaction.
 */
@Service
public class EventService {

	private static final Logger log = LoggerFactory.getLogger(EventService.class);

	private final EventRepository eventRepository;
	private final ShiftRepository shiftRepository;
	private final SignupService signupService;
	private final UserService userService;
	private final Clock clock;

	public EventService(EventRepository eventRepository, ShiftRepository shiftRepository,
			SignupService signupService, UserService userService, Clock clock) {
		this.eventRepository = eventRepository;
		this.shiftRepository = shiftRepository;
		this.signupService = signupService;
		this.userService = userService;
		this.clock = clock;
	}

	/** Upcoming events with their shifts, soonest first. */
	@Transactional(readOnly = true)
	public List<EventResponse> listUpcomingEvents(String viewerEmail) {
		List<Event> events = eventRepository.findUpcoming(LocalDateTime.now(clock));
		if (events.isEmpty()) {
			return List.of();
		}
		List<Long> eventIds = events.stream().map(Event::getId).toList();
		List<Shift> shifts = shiftRepository.findByEventIdInOrderByStartTime(eventIds);

		List<EventResponse> responses = buildResponses(events, shifts, viewerEmail);
		responses.sort(Comparator.comparing(EventService::firstShiftStart));
		return responses;
	}

	@Transactional(readOnly = true)
	public EventResponse getEvent(Long eventId, String viewerEmail) {
		Event event = findEvent(eventId);
		List<Shift> shifts = shiftRepository.findByEventIdOrderByStartTime(eventId);
		return buildResponses(List.of(event), shifts, viewerEmail).get(0);
	}

	@Transactional
	public EventResponse createEvent(EventRequest request, String coordinatorEmail) {
		AppUser coordinator = userService.getByEmail(coordinatorEmail);
		Event event = new Event(
				request.title().trim(),
				cleanDescription(request.description()),
				request.location().trim(),
				coordinator);
		Event saved = eventRepository.save(event);
		log.info("Event {} created by user {}", saved.getId(), coordinator.getId());
		return EventResponse.from(saved, List.of());
	}

	@Transactional
	public EventResponse updateEvent(Long eventId, EventRequest request, String viewerEmail) {
		Event event = findEvent(eventId);
		// No save() call needed: changes to a loaded entity are written when the transaction ends.
		event.updateDetails(request.title().trim(), cleanDescription(request.description()), request.location().trim());
		return getEvent(eventId, viewerEmail);
	}

	@Transactional
	public void deleteEvent(Long eventId) {
		Event event = findEvent(eventId);
		// Check first instead of letting the database refuse: a clear message, and no
		// dependence on when Hibernate happens to send the DELETE.
		if (signupService.eventHasSignups(eventId)) {
			throw new BusinessRuleException("This event has volunteer sign-ups, so it can't be deleted.");
		}
		List<Shift> shifts = shiftRepository.findByEventIdOrderByStartTime(eventId);
		signupService.deleteCancelledSignups(shifts.stream().map(Shift::getId).toList());
		shiftRepository.deleteAll(shifts);
		eventRepository.delete(event);
		log.info("Event {} deleted", eventId);
	}

	/**
	 * Builds the responses with three extra queries in total, however many events and shifts
	 * there are: shifts (passed in), sign-up counts, and the viewer's own sign-ups.
	 * Loading these one shift at a time would be the classic "N+1 queries" problem.
	 */
	private List<EventResponse> buildResponses(List<Event> events, List<Shift> shifts, String viewerEmail) {
		List<Long> shiftIds = shifts.stream().map(Shift::getId).toList();
		Map<Long, ShiftCounts> countsByShiftId = signupService.countsForShifts(shiftIds);
		Map<Long, SignupResponse> mySignupsByShiftId = signupService.mySignupsForShifts(viewerEmail, shiftIds);

		Map<Long, List<ShiftResponse>> shiftsByEventId = new HashMap<>();
		for (Shift shift : shifts) {
			ShiftResponse response = ShiftResponse.from(
					shift,
					countsByShiftId.getOrDefault(shift.getId(), ShiftCounts.NONE),
					mySignupsByShiftId.get(shift.getId()));
			shiftsByEventId.computeIfAbsent(shift.getEvent().getId(), id -> new ArrayList<>()).add(response);
		}

		List<EventResponse> responses = new ArrayList<>();
		for (Event event : events) {
			responses.add(EventResponse.from(event, shiftsByEventId.getOrDefault(event.getId(), List.of())));
		}
		return responses;
	}

	private Event findEvent(Long eventId) {
		return eventRepository.findById(eventId)
				.orElseThrow(() -> new NotFoundException("Event not found."));
	}

	/** Events without shifts sort last. */
	private static LocalDateTime firstShiftStart(EventResponse event) {
		if (event.shifts().isEmpty()) {
			return LocalDateTime.MAX;
		}
		return event.shifts().get(0).startTime();
	}

	private static String cleanDescription(String description) {
		if (description == null || description.isBlank()) {
			return null;
		}
		return description.trim();
	}

}
