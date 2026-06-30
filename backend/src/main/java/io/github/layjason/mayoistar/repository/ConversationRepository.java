package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.chat.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {}
