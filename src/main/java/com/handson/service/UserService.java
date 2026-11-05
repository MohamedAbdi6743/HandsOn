package com.handson.service;

import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.handson.dto.RegisterRequest;
import com.handson.entity.AppUser;
import com.handson.entity.Role;
import com.handson.exception.BusinessRuleException;
import com.handson.exception.NotFoundException;
import com.handson.repository.UserRepository;

/**
 * Rules for creating and finding user accounts.
 */
@Service
public class UserService {

	private static final Logger log = LoggerFactory.getLogger(UserService.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	/** Public sign-up always creates a volunteer. Coordinators are never created this way. */
	@Transactional
	public AppUser registerVolunteer(RegisterRequest request) {
		return createUser(request.fullName(), request.email(), request.password(), Role.VOLUNTEER);
	}

	@Transactional
	public AppUser createUser(String fullName, String email, String rawPassword, Role role) {
		String normalizedEmail = normalizeEmail(email);

		if (userRepository.existsByEmail(normalizedEmail)) {
			throw new BusinessRuleException("An account with this email already exists.");
		}

		String passwordHash = passwordEncoder.encode(rawPassword);
		AppUser user = new AppUser(normalizedEmail, passwordHash, fullName.trim(), role);
		AppUser savedUser = userRepository.save(user);

		// Log the id, not the email, to keep personal details out of log files.
		log.info("Created {} account with id {}", role, savedUser.getId());
		return savedUser;
	}

	@Transactional(readOnly = true)
	public AppUser getByEmail(String email) {
		return userRepository.findByEmail(normalizeEmail(email))
				.orElseThrow(() -> new NotFoundException("User not found."));
	}

	/** Like getByEmail, but returns an empty Optional instead of throwing when there's no such user. */
	@Transactional(readOnly = true)
	public Optional<AppUser> findByEmail(String email) {
		return userRepository.findByEmail(normalizeEmail(email));
	}

	@Transactional(readOnly = true)
	public boolean emailExists(String email) {
		return userRepository.existsByEmail(normalizeEmail(email));
	}

	/** " Sam@Example.COM " and "sam@example.com" are the same account. */
	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
