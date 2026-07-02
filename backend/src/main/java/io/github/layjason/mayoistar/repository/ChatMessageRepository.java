package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.chat.ChatMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {

    Page<ChatMessage> findByConversationIdOrderBySentAtDesc(String conversationId, Pageable pageable);

    List<ChatMessage> findByConversationId(String conversationId);

    Optional<ChatMessage> findByMessageIdAndConversationId(String messageId, String conversationId);

    @Modifying
    @Query("DELETE FROM ChatMessage cm WHERE cm.conversationId = :conversationId")
    void deleteByConversationId(String conversationId);
}
