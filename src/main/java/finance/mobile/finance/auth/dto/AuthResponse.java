package finance.mobile.finance.auth.dto;

public record AuthResponse(String token, String tokenType, UserResponse user) {}
