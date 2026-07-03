package com.vikisol.one.announcement.service;

import com.vikisol.one.announcement.dto.AnnouncementRequest;
import com.vikisol.one.announcement.dto.AnnouncementResponse;
import com.vikisol.one.announcement.entity.Announcement;
import com.vikisol.one.announcement.repository.AnnouncementRepository;
import com.vikisol.one.security.service.UserPrincipal;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public AnnouncementResponse create(AnnouncementRequest request, UserPrincipal principal) {
        Announcement announcement = Announcement.builder()
                .title(request.title())
                .message(request.message())
                .priority(request.priority() != null ? request.priority() : Announcement.Priority.NORMAL)
                .postedById(principal.getId().toString())
                .postedByName(principal.getFirstName() + " " + principal.getLastName())
                .isActive(true)
                .build();
        return toResponse(announcementRepository.save(announcement));
    }

    public AnnouncementResponse update(UUID id, AnnouncementRequest request) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Announcement not found"));
        announcement.setTitle(request.title());
        announcement.setMessage(request.message());
        if (request.priority() != null) announcement.setPriority(request.priority());
        return toResponse(announcementRepository.save(announcement));
    }

    public void delete(UUID id) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Announcement not found"));
        announcement.setActive(false);
        announcementRepository.save(announcement);
    }

    @Transactional(readOnly = true)
    public List<AnnouncementResponse> getAll() {
        return announcementRepository.findByIsActiveTrueOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    private AnnouncementResponse toResponse(Announcement a) {
        return new AnnouncementResponse(a.getId(), a.getTitle(), a.getMessage(), a.getPriority(), a.getPostedByName(), a.getCreatedAt());
    }
}
