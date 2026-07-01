package com.vikisol.one.resource.service;

import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.resource.dto.ResourceRequest;
import com.vikisol.one.resource.dto.ResourceResponse;
import com.vikisol.one.resource.entity.Resource;
import com.vikisol.one.resource.repository.ResourceRepository;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final EmployeeRepository employeeRepository;

    public List<ResourceResponse> getAllResources() {
        return resourceRepository.findByIsActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ResourceResponse getResourceById(UUID id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Resource not found with id: " + id));
        return toResponse(resource);
    }

    public ResourceResponse createResource(ResourceRequest request, UserPrincipal principal) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found for user"));

        Resource resource = Resource.builder()
                .title(request.title())
                .description(request.description())
                .category(request.category())
                .fileUrl(request.fileUrl())
                .externalLink(request.externalLink())
                .isPublic(request.isPublic())
                .uploadedById(employee.getId())
                .build();

        return toResponse(resourceRepository.save(resource));
    }

    public ResourceResponse updateResource(UUID id, ResourceRequest request) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Resource not found with id: " + id));

        resource.setTitle(request.title());
        resource.setDescription(request.description());
        resource.setCategory(request.category());
        resource.setFileUrl(request.fileUrl());
        resource.setExternalLink(request.externalLink());
        resource.setPublic(request.isPublic());

        return toResponse(resourceRepository.save(resource));
    }

    public void deleteResource(UUID id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Resource not found with id: " + id));
        resource.setActive(false);
        resourceRepository.save(resource);
    }

    public List<ResourceResponse> getResourcesByCategory(Resource.ResourceCategory category) {
        return resourceRepository.findByCategory(category).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<ResourceResponse> searchResources(String query) {
        return resourceRepository.findByTitleContainingIgnoreCase(query).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private ResourceResponse toResponse(Resource resource) {
        String uploadedByName = null;
        if (resource.getUploadedById() != null) {
            uploadedByName = employeeRepository.findById(resource.getUploadedById())
                    .map(e -> e.getFirstName() + " " + e.getLastName())
                    .orElse(null);
        }

        return new ResourceResponse(
                resource.getId(),
                resource.getTitle(),
                resource.getDescription(),
                resource.getCategory(),
                resource.getFileUrl(),
                resource.getExternalLink(),
                resource.isPublic(),
                resource.getUploadedById(),
                uploadedByName,
                resource.getCreatedAt(),
                resource.getUpdatedAt()
        );
    }
}
