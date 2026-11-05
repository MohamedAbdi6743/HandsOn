package com.handson.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.handson.dto.EventRequest;
import com.handson.dto.EventResponse;
import com.handson.service.EventService;

import jakarta.validation.Valid;

/**
 * Event endpoints. Who may call what (any user can read, only coordinators can change)
 * is decided in SecurityConfig before a request reaches this class.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

	private final EventService eventService;

	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@GetMapping
	public List<EventResponse> listUpcomingEvents(Authentication authentication) {
		return eventService.listUpcomingEvents(authentication.getName());
	}

	@GetMapping("/{eventId}")
	public EventResponse getEvent(@PathVariable Long eventId, Authentication authentication) {
		return eventService.getEvent(eventId, authentication.getName());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public EventResponse createEvent(@Valid @RequestBody EventRequest request, Authentication authentication) {
		return eventService.createEvent(request, authentication.getName());
	}

	@PutMapping("/{eventId}")
	public EventResponse updateEvent(@PathVariable Long eventId, @Valid @RequestBody EventRequest request,
			Authentication authentication) {
		return eventService.updateEvent(eventId, request, authentication.getName());
	}

	@DeleteMapping("/{eventId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteEvent(@PathVariable Long eventId) {
		eventService.deleteEvent(eventId);
	}

}
