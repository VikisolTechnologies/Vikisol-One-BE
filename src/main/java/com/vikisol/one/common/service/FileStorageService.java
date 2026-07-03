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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Stores every uploaded/generated file (resumes, documents, offer-letter PDFs, profile photos) in
// Cloudinary rather than local disk. Railway's local filesystem is ephemeral - wiped on every
// redeploy/restart - which is unacceptable for HR documents (offer letters, PAN/Aadhaar copies,
// payslips). Cloudinary gives durable, CDN-backed storage with no infrastructure to manage.
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

    // No file-type restriction existed before this - any authenticated user could upload any
    // file type (executables, scripts, etc). This allowlist covers every use case in the app
    // (profile photos, resumes, documents, assets, generated PDFs).
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".png", ".jpg", ".jpeg", ".gif", ".webp"
    );

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

    // subDirectory becomes a Cloudinary folder (e.g. "documents", "resumes") so files stay
    // organized the same way they were on local disk.
    public String storeFile(MultipartFile file, String subDirectory) {
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
            return upload(file.getBytes(), subDirectory, extension);
        } catch (IOException e) {
            throw new RuntimeException("Could not store file", e);
        }
    }

    // For server-generated files (e.g. the offer letter PDF) where there's no incoming MultipartFile.
    public String storeBytes(byte[] data, String subDirectory, String fileName) {
        String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".")).toLowerCase() : "";
        return upload(data, subDirectory, extension);
    }

    @SuppressWarnings("unchecked")
    private String upload(byte[] data, String subDirectory, String extension) {
        try {
            // raw for PDFs/Office docs, image for photos - Cloudinary needs the right resource_type
            // to serve non-image files correctly (images get optimization/transformation for free).
            boolean isImage = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp").contains(extension);
            Map<String, Object> options = ObjectUtils.asMap(
                    "folder", subDirectory,
                    "public_id", UUID.randomUUID().toString(),
                    "resource_type", isImage ? "image" : "raw"
            );
            Map<String, Object> result = cloudinary.uploader().upload(data, options);
            return (String) result.get("secure_url");
        } catch (IOException e) {
            throw new RuntimeException("Could not upload file to Cloudinary", e);
        }
    }

    public void deleteFile(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains("cloudinary.com")) return;
        try {
            // Cloudinary public_id is the path after /upload/v<version>/ minus the extension,
            // e.g. https://res.cloudinary.com/<cloud>/raw/upload/v123/documents/<uuid>.pdf
            //   -> public_id = documents/<uuid>
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
