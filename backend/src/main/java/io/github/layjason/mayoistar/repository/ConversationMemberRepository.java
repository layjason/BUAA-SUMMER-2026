package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.chat.ConversationMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, String> {

    boolean existsByConversationIdAndUserId(String conversationId, String userId);

    List<ConversationMember> findByConversationId(String conversationId);
}
