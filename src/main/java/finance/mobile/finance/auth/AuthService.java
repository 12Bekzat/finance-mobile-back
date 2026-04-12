package finance.mobile.finance.auth;

import finance.mobile.finance.auth.dto.AuthResponse;
import finance.mobile.finance.auth.dto.SignInRequest;
import finance.mobile.finance.auth.dto.SignUpRequest;
import finance.mobile.finance.auth.dto.UserResponse;
import finance.mobile.finance.user.UserAccount;
import finance.mobile.finance.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GoogleIdentityService googleIdentityService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        GoogleIdentityService googleIdentityService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleIdentityService = googleIdentityService;
    }

    @Transactional
    public AuthResponse register(SignUpRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        UserAccount existingUser = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (existingUser != null && existingUser.getPasswordHash() != null && !existingUser.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists.");
        }

        UserAccount user = existingUser == null ? new UserAccount() : existingUser;
        user.setFullName(normalizeName(request.fullName()));
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(request.password().trim()));

        UserAccount savedUser = userRepository.save(user);
        return buildAuthResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(SignInRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        UserAccount user = userRepository
            .findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password."));

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This account uses Google sign-in.");
        }

        if (!passwordEncoder.matches(request.password().trim(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse authenticateWithGoogle(String idToken) {
        GoogleIdentityService.GoogleUser googleUser = googleIdentityService.verify(idToken);
        UserAccount user = userRepository.findByGoogleSubject(googleUser.subject()).orElse(null);

        if (user == null) {
            user = userRepository.findByEmailIgnoreCase(normalizeEmail(googleUser.email())).orElseGet(UserAccount::new);
        }

        user.setGoogleSubject(googleUser.subject());
        user.setEmail(normalizeEmail(googleUser.email()));
        user.setFullName(normalizeName(googleUser.fullName()));

        if (googleUser.avatarUrl() != null && !googleUser.avatarUrl().isBlank()) {
            user.setAvatarUrl(googleUser.avatarUrl().trim());
        }

        UserAccount savedUser = userRepository.save(user);
        return buildAuthResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser() {
        return UserResponse.from(getCurrentUserEntity());
    }

    @Transactional(readOnly = true)
    public UserAccount getCurrentUserEntity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof UserAccount principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized.");
        }

        return userRepository
            .findById(principal.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized."));
    }

    private AuthResponse buildAuthResponse(UserAccount user) {
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return String.valueOf(email).trim().toLowerCase();
    }

    private String normalizeName(String fullName) {
        return String.valueOf(fullName).trim();
    }
}
