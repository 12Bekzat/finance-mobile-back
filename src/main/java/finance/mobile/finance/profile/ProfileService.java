package finance.mobile.finance.profile;

import finance.mobile.finance.auth.AuthService;
import finance.mobile.finance.auth.dto.UserResponse;
import finance.mobile.finance.profile.dto.UpdateProfileRequest;
import finance.mobile.finance.user.UserAccount;
import finance.mobile.finance.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(
        AuthService authService,
        UserRepository userRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile() {
        return UserResponse.from(authService.getCurrentUserEntity());
    }

    @Transactional
    public UserResponse updateProfile(UpdateProfileRequest request) {
        UserAccount user = authService.getCurrentUserEntity();
        String normalizedEmail = normalizeEmail(request.email());
        boolean isGoogleOnly = isBlank(user.getPasswordHash()) && !isBlank(user.getGoogleSubject());

        if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists.");
        }

        if (isGoogleOnly && !normalizedEmail.equalsIgnoreCase(user.getEmail())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Google-only accounts cannot change email manually."
            );
        }

        user.setFullName(normalizeName(request.fullName()));
        user.setEmail(normalizedEmail);
        user.setAvatarUrl(normalizeOptional(request.avatarUrl()));

        String nextPassword = normalizeOptional(request.newPassword());
        if (nextPassword != null) {
            if (nextPassword.length() < 6) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Password must be at least 6 characters."
                );
            }

            if (!isBlank(user.getPasswordHash())) {
                String currentPassword = normalizeOptional(request.currentPassword());
                if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
                }
            }

            user.setPasswordHash(passwordEncoder.encode(nextPassword));
        }

        UserAccount savedUser = userRepository.save(user);
        return UserResponse.from(savedUser);
    }

    private String normalizeEmail(String email) {
        return String.valueOf(email).trim().toLowerCase();
    }

    private String normalizeName(String fullName) {
        return String.valueOf(fullName).trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
