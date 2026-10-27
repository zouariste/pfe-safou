/*
 * Copyright (C) 2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.se2.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.uni_passau.fim.se2.api.dto.AuthResponse;
import de.uni_passau.fim.se2.api.dto.FimLoginRequest;
import de.uni_passau.fim.se2.api.dto.LoginRequest;
import de.uni_passau.fim.se2.api.dto.PasswordResetConfirmRequest;
import de.uni_passau.fim.se2.api.dto.PasswordResetRequest;
import de.uni_passau.fim.se2.api.dto.RegisterRequest;
import de.uni_passau.fim.se2.api.dto.StudentOnboardingRequest;
import de.uni_passau.fim.se2.api.dto.UpdateEmailRequest;
import de.uni_passau.fim.se2.api.dto.UpdatePasswordRequest;
import de.uni_passau.fim.se2.api.dto.UpdateProfileRequest;
import de.uni_passau.fim.se2.application.service.AuthService;
import de.uni_passau.fim.se2.domain.entities.UserDTO;
import de.uni_passau.fim.se2.domain.exceptions.AuthenticationException;
import de.uni_passau.fim.se2.domain.exceptions.DirectoryUnavailableException;
import de.uni_passau.fim.se2.domain.interfaces.ClassRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String NOT_LOGGED_IN = "Nicht eingeloggt.";
    private static final String MESSAGE_KEY = "message";

    private final AuthService authService;
    private final ClassRepository classRepository;

    /**
     * Handles teacher registration requests.
     *
     * @param request the new teacher's name, email address and password
     * @return a confirmation asking the teacher to verify their email address
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        authService.registerTeacher(
            request.getEmail(),
            request.getPassword(),
            request.getFirstName(),
            request.getLastName()
        );
        return ResponseEntity.ok(
            Map.of(
                MESSAGE_KEY, "Registrierung erfolgreich. Bitte bestätige deine Email-Adresse."
            )
        );
    }

    /**
     * Handles login requests for authentication.
     *
     * @param request the email address and password
     * @return the JWT and the user's profile
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.getEmail(), request.getPassword());
        return ResponseEntity.ok(loginResponse(token));
    }

    /**
     * Handles login requests with a FIM-Account (checked against the FIM LDAP server).
     *
     * @param request the FIM username and password
     * @return the JWT and the user's profile
     */
    @PostMapping("/login/fim")
    public ResponseEntity<AuthResponse> loginWithFimAccount(@Valid @RequestBody FimLoginRequest request) {
        String token = authService.loginWithFimAccount(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(loginResponse(token));
    }

    private AuthResponse loginResponse(String token) {
        UserDTO user = authService.getUserFromToken(token);
        List<String> classIds = null;
        if (user.getRole() == UserDTO.UserRole.STUDENT) {
            classIds = classRepository.findByStudentId(user.getUserId()).stream()
                .map(c -> c.getId().toString())
                .toList();
        }
        return AuthResponse.builder()
            .token(token)
            .role(user.getRole().name())
            .email(user.getEmail())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .userId(user.getUserId().toString())
            .classIds(classIds)
            .mustChangePassword(user.isMustChangePassword() ? true : null)
            .fimAccount(user.getFimUid() != null ? true : null)
            .build();
    }

    /**
     * Verifies the email address using a provided token.
     *
     * @param body the request body carrying the verification {@code token}
     * @return a confirmation that the address is verified
     */
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> body) {
        authService.verifyEmail(body.get("token"));
        return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Email erfolgreich bestätigt."));
    }

    /**
     * Initiates the password reset process for the user.
     *
     * @param request the email address to send the reset link to
     * @return the same confirmation whether or not the address has an account
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(
            Map.of(
                MESSAGE_KEY, "Falls ein Account mit dieser Email existiert, wurde eine Reset-Mail gesendet."
            )
        );
    }

    /**
     * Resets the password using the provided token and new password.
     *
     * @param request the reset token and the new password
     * @return a confirmation that the password was changed
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Passwort erfolgreich zurückgesetzt."));
    }

    /**
     * Validates a token for student registration or actions.
     *
     * @param body the request body carrying the onboarding {@code token}
     * @return the student's first name, last name and user ID
     */
    @PostMapping("/validate-token")
    public ResponseEntity<?> validateStudentToken(@RequestBody Map<String, String> body) {
        UserDTO student = authService.validateStudentToken(body.get("token"));
        return ResponseEntity.ok(
            Map.of(
                "firstName", student.getFirstName(),
                "lastName", student.getLastName(),
                "userId", student.getUserId().toString()
            )
        );
    }

    /**
     * Completes student onboarding by setting up their account.
     *
     * @param request the onboarding token and the email address and password the student chose
     * @return a confirmation asking the student to verify their email address
     */
    @PostMapping("/student-onboarding")
    public ResponseEntity<?> completeStudentOnboarding(@Valid @RequestBody StudentOnboardingRequest request) {
        authService.completeStudentOnboarding(
            request.getToken(),
            request.getEmail(),
            request.getPassword()
        );
        return ResponseEntity.ok(
            Map.of(
                MESSAGE_KEY, "Account eingerichtet. Bitte bestätige deine Email-Adresse."
            )
        );
    }

    /**
     * Retrieves the current user's profile information.
     *
     * @param userId the authenticated user
     * @return the user's profile, without a token
     */
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getCurrentUser(@AuthenticationPrincipal UUID userId) {
        if (userId == null) {
            throw new AuthenticationException(NOT_LOGGED_IN);
        }
        UserDTO user = authService.getUserById(userId);
        return ResponseEntity.ok(
            AuthResponse.builder()
                .token(null)
                .role(user.getRole().name())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .userId(user.getUserId().toString())
                .fimAccount(user.getFimUid() != null ? true : null)
                .build()
        );
    }

    /**
     * Updates the user's profile information.
     *
     * @param userId  the authenticated user
     * @param request the new first and last name
     * @return a confirmation
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
        @AuthenticationPrincipal UUID userId,
        @Valid @RequestBody UpdateProfileRequest request
    ) {
        if (userId == null) {
            throw new AuthenticationException(NOT_LOGGED_IN);
        }
        UserDTO updated = authService.updateProfile(userId, request.getFirstName(), request.getLastName());
        return ResponseEntity.ok(
            Map.of(
                MESSAGE_KEY, "Profil aktualisiert.",
                "firstName", updated.getFirstName(),
                "lastName", updated.getLastName()
            )
        );
    }

    /**
     * Updates the user's email address.
     *
     * @param userId  the authenticated user
     * @param request the new email address
     * @return a confirmation asking to verify the new address
     */
    @PutMapping("/profile/email")
    public ResponseEntity<?> updateEmail(
        @AuthenticationPrincipal UUID userId,
        @Valid @RequestBody UpdateEmailRequest request
    ) {
        if (userId == null) {
            throw new AuthenticationException(NOT_LOGGED_IN);
        }
        authService.updateEmail(userId, request.getNewEmail());
        return ResponseEntity.ok(
            Map.of(
                MESSAGE_KEY, "Email geändert. Bitte bestätige die neue Email-Adresse."
            )
        );
    }

    /**
     * Updates the user's password.
     *
     * @param userId  the authenticated user
     * @param request the current and the new password
     * @return a confirmation
     */
    @PutMapping("/profile/password")
    public ResponseEntity<?> updatePassword(
        @AuthenticationPrincipal UUID userId,
        @Valid @RequestBody UpdatePasswordRequest request
    ) {
        if (userId == null) {
            throw new AuthenticationException(NOT_LOGGED_IN);
        }
        authService.updatePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Passwort erfolgreich geändert."));
    }

    /**
     * Changes a temporary password set by a teacher. Does not require the old password. Only works when
     * mustChangePassword = true.
     *
     * @param userId the authenticated user
     * @param body   the request body carrying the {@code newPassword}
     * @return a confirmation, or 400 if the new password is blank
     */
    @PostMapping("/change-temp-password")
    public ResponseEntity<?> changeTempPassword(
        @AuthenticationPrincipal UUID userId,
        @RequestBody Map<String, String> body
    ) {
        if (userId == null) {
            throw new AuthenticationException(NOT_LOGGED_IN);
        }
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Neues Passwort darf nicht leer sein."));
        }
        authService.changeTempPassword(userId, newPassword);
        return ResponseEntity.ok(Map.of(MESSAGE_KEY, "Passwort erfolgreich geändert."));
    }

    /**
     * Handles authentication exceptions and returns an appropriate response.
     *
     * @param e the exception raised by the authentication code
     * @return 401 with the exception's message
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleAuthException(AuthenticationException e) {
        return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
    }

    /**
     * Handles an unreachable FIM LDAP server during FIM-Account login.
     *
     * @param e the exception raised when the LDAP server could not be reached
     * @return 503 with a message saying the FIM login is unavailable
     */
    @ExceptionHandler(DirectoryUnavailableException.class)
    public ResponseEntity<?> handleDirectoryUnavailable(DirectoryUnavailableException e) {
        return ResponseEntity.status(503).body(
            Map.of(
                "error", "Die FIM-Anmeldung ist gerade nicht erreichbar. Bitte versuche es später erneut."
            )
        );
    }
}
