package com.handson.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.handson.entity.AppUser;

/**
 * Database access for users. Spring Data writes the SQL from the method names:
 * findByEmail -> SELECT ... FROM app_user WHERE email = ?
 */
public interface UserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);

}
