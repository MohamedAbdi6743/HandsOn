package com.handson.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Services ask this Clock for "now" instead of calling LocalDateTime.now() directly,
 * so tests can replace it with a fixed time.
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}

}
