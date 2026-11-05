package com.handson.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.handson.dto.ShiftCounts;
import com.handson.dto.ShiftRequest;
import com.handson.dto.ShiftResponse;
import com.handson.entity.Event;
import com.handson.entity.Shift;
import com.handson.exception.BusinessRuleException;
import com.handson.exception.NotFoundException;
import com.handson.repository.EventRepository;
import com.handson.repository.ShiftRepository;

/** Rules for shifts inside an event. */
@Service
public class ShiftService {

	private static final Logger log = LoggerFactory.getLogger(ShiftService.class);

	private final ShiftRepository shiftRepository;
	private final EventRepository eventRepository;
	private final SignupService signupService;

	public ShiftService(ShiftRepository shiftRepository, EventRepository eventRepository, SignupService signupService) {
		this.shiftRepository = shiftRepository;
		this.eventRepository = eventRepository;
		this.signupService = signupService;
	}

	@Transactional
	public ShiftResponse addShift(Long eventId, ShiftRequest request) {
		Event event = eventRepository.findById(eventId)
				.orElseThrow(() -> new NotFoundException("Event not found."));
		Shift shift = new Shift(event, request.title().trim(), request.startTime(), request.endTime(), request.capacity());
		Shift saved = shiftRepository.save(shift);
		log.info("Shift {} added to event {}", saved.getId(), eventId);
		return ShiftResponse.from(saved, ShiftCounts.NONE, null);
	}

	@Transactional
	public ShiftResponse updateShift(Long shiftId, ShiftRequest request) {
		// Locked like a sign-up, because changing capacity changes who is confirmed.
		Shift shift = signupService.lockShift(shiftId);

		long confirmed = signupService.confirmedCount(shiftId);
		if (request.capacity() < confirmed) {
			throw new BusinessRuleException(
					"Capacity can't be lower than the " + confirmed + " volunteers already confirmed.");
		}

		shift.updateDetails(request.title().trim(), request.startTime(), request.endTime(), request.capacity());
		// If capacity went up, move people off the waitlist into the new spots.
		signupService.fillOpenSpots(shift);

		ShiftCounts counts = signupService.countsForShifts(List.of(shiftId)).getOrDefault(shiftId, ShiftCounts.NONE);
		return ShiftResponse.from(shift, counts, null);
	}

	@Transactional
	public void deleteShift(Long shiftId) {
		Shift shift = shiftRepository.findById(shiftId)
				.orElseThrow(() -> new NotFoundException("Shift not found."));
		if (signupService.shiftHasSignups(shiftId)) {
			throw new BusinessRuleException("This shift has volunteer sign-ups, so it can't be deleted.");
		}
		signupService.deleteCancelledSignups(List.of(shiftId));
		shiftRepository.delete(shift);
		log.info("Shift {} deleted", shiftId);
	}

}
