package com.vikisol.one.document.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.document.dto.DocumentResponse;
import com.vikisol.one.document.dto.DocumentUploadRequest;
import com.vikisol.one.document.entity.Document;
import com.vikisol.one.document.repository.DocumentRepository;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    public DocumentResponse uploadDocument(DocumentUploadRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found with id: " + request.employeeId()));

        Document document = Document.builder()
                .employee(employee)
                .title(request.title())
                .type(request.type())
                .fileUrl(request.fileUrl())
                .fileName(request.fileName())
                .fileSize(request.fileSize())
                .mimeType(request.mimeType())
                .description(request.description())
                .build();

        return toResponse(documentRepository.save(document));
    }

    public List<DocumentResponse> getEmployeeDocuments(UUID employeeId) {
        return documentRepository.findByEmployeeId(employeeId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<DocumentResponse> getMyDocuments(UserPrincipal principal) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found for user"));
        return getEmployeeDocuments(employee.getId());
    }

    public DocumentResponse verifyDocument(UUID documentId, UserPrincipal principal) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + documentId));

        Employee verifier = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found for user"));

        document.setVerified(true);
        document.setVerifiedById(verifier.getId());
        document.setVerifiedDate(LocalDateTime.now());

        return toResponse(documentRepository.save(document));
    }

    public List<DocumentResponse> getUnverifiedDocuments() {
        return documentRepository.findByIsVerifiedFalse().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public void deleteDocument(UUID id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        document.setActive(false);
        documentRepository.save(document);
        auditService.record("Document Deleted", document.getTitle(),
                "Belonging to " + document.getEmployee().getFirstName() + " " + document.getEmployee().getLastName());
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    /** Bytes + a human-readable filename, ready to stream back with our own Content-Disposition. */
    public record DownloadedFile(byte[] bytes, String fileName, String mimeType) {}

    // Proxies the file through our own server instead of relying on Cloudinary's fl_attachment
    // transformation flag - that flag requires "Allow dynamic raw file transformations" to be
    // enabled in the Cloudinary account's security settings (disabled by default), and returned
    // a 401 "deny or ACL failure" when tested live against this account. Proxying gives us full,
    // reliable control over the download filename/headers regardless of Cloudinary account
    // config, at the cost of the file passing through our server instead of being served
    // directly from Cloudinary's CDN.
    public DownloadedFile downloadDocument(UUID id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(document.getFileUrl()))
                    .timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Could not fetch stored file (status " + response.statusCode() + ")");
            }
            String fileName = document.getFileName() != null && !document.getFileName().isBlank()
                    ? document.getFileName()
                    : document.getTitle().replaceAll("[^a-zA-Z0-9]+", "_") + ".pdf";
            String mimeType = document.getMimeType() != null ? document.getMimeType() : "application/pdf";
            return new DownloadedFile(response.body(), fileName, mimeType);
        } catch (java.io.IOException | InterruptedException e) {
            throw new RuntimeException("Could not download file: " + e.getMessage(), e);
        }
    }

    private DocumentResponse toResponse(Document document) {
        String employeeName = document.getEmployee().getFirstName() + " " + document.getEmployee().getLastName();

        String verifiedByName = null;
        if (document.getVerifiedById() != null) {
            verifiedByName = employeeRepository.findById(document.getVerifiedById())
                    .map(e -> e.getFirstName() + " " + e.getLastName())
                    .orElse(null);
        }

        // Points at our own /documents/{id}/download proxy (see DocumentService.downloadDocument)
        // rather than the raw Cloudinary storage URL, so every download - not just the one right
        // after generation - gets the human-readable filename instead of a UUID, and so the
        // frontend never needs to know Cloudinary is involved at all.
        String downloadUrl = "/documents/" + document.getId() + "/download";

        return new DocumentResponse(
                document.getId(),
                document.getEmployee().getId(),
                employeeName,
                document.getTitle(),
                document.getType(),
                downloadUrl,
                document.getFileName(),
                document.getFileSize(),
                document.getMimeType(),
                document.getDescription(),
                document.isVerified(),
                document.getVerifiedById(),
                verifiedByName,
                document.getVerifiedDate(),
                document.getExpiryDate(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
