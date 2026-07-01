package com.vikisol.one.ticket.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TicketCommentResponse {

    private UUID id;
    private UUID commentById;
    private String commentByName;
    private String comment;
    private boolean isInternal;
    private LocalDateTime createdAt;
}
