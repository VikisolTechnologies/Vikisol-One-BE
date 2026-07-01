package com.vikisol.one.designation.service;

import com.vikisol.one.designation.dto.DesignationRequest;
import com.vikisol.one.designation.dto.DesignationResponse;
import com.vikisol.one.designation.entity.Designation;
import com.vikisol.one.designation.repository.DesignationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DesignationService {

    private final DesignationRepository designationRepository;

    @Transactional
    public DesignationResponse create(DesignationRequest request) {
        Designation designation = Designation.builder()
                .title(request.title())
                .level(request.level())
                .description(request.description())
                .isActive(true)
                .build();
        designation = designationRepository.save(designation);
        return toResponse(designation);
    }

    @Transactional
    public DesignationResponse update(UUID id, DesignationRequest request) {
        Designation designation = designationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Designation not found"));
        designation.setTitle(request.title());
        designation.setLevel(request.level());
        designation.setDescription(request.description());
        designation = designationRepository.save(designation);
        return toResponse(designation);
    }

    public DesignationResponse getById(UUID id) {
        Designation designation = designationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Designation not found"));
        return toResponse(designation);
    }

    public List<DesignationResponse> getAll() {
        return designationRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID id) {
        Designation designation = designationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Designation not found"));
        designation.setActive(false);
        designationRepository.save(designation);
    }

    private DesignationResponse toResponse(Designation designation) {
        return new DesignationResponse(
                designation.getId(),
                designation.getTitle(),
                designation.getLevel(),
                designation.getDescription(),
                designation.isActive()
        );
    }
}
