package finance.mobile.finance.auth.dto;

import finance.mobile.finance.user.UserAccount;
import java.time.Instant;

public record UserResponse(
    Long id,
    String fullName,
    String email,
    String avatarUrl,
    boolean googleLinked,
    boolean hasPassword,
    Instant createdAt,
    Instant updatedAt
) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(
            user.getId(),
            user.getFullName(),
            user.getEmail(),
            user.getAvatarUrl(),
            user.getGoogleSubject() != null && !user.getGoogleSubject().isBlank(),
            user.getPasswordHash() != null && !user.getPasswordHash().isBlank(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
