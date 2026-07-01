package com.vikisol.one.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TicketCommentRequest {

    @NotBlank
    private String comment;

    private boolean isInternal = false;
}
