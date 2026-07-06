package com.vikisol.one.emailtemplate.repository;

import com.vikisol.one.emailtemplate.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {
    Optional<EmailTemplate> findByTemplateKey(EmailTemplate.TemplateKey templateKey);
}
