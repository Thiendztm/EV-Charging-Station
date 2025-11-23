package uth.edu.vn.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import uth.edu.vn.entity.*;
import uth.edu.vn.enums.*;
import uth.edu.vn.exception.ResourceNotFoundException;
import uth.edu.vn.service.EVDriverService;
import uth.edu.vn.repository.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Payment Controller
 * REST API endpoints cho thanh toán và quản lý ví
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private EVDriverService evDriverService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhienSacRepository phienSacRepository;

    @Autowired
    private ThanhToanRepository thanhToanRepository;

    @Autowired
    private ChargerRepository chargerRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ==================== WALLET MANAGEMENT ====================

    /**
     * Lấy số dư ví
     * GET /api/payment/wallet/balance
     * Alias cho các luồng cũ sử dụng PaymentController thay vì ProfileController
     */
    @GetMapping("/wallet/balance")
    public ResponseEntity<Map<String, Object>> getWalletBalance(
            Authentication authentication) {
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

            Map<String, Object> walletData = new HashMap<>();
            walletData.put("userId", user.getId());
            walletData.put("balance", user.getWalletBalance() != null ? user.getWalletBalance() : 0.0);
            walletData.put("email", user.getEmail());
            walletData.put("fullName", user.getFirstName() + " " + user.getLastName());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("wallet", walletData);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy số dư ví: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Nạp tiền vào ví (alias cho frontend driverAPI.topUpWallet)
     * POST /api/payment/wallet/topup
     */
    @PostMapping("/wallet/topup")
    public ResponseEntity<Map<String, Object>> topUpWallet(
            Authentication authentication,
            @RequestBody Map<String, Object> fundData) {
        return addFunds(authentication, fundData);
    }

    /**
     * Nạp tiền vào ví (endpoint gốc)
     * POST /api/payment/wallet/add-funds
     */
    @PostMapping("/wallet/add-funds")
    public ResponseEntity<Map<String, Object>> addFunds(
            Authentication authentication,
            @RequestBody Map<String, Object> fundData) {
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

            Double amount = Double.parseDouble(fundData.get("amount").toString());

            if (amount <= 0) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Số tiền phải lớn hơn 0");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            boolean success = evDriverService.addFundsToWallet(user.getId(), amount);

            Map<String, Object> response = new HashMap<>();
            if (success) {
                // Lấy số dư mới
                User updatedUser = userRepository.findById(user.getId()).orElse(user);

                response.put("success", true);
                response.put("message", "Nạp tiền thành công");
                response.put("amount", amount);
                response.put("newBalance", updatedUser.getWalletBalance());
                response.put("timestamp", LocalDateTime.now().format(DATE_FORMATTER));

                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Không thể nạp tiền vào ví");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (NumberFormatException e) {
            logger.error("Invalid amount format: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Số tiền không hợp lệ");
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (ResourceNotFoundException e) {
            logger.error("User not found: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi nạp tiền: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    // ==================== PAYMENT PROCESSING ====================

    /**
     * Xử lý thanh toán cho phiên sạc (endpoint gốc)
     * POST /api/payment/process
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processPayment(
            Authentication authentication,
            @RequestBody Map<String, Object> paymentData) {
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

            Long sessionId = Long.parseLong(paymentData.get("sessionId").toString());
            String paymentMethodStr = (String) paymentData.get("paymentMethod");
            PaymentMethod paymentMethod = PaymentMethod.valueOf(paymentMethodStr.toUpperCase());

            // Xử lý thanh toán
            ThanhToan payment = evDriverService.processPayment(sessionId, paymentMethod);

            Map<String, Object> response = new HashMap<>();
            if (payment != null) {
                response.put("success", true);
                response.put("message", "Thanh toán thành công");
                response.put("payment", Map.of(
                        "paymentId", payment.getId(),
                        "amount", payment.getAmount(),
                        "paymentMethod", payment.getMethod(),
                        "status", payment.getStatus(),
                        "paymentTime", payment.getCreatedAt().format(DATE_FORMATTER)));

                // Số dư mới nếu thanh toán bằng ví
                if (paymentMethod == PaymentMethod.WALLET) {
                    User updatedUser = userRepository.findById(user.getId()).orElse(user);
                    response.put("newWalletBalance", updatedUser.getWalletBalance());
                }

                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("error", "Không thể xử lý thanh toán");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (NumberFormatException e) {
            logger.error("Invalid session ID format: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Session ID không hợp lệ");
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (ResourceNotFoundException e) {
            logger.error("Resource not found: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi xử lý thanh toán: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Thanh toán cho phiên sạc (alias cho driverAPI.paySession)
     * POST /api/payment/charge
     */
    @PostMapping("/charge")
    public ResponseEntity<Map<String, Object>> chargeSession(
            Authentication authentication,
            @RequestBody Map<String, Object> paymentData) {
        return processPayment(authentication, paymentData);
    }

    @PostMapping("/simulate-charge-session")
    public ResponseEntity<Map<String, Object>> simulateChargeSession(
            Authentication authentication,
            @RequestBody Map<String, Object> request) {
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

            Object chargerIdObj = request.get("chargerId");
            if (chargerIdObj == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Thiếu chargerId trong request");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            Long chargerId = Long.parseLong(chargerIdObj.toString());

            Charger charger = chargerRepository.findById(chargerId).orElse(null);
            if (charger == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Không tìm thấy điểm sạc");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            Double energy = null;
            Object energyObj = request.get("energyKwh");
            if (energyObj != null) {
                energy = Double.parseDouble(energyObj.toString());
            }
            if (energy == null || energy <= 0) {
                energy = 20.0;
            }

            Integer durationMinutes = 30;
            Object durationObj = request.get("durationMinutes");
            if (durationObj != null) {
                durationMinutes = Integer.parseInt(durationObj.toString());
            }

            Double pricePerKwh = charger.getPricePerKwh();
            if (pricePerKwh == null || pricePerKwh <= 0) {
                pricePerKwh = 3000.0;
            }

            Double totalCost = energy * pricePerKwh;

            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusMinutes(durationMinutes);

            PhienSac session = new PhienSac();
            session.setUser(user);
            session.setChargingPoint(charger);
            session.setStartTime(startTime);
            session.setEndTime(endTime);
            session.setEnergyConsumed(energy);
            session.setStartSoc(20);
            session.setEndSoc(80);
            session.setTotalCost(totalCost);
            session.setStatus(SessionStatus.COMPLETED);
            session.setQrCode("SIM-" + UUID.randomUUID());
            session.setCreatedAt(startTime);
            session.setUpdatedAt(endTime);

            session = phienSacRepository.save(session);

            Object methodObj = request.get("paymentMethod");
            String methodStr = methodObj != null ? methodObj.toString() : "SIMULATED";

            ThanhToan payment = new ThanhToan(
                    session.getSessionId(),
                    java.math.BigDecimal.valueOf(totalCost),
                    methodStr);
            payment.setStatus("COMPLETED");
            payment = thanhToanRepository.save(payment);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Phiên sạc giả lập đã được tạo");
            response.put("sessionId", session.getSessionId());
            response.put("totalCost", totalCost);
            response.put("energyConsumed", energy);
            response.put("stationName",
                    charger.getChargingStation() != null ? charger.getChargingStation().getName() : null);
            response.put("paymentId", payment.getId());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi giả lập phiên sạc: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Lấy hóa đơn/thông tin thanh toán theo sessionId
     * GET /api/payment/invoice/{sessionId}
     */
    @GetMapping("/invoice/{sessionId}")
    public ResponseEntity<Map<String, Object>> getInvoiceBySession(
            @PathVariable Long sessionId,
            Authentication authentication) {
        try {
            if (authentication == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Yêu cầu đăng nhập");
                return ResponseEntity.status(401).body(errorResponse);
            }

            List<ThanhToan> payments = thanhToanRepository.findBySessionId(sessionId);
            if (payments == null || payments.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Không tìm thấy thanh toán cho phiên sạc này");
                return ResponseEntity.notFound().build();
            }

            // Lấy payment mới nhất
            ThanhToan latest = payments.get(0);
            for (ThanhToan p : payments) {
                if (p.getCreatedAt() != null &&
                        latest.getCreatedAt() != null &&
                        p.getCreatedAt().isAfter(latest.getCreatedAt())) {
                    latest = p;
                }
            }

            Map<String, Object> paymentData = new HashMap<>();
            paymentData.put("paymentId", latest.getId());
            paymentData.put("amount", latest.getAmount());
            paymentData.put("paymentMethod", latest.getMethod());
            paymentData.put("status", latest.getStatus());
            paymentData.put("paymentTime", latest.getCreatedAt().format(DATE_FORMATTER));

            PhienSac session = phienSacRepository.findById(sessionId).orElse(null);
            if (session != null) {
                double energyValue = session.getEnergyConsumed() != null ? session.getEnergyConsumed() : 0.0;
                paymentData.put("session", Map.of(
                        "sessionId", session.getSessionId(),
                        "energyConsumed", energyValue,
                        "startTime", session.getStartTime().format(DATE_FORMATTER),
                        "endTime", session.getEndTime() != null ? session.getEndTime().format(DATE_FORMATTER) : null));

                if (session.getChargingPoint() != null &&
                        session.getChargingPoint().getChargingStation() != null) {
                    TramSac station = session.getChargingPoint().getChargingStation();
                    paymentData.put("station", Map.of(
                            "id", station.getId(),
                            "name", station.getName(),
                            "address", station.getAddress()));
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("payment", paymentData);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy hóa đơn: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Lấy lịch sử thanh toán cho người dùng hiện tại (alias tiện dụng)
     * GET /api/payment/history
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getUserPaymentHistory(Authentication authentication) {
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

            List<PhienSac> sessions = phienSacRepository.findByUserOrderByStartTimeDesc(user.getId());
            List<Map<String, Object>> paymentList = new ArrayList<>();

            for (PhienSac session : sessions) {
                List<ThanhToan> payments = thanhToanRepository.findBySessionId(session.getSessionId());
                for (ThanhToan payment : payments) {
                    Map<String, Object> paymentData = new HashMap<>();
                    paymentData.put("paymentId", payment.getId());
                    paymentData.put("amount", payment.getAmount());
                    paymentData.put("paymentMethod", payment.getMethod());
                    paymentData.put("status", payment.getStatus());
                    paymentData.put("paymentTime", payment.getCreatedAt().format(DATE_FORMATTER));
                    paymentData.put("sessionId", session.getSessionId());
                    paymentData.put("energyConsumed", session.getEnergyConsumed());

                    if (session.getChargingPoint() != null &&
                            session.getChargingPoint().getChargingStation() != null) {
                        TramSac station = session.getChargingPoint().getChargingStation();
                        paymentData.put("stationName", station.getName());
                    }

                    paymentList.add(paymentData);
                }
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
     * Lấy các phương thức thanh toán khả dụng
     * GET /api/payment/methods
     */
    @GetMapping("/methods")
    public ResponseEntity<Map<String, Object>> getPaymentMethods() {
        try {
            List<Map<String, Object>> methods = new ArrayList<>();

            // Wallet
            Map<String, Object> wallet = new HashMap<>();
            wallet.put("id", "WALLET");
            wallet.put("name", "Ví điện tử");
            wallet.put("description", "Thanh toán bằng số dư ví");
            wallet.put("icon", "wallet");
            methods.add(wallet);

            // Card
            Map<String, Object> card = new HashMap<>();
            card.put("id", "CARD");
            card.put("name", "Thẻ tín dụng/ghi nợ");
            card.put("description", "Thanh toán bằng thẻ");
            card.put("icon", "credit-card");
            methods.add(card);

            // Cash (for staff only)
            Map<String, Object> cash = new HashMap<>();
            cash.put("id", "CASH");
            cash.put("name", "Tiền mặt");
            cash.put("description", "Thanh toán tiền mặt tại trạm");
            cash.put("icon", "money-bill");
            cash.put("staffOnly", true);
            methods.add(cash);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("methods", methods);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy phương thức thanh toán: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Lấy chi tiết thanh toán
     * GET /api/payment/{paymentId}
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<Map<String, Object>> getPaymentDetails(
            @PathVariable Long paymentId,
            Authentication authentication) {
        try {
            if (authentication == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Yêu cầu đăng nhập");
                return ResponseEntity.status(401).body(errorResponse);
            }

            ThanhToan payment = thanhToanRepository.findById(paymentId).orElse(null);

            if (payment == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Không tìm thấy thanh toán");
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> paymentData = new HashMap<>();
            paymentData.put("paymentId", payment.getId());
            paymentData.put("amount", payment.getAmount());
            paymentData.put("paymentMethod", payment.getMethod());
            paymentData.put("status", payment.getStatus());
            paymentData.put("paymentTime", payment.getCreatedAt().format(DATE_FORMATTER));

            // Session info - tìm qua sessionId
            PhienSac session = phienSacRepository.findById(payment.getSessionId()).orElse(null);
            if (session != null) {
                double energyValue = session.getEnergyConsumed() != null ? session.getEnergyConsumed().doubleValue()
                        : 0.0;
                paymentData.put("session", Map.of(
                        "sessionId", session.getSessionId(),
                        "energyConsumed", energyValue,
                        "startTime", session.getStartTime().format(DATE_FORMATTER),
                        "endTime", session.getEndTime() != null ? session.getEndTime().format(DATE_FORMATTER) : null));

                // Station info
                if (session.getChargingPoint() != null &&
                        session.getChargingPoint().getChargingStation() != null) {
                    TramSac station = session.getChargingPoint().getChargingStation();
                    paymentData.put("station", Map.of(
                            "id", station.getId(),
                            "name", station.getName(),
                            "address", station.getAddress()));
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("payment", paymentData);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy chi tiết thanh toán: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    /**
     * Lấy tổng chi tiêu theo khoảng thời gian
     * GET /api/payment/total-spending
     */
    @GetMapping("/total-spending")
    public ResponseEntity<Map<String, Object>> getTotalSpending(
            Authentication authentication,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
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

            // Parse dates (mặc định tháng hiện tại)
            LocalDateTime start;
            LocalDateTime end;

            if (startDate != null && endDate != null) {
                start = LocalDateTime.parse(startDate + " 00:00:00", DATE_FORMATTER);
                end = LocalDateTime.parse(endDate + " 23:59:59", DATE_FORMATTER);
            } else {
                // Mặc định tháng hiện tại
                LocalDateTime now = LocalDateTime.now();
                start = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                end = now.withDayOfMonth(now.toLocalDate().lengthOfMonth())
                        .withHour(23).withMinute(59).withSecond(59);
            }

            // Lấy tất cả sessions trong khoảng thời gian
            List<PhienSac> sessions = phienSacRepository.findByUserOrderByStartTimeDesc(user.getId());

            double totalSpending = 0.0;
            int sessionCount = 0;

            for (PhienSac session : sessions) {
                if (session.getStartTime().isAfter(start) &&
                        session.getStartTime().isBefore(end) &&
                        session.getTotalCost() != null) {
                    totalSpending += session.getTotalCost();
                    sessionCount++;
                }
            }

            Map<String, Object> spendingData = new HashMap<>();
            spendingData.put("totalSpending", Math.round(totalSpending * 100.0) / 100.0);
            spendingData.put("sessionCount", sessionCount);
            spendingData.put("avgSpendingPerSession",
                    sessionCount > 0 ? Math.round((totalSpending / sessionCount) * 100.0) / 100.0 : 0.0);
            spendingData.put("startDate", start.format(DATE_FORMATTER));
            spendingData.put("endDate", end.format(DATE_FORMATTER));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("spending", spendingData);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Lỗi khi lấy tổng chi tiêu: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
}
