package com.handson.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.handson.dto.MyHoursResponse;
import com.handson.dto.VolunteerHoursResponse;
import com.handson.service.ReportService;

/** Hours reports: coordinators see everyone, volunteers see their own totals. */
@RestController
public class ReportController {

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	// Example: /api/reports/volunteer-hours?from=2026-01-01&to=2026-12-31 (both optional)
	@GetMapping("/api/reports/volunteer-hours")
	public List<VolunteerHoursResponse> volunteerHours(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return reportService.volunteerHours(from, to);
	}

	@GetMapping("/api/me/hours")
	public MyHoursResponse myHours(Authentication authentication) {
		return reportService.myHours(authentication.getName());
	}

}
