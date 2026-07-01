package com.vikisol.one.ticket.controller;

import com.vikisol.one.common.dto.ApiResponse;
import com.vikisol.one.common.dto.PagedResponse;
import com.vikisol.one.security.service.UserPrincipal;
import com.vikisol.one.ticket.dto.*;
import com.vikisol.one.ticket.service.TicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping
    public ResponseEntity<ApiResponse<TicketResponse>> raiseTicket(
            @Valid @RequestBody TicketRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TicketResponse response = ticketService.createTicket(request, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Ticket raised successfully", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<PagedResponse<TicketResponse>>> getAllTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<TicketResponse> tickets = ticketService.getAllTickets(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        PagedResponse<TicketResponse> pagedResponse = new PagedResponse<>(
                tickets.getContent(), tickets.getNumber(), tickets.getSize(),
                tickets.getTotalElements(), tickets.getTotalPages(), tickets.isLast());
        return ResponseEntity.ok(new ApiResponse<>(true, "Tickets fetched", pagedResponse));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PagedResponse<TicketResponse>>> getMyTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<TicketResponse> tickets = ticketService.getMyTickets(principal,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        PagedResponse<TicketResponse> pagedResponse = new PagedResponse<>(
                tickets.getContent(), tickets.getNumber(), tickets.getSize(),
                tickets.getTotalElements(), tickets.getTotalPages(), tickets.isLast());
        return ResponseEntity.ok(new ApiResponse<>(true, "My tickets fetched", pagedResponse));
    }

    @GetMapping("/assigned")
    public ResponseEntity<ApiResponse<PagedResponse<TicketResponse>>> getAssignedTickets(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<TicketResponse> tickets = ticketService.getAssignedTickets(principal,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        PagedResponse<TicketResponse> pagedResponse = new PagedResponse<>(
                tickets.getContent(), tickets.getNumber(), tickets.getSize(),
                tickets.getTotalElements(), tickets.getTotalPages(), tickets.isLast());
        return ResponseEntity.ok(new ApiResponse<>(true, "Assigned tickets fetched", pagedResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicketById(@PathVariable UUID id) {
        TicketResponse response = ticketService.getTicketById(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Ticket details fetched", response));
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR_MANAGER')")
    public ResponseEntity<ApiResponse<TicketResponse>> assignTicket(
            @PathVariable UUID id,
            @Valid @RequestBody TicketAssignRequest request) {
        TicketResponse response = ticketService.assignTicket(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Ticket assigned successfully", response));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<TicketResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody TicketStatusUpdateRequest request) {
        TicketResponse response = ticketService.updateStatus(id, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Ticket status updated", response));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<TicketCommentResponse>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody TicketCommentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        TicketCommentResponse response = ticketService.addComment(id, request, principal);
        return ResponseEntity.ok(new ApiResponse<>(true, "Comment added successfully", response));
    }
}
