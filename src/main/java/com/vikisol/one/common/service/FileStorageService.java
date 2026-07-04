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
            // Raw resources (PDFs, docs) need the extension baked into public_id - Cloudinary
            // derives the served file's extension from it for resource_type=raw, which is why
            // every generated document previously downloaded with no extension at all (a real
            // bug: browsers couldn't tell it was a PDF).
            String publicId = isImage ? UUID.randomUUID().toString() : UUID.randomUUID() + extension;
            // Non-image files upload as Cloudinary's "authenticated" delivery type, not the
            // default public "upload" type - confirmed live that a recognized-format raw file
            // (a real PDF) uploaded as type=upload gets a 401 "deny or ACL failure" on ANY fetch,
            // signed or not, because of this account's "Restricted media types" security setting.
            // type=authenticated is a structurally different, non-public asset from the start,
            // so a signed URL (built in buildSignedFetchUrl) reliably works regardless of that
            // setting. Images stay on the default public type since nothing in the app fetches
            // them through our own signed proxy - they're displayed directly via <img src>.
            Map<String, Object> options = ObjectUtils.asMap(
                    "folder", folder,
                    "public_id", publicId,
                    "resource_type", isImage ? "image" : "raw",
                    "type", isImage ? "upload" : "authenticated"
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
            boolean isImage = fileUrl.contains("/image/upload/");
            String publicId = parsePublicId(fileUrl, isImage);
            Map<String, Object> options = isImage
                    ? ObjectUtils.asMap("resource_type", "image")
                    : ObjectUtils.asMap("resource_type", "raw", "type", "authenticated");
            cloudinary.uploader().destroy(publicId, options);
        } catch (Exception e) {
            log.warn("Could not delete file from Cloudinary: {}", fileUrl, e);
        }
    }

    // Non-image files are uploaded as Cloudinary's "authenticated" delivery type (see upload()) -
    // a structurally non-public asset from the start, so a signed URL of this type reliably
    // works regardless of the account's "Restricted media types" setting. (A signed URL for a
    // plain public type=upload asset was tried first and still returned 401 live - that setting
    // blocks recognized-format raw files at the public-delivery level regardless of signing.)
    public String buildSignedFetchUrl(String fileUrl) {
        boolean isImage = fileUrl.contains("/image/upload/");
        String publicId = parsePublicId(fileUrl, isImage);
        var urlBuilder = cloudinary.url().resourceType(isImage ? "image" : "raw").signed(true);
        if (!isImage) urlBuilder = urlBuilder.type("authenticated");
        return urlBuilder.generate(publicId);
    }

    // Cloudinary separates public_id from format for images, but for resource_type=raw the
    // extension is part of the public_id itself (that's what makes raw downloads carry a real
    // .pdf extension - see upload() above) - so only images should have their extension stripped.
    // Authenticated-type delivery URLs also embed a "s--SIGNATURE--/" segment before the version
    // that plain "upload" type URLs don't have - strip it too if present.
    private String parsePublicId(String fileUrl, boolean isImage) {
        String deliveryTypeMarker = fileUrl.contains("/authenticated/") ? "/authenticated/" : "/upload/";
        String afterDeliveryType = fileUrl.substring(fileUrl.indexOf(deliveryTypeMarker) + deliveryTypeMarker.length());
        String withoutSignature = afterDeliveryType.replaceFirst("^s--[A-Za-z0-9_-]+--/", "");
        String withoutVersion = withoutSignature.replaceFirst("^v\\d+/", "");
        return (isImage && withoutVersion.contains("."))
                ? withoutVersion.substring(0, withoutVersion.lastIndexOf("."))
                : withoutVersion;
    }
}
