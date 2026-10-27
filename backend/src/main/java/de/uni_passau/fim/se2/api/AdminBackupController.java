/*
 * Copyright (C) 2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.se2.api;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.uni_passau.fim.se2.api.dto.AdminAuditLogResponseDTO;
import de.uni_passau.fim.se2.application.service.AdminAuditService;
import de.uni_passau.fim.se2.application.service.BackupService;
import de.uni_passau.fim.se2.application.service.BackupService.BackupBusyException;
import de.uni_passau.fim.se2.application.service.ResearchExportService;
import de.uni_passau.fim.se2.domain.entities.AdminAuditLogDTO;
import de.uni_passau.fim.se2.domain.interfaces.DatabaseDumpExecutor.DumpException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Admin-only DB-access endpoints: full backup + research export + audit log viewer. All endpoints under /api/admin/**
 * require ADMIN role (enforced in SecurityConfig).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminBackupController {

    private final BackupService backupService;
    private final ResearchExportService researchExportService;
    private final AdminAuditService auditService;

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Streams a full pg_dump backup to the client as a file download. Mutex-locked: only one backup runs at a time
     * across all admins.
     *
     * @param adminId the admin requesting the backup, recorded in the audit log
     * @param req     the request, for the client address and user agent in the audit log
     * @param resp    the response the dump is streamed into
     */
    @PostMapping("/backup")
    public void downloadFullBackup(
        @AuthenticationPrincipal UUID adminId,
        HttpServletRequest req,
        HttpServletResponse resp
    ) throws IOException, DumpException, InterruptedException {
        String filename = "codeowl-backup-" + LocalDateTime.now().format(FILE_TS) + ".dump";

        resp.setStatus(HttpStatus.OK.value());
        resp.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        // No Content-Length header — we stream chunked

        try {
            backupService.streamFullBackup(
                resp.getOutputStream(),
                adminId,
                req.getRemoteAddr(),
                req.getHeader("User-Agent")
            );
        }
        catch (BackupBusyException e) {
            log.warn("Backup busy for admin={}", adminId);
            throw e;
        }
        catch (DumpException e) {
            log.error("pg_dump failed for admin={}: {}", adminId, e.getMessage());
            throw e;
        }
    }

    /**
     * Streams a ZIP of research-relevant CSV exports. Pseudonymized — no PII columns included.
     *
     * @param adminId the admin requesting the export, recorded in the audit log
     * @param req     the request, for the client address and user agent in the audit log
     * @param resp    the response the ZIP is streamed into
     */
    @GetMapping("/research-export")
    public void downloadResearchExport(
        @AuthenticationPrincipal UUID adminId,
        HttpServletRequest req,
        HttpServletResponse resp
    ) throws IOException {
        String filename = "codeowl-research-" + LocalDateTime.now().format(FILE_TS) + ".zip";
        resp.setStatus(HttpStatus.OK.value());
        resp.setContentType("application/zip");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        String adminShort = adminId != null ? adminId.toString().substring(0, 8) : "unknown";
        try {
            long rows = researchExportService.streamExport(resp.getOutputStream(), adminShort);
            auditService.logSuccess(
                adminId, AdminAuditLogDTO.Action.RESEARCH_EXPORT,
                req.getRemoteAddr(), req.getHeader("User-Agent"), null, rows
            );
        }
        catch (Exception e) {
            log.error("Research export failed for admin={}: {}", adminId, e.getMessage());
            auditService.logFailure(
                adminId, AdminAuditLogDTO.Action.RESEARCH_EXPORT,
                req.getRemoteAddr(), req.getHeader("User-Agent"), e.getMessage()
            );
            throw e;
        }
    }

    /**
     * Returns the most recent admin audit log entries (all admins, all actions).
     *
     * @param limit the maximum number of entries to return
     * @return the newest audit log entries, newest first
     */
    @GetMapping("/audit-log")
    public ResponseEntity<List<AdminAuditLogResponseDTO>> getAuditLog(
        @RequestParam(defaultValue = "100") int limit
    ) {
        List<AdminAuditLogDTO> entries = auditService.getRecent(limit);
        return ResponseEntity.ok(entries.stream().map(this::toResponseDTO).toList());
    }

    private AdminAuditLogResponseDTO toResponseDTO(AdminAuditLogDTO e) {
        return AdminAuditLogResponseDTO.builder()
            .id(e.getId())
            .adminId(e.getAdminId() != null ? e.getAdminId().toString() : null)
            .action(e.getAction())
            .ipAddress(e.getIpAddress())
            .bytesSent(e.getBytesSent())
            .rowsSent(e.getRowsSent())
            .success(e.isSuccess())
            .errorMsg(e.getErrorMsg())
            .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
            .build();
    }
}
