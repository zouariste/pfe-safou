package de.uni_passau.fim.se2.application.service;

import de.uni_passau.fim.se2.domain.entities.AdminAuditLogDTO;
import de.uni_passau.fim.se2.domain.interfaces.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for logging admin actions to admin_audit_log.
 * Separated from AdminService to avoid mixing concerns.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuditService {

    private final AdminAuditLogRepository repository;

    /**
     * Records a successful admin action.
     */
    public void logSuccess(UUID adminId, String action, String ip, String userAgent,
                           Long bytesSent, Long rowsSent) {
        save(adminId, action, ip, userAgent, bytesSent, rowsSent, true, null);
    }

    /**
     * Records a failed admin action with an error message.
     */
    public void logFailure(UUID adminId, String action, String ip, String userAgent,
                           String errorMsg) {
        save(adminId, action, ip, userAgent, null, null, false, errorMsg);
    }

    public List<AdminAuditLogDTO> getRecent(int limit) {
        return repository.findRecent(Math.min(Math.max(limit, 1), 500));
    }

    private void save(UUID adminId, String action, String ip, String userAgent,
                      Long bytesSent, Long rowsSent, boolean success, String errorMsg) {
        try {
            repository.save(AdminAuditLogDTO.builder()
                    .adminId(adminId)
                    .action(action)
                    .ipAddress(ip)
                    .userAgent(userAgent)
                    .bytesSent(bytesSent)
                    .rowsSent(rowsSent)
                    .success(success)
                    .errorMsg(errorMsg)
                    .build());
        } catch (Exception e) {
            // Audit-Logging darf NIE den eigentlichen Request brechen.
            log.error("Failed to write admin audit log entry: action={}, admin={}, error={}",
                    action, adminId, e.getMessage());
        }
    }
}
