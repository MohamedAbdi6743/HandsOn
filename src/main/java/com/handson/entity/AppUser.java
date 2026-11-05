package com.handson.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A person who can log in. Maps to the app_user table.
 * Spring converts camelCase field names to snake_case columns (passwordHash -> password_hash).
 */
@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String fullName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	/** Required by JPA, which creates objects when loading rows. Not for our own code. */
	protected AppUser() {
	}

	public AppUser(String email, String passwordHash, String fullName, Role role) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.fullName = fullName;
		this.role = role;
	}

	@PrePersist
	void setCreatedAtBeforeFirstSave() {
		this.createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getFullName() {
		return fullName;
	}

	public Role getRole() {
		return role;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

}
