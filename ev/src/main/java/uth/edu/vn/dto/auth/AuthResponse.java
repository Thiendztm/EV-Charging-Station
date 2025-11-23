package uth.edu.vn.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Authentication Response DTO
 * Trả về cho client sau khi đăng nhập/đăng ký thành công
 * Chứa JWT tokens + thông tin user cơ bản
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private Long expiresIn; // seconds

    // User info
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String name; // convenience full name for frontend display
    private String role;
    private String phoneNumber;

    /**
     * Constructor đơn giản chỉ với token
     * Dùng cho trường hợp chỉ cần trả về token
     */
    public AuthResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = "Bearer";
    }
}
