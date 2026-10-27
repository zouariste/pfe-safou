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
package de.uni_passau.fim.se2.application.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import de.uni_passau.fim.se2.domain.entities.AuthTokenDTO;
import de.uni_passau.fim.se2.domain.entities.UserDTO;
import de.uni_passau.fim.se2.domain.exceptions.AuthenticationException;
import de.uni_passau.fim.se2.domain.interfaces.FimAccountDirectory;
import de.uni_passau.fim.se2.domain.interfaces.UserRepository;
import de.uni_passau.fim.se2.domain.model.FimAccount;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AuthService {

    private static final String USER_NOT_FOUND = "Benutzer nicht gefunden.";

    /** Shown when a password is changed or reset for an account whose password lives in the FIM directory. */
    private static final String FIM_PASSWORD_MESSAGE = "Das Passwort eines FIM-Accounts kann nicht in CodeOwl geändert werden.";

    private final UserRepository userRepository;
    private final PasswordHasherService passwordHasher;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final FimAccountDirectory fimAccountDirectory;
    private final String rootAdminEmail;

    public AuthService(
        UserRepository userRepository,
        PasswordHasherService passwordHasher,
        JwtService jwtService,
        TokenService tokenService,
        EmailService emailService,
        FimAccountDirectory fimAccountDirectory,
        @Value("${auth.root-admin-email:}") String rootAdminEmail
    ) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.fimAccountDirectory = fimAccountDirectory;
        this.rootAdminEmail = rootAdminEmail;
    }

    // ── Teacher Registration ──────────────────────────────────

    /**
     * Registers a new teacher. Sends email verification. Root admin email automatically gets ADMIN role.
     *
     * @param email     the teacher's email address
     * @param password  the chosen password
     * @param firstName the teacher's first name
     * @param lastName  the teacher's last name
     */
    @Transactional
    public void registerTeacher(String email, String password, String firstName, String lastName) {
        if (userRepository.existsByEmail(email)) {
            throw new AuthenticationException("Ein Account mit dieser Email existiert bereits.");
        }

        boolean isRootAdmin = rootAdminEmail != null && !rootAdminEmail.isBlank()
            && rootAdminEmail.equalsIgnoreCase(email.trim());

        UserDTO user = UserDTO.builder()
            .firstName(firstName)
            .lastName(lastName)
            .email(email)
            .passwordHash(passwordHasher.hashPassword(password))
            .role(isRootAdmin ? UserDTO.UserRole.ADMIN : UserDTO.UserRole.TEACHER)
            .verified(isRootAdmin) // Root admin is auto-verified
            .active(true)
            .build();

        userRepository.save(user);

        AuthTokenDTO verifyToken = tokenService.createEmailVerificationToken(user.getUserId());
        emailService.sendVerificationEmail(email, verifyToken.getTokenValue());

        log.info("Teacher registered: {} ({})", email, user.getUserId());
    }

    // ── Login ────────────────────────────────────────────────

    /**
     * Login with email + password. Returns JWT.
     *
     * @param email    the user's email address
     * @param password the user's password
     * @return a signed JWT for the user
     */
    public String login(String email, String password) {
        UserDTO user = userRepository.findByEmail(email)
            .orElseThrow(() -> new AuthenticationException("Ungültige Anmeldedaten."));

        if (!user.isActive()) {
            throw new AuthenticationException("Account ist deaktiviert.");
        }
        if (user.getPasswordHash() == null || !passwordHasher.verifyPassword(password, user.getPasswordHash())) {
            throw new AuthenticationException("Ungültige Anmeldedaten.");
        }
        if (!user.isVerified()) {
            throw new AuthenticationException("Bitte bestätige zuerst deine Email-Adresse.");
        }

        restoreRootAdminRole(user);

        log.info("Login successful: {} (role={})", email, user.getRole());
        return jwtService.generateToken(user);
    }

    /**
     * Login with a FIM-Account. The FIM LDAP server checks the credentials; on the first login the FIM-Account is
     * linked to a CodeOwl account (see {@link #linkOrCreateFimUser}). Returns JWT.
     *
     * @param username the FIM username
     * @param password the FIM password
     * @return a signed JWT for the linked CodeOwl account
     */
    @Transactional
    public String loginWithFimAccount(String username, String password) {
        FimAccount account = fimAccountDirectory.authenticate(username, password)
            .orElseThrow(() -> new AuthenticationException("Ungültige Anmeldedaten."));

        UserDTO user = userRepository.findByFimUid(account.uid())
            .map(existing -> completeFimProfile(existing, account))
            .orElseGet(() -> linkOrCreateFimUser(account));

        if (!user.isActive()) {
            throw new AuthenticationException("Account ist deaktiviert.");
        }

        restoreRootAdminRole(user);

        log.info("FIM login successful: {} (role={})", account.uid(), user.getRole());
        return jwtService.generateToken(user);
    }

    /**
     * Fills in what a teacher could not know when adding a FIM student before their first login (see
     * {@code ClassService.addFimStudents}): the name, still the username then, and the email, still empty.
     *
     * Only placeholders are replaced. A name someone has since edited stays as it is, and an email is not taken if
     * another account already uses it.
     */
    private UserDTO completeFimProfile(UserDTO user, FimAccount account) {
        boolean changed = false;

        boolean namePending = account.uid().equals(user.getFirstName())
            && (user.getLastName() == null || user.getLastName().isEmpty());
        if (namePending && account.firstName() != null) {
            user.setFirstName(account.firstName());
            user.setLastName(account.lastName() != null ? account.lastName() : "");
            changed = true;
        }

        if (
            user.getEmail() == null && account.email() != null && !account.email().isBlank()
                && !userRepository.existsByEmail(account.email())
        ) {
            user.setEmail(account.email());
            changed = true;
        }

        if (changed) {
            userRepository.update(user);
            log.info("Profile of FIM-Account {} completed from the directory", account.uid());
        }
        return user;
    }

    /**
     * Links a FIM-Account seen for the first time to the existing account with the same email, or creates a verified
     * student account for it. An admin can promote it to teacher or admin afterwards; a linked existing account keeps
     * its role.
     *
     * An existing account is only linked if its email is verified: an unverified account may have been registered by
     * someone who merely typed this address, and linking it would give them — through its password — access to
     * everything the FIM user does in it.
     */
    private UserDTO linkOrCreateFimUser(FimAccount account) {
        if (account.email() != null && !account.email().isBlank()) {
            Optional<UserDTO> existing = userRepository.findByEmail(account.email());
            if (existing.isPresent()) {
                UserDTO user = existing.get();
                if (!user.isVerified() || user.getFimUid() != null) {
                    throw new AuthenticationException(
                        "Für die Email-Adresse dieses FIM-Accounts existiert bereits ein anderer CodeOwl-Account."
                    );
                }
                user.setFimUid(account.uid());
                userRepository.update(user);
                log.info("FIM-Account {} linked to existing user {}", account.uid(), user.getUserId());
                return user;
            }
        }

        UserDTO user = UserDTO.builder()
            .firstName(account.firstName() != null ? account.firstName() : account.uid())
            .lastName(account.lastName() != null ? account.lastName() : "")
            .email(account.email())
            .fimUid(account.uid())
            .role(UserDTO.UserRole.STUDENT)
            .verified(true) // the FIM directory vouches for the account
            .active(true)
            .build();
        userRepository.save(user);

        log.info("User created for FIM-Account {} ({})", account.uid(), user.getUserId());
        return user;
    }

    /**
     * Ensures the root admin always has the ADMIN role.
     */
    private void restoreRootAdminRole(UserDTO user) {
        boolean isRootAdmin = StringUtils.hasText(rootAdminEmail) && user.getEmail() != null
            && rootAdminEmail.equalsIgnoreCase(user.getEmail().trim());
        if (isRootAdmin && user.getRole() != UserDTO.UserRole.ADMIN) {
            user.setRole(UserDTO.UserRole.ADMIN);
            userRepository.update(user);
            log.info("Root admin role restored for: {}", user.getEmail());
        }
    }

    // ── Email Verification ───────────────────────────────────

    /**
     * Verifies email using token.
     *
     * @param tokenValue the verification token from the email
     */
    public void verifyEmail(String tokenValue) {
        AuthTokenDTO token = tokenService.validateAndConsume(tokenValue, AuthTokenDTO.TokenType.EMAIL_VERIFICATION);

        UserDTO user = userRepository.findById(token.getUserId())
            .orElseThrow(() -> new AuthenticationException(USER_NOT_FOUND));

        user.setVerified(true);
        userRepository.update(user);

        log.info("Email verified for user: {}", user.getUserId());
    }

    // ── Password Reset ───────────────────────────────────────

    /**
     * Initiates password reset. Sends email with reset token.
     *
     * @param email the address to send the reset link to
     */
    public void requestPasswordReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> sendPasswordReset(user, email));
        // Always succeed to not reveal whether email exists
    }

    private void sendPasswordReset(UserDTO user, String email) {
        // A FIM-Account's password is reset at the FIM, not here. Skipped silently:
        // the response must not reveal which addresses belong to an account.
        if (user.getFimUid() != null) {
            log.info("Password reset ignored for FIM-Account user: {}", user.getUserId());
            return;
        }
        AuthTokenDTO resetToken = tokenService.createPasswordResetToken(user.getUserId());
        emailService.sendPasswordResetEmail(email, resetToken.getTokenValue());
        log.info("Password reset requested for: {}", email);
    }

    /**
     * Resets password using token.
     *
     * @param tokenValue  the reset token from the email
     * @param newPassword the new password
     */
    public void resetPassword(String tokenValue, String newPassword) {
        AuthTokenDTO token = tokenService.validateAndConsume(tokenValue, AuthTokenDTO.TokenType.PASSWORD_RESET);

        UserDTO user = userRepository.findById(token.getUserId())
            .orElseThrow(() -> new AuthenticationException(USER_NOT_FOUND));

        // The token may predate the account being linked to a FIM-Account.
        if (user.getFimUid() != null) {
            throw new AuthenticationException(FIM_PASSWORD_MESSAGE);
        }

        user.setPasswordHash(passwordHasher.hashPassword(newPassword));
        userRepository.update(user);

        log.info("Password reset completed for user: {}", user.getUserId());
    }

    // ── Student Invite Token Flow ────────────────────────────

    /**
     * Validates a student invite token (without consuming). Returns the student user info so the frontend can show the
     * onboarding form.
     *
     * @param tokenValue the invite token from the onboarding link
     * @return the student the token belongs to
     */
    public UserDTO validateStudentToken(String tokenValue) {
        AuthTokenDTO token = tokenService.validateWithoutConsuming(tokenValue, AuthTokenDTO.TokenType.STUDENT_INVITE);
        return userRepository.findById(token.getUserId())
            .orElseThrow(() -> new AuthenticationException(USER_NOT_FOUND));
    }

    /**
     * Completes student onboarding: sets email + password, sends verification email, invalidates invite token.
     *
     * @param tokenValue the invite token from the onboarding link
     * @param email      the email address the student chose
     * @param password   the password the student chose
     */
    @Transactional
    public void completeStudentOnboarding(String tokenValue, String email, String password) {
        AuthTokenDTO token = tokenService.validateAndConsume(tokenValue, AuthTokenDTO.TokenType.STUDENT_INVITE);

        if (userRepository.existsByEmail(email)) {
            throw new AuthenticationException("Diese Email-Adresse wird bereits verwendet.");
        }

        UserDTO student = userRepository.findById(token.getUserId())
            .orElseThrow(() -> new AuthenticationException(USER_NOT_FOUND));

        student.setEmail(email);
        student.setPasswordHash(passwordHasher.hashPassword(password));
        student.setVerified(false);
        userRepository.update(student);

        AuthTokenDTO verifyToken = tokenService.createEmailVerificationToken(student.getUserId());
        emailService.sendVerificationEmail(email, verifyToken.getTokenValue());

        log.info("Student onboarding started: {} ({})", email, student.getUserId());
    }

    // ── Profile Editing ──────────────────────────────────────

    /**
     * Updates the user's first and last name.
     *
     * @param userId    the user to update
     * @param firstName the new first name
     * @param lastName  the new last name
     * @return the updated user
     */
    public UserDTO updateProfile(UUID userId, String firstName, String lastName) {
        UserDTO user = userRepository.findById(userId)
            .orElseThrow(() -> new AuthenticationException(USER_NOT_FOUND));
        user.setFirstName(firstName);
        user.setLastName(lastName);
        userRepository.update(user);
        log.info("Profile updated for user: {}", userId);
        return user;
    }

    /**
     * Updates the user's email. Marks as unverified and sends verification email.
     *
     * @param userId   the user to update
     * @param newEmail the new email address
     */
    @Transactional
    public void updateEmail(UUID userId, String newEmail) {
        UserDTO user = userRepository.findById(userId)
            .orElseThrow(() -> new AuthenticationException(USER_NOT_FOUND));

        // Check if new email is already taken by another user
        userRepository.findByEmail(newEmail).ifPresent(existing -> {
            if (!existing.getUserId().equals(userId)) {
                throw new AuthenticationException("Diese Email-Adresse wird bereits verwendet.");
            }
        });

        user.setEmail(newEmail);
        user.setVerified(false);
        userRepository.update(user);

        AuthTokenDTO verifyToken = tokenService.createEmailVerificationToken(userId);
        emailService.sendVerificationEmail(newEmail, verifyToken.getTokenValue());

        log.info("Email updated for user: {} -> {}", userId, newEmail);
    }

    /**
     * Changes the user's password. Requires current password for verification.
     *
     * @param userId          the user to update
     * @param currentPassword the current password, to confirm the change
     * @param newPassword     the new password
     */
    public void updatePassword(UUID userId, String currentPassword, String newPassword) {
        UserDTO user = userRepository.findById(userId)
            .orElseThrow(() -> new AuthenticationException(USER_NOT_FOUND));

        // A FIM-Account's password lives in the FIM directory; changing the local hash
        // would not change the one that logs the user in.
        if (user.getFimUid() != null) {
            throw new AuthenticationException(FIM_PASSWORD_MESSAGE);
        }

        if (user.getPasswordHash() == null || !passwordHasher.verifyPassword(currentPassword, user.getPasswordHash())) {
            throw new AuthenticationException("Aktuelles Passwort ist falsch.");
        }

        user.setPasswordHash(passwordHasher.hashPassword(newPassword));
        user.setMustChangePassword(false);
        userRepository.update(user);

        log.info("Password changed for user: {}", userId);
    }

    /**
     * Changes a temporary password without requiring the old password. Only valid when the user has mustChangePassword
     * = true.
     *
     * @param userId      the user to update
     * @param newPassword the new password
     */
    public void changeTempPassword(UUID userId, String newPassword) {
        UserDTO user = userRepository.findById(userId)
            .orElseThrow(() -> new AuthenticationException(USER_NOT_FOUND));

        if (user.getFimUid() != null) {
            throw new AuthenticationException(FIM_PASSWORD_MESSAGE);
        }

        if (!user.isMustChangePassword()) {
            throw new AuthenticationException("Kein temporäres Passwort gesetzt.");
        }

        user.setPasswordHash(passwordHasher.hashPassword(newPassword));
        user.setMustChangePassword(false);
        userRepository.update(user);

        log.info("Temporary password changed for user: {}", userId);
    }

    // ── Helper ───────────────────────────────────────────────

    /**
     * Get current user from JWT token.
     *
     * @param token the JWT to read the user ID from
     * @return the user the token was issued for
     */
    public UserDTO getUserFromToken(String token) {
        UUID userId = jwtService.extractUserId(token);
        return userRepository.findById(userId)
            .orElseThrow(() -> new AuthenticationException(USER_NOT_FOUND));
    }

    /**
     * Get user by ID.
     *
     * @param userId the user to load
     * @return the user
     */
    public UserDTO getUserById(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new AuthenticationException(USER_NOT_FOUND));
    }
}
