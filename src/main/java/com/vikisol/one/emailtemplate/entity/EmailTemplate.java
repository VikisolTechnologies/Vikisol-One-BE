package com.vikisol.one.emailtemplate.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// One row per authentication email (subject + body with {{placeholder}} tokens). EmailService
// renders these instead of building HTML inline, so CEO/Admin can edit wording without a
// redeploy. Body is wrapped in the shared brand shell (logo/footer) at send time, same as before -
// only the inner content is template-driven.
@Entity
@Table(name = "email_templates")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EmailTemplate extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private TemplateKey templateKey;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String bodyHtml;

    private String updatedByEmail;

    public enum TemplateKey {
        ACCOUNT_ACTIVATION, PASSWORD_RESET, PASSWORD_CHANGED, ACCOUNT_LOCKED, ACCOUNT_UNLOCKED, WELCOME_EMAIL
    }
}
