package com.handson.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.handson.TestcontainersConfiguration;
import com.handson.entity.AppUser;
import com.handson.entity.Role;
import com.handson.repository.UserRepository;

/**
 * Integration tests: the whole app runs (security, controllers, services, real PostgreSQL),
 * and MockMvc sends HTTP requests to it without opening a real network port.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthApiTest {

	private static final String SAM_JSON = """
			{ "fullName": "Sam Ortiz", "email": "Sam@Example.com", "password": "correct-horse-42" }
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Test
	void registerCreatesVolunteerAndNeverReturnsThePassword() throws Exception {
		mockMvc.perform(post("/api/auth/register").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(SAM_JSON))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("sam@example.com"))
				.andExpect(jsonPath("$.role").value("VOLUNTEER"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void registerWithInvalidFieldsReturns400AndNamesEachField() throws Exception {
		String badJson = """
				{ "fullName": "", "email": "not-an-email", "password": "short" }
				""";

		mockMvc.perform(post("/api/auth/register").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(badJson))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors.fullName").exists())
				.andExpect(jsonPath("$.fieldErrors.email").exists())
				.andExpect(jsonPath("$.fieldErrors.password").exists());
	}

	@Test
	void registerWithTakenEmailReturns409() throws Exception {
		registerSam();

		mockMvc.perform(post("/api/auth/register").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(SAM_JSON))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("An account with this email already exists."));
	}

	@Test
	void registerWithoutCsrfTokenIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON).content(SAM_JSON))
				.andExpect(status().isForbidden());
	}

	@Test
	void loginWithCorrectPasswordReturnsTheUser() throws Exception {
		registerSam();

		mockMvc.perform(post("/api/auth/login").with(csrf())
						.param("email", "sam@example.com")
						.param("password", "correct-horse-42"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Sam Ortiz"));
	}

	@Test
	void loginWithWrongPasswordReturns401() throws Exception {
		registerSam();

		mockMvc.perform(post("/api/auth/login").with(csrf())
						.param("email", "sam@example.com")
						.param("password", "wrong-password-1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Email or password is incorrect."));
	}

	@Test
	void currentUserWithoutLoginReturns401() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void currentUserAfterLoginReturnsThatUser() throws Exception {
		registerSam();
		MockHttpSession session = loginAsSam();

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("sam@example.com"));
	}

	@Test
	void logoutEndsTheSession() throws Exception {
		registerSam();
		MockHttpSession session = loginAsSam();

		mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void pagesAreServedWithContentSecurityPolicy() throws Exception {
		mockMvc.perform(get("/index.html"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
	}

	@Test
	void coordinatorAccountIsCreatedFromSettingsAtStartup() {
		AppUser coordinator = userRepository.findByEmail("coordinator@handson.test").orElseThrow();

		assertThat(coordinator.getRole()).isEqualTo(Role.COORDINATOR);
	}

	private void registerSam() throws Exception {
		mockMvc.perform(post("/api/auth/register").with(csrf())
						.contentType(MediaType.APPLICATION_JSON).content(SAM_JSON))
				.andExpect(status().isCreated());
	}

	private MockHttpSession loginAsSam() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login").with(csrf())
						.param("email", "sam@example.com")
						.param("password", "correct-horse-42"))
				.andExpect(status().isOk())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

}
