package com.handson.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import com.handson.TestcontainersConfiguration;

/**
 * Starts the real web server on a random port and makes a genuine HTTP request, to check the
 * exact cookie a browser receives. (MockMvc simulates the server and handles CSRF tokens
 * differently, so it can't show this.)
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CsrfCookieTest {

	@LocalServerPort
	private int port;

	@Test
	void csrfEndpointSendsATokenCookieThatJavaScriptCanRead() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/csrf")).build();

		HttpResponse<Void> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());

		List<String> cookies = response.headers().allValues("Set-Cookie");
		assertThat(response.statusCode()).isEqualTo(204);
		assertThat(cookies).anySatisfy(cookie -> {
			assertThat(cookie).startsWith("XSRF-TOKEN=");
			// Our JavaScript must read this cookie to copy it into a header, so it must not be HttpOnly.
			assertThat(cookie).doesNotContainIgnoringCase("HttpOnly");
		});
	}

}
