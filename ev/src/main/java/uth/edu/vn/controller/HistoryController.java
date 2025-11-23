package uth.edu.vn.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import uth.edu.vn.entity.*;
import uth.edu.vn.service.EVDriverService;
import uth.edu.vn.repository.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * History Controller
 * REST API endpoints cho lịch sử sạc, booking, và thanh toán
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    @Autowired
    private EVDriverService evDriverService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhienSacRepository phienSacRepository;

    @Autowired
    private DatChoRepository datChoRepository;

    @Autowired
    private ThanhToanRepository thanhToanRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Lấy lịch sử sạc của user
     * GET /api/history/charging
     */
    @GetMapping("/charging")
    public ResponseEntity<Map<String, Object>> getChargingHistory(
            Authentication authentication,
            @RequestParam(required = false) Integer limit) {
        try {
            if (authentication == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Yêu cầu đăng nhập");
                return ResponseEntity.status(401).body(errorResponse);
            }

            String email = authentication.getName();
            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Không tìm thấy người dùng");
                return ResponseEntity.notFound().build();
            }

            // Lấy charging history
            List<PhienSac> sessions = evDriverService.getChargingHistory(user.getId());

            if (sessions == null) {
                sessions = new ArrayList<>();
            }

            // Apply limit nếu có
            if (limit != null && limit > 0 && sessions.size() > limit) {
                sessions = sessions.subList(0, limit);
            }

            // Convert to response format
            List<Map<String, Object>> historyList = new ArrayList<>();

            for (PhienSac session : sessions) {
                Map<String, Object> sessionData = new HashMap<>();
                sessionData.put("sessionId", session.getSessionId());
                sessionData.put("startTime",
                        session.getStartTime() != null ? session.getStartTime().format(DATE_FORMATTER) : null);
                sessionData.put("endTime",
                        session.getEndTime() != null ? session.getEndTime().format(DATE_FORMATTER) : null);
                sessionData.put("energyConsumed", session.getEnergyConsumed());
                sessionData.put("totalCost", session.getTotalCost());
                sessionData.put("status", session.getStatus());
                sessionData.put("qrCode", session.getQrCode());
                // SOC information for UI
                sessionData.put("startSoc", session.getStartSoc());
                sessionData.put("endSoc", session.getEndSoc());

                // Charger info
                if (session.getChargingPoint() != null) {
                    Charger charger = session.getChargingPoint();
                    sessionData.put("charger", Map.of(
                            "id", charger.getPointId(),
                            "name", charger.getPointName(),
                            "connectorType", charger.getConnectorType(),
                            "powerCapacity", charger.getPowerCapacity()));

                    // Station info
                    if (charger.getChargingStation() != null) {
                        TramSac station = charger.getChargingStation();
                        sessionData.put("station", Map.of(
                                "id", station.getId(),
                                "name", station.getName(),
                                "address", station.getAddress()));
                        sessionData.put("stationName", station.getName());
                    }
                }

                historyList.add(sessionData);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("history", historyList);
            response.put("total", historyList.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy lịch sử sạc: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Lấy lịch sử booking
     * GET /api/history/bookings
     */
    @GetMapping("/bookings")
    public ResponseEntity<Map<String, Object>> getBookingHistory(
            Authentication authentication,
            @RequestParam(required = false) Integer limit) {
        try {
            if (authentication == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Yêu cầu đăng nhập");
                return ResponseEntity.status(401).body(errorResponse);
            }

            String email = authentication.getName();
            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Không tìm thấy người dùng");
                return ResponseEntity.notFound().build();
            }

            // Lấy bookings
            List<DatCho> bookings = datChoRepository.findByUserId(user.getId());

            if (bookings == null) {
                bookings = new ArrayList<>();
            }

            // Sort by created time desc
            bookings.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

            // Apply limit
            if (limit != null && limit > 0 && bookings.size() > limit) {
                bookings = bookings.subList(0, limit);
            }

            // Convert to response format
            List<Map<String, Object>> bookingList = new ArrayList<>();

            for (DatCho booking : bookings) {
                Map<String, Object> bookingData = new HashMap<>();
                bookingData.put("bookingId", booking.getBookingId());
                bookingData.put("bookingTime", booking.getCreatedAt().format(DATE_FORMATTER));
                bookingData.put("startTime", booking.getStartTime().format(DATE_FORMATTER));
                bookingData.put("endTime", booking.getEndTime().format(DATE_FORMATTER));
                bookingData.put("status", booking.getStatus());

                // Charger info
                if (booking.getChargingPoint() != null) {
                    Charger charger = booking.getChargingPoint();
                    bookingData.put("charger", Map.of(
                            "id", charger.getPointId(),
                            "name", charger.getPointName(),
                            "connectorType", charger.getConnectorType()));

                    // Station info
                    if (charger.getChargingStation() != null) {
                        TramSac station = charger.getChargingStation();
                        bookingData.put("station", Map.of(
                                "id", station.getId(),
                                "name", station.getName(),
                                "address", station.getAddress()));
                    }
                }

                bookingList.add(bookingData);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("bookings", bookingList);
            response.put("total", bookingList.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy lịch sử booking: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Lấy lịch sử thanh toán
     * GET /api/history/payments
     */
    @GetMapping("/payments")
    public ResponseEntity<Map<String, Object>> getPaymentHistory(
            Authentication authentication,
            @RequestParam(required = false) Integer limit) {
        try {
            if (authentication == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Yêu cầu đăng nhập");
                return ResponseEntity.status(401).body(errorResponse);
            }

            String email = authentication.getName();
            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Không tìm thấy người dùng");
                return ResponseEntity.notFound().build();
            }

            // Lấy payments (through sessions)
            List<PhienSac> sessions = phienSacRepository.findByUserOrderByStartTimeDesc(user.getId());
            List<Map<String, Object>> paymentList = new ArrayList<>();

            for (PhienSac session : sessions) {
                // Tìm payment cho session này
                List<ThanhToan> payments = thanhToanRepository.findBySessionId(session.getSessionId());

                for (ThanhToan payment : payments) {
                    Map<String, Object> paymentData = new HashMap<>();
                    paymentData.put("paymentId", payment.getId());
                    paymentData.put("amount", payment.getAmount());
                    paymentData.put("paymentMethod", payment.getMethod());
                    paymentData.put("status", payment.getStatus());
                    paymentData.put("paymentTime", payment.getCreatedAt().format(DATE_FORMATTER));

                    // Session info
                    paymentData.put("sessionId", session.getSessionId());
                    paymentData.put("energyConsumed", session.getEnergyConsumed());

                    // Station info
                    if (session.getChargingPoint() != null &&
                            session.getChargingPoint().getChargingStation() != null) {
                        TramSac station = session.getChargingPoint().getChargingStation();
                        paymentData.put("stationName", station.getName());
                    }

                    paymentList.add(paymentData);
                }
            }

            // Apply limit
            if (limit != null && limit > 0 && paymentList.size() > limit) {
                paymentList = paymentList.subList(0, limit);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("payments", paymentList);
            response.put("total", paymentList.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy lịch sử thanh toán: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Lấy thống kê chi phí hàng tháng
     * GET /api/history/monthly-cost
     */
    @GetMapping("/monthly-cost")
    public ResponseEntity<Map<String, Object>> getMonthlyCost(
            Authentication authentication,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        try {
            if (authentication == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Yêu cầu đăng nhập");
                return ResponseEntity.status(401).body(errorResponse);
            }

            String email = authentication.getName();
            User user = userRepository.findByEmail(email).orElse(null);

            if (user == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Không tìm thấy người dùng");
                return ResponseEntity.notFound().build();
            }

            // Mặc định tháng hiện tại
            LocalDateTime now = LocalDateTime.now();
            if (year == null)
                year = now.getYear();
            if (month == null)
                month = now.getMonthValue();

            Double monthlyCost = evDriverService.getMonthlyChargingCost(user.getId(), year, month);

            Map<String, Object> costData = new HashMap<>();
            costData.put("year", year);
            costData.put("month", month);
            costData.put("totalCost", monthlyCost != null ? monthlyCost : 0.0);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("monthlyCost", costData);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy chi phí hàng tháng: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("History test endpoint working!");
    }
}