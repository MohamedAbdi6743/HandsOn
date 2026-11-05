package com.handson.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.handson.entity.Signup;
import com.handson.entity.SignupStatus;

public interface SignupRepository extends JpaRepository<Signup, Long> {

	Optional<Signup> findByShiftIdAndUserId(Long shiftId, Long userId);

	long countByShiftIdAndStatus(Long shiftId, SignupStatus status);

	/** How many people joined the waitlist before a given time. Used for "you are #3". */
	long countByShiftIdAndStatusAndCreatedAtBefore(Long shiftId, SignupStatus status, LocalDateTime createdAt);

	/** The person at the front of the waitlist. */
	Optional<Signup> findFirstByShiftIdAndStatusOrderByCreatedAtAscIdAsc(Long shiftId, SignupStatus status);

	boolean existsByShiftIdAndStatusIn(Long shiftId, Collection<SignupStatus> statuses);

	boolean existsByShiftEventIdAndStatusIn(Long eventId, Collection<SignupStatus> statuses);

	List<Signup> findByShiftIdIn(Collection<Long> shiftIds);

	/**
	 * The volunteer's active sign-ups on OTHER shifts whose times overlap [start, end).
	 * Two shifts overlap when each one starts before the other ends.
	 * Back-to-back shifts (one ends at 10:00, the next starts at 10:00) do not overlap.
	 */
	@Query("""
			SELECT s FROM Signup s JOIN FETCH s.shift sh
			WHERE s.user.id = :userId
			  AND s.status IN :statuses
			  AND sh.id <> :shiftId
			  AND sh.startTime < :end
			  AND sh.endTime > :start
			ORDER BY sh.startTime
			""")
	List<Signup> findOverlapping(@Param("userId") Long userId, @Param("shiftId") Long shiftId,
			@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
			@Param("statuses") Collection<SignupStatus> statuses);

	/** Sign-up counts for many shifts in one query, grouped by shift and status. */
	@Query("""
			SELECT new com.handson.repository.ShiftStatusCount(s.shift.id, s.status, COUNT(s))
			FROM Signup s
			WHERE s.shift.id IN :shiftIds AND s.status IN :statuses
			GROUP BY s.shift.id, s.status
			""")
	List<ShiftStatusCount> countByShiftAndStatus(@Param("shiftIds") Collection<Long> shiftIds,
			@Param("statuses") Collection<SignupStatus> statuses);

	List<Signup> findByUserIdAndShiftIdIn(Long userId, Collection<Long> shiftIds);

	/** A volunteer's sign-ups with their shift and event, soonest shift first. */
	@Query("""
			SELECT s FROM Signup s JOIN FETCH s.shift sh JOIN FETCH sh.event
			WHERE s.user.id = :userId AND s.status <> :excluded
			ORDER BY sh.startTime
			""")
	List<Signup> findForVolunteer(@Param("userId") Long userId, @Param("excluded") SignupStatus excluded);

	/** Everyone on a shift (with their user details), in the order they joined. */
	@Query("""
			SELECT s FROM Signup s JOIN FETCH s.user
			WHERE s.shift.id = :shiftId AND s.status <> :excluded
			ORDER BY s.createdAt, s.id
			""")
	List<Signup> findRoster(@Param("shiftId") Long shiftId, @Param("excluded") SignupStatus excluded);

}
