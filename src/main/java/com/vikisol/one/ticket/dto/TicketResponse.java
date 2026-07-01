package com.vikisol.one.ticket.dto;

import com.vikisol.one.ticket.entity.Ticket;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class TicketResponse {

    private UUID id;
    private String ticketNumber;
    private String title;
    private String description;
    private Ticket.Category category;
    private Ticket.Priority priority;
    private Ticket.Status status;
    private UUID raisedById;
    private String raisedByName;
    private UUID assignedToId;
    private String assignedToName;
    private LocalDateTime resolvedDate;
    private String resolutionComments;
    private LocalDateTime closedDate;
    private List<TicketCommentResponse> comments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
