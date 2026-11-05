package com.handson.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.handson.dto.RegisterRequest;
import com.handson.entity.AppUser;
import com.handson.entity.Role;
import com.handson.exception.BusinessRuleException;
import com.handson.repository.UserRepository;

/**
 * Unit tests: no Spring, no database. The repository is a Mockito fake, so these tests
 * check only UserService's own rules and run in milliseconds.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	private UserService userService;

	@BeforeEach
	void setUp() {
		userService = new UserService(userRepository, passwordEncoder);
	}

	@Test
	void registerVolunteerSavesNormalizedEmailHashedPasswordAndVolunteerRole() {
		when(userRepository.existsByEmail("sam@example.com")).thenReturn(false);
		// Make the fake repository hand back whatever it was asked to save.
		when(userRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

		RegisterRequest request = new RegisterRequest("  Sam Ortiz ", "  Sam@Example.COM ", "correct-horse-42");
		AppUser saved = userService.registerVolunteer(request);

		assertThat(saved.getEmail()).isEqualTo("sam@example.com");
		assertThat(saved.getFullName()).isEqualTo("Sam Ortiz");
		assertThat(saved.getRole()).isEqualTo(Role.VOLUNTEER);
		assertThat(saved.getPasswordHash()).isNotEqualTo("correct-horse-42");
		assertThat(passwordEncoder.matches("correct-horse-42", saved.getPasswordHash())).isTrue();
	}

	@Test
	void registerVolunteerRejectsAnEmailThatIsAlreadyTaken() {
		when(userRepository.existsByEmail("sam@example.com")).thenReturn(true);

		RegisterRequest request = new RegisterRequest("Sam Ortiz", "SAM@example.com", "correct-horse-42");

		assertThatThrownBy(() -> userService.registerVolunteer(request))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("already exists");
		verify(userRepository, never()).save(any());
	}

	@Test
	void normalizeEmailTrimsAndLowercases() {
		assertThat(UserService.normalizeEmail("  Priya@Example.ORG ")).isEqualTo("priya@example.org");
	}

}
