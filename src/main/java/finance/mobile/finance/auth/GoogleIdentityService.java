package finance.mobile.finance.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoogleIdentityService {

    private final List<String> allowedAudiences;

    public GoogleIdentityService(@Value("${google.oauth.allowed-audiences:}") String allowedAudiencesValue) {
        this.allowedAudiences = Arrays
            .stream(allowedAudiencesValue.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }

    public GoogleUser verify(String idTokenValue) {
        if (allowedAudiences.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Google sign-in is not configured on the server."
            );
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance()
            )
                .setAudience(allowedAudiences)
                .build();

            GoogleIdToken idToken = verifier.verify(idTokenValue);

            if (idToken == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google ID token.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            boolean emailVerified = Objects.equals(payload.getEmailVerified(), true);

            if (email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google account email is unavailable.");
            }

            if (!emailVerified) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google account email is not verified.");
            }

            return new GoogleUser(
                payload.getSubject(),
                email,
                emailVerified,
                Objects.toString(payload.get("name"), email),
                Objects.toString(payload.get("picture"), null)
            );
        } catch (GeneralSecurityException | IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Failed to validate Google credentials.",
                exception
            );
        }
    }

    public record GoogleUser(
        String subject,
        String email,
        boolean emailVerified,
        String fullName,
        String avatarUrl
    ) {}
}
