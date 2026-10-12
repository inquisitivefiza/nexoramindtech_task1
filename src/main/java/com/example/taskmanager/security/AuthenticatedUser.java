package com.example.taskmanager.security;

/** Principal stored in the SecurityContext after a JWT has been verified. */
public record AuthenticatedUser(Long id, String email) {
}
