package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.chat.ChatMessage;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    Page<ChatMessage> findByConversationIdOrderBySentAtDesc(String conversationId, Pageable pageable);

    Optional<ChatMessage> findByMessageIdAndConversationId(String messageId, String conversationId);
}
