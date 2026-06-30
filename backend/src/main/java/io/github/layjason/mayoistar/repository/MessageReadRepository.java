package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.chat.MessageRead;
import io.github.layjason.mayoistar.entity.chat.MessageReadStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageReadRepository extends JpaRepository<MessageRead, String> {

    List<MessageRead> findByMessageIdInAndUserId(Collection<String> messageIds, String userId);

    boolean existsByMessageIdAndUserId(String messageId, String userId);

    @Modifying(clearAutomatically = true)
    @Query(
            "UPDATE MessageRead mr SET mr.status = :status, mr.readAt = :now WHERE mr.messageId IN :messageIds AND mr.userId = :userId AND mr.status = 'unread'")
    int markAsRead(List<String> messageIds, String userId, MessageReadStatus status, Instant now);

    @Modifying
    @Query("UPDATE MessageRead mr SET mr.status = :status WHERE mr.messageId = :messageId")
    int updateStatusByMessageId(String messageId, MessageReadStatus status);
}
