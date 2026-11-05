package com.handson.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.handson.entity.Role;
import com.handson.service.UserService;

/**
 * Creates the first coordinator account when the app starts, using settings from the environment
 * (HANDSON_ADMIN_EMAIL and HANDSON_ADMIN_PASSWORD). Public registration only creates volunteers,
 * so without this there would be no way to get a coordinator. Keeping it in settings means no
 * password is ever written into the code or the Git history.
 */
@Component
public class CoordinatorAccountInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CoordinatorAccountInitializer.class);
	private static final int MIN_PASSWORD_LENGTH = 10;

	private final UserService userService;
	private final String email;
	private final String password;

	public CoordinatorAccountInitializer(
			UserService userService,
			@Value("${handson.admin.email:}") String email,
			@Value("${handson.admin.password:}") String password) {
		this.userService = userService;
		this.email = email;
		this.password = password;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (email.isBlank()) {
			log.info("HANDSON_ADMIN_EMAIL is not set, so no coordinator account was created.");
			return;
		}
		if (userService.emailExists(email)) {
			return;
		}
		// Fail loudly at startup rather than create a coordinator with a weak password.
		if (password.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalStateException(
					"HANDSON_ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters.");
		}
		userService.createUser("Coordinator", email, password, Role.COORDINATOR);
		log.info("Created the first coordinator account from settings.");
	}

}
