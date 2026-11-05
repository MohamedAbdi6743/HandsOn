package com.handson.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.handson.TestcontainersConfiguration;
import com.handson.dto.SignupResponse;
import com.handson.entity.Event;
import com.handson.entity.Role;
import com.handson.entity.Shift;
import com.handson.entity.SignupStatus;
import com.handson.repository.EventRepository;
import com.handson.repository.ShiftRepository;

/**
 * The "last spot" problem: many volunteers click Sign up at the same moment.
 * Ten threads sign up for a shift with three spots, all released at once.
 * Exactly three must be confirmed and seven waitlisted at positions 1 to 7.
 *
 * Not @Transactional: each sign-up must run and commit in its own transaction, exactly
 * like real requests, so the cleanup method removes the data afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SignupConcurrencyTest {

	private static final int VOLUNTEERS = 10;
	private static final int CAPACITY = 3;

	@Autowired
	private SignupService signupService;

	@Autowired
	private UserService userService;

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private ShiftRepository shiftRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long eventId;

	@Test
	void tenVolunteersRacingForThreeSpotsNeverOverbook() throws Exception {
		Event event = eventRepository.save(new Event("Race test", null, "Hall",
				userService.getByEmail("coordinator@handson.test")));
		eventId = event.getId();
		LocalDateTime start = LocalDateTime.now().plusDays(3);
		Long shiftId = shiftRepository.save(new Shift(event, "Popular shift", start, start.plusHours(2), CAPACITY)).getId();

		List<String> emails = new ArrayList<>();
		for (int i = 1; i <= VOLUNTEERS; i++) {
			String email = "racer" + i + "@race.test";
			userService.createUser("Racer " + i, email, "correct-horse-42", Role.VOLUNTEER);
			emails.add(email);
		}

		// Every thread waits at the starting gate, then all are released together.
		CountDownLatch startingGate = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(VOLUNTEERS);
		List<Future<SignupResponse>> results = new ArrayList<>();
		for (String email : emails) {
			Callable<SignupResponse> signUp = () -> {
				startingGate.await();
				return signupService.signUp(shiftId, email);
			};
			results.add(pool.submit(signUp));
		}
		startingGate.countDown();

		int confirmed = 0;
		List<Integer> waitlistPositions = new ArrayList<>();
		for (Future<SignupResponse> result : results) {
			SignupResponse response = result.get();
			if (response.status() == SignupStatus.CONFIRMED) {
				confirmed++;
			}
			else {
				waitlistPositions.add(response.waitlistPosition());
			}
		}
		pool.shutdown();

		assertThat(confirmed).isEqualTo(CAPACITY);
		assertThat(waitlistPositions).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7);
		assertThat(signupService.confirmedCount(shiftId)).isEqualTo(CAPACITY);
	}

	@AfterEach
	void removeTestData() {
		jdbcTemplate.update("DELETE FROM signup WHERE user_id IN (SELECT id FROM app_user WHERE email LIKE '%@race.test')");
		if (eventId != null) {
			jdbcTemplate.update("DELETE FROM shift WHERE event_id = ?", eventId);
			jdbcTemplate.update("DELETE FROM event WHERE id = ?", eventId);
		}
		jdbcTemplate.update("DELETE FROM app_user WHERE email LIKE '%@race.test'");
	}

}
