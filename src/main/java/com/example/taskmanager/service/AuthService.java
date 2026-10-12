package com.example.taskmanager.service;

import com.example.taskmanager.dto.AuthResponse;
import com.example.taskmanager.dto.LoginRequest;
import com.example.taskmanager.dto.RegisterRequest;
import com.example.taskmanager.dto.UserResponse;
import com.example.taskmanager.entity.User;
import com.example.taskmanager.exception.EmailAlreadyExistsException;
import com.example.taskmanager.exception.InvalidCredentialsException;
import com.example.taskmanager.repository.UserRepository;
import com.example.taskmanager.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Compared against when the email is unknown, so response time doesn't reveal which emails exist. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode("timing-attack-mitigation");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // Two concurrent registrations passed the exists-check; the unique index caught the loser.
            throw new EmailAlreadyExistsException(email);
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
        boolean passwordMatches = passwordEncoder.matches(
                request.password(), user != null ? user.getPasswordHash() : dummyHash);

        // Same error for "unknown email" and "wrong password" to avoid user enumeration.
        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        return AuthResponse.bearer(jwtService.generateToken(user), jwtService.getExpirationSeconds(),
                UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
