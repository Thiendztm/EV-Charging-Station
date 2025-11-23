package uth.edu.vn.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uth.edu.vn.dto.auth.AuthResponse;
import uth.edu.vn.dto.auth.LoginRequest;
import uth.edu.vn.dto.auth.RegisterRequest;
import uth.edu.vn.entity.User;
import uth.edu.vn.entity.Xe;
import uth.edu.vn.enums.UserRole;
import uth.edu.vn.exception.BadRequestException;
import uth.edu.vn.exception.UnauthorizedException;
import uth.edu.vn.repository.UserRepository;
import uth.edu.vn.repository.XeRepository;
import uth.edu.vn.security.JwtTokenProvider;

/**
 * Authentication Service
 * Xử lý logic đăng ký, đăng nhập, và tạo JWT tokens
 * REFACTORED: Sử dụng Spring Data JPA thay vì Hibernate Session
 */
@Service
@Transactional
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private XeRepository xeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private AuthenticationManager authenticationManager;

    /**
     * Đăng ký user mới (EV Driver)
     */
    public AuthResponse register(RegisterRequest request) {
        // 1. Check email đã tồn tại chưa
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email đã được sử dụng: " + request.getEmail());
        }

        // 2. Tạo User entity
        User user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()), // Hash password với BCrypt
                request.getFirstName(),
                request.getLastName(),
                UserRole.EV_DRIVER // Mặc định là EV_DRIVER
        );

        // Set thông tin bổ sung
        user.setPhone(request.getPhoneNumber());

        // 3. Lưu vào database
        user = userRepository.save(user);

        // 4. Lưu thông tin xe (nếu có)
        if (request.getVehiclePlate() != null && !request.getVehiclePlate().trim().isEmpty()) {
            // Kiểm tra biển số đã tồn tại chưa
            if (xeRepository.existsByPlateNumber(request.getVehiclePlate())) {
                throw new BadRequestException(
                        "Biển số xe '" + request.getVehiclePlate() + "' đã được đăng ký bởi người dùng khác.");
            }

            Xe vehicle = new Xe(
                    user.getId(),
                    request.getVehicleModel() != null ? request.getVehicleModel() : "Unknown",
                    "Unknown", // model
                    request.getVehiclePlate(),
                    request.getConnectorType() != null ? request.getConnectorType() : "Type 2");
            xeRepository.save(vehicle);
        }

        // 5. Generate JWT tokens
        String accessToken = tokenProvider.generateTokenFromUsername(user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getEmail());

        // 6. Trả về AuthResponse
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L) // 24 hours in seconds
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .name(String.format("%s %s",
                        user.getFirstName() != null ? user.getFirstName() : "",
                        user.getLastName() != null ? user.getLastName() : "").trim())
                .role(user.getRole().name())
                .phoneNumber(user.getPhone())
                .build();
    }

    /**
     * Đăng nhập
     */
    public AuthResponse login(LoginRequest request) {
        // 1. Authenticate với Spring Security
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 2. Tìm user từ database
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Email hoặc mật khẩu không đúng"));

        // 3. Generate JWT tokens
        String accessToken = tokenProvider.generateTokenFromUsername(user.getEmail());
        String refreshToken = tokenProvider.generateRefreshToken(user.getEmail());

        // 4. Trả về AuthResponse
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .name(String.format("%s %s",
                        user.getFirstName() != null ? user.getFirstName() : "",
                        user.getLastName() != null ? user.getLastName() : "").trim())
                .role(user.getRole().name())
                .phoneNumber(user.getPhone())
                .build();
    }

    /**
     * Refresh access token
     */
    public AuthResponse refreshToken(String refreshToken) {
        // 1. Validate refresh token
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("Refresh token không hợp lệ hoặc đã hết hạn");
        }

        // 2. Lấy email từ refresh token
        String email = tokenProvider.getUsernameFromToken(refreshToken);

        // 3. Tìm user
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User không tồn tại"));

        // 4. Generate new access token
        String newAccessToken = tokenProvider.generateTokenFromUsername(user.getEmail());

        // 5. Trả về AuthResponse
        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken) // Giữ nguyên refresh token
                .tokenType("Bearer")
                .expiresIn(86400L)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .name(String.format("%s %s",
                        user.getFirstName() != null ? user.getFirstName() : "",
                        user.getLastName() != null ? user.getLastName() : "").trim())
                .role(user.getRole().name())
                .phoneNumber(user.getPhone())
                .build();
    }

    /**
     * Tìm user theo email
     * Dùng cho getCurrentUser() trong AuthController
     */
    @Transactional(readOnly = true)
    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email).orElse(null);
    }
}