package de.uni_passau.fim.se2.domain.interfaces;

import de.uni_passau.fim.se2.domain.entities.AdminAuditLogDTO;
import java.util.List;
import java.util.UUID;

public interface AdminAuditLogRepository {
    AdminAuditLogDTO save(AdminAuditLogDTO entry);
    List<AdminAuditLogDTO> findRecent(int limit);
    List<AdminAuditLogDTO> findByAdminId(UUID adminId, int limit);
}
