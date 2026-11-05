package com.handson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checks that the Flyway migration created the tables and that PostgreSQL itself
 * rejects bad data. These constraints are the last line of defense if the Java code has a bug.
 *
 * @Transactional rolls back every test, so tests never see each other's data.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class DatabaseSchemaTest {

	private static final LocalDateTime NINE_AM = LocalDateTime.of(2026, 12, 5, 9, 0);
	private static final LocalDateTime ELEVEN_AM = LocalDateTime.of(2026, 12, 5, 11, 0);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long coordinatorId;
	private Long eventId;

	@BeforeEach
	void createCoordinatorAndEvent() {
		coordinatorId = insertUser("dana@handson.test", "COORDINATOR");
		eventId = jdbcTemplate.queryForObject(
				"INSERT INTO event (title, location, created_by) VALUES (?, ?, ?) RETURNING id",
				Long.class, "Food Drive", "Community Center", coordinatorId);
	}

	@Test
	void migrationCreatesAllFourTables() {
		List<String> tables = jdbcTemplate.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
				String.class);

		assertThat(tables).contains("app_user", "event", "shift", "signup");
	}

	@Test
	void validShiftIsSaved() {
		Long shiftId = insertShift(NINE_AM, ELEVEN_AM, 5);

		assertThat(shiftId).isNotNull();
	}

	@Test
	void shiftEndingBeforeItStartsIsRejected() {
		assertThatThrownBy(() -> insertShift(ELEVEN_AM, NINE_AM, 5))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void shiftWithZeroCapacityIsRejected() {
		assertThatThrownBy(() -> insertShift(NINE_AM, ELEVEN_AM, 0))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void sameVolunteerCannotSignUpForSameShiftTwice() {
		Long shiftId = insertShift(NINE_AM, ELEVEN_AM, 5);
		Long volunteerId = insertUser("sam@example.com", "VOLUNTEER");
		insertSignup(shiftId, volunteerId, "CONFIRMED");

		assertThatThrownBy(() -> insertSignup(shiftId, volunteerId, "CONFIRMED"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void duplicateEmailIsRejected() {
		assertThatThrownBy(() -> insertUser("dana@handson.test", "VOLUNTEER"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void uppercaseEmailIsRejected() {
		assertThatThrownBy(() -> insertUser("Sam@Example.com", "VOLUNTEER"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void unknownRoleIsRejected() {
		assertThatThrownBy(() -> insertUser("sam@example.com", "ADMIN"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void unknownSignupStatusIsRejected() {
		Long shiftId = insertShift(NINE_AM, ELEVEN_AM, 5);
		Long volunteerId = insertUser("sam@example.com", "VOLUNTEER");

		assertThatThrownBy(() -> insertSignup(shiftId, volunteerId, "MAYBE"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void shiftWithSignupsCannotBeDeleted() {
		Long shiftId = insertShift(NINE_AM, ELEVEN_AM, 5);
		Long volunteerId = insertUser("sam@example.com", "VOLUNTEER");
		insertSignup(shiftId, volunteerId, "CONFIRMED");

		assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM shift WHERE id = ?", shiftId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Long insertUser(String email, String role) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO app_user (email, password_hash, full_name, role) VALUES (?, ?, ?, ?) RETURNING id",
				Long.class, email, "not-a-real-hash", "Test User", role);
	}

	private Long insertShift(LocalDateTime start, LocalDateTime end, int capacity) {
		return jdbcTemplate.queryForObject(
				"INSERT INTO shift (event_id, title, start_time, end_time, capacity) VALUES (?, ?, ?, ?, ?) RETURNING id",
				Long.class, eventId, "Setup crew", start, end, capacity);
	}

	private void insertSignup(Long shiftId, Long userId, String status) {
		jdbcTemplate.update(
				"INSERT INTO signup (shift_id, user_id, status) VALUES (?, ?, ?)",
				shiftId, userId, status);
	}

}
