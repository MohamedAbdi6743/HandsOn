package com.handson.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfException;

import com.handson.dto.UserResponse;
import com.handson.exception.ApiError;
import com.handson.service.UserService;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Who can call which URL, how login and logout work, and CSRF protection.
 *
 * Login uses a server-side session: after a successful login the browser gets an HttpOnly
 * session cookie that it sends with every request. Spring Security's defaults are built for
 * HTML login pages and redirects, so the handlers below answer with JSON and status codes instead.
 */
@Configuration
public class SecurityConfig {

	private final ObjectMapper objectMapper;

	public SecurityConfig(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	// UserService is a method parameter, not a constructor parameter: UserService needs the
	// PasswordEncoder created below, so asking for it in the constructor would be a circular dependency.
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, UserService userService) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
				// Rules are checked top to bottom; the first match wins.
				.requestMatchers(HttpMethod.GET, "/api/events/**").authenticated()
				.requestMatchers("/api/events/**", "/api/shifts/**").hasRole("COORDINATOR")
				.requestMatchers("/api/**").authenticated()
				// HTML, CSS and JS files are public. The data they show comes from /api, which is protected.
				.anyRequest().permitAll())

			// The CSRF token is stored in an XSRF-TOKEN cookie. Our JavaScript copies it into an
			// X-XSRF-TOKEN header on every POST/PUT/DELETE. Another website can't read our cookie,
			// so it can't forge that header.
			.csrf(csrf -> csrf.spa())

			// POST /api/auth/login with form fields "email" and "password".
			.formLogin(form -> form
				.loginProcessingUrl("/api/auth/login")
				.usernameParameter("email")
				.passwordParameter("password")
				.successHandler((request, response, authentication) -> {
					UserResponse user = UserResponse.from(userService.getByEmail(authentication.getName()));
					writeJson(response, HttpStatus.OK, user);
				})
				.failureHandler((request, response, exception) ->
					writeJson(response, HttpStatus.UNAUTHORIZED,
						ApiError.of(HttpStatus.UNAUTHORIZED, "Email or password is incorrect."))))

			.logout(logout -> logout
				.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))

			.exceptionHandling(errors -> errors
				// Not logged in: 401 instead of a redirect to an HTML login page.
				.authenticationEntryPoint((request, response, exception) ->
					writeJson(response, HttpStatus.UNAUTHORIZED,
						ApiError.of(HttpStatus.UNAUTHORIZED, "Please log in first.")))
				// Logged in but not allowed, or the CSRF token is missing.
				.accessDeniedHandler((request, response, exception) -> {
					String message = (exception instanceof CsrfException)
						? "Missing or invalid CSRF token. Refresh the page and try again."
						: "You don't have permission to do this.";
					writeJson(response, HttpStatus.FORBIDDEN, ApiError.of(HttpStatus.FORBIDDEN, message));
				}));

		return http.build();
	}

	/** BCrypt is deliberately slow, which makes guessing passwords from a stolen hash impractical. */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	private void writeJson(HttpServletResponse response, HttpStatus status, Object body) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), body);
	}

}
