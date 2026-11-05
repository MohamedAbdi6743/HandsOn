package com.handson.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.handson.dto.MySignupResponse;
import com.handson.dto.RosterEntryResponse;
import com.handson.dto.ShiftCounts;
import com.handson.dto.SignupResponse;
import com.handson.entity.AppUser;
import com.handson.entity.Shift;
import com.handson.entity.Signup;
import com.handson.entity.SignupStatus;
import com.handson.exception.BusinessRuleException;
import com.handson.exception.NotFoundException;
import com.handson.repository.ShiftRepository;
import com.handson.repository.ShiftStatusCount;
import com.handson.repository.SignupRepository;

/**
 * The sign-up rules:
 *   1. You can't sign up for a shift that has started.
 *   2. You can't sign up for the same shift twice.
 *   3. You can't hold two shifts that overlap in time.
 *   4. If the shift is full, you join the waitlist instead.
 *   5. Confirmed spots can't be cancelled less than 12 hours (configurable) before the shift.
 *   6. When a confirmed person cancels, the first person on the waitlist is promoted.
 */
@Service
public class SignupService {

	private static final Logger log = LoggerFactory.getLogger(SignupService.class);

	static final List<SignupStatus> ACTIVE = List.of(SignupStatus.CONFIRMED, SignupStatus.WAITLISTED);

	// Any of these means real people are attached to a shift, so it must not be deleted.
	private static final List<SignupStatus> BLOCKS_DELETION =
			List.of(SignupStatus.CONFIRMED, SignupStatus.WAITLISTED, SignupStatus.ATTENDED, SignupStatus.NO_SHOW);

	private static final DateTimeFormatter SHIFT_TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH);

	private final SignupRepository signupRepository;
	private final ShiftRepository shiftRepository;
	private final UserService userService;
	private final Clock clock;
	private final long cancellationCutoffHours;

	public SignupService(SignupRepository signupRepository, ShiftRepository shiftRepository,
			UserService userService, Clock clock,
			@Value("${handson.cancellation-cutoff-hours:12}") long cancellationCutoffHours) {
		this.signupRepository = signupRepository;
		this.shiftRepository = shiftRepository;
		this.userService = userService;
		this.clock = clock;
		this.cancellationCutoffHours = cancellationCutoffHours;
	}

	// ---------- signing up and cancelling ----------

	@Transactional
	public SignupResponse signUp(Long shiftId, String volunteerEmail) {
		// Lock the shift row until this transaction ends. A second sign-up for the same shift
		// waits on this line, then counts again, so two people can never both take the last spot.
		Shift shift = lockShift(shiftId);
		LocalDateTime now = LocalDateTime.now(clock);

		if (!shift.getStartTime().isAfter(now)) {
			throw new BusinessRuleException("This shift has already started.");
		}

		AppUser volunteer = userService.getByEmail(volunteerEmail);
		Optional<Signup> existing = signupRepository.findByShiftIdAndUserId(shiftId, volunteer.getId());
		if (existing.isPresent() && existing.get().isActive()) {
			throw new BusinessRuleException("You're already signed up for this shift.");
		}

		checkNoOverlap(volunteer, shift);

		SignupStatus status = hasOpenSpot(shift) ? SignupStatus.CONFIRMED : SignupStatus.WAITLISTED;
		Signup signup;
		if (existing.isPresent()) {
			// They cancelled earlier. Reuse their row (one row per person per shift).
			signup = existing.get();
			signup.rejoin(status, now);
		}
		else {
			signup = signupRepository.save(new Signup(shift, volunteer, status, now));
		}

		log.info("User {} signed up for shift {} as {}", volunteer.getId(), shiftId, status);
		return toResponse(signup);
	}

	@Transactional
	public SignupResponse cancel(Long signupId, String volunteerEmail) {
		String email = UserService.normalizeEmail(volunteerEmail);
		// Someone else's sign-up is reported as "not found", so the API never reveals it exists.
		Signup signup = signupRepository.findById(signupId)
				.filter(found -> found.getUser().getEmail().equals(email))
				.orElseThrow(() -> new NotFoundException("Sign-up not found."));

		// Same lock as signUp(), so a cancel and a sign-up on one shift can't interleave.
		Shift shift = lockShift(signup.getShift().getId());
		LocalDateTime now = LocalDateTime.now(clock);

		if (!signup.isActive()) {
			throw new BusinessRuleException("This sign-up is already cancelled or finished.");
		}

		boolean wasConfirmed = signup.getStatus() == SignupStatus.CONFIRMED;
		LocalDateTime cutoff = shift.getStartTime().minusHours(cancellationCutoffHours);
		if (wasConfirmed && now.isAfter(cutoff)) {
			throw new BusinessRuleException("Confirmed spots can't be cancelled less than "
					+ cancellationCutoffHours + " hours before the shift starts.");
		}

		signup.cancel(now);
		log.info("Sign-up {} cancelled", signupId);

		// Leaving the waitlist doesn't free a spot; leaving a confirmed spot does.
		if (wasConfirmed) {
			fillOpenSpots(shift);
		}
		return toResponse(signup);
	}

	/**
	 * Moves people from the waitlist into free spots, first come first served.
	 * The caller must already hold the shift's lock (see lockShift).
	 */
	@Transactional
	public void fillOpenSpots(Shift shift) {
		LocalDateTime now = LocalDateTime.now(clock);
		while (hasOpenSpot(shift)) {
			Optional<Signup> next = signupRepository
					.findFirstByShiftIdAndStatusOrderByCreatedAtAscIdAsc(shift.getId(), SignupStatus.WAITLISTED);
			if (next.isEmpty()) {
				return;
			}
			next.get().promoteFromWaitlist(now);
			log.info("Sign-up {} promoted from the waitlist on shift {}", next.get().getId(), shift.getId());
		}
	}

	// ---------- reading ----------

	@Transactional(readOnly = true)
	public List<MySignupResponse> listMySignups(String volunteerEmail) {
		AppUser volunteer = userService.getByEmail(volunteerEmail);
		List<MySignupResponse> result = new ArrayList<>();
		for (Signup signup : signupRepository.findForVolunteer(volunteer.getId(), SignupStatus.CANCELLED)) {
			Shift shift = signup.getShift();
			result.add(new MySignupResponse(
					signup.getId(),
					signup.getStatus(),
					waitlistPosition(signup),
					shift.getId(),
					shift.getTitle(),
					shift.getStartTime(),
					shift.getEndTime(),
					shift.getEvent().getId(),
					shift.getEvent().getTitle(),
					shift.getEvent().getLocation()));
		}
		return result;
	}

	/** Confirmed (and attended / no-show) people first, then the waitlist in order. */
	@Transactional(readOnly = true)
	public List<RosterEntryResponse> getRoster(Long shiftId) {
		if (!shiftRepository.existsById(shiftId)) {
			throw new NotFoundException("Shift not found.");
		}
		List<RosterEntryResponse> onShift = new ArrayList<>();
		List<RosterEntryResponse> waitlist = new ArrayList<>();
		for (Signup signup : signupRepository.findRoster(shiftId, SignupStatus.CANCELLED)) {
			AppUser user = signup.getUser();
			if (signup.getStatus() == SignupStatus.WAITLISTED) {
				waitlist.add(new RosterEntryResponse(signup.getId(), user.getId(), user.getFullName(),
						user.getEmail(), signup.getStatus(), waitlist.size() + 1));
			}
			else {
				onShift.add(new RosterEntryResponse(signup.getId(), user.getId(), user.getFullName(),
						user.getEmail(), signup.getStatus(), null));
			}
		}
		onShift.addAll(waitlist);
		return onShift;
	}

	/** Confirmed and waitlisted counts for many shifts, using one query. */
	@Transactional(readOnly = true)
	public Map<Long, ShiftCounts> countsForShifts(Collection<Long> shiftIds) {
		Map<Long, ShiftCounts> counts = new HashMap<>();
		if (shiftIds.isEmpty()) {
			return counts;
		}
		for (ShiftStatusCount row : signupRepository.countByShiftAndStatus(shiftIds, ACTIVE)) {
			ShiftCounts current = counts.getOrDefault(row.shiftId(), ShiftCounts.NONE);
			if (row.status() == SignupStatus.CONFIRMED) {
				counts.put(row.shiftId(), new ShiftCounts(row.count(), current.waitlisted()));
			}
			else {
				counts.put(row.shiftId(), new ShiftCounts(current.confirmed(), row.count()));
			}
		}
		return counts;
	}

	/** The viewer's own active sign-ups on the given shifts, keyed by shift id. */
	@Transactional(readOnly = true)
	public Map<Long, SignupResponse> mySignupsForShifts(String viewerEmail, Collection<Long> shiftIds) {
		Map<Long, SignupResponse> mine = new HashMap<>();
		Optional<AppUser> viewer = userService.findByEmail(viewerEmail);
		if (shiftIds.isEmpty() || viewer.isEmpty()) {
			return mine;
		}
		for (Signup signup : signupRepository.findByUserIdAndShiftIdIn(viewer.get().getId(), shiftIds)) {
			if (signup.isActive()) {
				mine.put(signup.getShift().getId(), toResponse(signup));
			}
		}
		return mine;
	}

	// ---------- used by ShiftService and EventService ----------

	public Shift lockShift(Long shiftId) {
		return shiftRepository.findByIdForUpdate(shiftId)
				.orElseThrow(() -> new NotFoundException("Shift not found."));
	}

	public long confirmedCount(Long shiftId) {
		return signupRepository.countByShiftIdAndStatus(shiftId, SignupStatus.CONFIRMED);
	}

	public boolean shiftHasSignups(Long shiftId) {
		return signupRepository.existsByShiftIdAndStatusIn(shiftId, BLOCKS_DELETION);
	}

	public boolean eventHasSignups(Long eventId) {
		return signupRepository.existsByShiftEventIdAndStatusIn(eventId, BLOCKS_DELETION);
	}

	/** Removes leftover CANCELLED rows so the shifts they point to can be deleted. */
	public void deleteCancelledSignups(Collection<Long> shiftIds) {
		if (!shiftIds.isEmpty()) {
			signupRepository.deleteAll(signupRepository.findByShiftIdIn(shiftIds));
		}
	}

	// ---------- helpers ----------

	private boolean hasOpenSpot(Shift shift) {
		return confirmedCount(shift.getId()) < shift.getCapacity();
	}

	private void checkNoOverlap(AppUser volunteer, Shift shift) {
		List<Signup> overlapping = signupRepository.findOverlapping(
				volunteer.getId(), shift.getId(), shift.getStartTime(), shift.getEndTime(), ACTIVE);
		if (!overlapping.isEmpty()) {
			Shift other = overlapping.get(0).getShift();
			throw new BusinessRuleException("This shift overlaps with your '" + other.getTitle() + "' shift ("
					+ SHIFT_TIME.format(other.getStartTime()) + ").");
		}
	}

	private SignupResponse toResponse(Signup signup) {
		return new SignupResponse(signup.getId(), signup.getShift().getId(), signup.getStatus(), waitlistPosition(signup));
	}

	/** 1 = front of the line. Null for anyone not on the waitlist. */
	private Integer waitlistPosition(Signup signup) {
		if (signup.getStatus() != SignupStatus.WAITLISTED) {
			return null;
		}
		long aheadOfMe = signupRepository.countByShiftIdAndStatusAndCreatedAtBefore(
				signup.getShift().getId(), SignupStatus.WAITLISTED, signup.getCreatedAt());
		return (int) aheadOfMe + 1;
	}

}
