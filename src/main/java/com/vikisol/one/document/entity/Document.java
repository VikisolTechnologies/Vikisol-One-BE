package com.vikisol.one.document.entity;

import com.vikisol.one.common.entity.BaseEntity;
import com.vikisol.one.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Document extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type;

    @Column(nullable = false)
    private String fileUrl;

    private String fileName;

    private long fileSize;

    private String mimeType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    private boolean isVerified = false;

    private UUID verifiedById;

    private LocalDateTime verifiedDate;

    private LocalDate expiryDate;

    @Builder.Default
    private boolean isActive = true;

    public enum DocumentType {
        OFFER_LETTER, APPOINTMENT_LETTER, ID_PROOF, ADDRESS_PROOF,
        EDUCATION_CERTIFICATE, EXPERIENCE_LETTER, PAYSLIP, TAX_FORM, OTHER,
        RELIEVING_LETTER, SALARY_CERTIFICATE,
        JOINING_LETTER, CONFIRMATION_LETTER, PROMOTION_LETTER, SALARY_REVISION_LETTER,
        RESIGNATION_ACCEPTANCE_LETTER, TERMINATION_LETTER, WARNING_LETTER, INTERNSHIP_LETTER,
        CONTRACT_LETTER, LEAVE_APPROVAL_LETTER, LEAVE_REJECTION_LETTER, EMPLOYMENT_VERIFICATION_LETTER
    }
}
