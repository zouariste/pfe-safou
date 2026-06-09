package com.cisternino.domain.interfaces;

import com.cisternino.domain.entities.AdminAuditLogDTO;
import java.util.List;
import java.util.UUID;

public interface AdminAuditLogRepository {
    AdminAuditLogDTO save(AdminAuditLogDTO entry);
    List<AdminAuditLogDTO> findRecent(int limit);
    List<AdminAuditLogDTO> findByAdminId(UUID adminId, int limit);
}
