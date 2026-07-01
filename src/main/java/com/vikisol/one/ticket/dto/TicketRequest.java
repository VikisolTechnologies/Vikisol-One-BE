package com.vikisol.one.ticket.dto;

import com.vikisol.one.ticket.entity.Ticket;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TicketRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String description;

    @NotNull
    private Ticket.Category category;

    private Ticket.Priority priority = Ticket.Priority.MEDIUM;
}
