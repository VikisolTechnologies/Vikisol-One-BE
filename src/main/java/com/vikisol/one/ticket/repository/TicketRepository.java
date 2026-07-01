package com.vikisol.one.ticket.repository;

import com.vikisol.one.ticket.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Page<Ticket> findByRaisedById(UUID raisedById, Pageable pageable);

    Page<Ticket> findByAssignedToId(UUID assignedToId, Pageable pageable);

    List<Ticket> findByStatus(Ticket.Status status);

    List<Ticket> findByCategory(Ticket.Category category);

    Optional<Ticket> findByTicketNumber(String ticketNumber);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(t.ticketNumber, 5) AS int)), 0) FROM Ticket t")
    int findMaxTicketNumber();
}
