package com.handson.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.handson.entity.Event;

public interface EventRepository extends JpaRepository<Event, Long> {

	/**
	 * Events that still have a shift ending in the future, plus events with no shifts yet
	 * (so a coordinator can see an event they just created before adding shifts).
	 * Written in JPQL, which uses entity and field names instead of table and column names.
	 */
	@Query("""
			SELECT e FROM Event e
			WHERE NOT EXISTS (SELECT s FROM Shift s WHERE s.event = e)
			   OR EXISTS (SELECT s FROM Shift s WHERE s.event = e AND s.endTime > :now)
			""")
	List<Event> findUpcoming(@Param("now") LocalDateTime now);

}
