package com.vikisol.one.ticket.repository;

import com.vikisol.one.ticket.entity.TicketComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TicketCommentRepository extends JpaRepository<TicketComment, UUID> {

    List<TicketComment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
}
