package com.vikisol.one.document.service;

import com.vikisol.one.audit.service.AuditService;
import com.vikisol.one.common.service.FileStorageService;
import com.vikisol.one.document.dto.DocumentResponse;
import com.vikisol.one.document.dto.DocumentUploadRequest;
import com.vikisol.one.document.entity.Document;
import com.vikisol.one.document.repository.DocumentRepository;
import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
    private final FileStorageService fileStorageService;

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

    private DocumentResponse toResponse(Document document) {
        String employeeName = document.getEmployee().getFirstName() + " " + document.getEmployee().getLastName();

        String verifiedByName = null;
        if (document.getVerifiedById() != null) {
            verifiedByName = employeeRepository.findById(document.getVerifiedById())
                    .map(e -> e.getFirstName() + " " + e.getLastName())
                    .orElse(null);
        }

        // Wrapped so every download - not just the one right after generation - gets the
        // human-readable filename (e.g. "Offer_Letter_John_Doe_VIK-0007_2026-07-04.pdf")
        // instead of the raw Cloudinary storage URL's UUID. Falls back to the stored fileName,
        // or the document title, for documents uploaded before this existed.
        String downloadName = document.getFileName() != null && !document.getFileName().isBlank()
                ? document.getFileName()
                : document.getTitle() + ".pdf";
        String downloadUrl = fileStorageService.buildDownloadUrl(document.getFileUrl(), downloadName);

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
