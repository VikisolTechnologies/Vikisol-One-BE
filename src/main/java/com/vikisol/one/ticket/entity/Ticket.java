package com.vikisol.one.ticket.entity;

import com.vikisol.one.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Ticket extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String ticketNumber;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.OPEN;

    @Column(nullable = false)
    private UUID raisedById;

    @Column(nullable = false)
    private String raisedByName;

    private UUID assignedToId;

    private String assignedToName;

    private LocalDateTime resolvedDate;

    @Column(columnDefinition = "TEXT")
    private String resolutionComments;

    private LocalDateTime closedDate;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TicketComment> comments = new ArrayList<>();

    public enum Category {
        IT, HR, ADMIN, FINANCE, FACILITIES
    }

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum Status {
        OPEN, IN_PROGRESS, RESOLVED, CLOSED, REOPENED
    }
}
