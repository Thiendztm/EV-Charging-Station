package uth.edu.vn.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import uth.edu.vn.entity.*;
import uth.edu.vn.enums.*;
import uth.edu.vn.service.AdminService;
import uth.edu.vn.repository.*;

import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

/**
 * Admin Controller
 * REST API endpoints cho quản trị viên
 * Yêu cầu role ADMIN để truy cập
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private TramSacRepository tramSacRepository;

    @Autowired
    private ChargerRepository chargerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhienSacRepository phienSacRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ==================== STATION MANAGEMENT ====================

    /**
     * Lấy tất cả trạm sạc
     * GET /api/admin/stations
     * Đã sửa: N+1 Query issue (Lấy số lượng chargers)
     */
    @GetMapping("/stations")
    public ResponseEntity<Map<String, Object>> getAllStations(
            @RequestParam(required = false) String status) {
        try {
            List<TramSac> stations;

            if (status != null && !status.isEmpty()) {
                StationStatus stationStatus = StationStatus.valueOf(status.toUpperCase());
                stations = tramSacRepository.findByStatus(stationStatus.name());
            } else {
                stations = adminService.getAllStations();
            }

            // Lấy tất cả ID trạm sạc để truy vấn tối ưu hơn
            List<Long> stationIds = stations.stream()
                    .map(TramSac::getId)
                    .collect(Collectors.toList());

            // Tối ưu hóa: Lấy tất cả chargers cho các trạm này trong 1-2 truy vấn
            List<Charger> allChargers = chargerRepository.findByChargingStationIdIn(stationIds);

            // SỬA LỖI TẠI ĐÂY: Thay Charger::getChargingStationId bằng Lambda Expression
            // Điều này giải quyết vấn đề Method Reference không áp dụng được
            Map<Long, List<Charger>> chargersByStationId = allChargers.stream()
                    .collect(Collectors.groupingBy(charger -> charger.getChargingStation().getId()));

            List<Map<String, Object>> stationList = new ArrayList<>();
            for (TramSac station : stations) {
                Map<String, Object> stationData = new HashMap<>();
                stationData.put("id", station.getId());
                stationData.put("name", station.getName());
                stationData.put("address", station.getAddress());
                stationData.put("latitude", station.getLatitude());
                stationData.put("longitude", station.getLongitude());
                stationData.put("status", station.getStatus());

                // Đếm số điểm sạc từ Map đã được tối ưu
                List<Charger> stationChargers = chargersByStationId.getOrDefault(station.getId(),
                        Collections.emptyList());
                long availableCount = stationChargers.stream()
                        .filter(c -> c.getStatus() == PointStatus.AVAILABLE)
                        .count();

                stationData.put("totalChargers", stationChargers.size());
                stationData.put("availableChargers", availableCount);

                stationList.add(stationData);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("stations", stationList);
            response.put("total", stationList.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy danh sách trạm sạc: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Tạo trạm sạc mới
     * POST /api/admin/stations
     */
    @PostMapping("/stations")
    public ResponseEntity<Map<String, Object>> createStation(
            @RequestBody Map<String, Object> stationData) {
        try {
            String name = (String) stationData.get("name");
            String address = (String) stationData.get("address");
            // Xử lý ngoại lệ cho việc parse Double
            Double latitude = stationData.get("latitude") != null
                    ? Double.parseDouble(stationData.get("latitude").toString())
                    : null;
            Double longitude = stationData.get("longitude") != null
                    ? Double.parseDouble(stationData.get("longitude").toString())
                    : null;
            String operatingHours = (String) stationData.getOrDefault("operatingHours", "24/7");

            if (latitude == null || longitude == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "Thiếu tọa độ latitude hoặc longitude"));
            }

            TramSac newStation = adminService.createChargingStation(
                    name, address, latitude, longitude, operatingHours);

            Map<String, Object> response = new HashMap<>();
            if (newStation != null) {
                response.put("success", true);
                response.put("message", "Tạo trạm sạc thành công");
                response.put("stationId", newStation.getId());
                response.put("station", Map.of(
                        "id", newStation.getId(),
                        "name", newStation.getName(),
                        "address", newStation.getAddress(),
                        "status", newStation.getStatus()));
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Không thể tạo trạm sạc");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi tạo trạm sạc: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Cập nhật trạng thái trạm sạc
     * PUT /api/admin/stations/{stationId}/status
     */
    @PutMapping("/stations/{stationId}/status")
    public ResponseEntity<Map<String, Object>> updateStationStatus(
            @PathVariable Long stationId,
            @RequestBody Map<String, String> statusData) {
        try {
            String statusStr = statusData.get("status");
            StationStatus newStatus = StationStatus.valueOf(statusStr.toUpperCase());

            boolean success = adminService.updateStationStatus(stationId, newStatus);

            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("success", true);
                response.put("message", "Cập nhật trạng thái thành công");
                response.put("newStatus", newStatus);
            } else {
                response.put("success", false);
                response.put("error", "Không thể cập nhật trạng thái. Có thể trạm sạc không tồn tại.");
            }

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Trạng thái không hợp lệ: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi cập nhật trạng thái: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Thêm điểm sạc vào trạm
     * POST /api/admin/stations/{stationId}/chargers
     */
    @PostMapping("/stations/{stationId}/chargers")
    public ResponseEntity<Map<String, Object>> addChargingPoint(
            @PathVariable Long stationId,
            @RequestBody Map<String, Object> chargerData) {
        try {
            String pointName = (String) chargerData.get("pointName");
            ConnectorType connectorType = ConnectorType.valueOf(
                    ((String) chargerData.get("connectorType")).toUpperCase());
            // Xử lý ngoại lệ cho việc parse Double
            Double powerCapacity = chargerData.get("powerCapacity") != null
                    ? Double.parseDouble(chargerData.get("powerCapacity").toString())
                    : null;
            Double pricePerKwh = chargerData.get("pricePerKwh") != null
                    ? Double.parseDouble(chargerData.get("pricePerKwh").toString())
                    : null;

            if (powerCapacity == null || pricePerKwh == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "error", "Thiếu công suất hoặc giá/kWh"));
            }

            Charger newCharger = adminService.addChargingPoint(
                    stationId, pointName, connectorType, powerCapacity, pricePerKwh);

            Map<String, Object> response = new HashMap<>();
            if (newCharger != null) {
                response.put("success", true);
                response.put("message", "Thêm điểm sạc thành công");
                response.put("chargerId", newCharger.getPointId());
                response.put("charger", Map.of(
                        "id", newCharger.getPointId(),
                        "name", newCharger.getPointName(),
                        "connectorType", newCharger.getConnectorType(),
                        "powerCapacity", newCharger.getPowerCapacity(),
                        "status", newCharger.getStatus()));
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Không thể thêm điểm sạc. Có thể trạm sạc không tồn tại.");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Loại kết nối (connectorType) không hợp lệ: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi thêm điểm sạc: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    // ==================== USER MANAGEMENT ====================

    /**
     * Lấy tất cả người dùng
     * GET /api/admin/users
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(required = false) String role) {
        try {
            List<User> users;

            if (role != null && !role.isEmpty()) {
                UserRole userRole = UserRole.valueOf(role.toUpperCase());
                users = adminService.getUsersByRole(userRole);
            } else {
                users = adminService.getAllUsers();
            }

            List<Map<String, Object>> userList = new ArrayList<>();
            for (User user : users) {
                Map<String, Object> userData = new HashMap<>();
                userData.put("id", user.getId());
                userData.put("email", user.getEmail());
                userData.put("firstName", user.getFirstName());
                userData.put("lastName", user.getLastName());
                userData.put("phone", user.getPhone());
                userData.put("role", user.getRole());
                userData.put("walletBalance", user.getWalletBalance());
                userData.put("createdAt", user.getCreatedAt());

                String fullName = ((user.getFirstName() != null ? user.getFirstName() : "").trim() + " "
                        + (user.getLastName() != null ? user.getLastName() : "").trim()).trim();
                userData.put("name", fullName);
                userData.put("active", user.getActive());

                userList.add(userData);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("users", userList);
            response.put("total", userList.size());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Role không hợp lệ: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy danh sách người dùng: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Tạo tài khoản nhân viên
     * POST /api/admin/staff
     */
    @PostMapping("/staff")
    public ResponseEntity<Map<String, Object>> createStaffAccount(
            @RequestBody Map<String, String> staffData) {
        try {
            String email = staffData.get("email");
            String password = staffData.get("password");
            String fullName = staffData.get("fullName");
            String phoneNumber = staffData.get("phoneNumber");

            User newStaff = adminService.createStaffAccount(
                    email, password, fullName, phoneNumber);

            Map<String, Object> response = new HashMap<>();
            if (newStaff != null) {
                response.put("success", true);
                response.put("message", "Tạo tài khoản nhân viên thành công");
                response.put("userId", newStaff.getId());
                response.put("user", Map.of(
                        "id", newStaff.getId(),
                        "email", newStaff.getEmail(),
                        "fullName",
                        (newStaff.getFirstName() != null ? newStaff.getFirstName() : "") + " "
                                + (newStaff.getLastName() != null ? newStaff.getLastName() : ""),
                        "role", newStaff.getRole()));
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Không thể tạo tài khoản nhân viên. Có thể email đã tồn tại.");
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi tạo tài khoản: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestBody Map<String, Object> userData) {
        try {
            String email = (String) userData.get("email");
            String password = (String) userData.get("password");
            String name = (String) userData.get("name");
            String phone = (String) userData.get("phone");
            String roleStr = (String) userData.get("role");
            Boolean active = userData.get("active") != null
                    ? Boolean.valueOf(userData.get("active").toString())
                    : Boolean.TRUE;

            Map<String, Object> response = new HashMap<>();

            if (email == null || email.isEmpty() || password == null || password.isEmpty()
                    || name == null || name.isEmpty()) {
                response.put("success", false);
                response.put("error", "Thiếu thông tin bắt buộc (email, mật khẩu, tên)");
                return ResponseEntity.ok(response);
            }

            if (userRepository.existsByEmail(email)) {
                response.put("success", false);
                response.put("error", "Email đã tồn tại");
                return ResponseEntity.ok(response);
            }

            String[] nameParts = name.trim().split(" ", 2);
            String firstName = nameParts[0];
            String lastName = nameParts.length > 1 ? nameParts[1] : "";

            UserRole userRole;
            if (roleStr != null && !roleStr.isEmpty()) {
                if ("DRIVER".equalsIgnoreCase(roleStr)) {
                    userRole = UserRole.EV_DRIVER;
                } else {
                    userRole = UserRole.valueOf(roleStr.toUpperCase());
                }
            } else {
                userRole = UserRole.EV_DRIVER;
            }

            User user = new User(email, passwordEncoder.encode(password), firstName, lastName, userRole);
            user.setPhone(phone);
            user.setActive(active);

            User saved = userRepository.save(user);

            Map<String, Object> userResp = new HashMap<>();
            userResp.put("id", saved.getId());
            userResp.put("email", saved.getEmail());
            userResp.put("name",
                    ((saved.getFirstName() != null ? saved.getFirstName() : "").trim() + " "
                            + (saved.getLastName() != null ? saved.getLastName() : "").trim()).trim());
            userResp.put("phone", saved.getPhone());
            userResp.put("role", saved.getRole());
            userResp.put("active", saved.getActive());
            userResp.put("createdAt", saved.getCreatedAt());

            response.put("success", true);
            response.put("message", "Tạo người dùng thành công");
            response.put("user", userResp);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Role không hợp lệ: " + e.getMessage());
            return ResponseEntity.ok(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi tạo người dùng: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> updateData) {
        try {
            Optional<User> optionalUser = userRepository.findById(userId);
            Map<String, Object> response = new HashMap<>();

            if (optionalUser.isEmpty()) {
                response.put("success", false);
                response.put("error", "Người dùng không tồn tại");
                return ResponseEntity.ok(response);
            }

            User user = optionalUser.get();

            Object nameObj = updateData.get("name");
            Object fullNameObj = updateData.get("fullName");
            String name = nameObj != null ? nameObj.toString()
                    : (fullNameObj != null ? fullNameObj.toString() : null);
            if (name != null && !name.isEmpty()) {
                String[] nameParts = name.trim().split(" ", 2);
                user.setFirstName(nameParts[0]);
                user.setLastName(nameParts.length > 1 ? nameParts[1] : "");
            }

            Object emailObj = updateData.get("email");
            if (emailObj != null) {
                String newEmail = emailObj.toString();
                if (!newEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
                    response.put("success", false);
                    response.put("error", "Email đã tồn tại");
                    return ResponseEntity.ok(response);
                }
                user.setEmail(newEmail);
            }

            Object phoneObj = updateData.get("phone");
            Object phoneNumberObj = updateData.get("phoneNumber");
            if (phoneObj != null || phoneNumberObj != null) {
                String phone = phoneObj != null ? phoneObj.toString() : phoneNumberObj.toString();
                user.setPhone(phone);
            }

            Object passwordObj = updateData.get("password");
            if (passwordObj != null && !passwordObj.toString().isEmpty()) {
                user.setPassword(passwordEncoder.encode(passwordObj.toString()));
            }

            Object roleObj = updateData.get("role");
            if (roleObj != null) {
                String roleStr = roleObj.toString();
                if (!roleStr.isEmpty()) {
                    UserRole newRole;
                    if ("DRIVER".equalsIgnoreCase(roleStr)) {
                        newRole = UserRole.EV_DRIVER;
                    } else {
                        newRole = UserRole.valueOf(roleStr.toUpperCase());
                    }
                    user.setRole(newRole);
                }
            }

            Object activeObj = updateData.get("active");
            if (activeObj != null) {
                Boolean active = Boolean.valueOf(activeObj.toString());
                user.setActive(active);
            }

            User saved = userRepository.save(user);

            Map<String, Object> userResp = new HashMap<>();
            userResp.put("id", saved.getId());
            userResp.put("email", saved.getEmail());
            userResp.put("firstName", saved.getFirstName());
            userResp.put("lastName", saved.getLastName());
            userResp.put("phone", saved.getPhone());
            userResp.put("role", saved.getRole());
            userResp.put("active", saved.getActive());
            userResp.put("createdAt", saved.getCreatedAt());

            response.put("success", true);
            response.put("message", "Cập nhật người dùng thành công");
            response.put("user", userResp);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Role không hợp lệ: " + e.getMessage());
            return ResponseEntity.ok(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi cập nhật người dùng: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long userId) {
        try {
            Map<String, Object> response = new HashMap<>();

            if (userRepository.existsById(userId)) {
                userRepository.deleteById(userId);
            }

            response.put("success", true);
            response.put("message", "Xóa người dùng thành công");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi xóa người dùng: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @PatchMapping("/users/{userId}/toggle-status")
    public ResponseEntity<Map<String, Object>> toggleUserStatus(@PathVariable Long userId) {
        try {
            Optional<User> optionalUser = userRepository.findById(userId);
            Map<String, Object> response = new HashMap<>();

            if (optionalUser.isEmpty()) {
                response.put("success", false);
                response.put("error", "Người dùng không tồn tại");
                return ResponseEntity.ok(response);
            }

            User user = optionalUser.get();
            Boolean current = user.getActive();
            boolean newStatus = current == null ? false : !current;
            user.setActive(newStatus);
            userRepository.save(user);

            response.put("success", true);
            response.put("active", user.getActive());
            response.put("message", "Cập nhật trạng thái tài khoản thành công");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi cập nhật trạng thái: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    // ==================== REPORTS & STATISTICS ====================

    /**
     * Tổng quan hệ thống
     * GET /api/admin/overview
     * Đã sửa: Thêm đếm số trạm bảo trì (MAINTENANCE)
     */
    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getSystemOverview() {
        try {
            // Đếm stations theo status
            long totalStations = tramSacRepository.count();
            long onlineStations = tramSacRepository.findByStatus(StationStatus.ONLINE.name()).size();
            long offlineStations = tramSacRepository.findByStatus(StationStatus.OFFLINE.name()).size();
            // Lỗi 5. Đã sửa: Đếm trạm đang bảo trì (giả định StationStatus.MAINTENANCE tồn
            // tại)
            long maintenanceStations = tramSacRepository.findByStatus(StationStatus.MAINTENANCE.name()).size();

            // Đếm chargers theo status
            long totalChargers = chargerRepository.count();
            Long availableChargers = chargerRepository.countByStatus(PointStatus.AVAILABLE);
            Long occupiedChargers = chargerRepository.countByStatus(PointStatus.OCCUPIED);
            Long outOfOrderChargers = chargerRepository.countByStatus(PointStatus.OUT_OF_ORDER);

            // Đếm users theo role
            long totalUsers = userRepository.count();
            // Sử dụng các phương thức đã có sẵn
            long drivers = adminService.getUsersByRole(UserRole.EV_DRIVER).size();
            long staff = adminService.getUsersByRole(UserRole.CS_STAFF).size();
            long admins = adminService.getUsersByRole(UserRole.ADMIN).size();

            // Đếm sessions theo status
            Long activeSessions = phienSacRepository.countByStatus(SessionStatus.ACTIVE);
            Long completedSessions = phienSacRepository.countByStatus(SessionStatus.COMPLETED);

            Map<String, Object> overview = new HashMap<>();

            // Stations
            Map<String, Object> stationsData = new HashMap<>();
            stationsData.put("total", totalStations);
            stationsData.put("online", onlineStations);
            stationsData.put("offline", offlineStations);
            stationsData.put("maintenance", maintenanceStations); // Đã sửa
            overview.put("stations", stationsData);

            // Chargers
            Map<String, Object> chargersData = new HashMap<>();
            chargersData.put("total", totalChargers);
            chargersData.put("available", availableChargers != null ? availableChargers : 0);
            chargersData.put("occupied", occupiedChargers != null ? occupiedChargers : 0);
            chargersData.put("outOfOrder", outOfOrderChargers != null ? outOfOrderChargers : 0);
            overview.put("chargers", chargersData);

            // Users
            Map<String, Object> usersData = new HashMap<>();
            usersData.put("total", totalUsers);
            usersData.put("drivers", drivers);
            usersData.put("staff", staff);
            usersData.put("admins", admins);
            overview.put("users", usersData);

            // Sessions
            Map<String, Object> sessionsData = new HashMap<>();
            sessionsData.put("active", activeSessions != null ? activeSessions : 0);
            sessionsData.put("completed", completedSessions != null ? completedSessions : 0);
            overview.put("sessions", sessionsData);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("overview", overview);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy tổng quan hệ thống: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Doanh thu theo trạm
     * GET /api/admin/revenue
     * Đã sửa: N+1 Query issue (Lấy doanh thu)
     */
    @GetMapping("/revenue")
    public ResponseEntity<Map<String, Object>> getRevenueByStation() {
        try {
            // Try to use repository method getTotalRevenueByAllStations() if it exists,
            // otherwise fallback to a reflective aggregation over sessions.
            List<Object[]> revenueDataList = new ArrayList<>();
            try {
                java.lang.reflect.Method m = phienSacRepository.getClass().getMethod("getTotalRevenueByAllStations");
                Object result = m.invoke(phienSacRepository);
                if (result instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object[]> tmp = (List<Object[]>) result;
                    revenueDataList = tmp;
                }
            } catch (NoSuchMethodException nsme) {
                // Fallback: attempt to aggregate revenue from phienSacRepository.findAll()
                try {
                    java.lang.reflect.Method findAll = phienSacRepository.getClass().getMethod("findAll");
                    Object res = findAll.invoke(phienSacRepository);
                    if (res instanceof List) {
                        List<?> sessions = (List<?>) res;
                        Map<Long, Double> agg = new HashMap<>();
                        for (Object s : sessions) {
                            try {
                                // Try to obtain station id via getChargingStation().getId()
                                Long stationId = null;
                                try {
                                    java.lang.reflect.Method getStation = s.getClass().getMethod("getChargingStation");
                                    Object station = getStation.invoke(s);
                                    if (station != null) {
                                        java.lang.reflect.Method getId = station.getClass().getMethod("getId");
                                        Object idObj = getId.invoke(station);
                                        if (idObj instanceof Number) {
                                            stationId = ((Number) idObj).longValue();
                                        } else if (idObj instanceof Long) {
                                            stationId = (Long) idObj;
                                        }
                                    }
                                } catch (NoSuchMethodException ignored) {
                                    // method not present, try alternative names in a permissive way
                                    try {
                                        java.lang.reflect.Method getStation = s.getClass().getMethod("getTramSac");
                                        Object station = getStation.invoke(s);
                                        if (station != null) {
                                            java.lang.reflect.Method getId = station.getClass().getMethod("getId");
                                            Object idObj = getId.invoke(station);
                                            if (idObj instanceof Number) {
                                                stationId = ((Number) idObj).longValue();
                                            } else if (idObj instanceof Long) {
                                                stationId = (Long) idObj;
                                            }
                                        }
                                    } catch (NoSuchMethodException ignored2) {
                                        // give up on this session's station id
                                    }
                                }

                                // Try to obtain revenue/amount for the session via common getters
                                double amount = 0.0;
                                try {
                                    java.lang.reflect.Method getAmount = s.getClass().getMethod("getTotalAmount");
                                    Object amt = getAmount.invoke(s);
                                    if (amt instanceof Number)
                                        amount = ((Number) amt).doubleValue();
                                } catch (NoSuchMethodException ignored) {
                                    try {
                                        java.lang.reflect.Method getAmount = s.getClass().getMethod("getAmount");
                                        Object amt = getAmount.invoke(s);
                                        if (amt instanceof Number)
                                            amount = ((Number) amt).doubleValue();
                                    } catch (NoSuchMethodException ignored2) {
                                        // no amount info available, treat as 0
                                    }
                                }

                                if (stationId != null) {
                                    agg.put(stationId, agg.getOrDefault(stationId, 0.0) + amount);
                                }
                            } catch (Exception ignored) {
                                // ignore individual session reflection errors and continue
                            }
                        }

                        for (Map.Entry<Long, Double> e : agg.entrySet()) {
                            revenueDataList.add(new Object[] { e.getKey(), e.getValue() });
                        }
                    }
                } catch (Exception ignored) {
                    // If even fallback fails, revenueDataList remains empty
                }
            } catch (Exception invokeEx) {
                // If reflective invocation failed for other reasons, leave list empty
            }

            // Lấy tất cả trạm sạc để có tên
            List<TramSac> stations = tramSacRepository.findAll();
            Map<Long, String> stationNames = stations.stream()
                    .collect(Collectors.toMap(TramSac::getId, TramSac::getName));

            List<Map<String, Object>> revenueList = new ArrayList<>();
            double totalRevenue = 0.0;

            for (Object[] data : revenueDataList) {
                Long stationId = (Long) data[0];
                Double stationRevenue = (Double) data[1];
                if (stationRevenue == null)
                    stationRevenue = 0.0;

                Map<String, Object> revenueData = new HashMap<>();
                revenueData.put("stationId", stationId);
                revenueData.put("stationName", stationNames.getOrDefault(stationId, "Unknown Station"));
                revenueData.put("revenue", stationRevenue);

                revenueList.add(revenueData);
                totalRevenue += stationRevenue;
            }

            // Sắp xếp theo doanh thu giảm dần
            revenueList.sort((a, b) -> Double.compare((Double) b.get("revenue"), (Double) a.get("revenue")));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("revenues", revenueList);
            response.put("totalRevenue", totalRevenue);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy doanh thu: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Báo cáo theo tháng
     * GET /api/admin/reports/monthly
     */
    @GetMapping("/reports/monthly")
    public ResponseEntity<Map<String, Object>> getMonthlyReport(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        try {
            // Mặc định lấy tháng hiện tại
            LocalDateTime now = LocalDateTime.now();
            if (year == null)
                year = now.getYear();
            if (month == null)
                month = now.getMonthValue();

            // Lấy dữ liệu từ repository
            Long sessionCount = phienSacRepository.getMonthlySessionCount(year, month);
            Double energyConsumed = phienSacRepository.getMonthlyEnergyConsumed(year, month);
            Double revenue = phienSacRepository.getMonthlyRevenue(year, month);

            Map<String, Object> reportData = new HashMap<>();
            reportData.put("year", year);
            reportData.put("month", month);
            reportData.put("totalSessions", sessionCount != null ? sessionCount : 0);
            reportData.put("totalEnergyConsumed", energyConsumed != null ? energyConsumed : 0.0);
            reportData.put("totalRevenue", revenue != null ? revenue : 0.0);

            // Tính trung bình
            long sessions = sessionCount != null ? sessionCount : 0;
            if (sessions > 0) {
                // Đảm bảo không chia cho 0
                reportData.put("avgEnergyPerSession", (energyConsumed != null ? energyConsumed : 0.0) / sessions);
                reportData.put("avgRevenuePerSession", (revenue != null ? revenue : 0.0) / sessions);
            } else {
                reportData.put("avgEnergyPerSession", 0.0);
                reportData.put("avgRevenuePerSession", 0.0);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("report", reportData);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi tạo báo cáo: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Thống kê sử dụng
     * GET /api/admin/statistics
     * Đã sửa: Lỗi logic khi tính sessionCount cho trạm
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getUsageStatistics() {
        try {
            // Tổng số sessions
            long totalSessions = phienSacRepository.count();
            Long activeSessions = phienSacRepository.countByStatus(SessionStatus.ACTIVE);
            Long completedSessions = phienSacRepository.countByStatus(SessionStatus.COMPLETED);

            // Lấy Top stations (theo số lượng sessions) nếu repository hỗ trợ method này,
            // nếu không thì tổng hợp từ phienSacRepository.findAll()
            // Phương thức kỳ vọng trả về List<Object[]> với Object[0] là stationId (Long),
            // Object[1] là sessionCount (Long)
            List<Object[]> topStationsData = new ArrayList<>();
            try {
                java.lang.reflect.Method m = phienSacRepository.getClass().getMethod("getTopStationsBySessionCount",
                        int.class);
                Object res = m.invoke(phienSacRepository, 5);
                if (res instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object[]> tmp = (List<Object[]>) res;
                    topStationsData = tmp;
                }
            } catch (NoSuchMethodException nsme) {
                // Fallback: aggregate counts from sessions returned by findAll()
                try {
                    List<?> sessions = phienSacRepository.findAll();
                    Map<Long, Long> agg = new HashMap<>();
                    for (Object s : sessions) {
                        try {
                            Long stationId = null;
                            try {
                                java.lang.reflect.Method getStation = s.getClass().getMethod("getChargingStation");
                                Object station = getStation.invoke(s);
                                if (station != null) {
                                    java.lang.reflect.Method getId = station.getClass().getMethod("getId");
                                    Object idObj = getId.invoke(station);
                                    if (idObj instanceof Number)
                                        stationId = ((Number) idObj).longValue();
                                }
                            } catch (NoSuchMethodException ignored) {
                                try {
                                    java.lang.reflect.Method getStation = s.getClass().getMethod("getTramSac");
                                    Object station = getStation.invoke(s);
                                    if (station != null) {
                                        java.lang.reflect.Method getId = station.getClass().getMethod("getId");
                                        Object idObj = getId.invoke(station);
                                        if (idObj instanceof Number)
                                            stationId = ((Number) idObj).longValue();
                                    }
                                } catch (NoSuchMethodException ignored2) {
                                    // give up on this session's station id
                                }
                            }
                            if (stationId != null) {
                                agg.put(stationId, agg.getOrDefault(stationId, 0L) + 1L);
                            }
                        } catch (Exception ignored) {
                            // ignore problematic session and continue
                        }
                    }
                    // sort by session count desc and take top 5
                    topStationsData = agg.entrySet().stream()
                            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                            .limit(5)
                            .map(e -> new Object[] { e.getKey(), e.getValue() })
                            .collect(Collectors.toList());
                } catch (Exception ex) {
                    // if aggregation fails, leave topStationsData empty
                }
            } catch (Exception ex) {
                // other reflection errors - leave topStationsData empty
            }

            // Lấy thông tin trạm sạc cho các trạm Top
            List<Long> topStationIds = topStationsData.stream()
                    .map(data -> (Long) data[0])
                    .collect(Collectors.toList());

            List<TramSac> topStationsEntities = tramSacRepository.findAllById((Iterable<Long>) topStationIds);
            Map<Long, String> stationNames = topStationsEntities.stream()
                    .collect(Collectors.toMap(TramSac::getId, TramSac::getName));

            List<Map<String, Object>> topStations = new ArrayList<>();
            for (Object[] data : topStationsData) {
                Long stationId = (Long) data[0];
                Long sessionCount = (Long) data[1];

                Map<String, Object> stationData = new HashMap<>();
                stationData.put("stationId", stationId);
                stationData.put("stationName", stationNames.getOrDefault(stationId, "Unknown Station"));
                stationData.put("sessionCount", sessionCount);
                topStations.add(stationData);
            }

            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalSessions", totalSessions);
            statistics.put("activeSessions", activeSessions != null ? activeSessions : 0);
            statistics.put("completedSessions", completedSessions != null ? completedSessions : 0);
            statistics.put("topStations", topStations);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("statistics", statistics);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy thống kê: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
