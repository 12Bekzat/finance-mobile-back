package finance.mobile.finance.profile.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank(message = "Name is required.")
    @Size(min = 2, message = "Name must be at least 2 characters.")
    String fullName,
    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    String email,
    @Size(max = 512, message = "Avatar URL is too long.")
    String avatarUrl,
    String currentPassword,
    String newPassword
) {}
