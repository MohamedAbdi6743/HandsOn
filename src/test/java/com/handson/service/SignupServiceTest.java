package com.handson.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.handson.dto.SignupResponse;
import com.handson.entity.AppUser;
import com.handson.entity.Event;
import com.handson.entity.Role;
import com.handson.entity.Shift;
import com.handson.entity.Signup;
import com.handson.entity.SignupStatus;
import com.handson.exception.BusinessRuleException;
import com.handson.repository.ShiftRepository;
import com.handson.repository.SignupRepository;

/**
 * Unit tests for SignupService with a FROZEN clock: "now" is always Dec 1, 2026 at 09:00.
 * That makes the time rules exact, e.g. a shift starting at 20:59 the same day is
 * 11 h 59 min away (inside the 12-hour cutoff) and one at 21:01 is outside it.
 */
@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

	private static final ZoneId ZONE = ZoneId.of("America/Toronto");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 1, 9, 0);

	@Mock
	private SignupRepository signupRepository;

	@Mock
	private ShiftRepository shiftRepository;

	@Mock
	private UserService userService;

	private SignupService signupService;

	private final AppUser coordinator = new AppUser("dana@handson.test", "hash", "Dana Lee", Role.COORDINATOR);
	private final AppUser sam = new AppUser("sam@example.com", "hash", "Sam Ortiz", Role.VOLUNTEER);
	private final Event event = new Event("Food Drive", null, "Hall", coordinator);

	@BeforeEach
	void setUp() {
		Clock frozenClock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
		signupService = new SignupService(signupRepository, shiftRepository, userService, frozenClock, 12);
	}

	@Test
	void fullShiftPutsVolunteerOnWaitlist() {
		Shift shift = new Shift(event, "Setup", NOW.plusDays(2), NOW.plusDays(2).plusHours(2), 3);
		when(shiftRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(shift));
		when(userService.getByEmail("sam@example.com")).thenReturn(sam);
		when(signupRepository.findByShiftIdAndUserId(any(), any())).thenReturn(Optional.empty());
		when(signupRepository.findOverlapping(any(), any(), any(), any(), any())).thenReturn(List.of());
		when(signupRepository.countByShiftIdAndStatus(any(), eq(SignupStatus.CONFIRMED))).thenReturn(3L);   // full
		when(signupRepository.save(any(Signup.class))).thenAnswer(call -> call.getArgument(0));

		SignupResponse response = signupService.signUp(7L, "sam@example.com");

		assertThat(response.status()).isEqualTo(SignupStatus.WAITLISTED);
		assertThat(response.waitlistPosition()).isEqualTo(1);
	}

	@Test
	void confirmedSpotInsideTheCutoffCannotBeCancelled() {
		// Starts at 20:59 today: 11 h 59 min from now.
		Shift shift = new Shift(event, "Evening", NOW.plusHours(11).plusMinutes(59), NOW.plusHours(14), 3);
		Signup samSignup = new Signup(shift, sam, SignupStatus.CONFIRMED, NOW.minusDays(1));
		when(signupRepository.findById(5L)).thenReturn(Optional.of(samSignup));
		when(shiftRepository.findByIdForUpdate(any())).thenReturn(Optional.of(shift));

		assertThatThrownBy(() -> signupService.cancel(5L, "sam@example.com"))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("less than 12 hours");
		assertThat(samSignup.getStatus()).isEqualTo(SignupStatus.CONFIRMED);
	}

	@Test
	void cancellingConfirmedSpotJustOutsideTheCutoffPromotesNextInLine() {
		// Starts at 21:01 today: 12 h 1 min from now.
		Shift shift = new Shift(event, "Evening", NOW.plusHours(12).plusMinutes(1), NOW.plusHours(14), 1);
		AppUser priya = new AppUser("priya@example.com", "hash", "Priya Shah", Role.VOLUNTEER);
		Signup samSignup = new Signup(shift, sam, SignupStatus.CONFIRMED, NOW.minusDays(2));
		Signup priyaSignup = new Signup(shift, priya, SignupStatus.WAITLISTED, NOW.minusDays(1));

		when(signupRepository.findById(5L)).thenReturn(Optional.of(samSignup));
		when(shiftRepository.findByIdForUpdate(any())).thenReturn(Optional.of(shift));
		// After Sam cancels nobody is confirmed; after Priya is promoted, one is.
		when(signupRepository.countByShiftIdAndStatus(any(), eq(SignupStatus.CONFIRMED))).thenReturn(0L, 1L);
		when(signupRepository.findFirstByShiftIdAndStatusOrderByCreatedAtAscIdAsc(any(), eq(SignupStatus.WAITLISTED)))
				.thenReturn(Optional.of(priyaSignup));

		SignupResponse response = signupService.cancel(5L, "sam@example.com");

		assertThat(response.status()).isEqualTo(SignupStatus.CANCELLED);
		assertThat(priyaSignup.getStatus()).isEqualTo(SignupStatus.CONFIRMED);
	}

	@Test
	void shiftStartingNowIsTooLateToJoin() {
		Shift shift = new Shift(event, "Now", NOW, NOW.plusHours(2), 3);
		when(shiftRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(shift));

		assertThatThrownBy(() -> signupService.signUp(7L, "sam@example.com"))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessage("This shift has already started.");
	}

}
