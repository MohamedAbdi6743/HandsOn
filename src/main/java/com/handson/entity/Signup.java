package com.handson.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Links one volunteer to one shift. There is at most one row per person per shift
 * (a database rule), so cancelling and signing up again reuses the same row.
 */
@Entity
@Table(name = "signup")
public class Signup {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shift_id")
	private Shift shift;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private AppUser user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SignupStatus status;

	// When the person (re)joined this shift. The waitlist is ordered by this: first come, first served.
	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	protected Signup() {
	}

	public Signup(Shift shift, AppUser user, SignupStatus status, LocalDateTime now) {
		this.shift = shift;
		this.user = user;
		this.status = status;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** Confirmed or waitlisted: the person still plans to come. */
	public boolean isActive() {
		return status == SignupStatus.CONFIRMED || status == SignupStatus.WAITLISTED;
	}

	/** Signing up again after cancelling. They go to the back of the line, like anyone new. */
	public void rejoin(SignupStatus newStatus, LocalDateTime now) {
		this.status = newStatus;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void cancel(LocalDateTime now) {
		this.status = SignupStatus.CANCELLED;
		this.updatedAt = now;
	}

	public void promoteFromWaitlist(LocalDateTime now) {
		this.status = SignupStatus.CONFIRMED;
		this.updatedAt = now;
	}

	/** Recorded by a coordinator after the shift starts. Can be changed if it was marked wrong. */
	public void recordAttendance(boolean attended, LocalDateTime now) {
		this.status = attended ? SignupStatus.ATTENDED : SignupStatus.NO_SHOW;
		this.updatedAt = now;
	}

	/** Confirmed, or attendance already recorded (so a mistake can be corrected). */
	public boolean canRecordAttendance() {
		return status == SignupStatus.CONFIRMED || status == SignupStatus.ATTENDED || status == SignupStatus.NO_SHOW;
	}

	public Long getId() {
		return id;
	}

	public Shift getShift() {
		return shift;
	}

	public AppUser getUser() {
		return user;
	}

	public SignupStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

}
