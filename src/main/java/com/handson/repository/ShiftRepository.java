package com.handson.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.handson.entity.Shift;

import jakarta.persistence.LockModeType;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

	List<Shift> findByEventIdOrderByStartTime(Long eventId);

	/** All shifts for several events in ONE query, instead of one query per event. */
	List<Shift> findByEventIdInOrderByStartTime(Collection<Long> eventIds);

	/**
	 * Loads a shift and LOCKS its row until the current transaction ends
	 * (SQL: SELECT ... FOR UPDATE). Any other transaction asking for the same lock waits.
	 * Used whenever sign-ups for a shift are about to change, so two requests can't both
	 * count "1 spot left" and both take it.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Shift s WHERE s.id = :id")
	Optional<Shift> findByIdForUpdate(@Param("id") Long id);

}
