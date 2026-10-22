package com.psytrance.psytrance_tracker_backend.controller;

import com.psytrance.psytrance_tracker_backend.dto.ForgotPasswordRequest;
import com.psytrance.psytrance_tracker_backend.dto.LoginRequest;
import com.psytrance.psytrance_tracker_backend.dto.RegisterRequest;
import com.psytrance.psytrance_tracker_backend.dto.ResetPasswordRequest;
import com.psytrance.psytrance_tracker_backend.config.Admins;
import com.psytrance.psytrance_tracker_backend.dto.UserResponse;
import com.psytrance.psytrance_tracker_backend.model.User;
import com.psytrance.psytrance_tracker_backend.service.EmailVerification;
import com.psytrance.psytrance_tracker_backend.service.LoginAttempts;
import com.psytrance.psytrance_tracker_backend.service.PasswordResetService;
import com.psytrance.psytrance_tracker_backend.service.UserService;
import com.psytrance.psytrance_tracker_backend.util.JwtUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordResetService passwordResetService;
    private final Admins admins;
    private final LoginAttempts loginAttempts;
    private final EmailVerification emailVerification;

    public AuthController(UserService userService, JwtUtil jwtUtil, PasswordResetService passwordResetService, Admins admins,
                          LoginAttempts loginAttempts, EmailVerification emailVerification) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.passwordResetService = passwordResetService;
        this.admins = admins;
        this.loginAttempts = loginAttempts;
        this.emailVerification = emailVerification;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.register(request);
        // The account works right away; the link only unlocks adding events
        emailVerification.sendLink(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        // Too many wrong passwords for this username: 429 before the password is even checked
        loginAttempts.checkAllowed(request.username());
        return userService.login(request.username(), request.password())
                .map(user -> {
                    loginAttempts.succeeded(request.username());
                    return ResponseEntity.ok(Map.of("token", jwtUtil.generateToken(user.getUsername())));
                })
                .orElseGet(() -> {
                    loginAttempts.failed(request.username());
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid username or password"));
                });
    }

    /** SecurityConfig only lets requests with a valid token reach this endpoint. */
    @GetMapping("/me")
    public UserResponse getCurrentUser(Authentication authentication) {
        return userService.findByUsername(authentication.getName())
                .map(user -> UserResponse.from(user, admins.roleOf(user.getUsername()).orElse(null)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        // Same answer whether or not the email exists, so this can't be used to find out who has an account
        return ResponseEntity.ok(Map.of("message", "If an account exists for that email, a reset link has been sent."));
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    /** The link from the verification email (the frontend's /verify-email page sends its token here) */
    @PostMapping("/verify-email")
    public Map<String, String> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return Map.of("username", emailVerification.verify(request.token()),
                "message", "Your email is confirmed. You can add events now.");
    }

    /** "Send the link again" on the profile. Needs login (SecurityConfig). */
    @PostMapping("/resend-verification")
    public Map<String, String> resendVerification(Authentication authentication) {
        emailVerification.resend(authentication.getName());
        return Map.of("message", "We sent you a new link. Check your inbox (and spam).");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(Map.of("message", "Your password has been changed. You can log in now."));
    }
}
