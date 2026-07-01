package com.vikisol.one.ticket.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class TicketAssignRequest {

    @NotNull
    private UUID assignedToId;
}
