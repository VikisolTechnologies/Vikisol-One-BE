package com.vikisol.one.ticket.dto;

import com.vikisol.one.ticket.entity.Ticket;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TicketStatusUpdateRequest {

    @NotNull
    private Ticket.Status status;

    private String comments;
}
