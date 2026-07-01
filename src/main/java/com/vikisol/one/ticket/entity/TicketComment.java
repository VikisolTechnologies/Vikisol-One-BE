package com.vikisol.one.ticket.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ticket_comments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TicketComment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(nullable = false)
    private UUID commentById;

    @Column(nullable = false)
    private String commentByName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String comment;

    @Builder.Default
    private boolean isInternal = false;
}
