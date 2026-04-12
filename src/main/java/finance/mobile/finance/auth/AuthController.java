package finance.mobile.finance.auth;

import finance.mobile.finance.auth.dto.AuthResponse;
import finance.mobile.finance.auth.dto.GoogleAuthRequest;
import finance.mobile.finance.auth.dto.SignInRequest;
import finance.mobile.finance.auth.dto.SignUpRequest;
import finance.mobile.finance.auth.dto.UserEnvelope;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody SignUpRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody SignInRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleAuthRequest request) {
        return authService.authenticateWithGoogle(request.idToken());
    }

    @GetMapping("/me")
    public UserEnvelope me() {
        return new UserEnvelope(authService.getCurrentUser());
    }
}
