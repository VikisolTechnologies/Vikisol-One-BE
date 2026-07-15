package com.vikisol.one.backup.controller;

import com.vikisol.one.backup.service.BackupService;
import com.vikisol.one.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

// CEO/Admin only, and deliberately scoped to configuration data (see BackupData) - not raw
// employee/payroll records, which are far too risky to let a self-service UI button overwrite on
// a live production system.
@RestController
@RequestMapping("/admin/backup")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CEO','ADMIN')")
public class BackupController {

    private final BackupService backupService;

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportBackup() throws Exception {
        byte[] json = backupService.exportBackup();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"VikisolOne_Backup_" + LocalDate.now() + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @PostMapping(value = "/restore", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> restoreBackup(@RequestParam("file") MultipartFile file) throws Exception {
        backupService.restoreBackup(file.getBytes());
        return ResponseEntity.ok(new ApiResponse<>(true, "Backup restored", null));
    }
}
