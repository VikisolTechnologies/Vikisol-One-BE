package com.vikisol.one.common.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.vikisol.one.common.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Stores every uploaded/generated file (resumes, documents, offer-letter PDFs, profile photos) in
// Cloudinary rather than local disk. Railway's local filesystem is ephemeral - wiped on every
// redeploy/restart - which is unacceptable for HR documents (offer letters, PAN/Aadhaar copies,
// payslips). Cloudinary gives durable, CDN-backed storage with no infrastructure to manage.
//
// Folder layout is centralized here - callers only ever provide (module, entityId, documentType);
// no other class in the app constructs a Cloudinary path by hand. See FileModule for the module
// list and resolveFolder() below for exactly how each one maps to a path, e.g.:
//   EMPLOYEE + "VIK-0008" + "identity"   -> vikisol-one/employees/VIK-0008/identity/
//   PAYROLL  + "VIK-0008" + "payslips"   -> vikisol-one/payroll/payslips/2026/07/
//   RECRUITMENT_CANDIDATE + "CAND-0003" + "resumes" -> vikisol-one/recruitment/candidates/CAND-0003/resumes/
@Service
@Slf4j
public class FileStorageService {

    private Cloudinary cloudinary;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    // All app uploads live under this root folder in the Cloudinary media library, separate from
    // anything else in the account (e.g. the logo, uploaded directly by hand outside the app).
    @Value("${cloudinary.root-folder:vikisol-one}")
    private String rootFolder;

    // No file-type restriction existed before this - any authenticated user could upload any
    // file type (executables, scripts, etc). This allowlist covers every use case in the app
    // (profile photos, resumes, documents, assets, generated PDFs).
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".png", ".jpg", ".jpeg", ".gif", ".webp"
    );
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp");

    @PostConstruct
    public void init() {
        if (cloudName == null || cloudName.isBlank()) {
            throw new IllegalStateException("CLOUDINARY_CLOUD_NAME is not configured");
        }
        cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
    }

    public String storeFile(MultipartFile file, FileModule module, String entityId, String documentType) {
        if (file.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        String originalFileName = file.getOriginalFilename();
        String extension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf(".")).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("File type not allowed. Supported: PDF, Word, Excel, and common image formats.");
        }
        try {
            return upload(file.getBytes(), module, entityId, documentType, extension);
        } catch (IOException e) {
            throw new RuntimeException("Could not store file", e);
        }
    }

    // For server-generated files (e.g. the offer letter PDF) where there's no incoming MultipartFile.
    public String storeBytes(byte[] data, String fileName, FileModule module, String entityId, String documentType) {
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".")).toLowerCase() : "";
        return upload(data, module, entityId, documentType, extension);
    }

    @SuppressWarnings("unchecked")
    private String upload(byte[] data, FileModule module, String entityId, String documentType, String extension) {
        try {
            boolean isImage = IMAGE_EXTENSIONS.contains(extension);
            String folder = resolveFolder(module, entityId, documentType);
            Map<String, Object> options = ObjectUtils.asMap(
                    "folder", folder,
                    "public_id", UUID.randomUUID().toString(),
                    "resource_type", isImage ? "image" : "raw"
            );
            Map<String, Object> result = cloudinary.uploader().upload(data, options);
            return (String) result.get("secure_url");
        } catch (IOException e) {
            throw new RuntimeException("Could not upload file to Cloudinary", e);
        }
    }

    // The single place all folder-path logic lives - see FileModule for what each case means.
    // entityId and documentType are sanitized so neither can be used for path traversal or to
    // create arbitrary folders outside this hierarchy (e.g. "../../etc" or "documents/../../x").
    private String resolveFolder(FileModule module, String entityId, String documentType) {
        String safeEntityId = sanitize(entityId);
        String safeDocType = sanitize(documentType);
        LocalDate now = LocalDate.now();

        return switch (module) {
            case RECRUITMENT_CANDIDATE -> "%s/recruitment/candidates/%s/%s".formatted(rootFolder, safeEntityId, safeDocType);
            case EMPLOYEE -> "%s/employees/%s/%s".formatted(rootFolder, safeEntityId, safeDocType);
            case PAYROLL -> "%s/payroll/%s/%04d/%02d".formatted(rootFolder, safeDocType, now.getYear(), now.getMonthValue());
            case ATTENDANCE -> "%s/attendance/%s".formatted(rootFolder, safeDocType);
            case LEAVE -> "%s/leave/%s".formatted(rootFolder, safeDocType);
            case ASSET -> "%s/assets/%s".formatted(rootFolder, safeDocType);
            case PROJECT -> "%s/projects/%s".formatted(rootFolder, safeDocType);
            case TICKET -> "%s/tickets/%s".formatted(rootFolder, safeDocType);
            case COMPANY -> "%s/company/%s".formatted(rootFolder, safeDocType);
        };
    }

    // Only letters, digits, hyphens and underscores survive - blocks path traversal ("../"),
    // absolute paths, and null-byte tricks from ever reaching Cloudinary's folder parameter.
    private String sanitize(String value) {
        if (value == null || value.isBlank()) return "unspecified";
        String cleaned = value.replaceAll("[^a-zA-Z0-9_-]", "");
        return cleaned.isBlank() ? "unspecified" : cleaned;
    }

    public void deleteFile(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains("cloudinary.com")) return;
        try {
            // Cloudinary public_id is the path after /upload/v<version>/ minus the extension,
            // e.g. https://res.cloudinary.com/<cloud>/raw/upload/v123/vikisol-one/employees/VIK-0008/documents/<uuid>.pdf
            //   -> public_id = vikisol-one/employees/VIK-0008/documents/<uuid>
            String afterUpload = fileUrl.substring(fileUrl.indexOf("/upload/") + "/upload/".length());
            String withoutVersion = afterUpload.replaceFirst("^v\\d+/", "");
            String publicId = withoutVersion.contains(".")
                    ? withoutVersion.substring(0, withoutVersion.lastIndexOf("."))
                    : withoutVersion;
            boolean isImage = fileUrl.contains("/image/upload/");
            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", isImage ? "image" : "raw"));
        } catch (Exception e) {
            log.warn("Could not delete file from Cloudinary: {}", fileUrl, e);
        }
    }
}
