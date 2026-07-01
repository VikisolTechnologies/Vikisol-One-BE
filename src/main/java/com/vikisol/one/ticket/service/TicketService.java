package com.vikisol.one.ticket.service;

import com.vikisol.one.employee.entity.Employee;
import com.vikisol.one.employee.repository.EmployeeRepository;
import com.vikisol.one.ticket.dto.*;
import com.vikisol.one.ticket.entity.Ticket;
import com.vikisol.one.ticket.entity.TicketComment;
import com.vikisol.one.ticket.repository.TicketCommentRepository;
import com.vikisol.one.ticket.repository.TicketRepository;
import com.vikisol.one.security.service.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional
    public TicketResponse createTicket(TicketRequest request, UserPrincipal principal) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        int nextNumber = ticketRepository.findMaxTicketNumber() + 1;
        String ticketNumber = String.format("TKT-%04d", nextNumber);

        Ticket ticket = Ticket.builder()
                .ticketNumber(ticketNumber)
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .priority(request.getPriority())
                .status(Ticket.Status.OPEN)
                .raisedById(employee.getId())
                .raisedByName(employee.getFirstName() + " " + employee.getLastName())
                .build();

        ticket = ticketRepository.save(ticket);
        return mapToResponse(ticket, null);
    }

    @Transactional
    public TicketResponse assignTicket(UUID ticketId, TicketAssignRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        Employee assignee = employeeRepository.findById(request.getAssignedToId())
                .orElseThrow(() -> new RuntimeException("Assignee not found"));

        ticket.setAssignedToId(assignee.getId());
        ticket.setAssignedToName(assignee.getFirstName() + " " + assignee.getLastName());
        if (ticket.getStatus() == Ticket.Status.OPEN) {
            ticket.setStatus(Ticket.Status.IN_PROGRESS);
        }

        ticket = ticketRepository.save(ticket);
        return mapToResponse(ticket, null);
    }

    @Transactional
    public TicketResponse updateStatus(UUID ticketId, TicketStatusUpdateRequest request) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        ticket.setStatus(request.getStatus());

        if (request.getStatus() == Ticket.Status.RESOLVED) {
            ticket.setResolvedDate(LocalDateTime.now());
            ticket.setResolutionComments(request.getComments());
        } else if (request.getStatus() == Ticket.Status.CLOSED) {
            ticket.setClosedDate(LocalDateTime.now());
        }

        ticket = ticketRepository.save(ticket);
        return mapToResponse(ticket, null);
    }

    @Transactional
    public TicketCommentResponse addComment(UUID ticketId, TicketCommentRequest request, UserPrincipal principal) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        TicketComment comment = TicketComment.builder()
                .ticket(ticket)
                .commentById(employee.getId())
                .commentByName(employee.getFirstName() + " " + employee.getLastName())
                .comment(request.getComment())
                .isInternal(request.isInternal())
                .build();

        comment = ticketCommentRepository.save(comment);
        return mapToCommentResponse(comment);
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getMyTickets(UserPrincipal principal, Pageable pageable) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        return ticketRepository.findByRaisedById(employee.getId(), pageable)
                .map(t -> mapToResponse(t, null));
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getAssignedTickets(UserPrincipal principal, Pageable pageable) {
        Employee employee = employeeRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        return ticketRepository.findByAssignedToId(employee.getId(), pageable)
                .map(t -> mapToResponse(t, null));
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getAllTickets(Pageable pageable) {
        return ticketRepository.findAll(pageable)
                .map(t -> mapToResponse(t, null));
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        List<TicketComment> comments = ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        return mapToResponse(ticket, comments);
    }

    private TicketResponse mapToResponse(Ticket ticket, List<TicketComment> comments) {
        List<TicketCommentResponse> commentResponses = comments != null
                ? comments.stream().map(this::mapToCommentResponse).toList()
                : null;

        return TicketResponse.builder()
                .id(ticket.getId())
                .ticketNumber(ticket.getTicketNumber())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .category(ticket.getCategory())
                .priority(ticket.getPriority())
                .status(ticket.getStatus())
                .raisedById(ticket.getRaisedById())
                .raisedByName(ticket.getRaisedByName())
                .assignedToId(ticket.getAssignedToId())
                .assignedToName(ticket.getAssignedToName())
                .resolvedDate(ticket.getResolvedDate())
                .resolutionComments(ticket.getResolutionComments())
                .closedDate(ticket.getClosedDate())
                .comments(commentResponses)
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    private TicketCommentResponse mapToCommentResponse(TicketComment comment) {
        return TicketCommentResponse.builder()
                .id(comment.getId())
                .commentById(comment.getCommentById())
                .commentByName(comment.getCommentByName())
                .comment(comment.getComment())
                .isInternal(comment.isInternal())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
