package com.handson.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.handson.entity.Shift;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

	List<Shift> findByEventIdOrderByStartTime(Long eventId);

	/** All shifts for several events in ONE query, instead of one query per event. */
	List<Shift> findByEventIdInOrderByStartTime(Collection<Long> eventIds);

	// Plain SQL ("native") queries, because the signup table doesn't have a Java entity yet.
	// The sign-ups milestone adds one and can replace these.

	@Query(value = "SELECT COUNT(*) FROM signup WHERE shift_id = :shiftId", nativeQuery = true)
	long countSignupsForShift(@Param("shiftId") Long shiftId);

	@Query(value = """
			SELECT COUNT(*) FROM signup su
			JOIN shift s ON s.id = su.shift_id
			WHERE s.event_id = :eventId
			""", nativeQuery = true)
	long countSignupsForEvent(@Param("eventId") Long eventId);

}
