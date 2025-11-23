package uth.edu.vn.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uth.edu.vn.entity.Charger;
import uth.edu.vn.entity.PhienSac;
import uth.edu.vn.entity.TramSac;
import uth.edu.vn.repository.PhienSacRepository;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Charging Session Controller
 * Cung cấp thông tin chi tiết phiên sạc cho các luồng staff/payment
 */
@RestController
@RequestMapping("/api/charging")
@PreAuthorize("hasRole('CS_STAFF') or hasRole('ADMIN')")
public class ChargingSessionController {

    @Autowired
    private PhienSacRepository phienSacRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Lấy thông tin chi tiết phiên sạc theo ID
     * GET /api/charging/sessions/{sessionId}
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> getSessionById(@PathVariable Long sessionId) {
        try {
            PhienSac session = phienSacRepository.findById(sessionId).orElse(null);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            Map<String, Object> sessionData = new HashMap<>();
            sessionData.put("sessionId", session.getSessionId());
            sessionData.put("startTime",
                    session.getStartTime() != null ? session.getStartTime().format(DATE_FORMATTER) : null);
            sessionData.put("endTime",
                    session.getEndTime() != null ? session.getEndTime().format(DATE_FORMATTER) : null);

            Double energy = session.getEnergyConsumed() != null ? session.getEnergyConsumed() : 0.0;
            sessionData.put("energyConsumed", energy);

            // User info
            if (session.getUser() != null) {
                sessionData.put("userId", session.getUser().getId());
                String fullName = (session.getUser().getFirstName() != null ? session.getUser().getFirstName() : "")
                        + " "
                        + (session.getUser().getLastName() != null ? session.getUser().getLastName() : "");
                sessionData.put("userName", fullName.trim());
                sessionData.put("userEmail", session.getUser().getEmail());
            }

            // Charger & station info
            Charger charger = session.getChargingPoint();
            if (charger != null) {
                sessionData.put("chargerId", charger.getPointId());
                sessionData.put("chargerName", charger.getPointName());
                sessionData.put("pricePerKwh", charger.getPricePerKwh());

                TramSac station = charger.getChargingStation();
                if (station != null) {
                    sessionData.put("stationId", station.getId());
                    sessionData.put("stationName", station.getName());
                }
            }

            // Tổng chi phí
            Double totalCost;
            if (session.getTotalCost() != null) {
                totalCost = session.getTotalCost();
            } else if (energy != null && charger != null && charger.getPricePerKwh() != null) {
                totalCost = energy * charger.getPricePerKwh();
            } else {
                totalCost = 0.0;
            }
            sessionData.put("totalCost", totalCost);

            return ResponseEntity.ok(sessionData);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
