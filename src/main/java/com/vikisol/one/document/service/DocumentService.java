package com.vikisol.one.document.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.common.exception.BadRequestException;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditService auditService;

    // Document types Finance may see for someone else's record - only what payroll actually
    // needs (tax/identity for TDS filing, payslips/salary docs), never education/personal proofs.
    private static final Set<Document.DocumentType> FINANCE_VISIBLE_TYPES = EnumSet.of(
            Document.DocumentType.TAX_FORM, Document.DocumentType.ID_PROOF, Document.DocumentType.PAYSLIP,
            Document.DocumentType.SALARY_CERTIFICATE, Document.DocumentType.SALARY_REVISION_LETTER);

    // Types a Manager must NOT see for a report unless it's their own record - identity/financial
    // documents (PAN/Aadhaar-class ID proof, address proof, tax forms, salary-related letters),
    // per "managers should not automatically see identity documents... or bank account details".
    private static final Set<Document.DocumentType> MANAGER_HIDDEN_TYPES = EnumSet.of(
            Document.DocumentType.ID_PROOF, Document.DocumentType.ADDRESS_PROOF, Document.DocumentType.TAX_FORM,
            Document.DocumentType.PAYSLIP, Document.DocumentType.SALARY_CERTIFICATE, Document.DocumentType.SALARY_REVISION_LETTER);

    private boolean hasRole(UserPrincipal principal, String role) {
        return principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private boolean isSelf(UUID employeeId, UserPrincipal principal) {
        return employeeRepository.findById(employeeId)
                .map(e -> e.getUser() != null && e.getUser().getId().equals(principal.getId()))
                .orElse(false);
    }

    // Trusted internal callers only (system-generated offer/experience/relieving letters etc.) -
    // no principal to check because these run as a server-side action already gated by their own
    // endpoint's @PreAuthorize, not a direct user-supplied upload.
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

    // Public-facing path (the /documents POST endpoint) - a real user is uploading a document
    // themselves, so it must be permission-checked: self, or CEO/HR/Admin uploading on someone's
    // behalf. Previously this endpoint had no restriction at all - any authenticated role,
    // including a plain EMPLOYEE, could upload a document tagged to any other employee's record.
    public DocumentResponse uploadDocument(DocumentUploadRequest request, UserPrincipal principal) {
        boolean privileged = hasRole(principal, "CEO") || hasRole(principal, "HR_MANAGER") || hasRole(principal, "ADMIN");
        if (!privileged && !isSelf(request.employeeId(), principal)) {
            throw new BadRequestException("You can only upload documents to your own profile");
        }
        return uploadDocument(request);
    }

    // Enforces who may view whose documents, and which document TYPES they may see - frontend
    // hiding is UX only (per this app's established security posture); this is the real boundary,
    // since this endpoint is what actually returns PAN/Aadhaar/bank-proof file links.
    public List<DocumentResponse> getEmployeeDocuments(UUID employeeId, UserPrincipal principal) {
        boolean self = isSelf(employeeId, principal);
        boolean fullAccess = hasRole(principal, "CEO") || hasRole(principal, "HR_MANAGER") || hasRole(principal, "ADMIN");
        boolean isFinance = hasRole(principal, "FINANCE");
        boolean isManager = hasRole(principal, "MANAGER");

        if (!self && !fullAccess && !isFinance && !isManager) {
            throw new BadRequestException("You do not have permission to view this employee's documents");
        }

        List<Document> documents = documentRepository.findByEmployeeId(employeeId);
        if (self || fullAccess) {
            return documents.stream().map(this::toResponse).collect(Collectors.toList());
        }
        if (isFinance) {
            return documents.stream().filter(d -> FINANCE_VISIBLE_TYPES.contains(d.getType())).map(this::toResponse).collect(Collectors.toList());
        }
        // Manager: everything except the identity/financial types
        return documents.stream().filter(d -> !MANAGER_HIDDEN_TYPES.contains(d.getType())).map(this::toResponse).collect(Collectors.toList());
    }

    // Most-recently-added documents for this employee, capped at `limit` - used by the dashboard's
    // "recent documents" widget. No extra permission check here since it's only ever called for
    // the dashboard owner's own employeeId (the controller already enforced self-or-privileged).
    public List<DocumentResponse> getRecentDocuments(UUID employeeId, int limit) {
        return documentRepository.findByEmployeeId(employeeId).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(limit)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<DocumentResponse> getMyDocuments(UserPrincipal principal) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found for user"));
        return documentRepository.findByEmployeeId(employee.getId()).stream().map(this::toResponse).collect(Collectors.toList());
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

    // Proxies the file through our own server instead of a direct Cloudinary CDN link, so we
    // control the Content-Disposition/filename ourselves. NOTE: PDF downloads currently fail
    // with a 401 from Cloudinary ("deny or ACL failure") - this account's "Restricted media
    // types" security setting blocks delivery of recognized PDF/ZIP files, and unlike most
    // Cloudinary restrictions this one is NOT bypassable from our side: fl_attachment, a
    // correctly-signed type=authenticated URL (verified with the exact right version), and HTTP
    // Basic Auth with the account's own API key/secret were all tried live and all rejected
    // identically. Per Cloudinary's own docs this setting must be disabled in the account
    // dashboard (Settings -> Security -> Restricted media types) - once that's done, this plain
    // fetch will work with no further code changes.
    public DownloadedFile downloadDocument(UUID id, UserPrincipal principal) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
        // This proxy returns the raw file bytes (PAN/Aadhaar/bank-proof scans included) - it must
        // enforce the exact same self/role/type boundary as getEmployeeDocuments, which previously
        // only gated the metadata *listing*; the actual byte-returning download endpoint had no
        // check at all, so any authenticated employee could download any other employee's ID
        // proof or salary documents just by guessing/discovering a document UUID.
        UUID employeeId = document.getEmployee().getId();
        boolean self = isSelf(employeeId, principal);
        boolean fullAccess = hasRole(principal, "CEO") || hasRole(principal, "HR_MANAGER") || hasRole(principal, "ADMIN");
        boolean isFinance = hasRole(principal, "FINANCE") && FINANCE_VISIBLE_TYPES.contains(document.getType());
        boolean isManager = hasRole(principal, "MANAGER") && !MANAGER_HIDDEN_TYPES.contains(document.getType());
        if (!self && !fullAccess && !isFinance && !isManager) {
            throw new BadRequestException("You do not have permission to download this document");
        }
        // Real bug, confirmed live: for a document whose stored fileUrl is null or missing a
        // scheme (a relative path from a legacy/broken upload, not a real Cloudinary URL),
        // URI.create() throws IllegalArgumentException("URI with undefined scheme") - previously
        // uncaught here, so that raw Java exception message surfaced directly in the user's toast
        // instead of a clear, actionable error.
        String fileUrl = document.getFileUrl();
        if (fileUrl == null || fileUrl.isBlank() || !(fileUrl.startsWith("http://") || fileUrl.startsWith("https://"))) {
            throw new com.vikisol.one.common.exception.BadRequestException(
                    "This document's file is missing or was never uploaded correctly. Please re-upload it.");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(fileUrl))
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
