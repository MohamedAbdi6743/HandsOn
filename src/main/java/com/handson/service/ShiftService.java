package com.handson.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

	public ShiftService(ShiftRepository shiftRepository, EventRepository eventRepository) {
		this.shiftRepository = shiftRepository;
		this.eventRepository = eventRepository;
	}

	@Transactional
	public ShiftResponse addShift(Long eventId, ShiftRequest request) {
		Event event = eventRepository.findById(eventId)
				.orElseThrow(() -> new NotFoundException("Event not found."));
		Shift shift = new Shift(event, request.title().trim(), request.startTime(), request.endTime(), request.capacity());
		Shift saved = shiftRepository.save(shift);
		log.info("Shift {} added to event {}", saved.getId(), eventId);
		return ShiftResponse.from(saved);
	}

	@Transactional
	public ShiftResponse updateShift(Long shiftId, ShiftRequest request) {
		Shift shift = findShift(shiftId);
		shift.updateDetails(request.title().trim(), request.startTime(), request.endTime(), request.capacity());
		return ShiftResponse.from(shift);
	}

	@Transactional
	public void deleteShift(Long shiftId) {
		Shift shift = findShift(shiftId);
		if (shiftRepository.countSignupsForShift(shiftId) > 0) {
			throw new BusinessRuleException("This shift has volunteer sign-ups, so it can't be deleted.");
		}
		shiftRepository.delete(shift);
		log.info("Shift {} deleted", shiftId);
	}

	private Shift findShift(Long shiftId) {
		return shiftRepository.findById(shiftId)
				.orElseThrow(() -> new NotFoundException("Shift not found."));
	}

}
