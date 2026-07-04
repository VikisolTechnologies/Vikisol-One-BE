package com.vikisol.one.doctemplate.service;

import com.vikisol.one.doctemplate.dto.TemplateVariableRequest;
import com.vikisol.one.doctemplate.entity.TemplateVariable;
import com.vikisol.one.doctemplate.repository.TemplateVariableRepository;
import com.vikisol.one.document.entity.Document;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateVariableService {

    private final TemplateVariableRepository variableRepository;

    public List<TemplateVariable> listAvailableFor(Document.DocumentType type) {
        return variableRepository.findByDocumentTypeIsNullOrDocumentType(type);
    }

    public List<TemplateVariable> listAll() {
        return variableRepository.findAll();
    }

    public TemplateVariable create(TemplateVariableRequest request) {
        if (variableRepository.existsByKey(request.key())) {
            throw new RuntimeException("A variable with key {{" + request.key() + "}} already exists");
        }
        return variableRepository.save(TemplateVariable.builder()
                .key(request.key())
                .label(request.label())
                .description(request.description())
                .documentType(request.documentType())
                .build());
    }
}
