package com.handson.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One time slot inside an event, e.g. "Setup crew, 08:00-10:00, 2 spots".
 */
@Entity
@Table(name = "shift")
public class Shift {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// Many shifts belong to one event. The event_id column holds the link.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id")
	private Event event;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private LocalDateTime startTime;

	@Column(nullable = false)
	private LocalDateTime endTime;

	@Column(nullable = false)
	private int capacity;

	protected Shift() {
	}

	public Shift(Event event, String title, LocalDateTime startTime, LocalDateTime endTime, int capacity) {
		this.event = event;
		this.title = title;
		this.startTime = startTime;
		this.endTime = endTime;
		this.capacity = capacity;
	}

	public void updateDetails(String title, LocalDateTime startTime, LocalDateTime endTime, int capacity) {
		this.title = title;
		this.startTime = startTime;
		this.endTime = endTime;
		this.capacity = capacity;
	}

	public Long getId() {
		return id;
	}

	public Event getEvent() {
		return event;
	}

	public String getTitle() {
		return title;
	}

	public LocalDateTime getStartTime() {
		return startTime;
	}

	public LocalDateTime getEndTime() {
		return endTime;
	}

	public int getCapacity() {
		return capacity;
	}

}
