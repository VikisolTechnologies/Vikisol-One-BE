package com.vikisol.one.integration.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// One row per integration type (Microsoft 365, Google Workspace, SMTP, Slack, Zoom, DocuSign,
// WhatsApp Business) - config is a JSON blob rather than typed columns since each provider needs
// a different shape (Microsoft 365 needs tenantId/clientId/clientSecret; SMTP needs host/port/
// username/password; Slack needs a bot token, etc.) and new providers shouldn't need a migration.
@Entity
@Table(name = "integration_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IntegrationSettings extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private IntegrationType type;

    @Builder.Default
    private boolean enabled = false;

    // Encrypted JSON (see CryptoUtil) - contains provider-specific fields, some of which are
    // themselves secrets (client secret, SMTP password, bot token) so the whole blob is encrypted
    // at rest rather than picking out which individual keys are "sensitive".
    @Column(columnDefinition = "TEXT")
    private String configJson;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ConnectionStatus status = ConnectionStatus.NOT_CONFIGURED;

    private java.time.LocalDateTime lastTestedAt;
    private String lastError;

    public enum IntegrationType {
        MICROSOFT_365, GOOGLE_WORKSPACE, SMTP, SLACK, ZOOM, DOCUSIGN, WHATSAPP
    }

    public enum ConnectionStatus {
        NOT_CONFIGURED, CONNECTED, ERROR
    }
}
