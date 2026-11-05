package com.handson.dto;

import com.handson.entity.AppUser;
import com.handson.entity.Role;

/**
 * What the API shows about a user. Deliberately leaves out the password hash.
 */
public record UserResponse(Long id, String fullName, String email, Role role) {

	public static UserResponse from(AppUser user) {
		return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
	}

}
