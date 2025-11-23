// File: uth/edu/vn/service/CSStaffService.java (Đã Khắc Phục Lỗi getId())

package uth.edu.vn.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uth.edu.vn.entity.*;
import uth.edu.vn.enums.*;
import uth.edu.vn.exception.ResourceNotFoundException;
import uth.edu.vn.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service class for Charging Station Staff functionalities
 * REFACTORED: Sử dụng Spring Data JPA thay vì HibernateUtil
 * * 1. Payment management at charging stations
 * 2. Monitoring and reporting
 */
@Service
@Transactional
public class CSStaffService {

    private static final Logger logger = LoggerFactory.getLogger(CSStaffService.class);
    private static final Long WALK_IN_USER_ID = 1000L; // Giả định User ID cố định

    @Autowired
    private ChargerRepository chargerRepository;

    @Autowired
    private PhienSacRepository phienSacRepository;

    @Autowired
    private ThanhToanRepository thanhToanRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private XeRepository xeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ==================== 1. PAYMENT MANAGEMENT AT CHARGING STATIONS
    // ====================

    /**
     * Start charging session by staff for walk-in customers
     */
    public PhienSac startSessionByStaff(Long pointId, String vehiclePlate) throws ResourceNotFoundException {

        Charger point = chargerRepository.findById(pointId)
                .orElseThrow(() -> new ResourceNotFoundException("Charging point not found with id: " + pointId));

        if (point.getStatus() != PointStatus.AVAILABLE) {
            logger.warn("Charging point {} is not available. Status: {}", pointId, point.getStatus());
            return null;
        }

        // Lấy User và Xe Walk-in cố định
        User walkInUser = userRepository.findById(WALK_IN_USER_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Walk-in user not found. Please create user ID: " + WALK_IN_USER_ID));

        // Tạo charging session
        PhienSac chargingSession = new PhienSac(walkInUser, point, "STAFF_" + System.currentTimeMillis());
        // Do phương thức setVehiclePlate(String) không tồn tại, ta bỏ qua dòng này.
        // chargingSession.setVehiclePlate(vehiclePlate);
        chargingSession.setStartTime(LocalDateTime.now());
        chargingSession.setStatus(SessionStatus.ACTIVE);

        PhienSac savedSession = phienSacRepository.save(chargingSession);

        // Update charging point status
        point.setStatus(PointStatus.OCCUPIED);
        chargerRepository.save(point);

        logger.info("Charging session started by staff for vehicle: {}", vehiclePlate);
        return savedSession;
    }

    /**
     * Stop charging session and calculate final cost
     */
    public boolean stopChargingSession(Long sessionId, Double energyConsumed, Integer endSoc)
            throws ResourceNotFoundException {

        PhienSac chargingSession = phienSacRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Charging session not found with id: " + sessionId));

        if (chargingSession.getStatus() != SessionStatus.ACTIVE) {
            logger.warn("Cannot stop session {}. Current status: {}", sessionId, chargingSession.getStatus());
            return false;
        }

        // Update session details
        chargingSession.setEndTime(LocalDateTime.now());
        chargingSession.setEnergyConsumed(energyConsumed);
        chargingSession.setEndSoc(endSoc);
        chargingSession.setStatus(SessionStatus.COMPLETED);

        // Calculate total cost
        Double pricePerKwh = chargingSession.getChargingPoint().getPricePerKwh();
        Double totalCost = energyConsumed * pricePerKwh;
        chargingSession.setTotalCost(totalCost);

        phienSacRepository.save(chargingSession);

        // Update charging point status
        Charger point = chargingSession.getChargingPoint();
        point.setStatus(PointStatus.AVAILABLE);
        chargerRepository.save(point);

        logger.info("Charging session stopped. Energy: {} kWh, Cost: ${}", energyConsumed, totalCost);
        return true;
    }

    /**
     * Process cash payment for completed charging session
     */
    public ThanhToan processCashPayment(Long sessionId) throws ResourceNotFoundException {

        PhienSac chargingSession = phienSacRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Charging session not found with id: " + sessionId));

        if (chargingSession.getTotalCost() == null) {
            logger.warn("Cannot process payment - session {} has no total cost (must be stopped first)", sessionId);
            return null;
        }

        // KHẮC PHỤC LỖI getId():
        // Đã đổi chargingSession.getId() thành chargingSession.getSessionId()
        // để phù hợp với tên trường ID trong Entity PhienSac (giả định)
        List<ThanhToan> existingPayments = thanhToanRepository
                .findBySessionIdAndStatus(chargingSession.getSessionId(), "COMPLETED");

        if (!existingPayments.isEmpty()) {
            logger.warn("Payment for session {} already processed.", sessionId);
            return null;
        }

        ThanhToan payment = new ThanhToan();

        // Nếu ThanhToanRepository dùng findByPhienSac_IdAndStatus, bạn cần thêm
        // setPhienSac
        // Nếu ThanhToan có phương thức setPhienSac(PhienSac) đã được thêm, bạn có thể
        // uncomment dòng này
        // payment.setPhienSac(chargingSession);

        // Gắn sessionId để lịch sử thanh toán truy vết được phiên sạc
        payment.setSessionId(chargingSession.getSessionId());
        payment.setAmount(java.math.BigDecimal.valueOf(chargingSession.getTotalCost()));

        // Sử dụng String literal để tránh lỗi Enum
        payment.setMethod("CASH");
        // Dùng trạng thái COMPLETED để thống nhất với các luồng thanh toán khác
        payment.setStatus("COMPLETED");

        payment.setCreatedAt(LocalDateTime.now());

        payment = thanhToanRepository.save(payment);

        logger.info("Cash payment processed: ${}", chargingSession.getTotalCost());
        return payment;
    }

    // ==================== 2. MONITORING AND REPORTING ====================

    /**
     * Get status of all charging points at a station
     */
    @Transactional(readOnly = true)
    public List<Charger> getStationStatus(Long stationId) {
        return chargerRepository.findByChargingStationId(stationId);
    }

    /**
     * Get all active charging sessions at a station
     */
    @Transactional(readOnly = true)
    public List<PhienSac> getActiveSessionsAtStation(Long stationId) {
        return phienSacRepository.findByChargingPointChargingStationIdAndStatus(stationId, SessionStatus.ACTIVE);
    }

    /**
     * Report incident and mark charging point as out of order
     */
    public boolean reportIncident(Long stationId, Long pointId, String description) throws ResourceNotFoundException {

        Charger point = chargerRepository.findById(pointId)
                .orElseThrow(() -> new ResourceNotFoundException("Charging point not found with id: " + pointId));

        if (point.getChargingStation() == null || !point.getChargingStation().getId().equals(stationId)) {
            logger.warn("Point {} does not belong to station {}", pointId, stationId);
            return false;
        }

        // Mark point as out of order
        point.setStatus(PointStatus.OUT_OF_ORDER);
        chargerRepository.save(point);

        logger.info("Incident reported for point {}. Point marked as OUT_OF_ORDER", pointId);
        return true;
    }

    /**
     * Generate daily report for a station
     */
    @Transactional(readOnly = true)
    public Map<String, Object> generateDailyReport(Long stationId) {

        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime endOfDay = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        List<PhienSac> todaySessions = phienSacRepository
                .findByChargingPointChargingStationIdAndStartTimeBetween(stationId, startOfDay, endOfDay);

        Double totalRevenue = 0.0;
        Double totalEnergy = 0.0;
        int completedSessions = 0;

        for (PhienSac cs : todaySessions) {
            if (cs.getTotalCost() != null) {
                totalRevenue += cs.getTotalCost();
            }
            if (cs.getEnergyConsumed() != null) {
                totalEnergy += cs.getEnergyConsumed();
            }
            if (cs.getStatus() == SessionStatus.COMPLETED) {
                completedSessions++;
            }
        }

        // Trả về Map<String, Object>
        return Map.of(
                "stationId", stationId,
                "date", LocalDateTime.now().toLocalDate().toString(),
                "totalSessions", todaySessions.size(),
                "completedSessions", completedSessions,
                "totalEnergy", totalEnergy,
                "totalRevenue", totalRevenue);
    }
}