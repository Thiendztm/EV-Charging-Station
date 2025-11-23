package uth.edu.vn.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import uth.edu.vn.entity.*;
import uth.edu.vn.enums.*;
import uth.edu.vn.service.CSStaffService;
import uth.edu.vn.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Charging Station Staff Controller
 * REST API endpoints cho nhân viên trạm sạc
 * Yêu cầu role CS_STAFF để truy cập
 */
@RestController
@RequestMapping("/api/staff")
@PreAuthorize("hasRole('CS_STAFF') or hasRole('ADMIN')")
public class CSStaffController {

    @Autowired
    private CSStaffService staffService;

    @Autowired
    private TramSacRepository tramSacRepository;

    @Autowired
    private ChargerRepository chargerRepository;

    @Autowired
    private PhienSacRepository phienSacRepository;

    @Autowired
    private UserRepository userRepository;

    // ==================== STATION MONITORING ====================

    /**
     * Lấy trạng thái trạm sạc
     * GET /api/staff/station/{stationId}/status
     */
    @GetMapping("/station/{stationId}/status")
    public ResponseEntity<Map<String, Object>> getStationStatus(
            @PathVariable Long stationId) {
        try {
            // Lấy thông tin trạm
            TramSac station = tramSacRepository.findById(stationId).orElse(null);
            if (station == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Không tìm thấy trạm sạc");
                return ResponseEntity.notFound().build();
            }

            // Lấy danh sách điểm sạc tại trạm
            List<Charger> chargers = staffService.getStationStatus(stationId);

            List<Map<String, Object>> chargerList = new ArrayList<>();
            int availableCount = 0;
            int occupiedCount = 0;
            int outOfOrderCount = 0;

            for (Charger charger : chargers) {
                Map<String, Object> chargerData = new HashMap<>();
                chargerData.put("id", charger.getPointId());
                chargerData.put("name", charger.getPointName());
                chargerData.put("connectorType", charger.getConnectorType());
                chargerData.put("powerCapacity", charger.getPowerCapacity());
                chargerData.put("status", charger.getStatus());
                chargerData.put("pricePerKwh", charger.getPricePerKwh());

                // Đếm theo status
                if (charger.getStatus() == PointStatus.AVAILABLE)
                    availableCount++;
                else if (charger.getStatus() == PointStatus.OCCUPIED)
                    occupiedCount++;
                else if (charger.getStatus() == PointStatus.OUT_OF_ORDER)
                    outOfOrderCount++;

                chargerList.add(chargerData);
            }

            // Lấy active sessions tại trạm
            List<PhienSac> activeSessions = staffService.getActiveSessionsAtStation(stationId);
            List<Map<String, Object>> sessionList = new ArrayList<>();

            for (PhienSac session : activeSessions) {
                Map<String, Object> sessionData = new HashMap<>();
                sessionData.put("sessionId", session.getSessionId());
                sessionData.put("chargerId", session.getChargingPoint().getPointId());
                sessionData.put("chargerName", session.getChargingPoint().getPointName());
                sessionData.put("startTime",
                        session.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                sessionData.put("qrCode", session.getQrCode());

                // User info (nếu có)
                if (session.getUser() != null) {
                    sessionData.put("userId", session.getUser().getId());
                    sessionData.put("userName",
                            session.getUser().getFirstName() + " " + session.getUser().getLastName());
                    sessionData.put("userEmail", session.getUser().getEmail());
                } else {
                    sessionData.put("userId", null);
                    sessionData.put("userName", "Walk-in customer");
                }

                // Trạng thái và các thông số phiên sạc để hiển thị trong bảng
                sessionData.put("status", session.getStatus() != null ? session.getStatus().name() : null);
                sessionData.put("energyConsumed", session.getEnergyConsumed());
                sessionData.put("currentCost", session.getTotalCost());
                // SOC hiện tại: ưu tiên endSoc nếu có, fallback về startSoc
                Integer currentSoc = session.getEndSoc() != null ? session.getEndSoc() : session.getStartSoc();
                sessionData.put("currentSoc", currentSoc);

                sessionList.add(sessionData);
            }

            Map<String, Object> stationData = new HashMap<>();
            stationData.put("id", station.getId());
            stationData.put("name", station.getName());
            stationData.put("address", station.getAddress());
            stationData.put("status", station.getStatus());

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalChargers", chargers.size());
            summary.put("availableChargers", availableCount);
            summary.put("occupiedChargers", occupiedCount);
            summary.put("outOfOrderChargers", outOfOrderCount);
            // Giữ lại các key cũ để tương thích ngược nếu nơi khác đang dùng
            summary.put("available", availableCount);
            summary.put("occupied", occupiedCount);
            summary.put("outOfOrder", outOfOrderCount);
            summary.put("activeSessions", activeSessions.size());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("station", stationData);
            response.put("summary", summary);
            response.put("chargers", chargerList);
            // Cho staff/sessions.js
            response.put("activeSessions", sessionList);
            response.put("sessions", sessionList);
            // Cho updateStationStats() trong sessions.js
            response.put("totalChargers", chargers.size());
            response.put("availableChargers", availableCount);
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy trạng thái trạm: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Alias cho staffAPI.getStationStatus
     * GET /api/staff/stations/{stationId}/status
     */
    @GetMapping("/stations/{stationId}/status")
    public ResponseEntity<Map<String, Object>> getStationStatusAlias(
            @PathVariable Long stationId) {
        return getStationStatus(stationId);
    }

    // ==================== SESSION MANAGEMENT ====================

    /**
     * Bắt đầu phiên sạc cho khách walk-in (endpoint gốc)
     * POST /api/staff/session/start
     */
    @PostMapping("/session/start")
    public ResponseEntity<Map<String, Object>> startSession(
            @RequestBody Map<String, Object> sessionData) {
        try {
            Long pointId = Long.parseLong(sessionData.get("pointId").toString());
            String vehiclePlate = (String) sessionData.get("vehiclePlate");

            PhienSac newSession = staffService.startSessionByStaff(pointId, vehiclePlate);

            Map<String, Object> response = new HashMap<>();
            if (newSession != null) {
                response.put("success", true);
                response.put("message", "Bắt đầu phiên sạc thành công");
                response.put("session", Map.of(
                        "sessionId", newSession.getSessionId(),
                        "qrCode", newSession.getQrCode(),
                        "startTime",
                        newSession.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        "chargerId", newSession.getChargingPoint().getPointId(),
                        "chargerName", newSession.getChargingPoint().getPointName()));
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Không thể bắt đầu phiên sạc. Điểm sạc có thể không khả dụng.");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi bắt đầu phiên sạc: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Alias cho staff/sessions.js và staffAPI.startSessionForDriver
     * POST /api/staff/sessions/start
     */
    @PostMapping("/sessions/start")
    public ResponseEntity<Map<String, Object>> startSessionAlias(
            @RequestBody Map<String, Object> sessionData) {
        // Ánh xạ chargerId -> pointId, dùng vehiclePlate mặc định nếu thiếu
        if (!sessionData.containsKey("pointId") && sessionData.containsKey("chargerId")) {
            sessionData.put("pointId", sessionData.get("chargerId"));
        }
        sessionData.putIfAbsent("vehiclePlate", "WALK_IN");
        return startSession(sessionData);
    }

    /**
     * Dừng phiên sạc (endpoint gốc)
     * PUT /api/staff/session/{sessionId}/stop
     */
    @PutMapping("/session/{sessionId}/stop")
    public ResponseEntity<Map<String, Object>> stopSession(
            @PathVariable Long sessionId,
            @RequestBody Map<String, Object> sessionData) {
        try {
            Double energyConsumed = Double.parseDouble(sessionData.get("energyConsumed").toString());
            Integer endSoc = Integer.parseInt(sessionData.get("endSoc").toString());

            boolean success = staffService.stopChargingSession(sessionId, energyConsumed, endSoc);

            Map<String, Object> response = new HashMap<>();
            if (success) {
                // Lấy thông tin session để trả về
                PhienSac session = phienSacRepository.findById(sessionId).orElse(null);
                if (session != null) {
                    Double totalCost = session.getTotalCost() != null ? session.getTotalCost() : 0.0;
                    response.put("success", true);
                    response.put("message", "Dừng phiên sạc thành công");
                    // Trường finalCost để staff/sessions.js hiển thị chi phí
                    response.put("finalCost", totalCost);
                    response.put("session", Map.of(
                            "sessionId", session.getSessionId(),
                            "energyConsumed", session.getEnergyConsumed(),
                            "totalCost", totalCost,
                            "startTime",
                            session.getStartTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                            "endTime",
                            session.getEndTime() != null
                                    ? session.getEndTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                    : null));
                } else {
                    response.put("success", true);
                    response.put("message", "Dừng phiên sạc thành công");
                }
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Không thể dừng phiên sạc");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi dừng phiên sạc: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Alias cho staff/sessions.js
     * POST /api/staff/sessions/{sessionId}/stop
     */
    @PostMapping("/sessions/{sessionId}/stop")
    public ResponseEntity<Map<String, Object>> stopSessionAlias(
            @PathVariable Long sessionId,
            @RequestBody Map<String, Object> sessionData) {
        // Nếu thiếu energyConsumed, cố gắng lấy từ session hiện tại
        if (!sessionData.containsKey("energyConsumed")) {
            PhienSac session = phienSacRepository.findById(sessionId).orElse(null);
            Double energyValue = (session != null && session.getEnergyConsumed() != null)
                    ? session.getEnergyConsumed()
                    : 0.0;
            sessionData.put("energyConsumed", energyValue);
        }
        return stopSession(sessionId, sessionData);
    }

    // ==================== PAYMENT ====================

    /**
     * Xử lý thanh toán tiền mặt (endpoint gốc)
     * POST /api/staff/payment/cash
     */
    @PostMapping("/payment/cash")
    public ResponseEntity<Map<String, Object>> processCashPayment(
            @RequestBody Map<String, Object> paymentData) {
        try {
            Long sessionId = Long.parseLong(paymentData.get("sessionId").toString());

            ThanhToan payment = staffService.processCashPayment(sessionId);

            Map<String, Object> response = new HashMap<>();
            if (payment != null) {
                response.put("success", true);
                response.put("message", "Thanh toán thành công");
                response.put("payment", Map.of(
                        "paymentId", payment.getId(),
                        "amount", payment.getAmount(),
                        "paymentMethod", payment.getMethod(),
                        "status", payment.getStatus(),
                        "paymentTime",
                        payment.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Không thể xử lý thanh toán");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi xử lý thanh toán: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Alias cho staffAPI.processCashPayment
     * POST /api/staff/payments/cash
     */
    @PostMapping("/payments/cash")
    public ResponseEntity<Map<String, Object>> processCashPaymentAlias(
            @RequestBody Map<String, Object> paymentData) {
        return processCashPayment(paymentData);
    }

    /**
     * Xác nhận thanh toán tại chỗ (cho staff/sessions.js & staff/payments.js)
     * POST /api/staff/payments/confirm
     */
    @PostMapping("/payments/confirm")
    public ResponseEntity<Map<String, Object>> confirmPayment(
            @RequestBody Map<String, Object> paymentData) {
        try {
            Long sessionId = Long.parseLong(paymentData.get("sessionId").toString());

            // Các field bổ sung từ frontend (không bắt buộc lưu DB nhưng dùng cho hiển thị)
            Double amountReceived = null;
            if (paymentData.get("amountReceived") != null) {
                amountReceived = Double.parseDouble(paymentData.get("amountReceived").toString());
            }
            String paymentMethod = paymentData.get("paymentMethod") != null
                    ? paymentData.get("paymentMethod").toString()
                    : "CASH";

            // Xử lý thanh toán tiền mặt cốt lõi
            ThanhToan payment = staffService.processCashPayment(sessionId);

            Map<String, Object> response = new HashMap<>();
            if (payment == null) {
                response.put("success", false);
                response.put("error",
                        "Không thể xử lý thanh toán. Phiên sạc có thể chưa được dừng hoặc đã thanh toán trước đó.");
                return ResponseEntity.badRequest().body(response);
            }

            // Lấy thông tin phiên sạc để dựng hóa đơn
            PhienSac session = phienSacRepository.findById(sessionId).orElse(null);

            String userName = "Walk-in customer";
            if (session != null && session.getUser() != null) {
                userName = (session.getUser().getFirstName() != null ? session.getUser().getFirstName() : "")
                        + " "
                        + (session.getUser().getLastName() != null ? session.getUser().getLastName() : "");
            }

            String stationName = null;
            String chargerName = null;
            Double pricePerKwh = 0.0;
            if (session != null && session.getChargingPoint() != null) {
                chargerName = session.getChargingPoint().getPointName();
                pricePerKwh = session.getChargingPoint().getPricePerKwh();
                if (session.getChargingPoint().getChargingStation() != null) {
                    stationName = session.getChargingPoint().getChargingStation().getName();
                }
            }

            Double energy = session != null && session.getEnergyConsumed() != null
                    ? session.getEnergyConsumed()
                    : 0.0;

            Double totalCost = null;
            if (payment.getAmount() != null) {
                totalCost = payment.getAmount().doubleValue();
            } else if (session != null && session.getTotalCost() != null) {
                totalCost = session.getTotalCost();
            } else if (energy != null && pricePerKwh != null) {
                totalCost = energy * pricePerKwh;
            } else {
                totalCost = 0.0;
            }

            if (amountReceived == null || amountReceived <= 0) {
                amountReceived = totalCost;
            }
            double changeAmount = Math.max(0.0, amountReceived - totalCost);

            // Tính thời lượng
            String durationStr = null;
            if (session != null && session.getStartTime() != null && session.getEndTime() != null) {
                Duration duration = Duration.between(session.getStartTime(), session.getEndTime());
                long minutes = duration.toMinutes();
                long hours = minutes / 60;
                long mins = minutes % 60;
                durationStr = String.format("%dh %dm", hours, mins);
            }

            Map<String, Object> paymentInfo = new HashMap<>();
            paymentInfo.put("id", payment.getId());
            paymentInfo.put("transactionId", payment.getId());
            paymentInfo.put("paymentTime", payment.getCreatedAt());
            paymentInfo.put("userName", userName);
            paymentInfo.put("stationName", stationName);
            paymentInfo.put("chargerName", chargerName);
            paymentInfo.put("energyConsumed", energy);
            paymentInfo.put("pricePerKwh", pricePerKwh);
            paymentInfo.put("duration", durationStr);
            paymentInfo.put("totalCost", totalCost);
            paymentInfo.put("paymentMethod", paymentMethod != null ? paymentMethod : payment.getMethod());
            paymentInfo.put("amountReceived", amountReceived);
            paymentInfo.put("changeAmount", changeAmount);

            response.put("success", true);
            response.put("message", "Thanh toán thành công");
            response.put("payment", paymentInfo);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi xử lý thanh toán: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    // ==================== INCIDENT REPORTING ====================

    /**
     * Báo cáo sự cố
     * POST /api/staff/incident
     */
    @PostMapping("/incident")
    public ResponseEntity<Map<String, Object>> reportIncident(
            @RequestBody Map<String, Object> incidentData) {
        try {
            Long stationId = Long.parseLong(incidentData.get("stationId").toString());
            Long pointId = Long.parseLong(incidentData.get("pointId").toString());
            String description = (String) incidentData.get("description");

            boolean success = staffService.reportIncident(stationId, pointId, description);

            Map<String, Object> response = new HashMap<>();
            if (success) {
                response.put("success", true);
                response.put("message", "Báo cáo sự cố thành công. Điểm sạc đã được đánh dấu OUT_OF_ORDER.");

                // Lấy thông tin charger
                Charger charger = chargerRepository.findById(pointId).orElse(null);
                if (charger != null) {
                    response.put("charger", Map.of(
                            "id", charger.getPointId(),
                            "name", charger.getPointName(),
                            "status", charger.getStatus()));
                }

                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Không thể báo cáo sự cố");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi báo cáo sự cố: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    // ==================== REPORTS ====================

    /**
     * Báo cáo hàng ngày
     * GET /api/staff/report/daily
     */
    @GetMapping("/report/daily")
    public ResponseEntity<Map<String, Object>> getDailyReport(
            @RequestParam Long stationId,
            @RequestParam(required = false) String date) {
        try {
            // Parse date (mặc định hôm nay)
            LocalDate reportDate;
            if (date != null && !date.isEmpty()) {
                reportDate = LocalDate.parse(date);
            } else {
                reportDate = LocalDate.now();
            }

            // Lấy start và end time cho ngày
            LocalDateTime startOfDay = reportDate.atStartOfDay();
            LocalDateTime endOfDay = reportDate.plusDays(1).atStartOfDay();

            // Lấy sessions trong ngày
            List<PhienSac> sessions = phienSacRepository
                    .findByChargingPointChargingStationIdAndStartTimeBetween(stationId, startOfDay, endOfDay);

            // Tính toán
            int totalSessions = sessions.size();
            double totalEnergy = 0.0;
            double totalRevenue = 0.0;
            int completedSessions = 0;
            int activeSessions = 0;

            for (PhienSac session : sessions) {
                if (session.getEnergyConsumed() != null) {
                    totalEnergy += session.getEnergyConsumed();
                }
                if (session.getTotalCost() != null) {
                    totalRevenue += session.getTotalCost();
                }
                if (session.getStatus() == SessionStatus.COMPLETED) {
                    completedSessions++;
                } else if (session.getStatus() == SessionStatus.ACTIVE) {
                    activeSessions++;
                }
            }

            // Lấy thông tin trạm
            TramSac station = tramSacRepository.findById(stationId).orElse(null);

            Map<String, Object> reportData = new HashMap<>();
            reportData.put("date", reportDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            reportData.put("stationId", stationId);
            reportData.put("stationName", station != null ? station.getName() : "Unknown");
            reportData.put("totalSessions", totalSessions);
            reportData.put("completedSessions", completedSessions);
            reportData.put("activeSessions", activeSessions);
            reportData.put("totalEnergy", Math.round(totalEnergy * 100.0) / 100.0);
            reportData.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);

            if (totalSessions > 0) {
                reportData.put("avgEnergyPerSession", Math.round((totalEnergy / totalSessions) * 100.0) / 100.0);
                reportData.put("avgRevenuePerSession", Math.round((totalRevenue / totalSessions) * 100.0) / 100.0);
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
     * Lấy danh sách trạm mà staff được phân công (simplified - lấy tất cả)
     * GET /api/staff/stations
     */
    @GetMapping("/stations")
    public ResponseEntity<Map<String, Object>> getAssignedStations(
            Authentication authentication) {
        try {
            // TODO: Trong thực tế, cần có bảng staff_station_assignment
            // Hiện tại đơn giản hóa bằng cách trả về tất cả stations

            List<TramSac> stations = tramSacRepository.findAll();
            List<Map<String, Object>> stationList = new ArrayList<>();

            for (TramSac station : stations) {
                Map<String, Object> stationData = new HashMap<>();
                stationData.put("id", station.getId());
                stationData.put("name", station.getName());
                stationData.put("address", station.getAddress());
                stationData.put("status", station.getStatus());

                // Count chargers
                List<Charger> chargers = chargerRepository.findByChargingStationId(station.getId());
                long availableCount = chargers.stream()
                        .filter(c -> c.getStatus() == PointStatus.AVAILABLE)
                        .count();

                stationData.put("totalChargers", chargers.size());
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
            errorResponse.put("error", "Lỗi khi lấy danh sách trạm: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
