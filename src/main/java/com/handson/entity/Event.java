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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Something volunteers help with, e.g. "Saturday Food Drive". Its shifts are loaded
 * separately through ShiftRepository, which keeps this class simple.
 */
@Entity
@Table(name = "event")
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String title;

	private String description;

	@Column(nullable = false)
	private String location;

	// Many events can be created by one coordinator. LAZY: the user row is only
	// loaded from the database if the code actually asks for it.
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by")
	private AppUser createdBy;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Event() {
	}

	public Event(String title, String description, String location, AppUser createdBy) {
		this.title = title;
		this.description = description;
		this.location = location;
		this.createdBy = createdBy;
	}

	@PrePersist
	void setCreatedAtBeforeFirstSave() {
		this.createdAt = LocalDateTime.now();
	}

	public void updateDetails(String title, String description, String location) {
		this.title = title;
		this.description = description;
		this.location = location;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getLocation() {
		return location;
	}

	public AppUser getCreatedBy() {
		return createdBy;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

}
