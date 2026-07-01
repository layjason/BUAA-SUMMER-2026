package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.chat.ConversationMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, String> {

    boolean existsByConversationIdAndUserId(String conversationId, String userId);

    List<ConversationMember> findByConversationId(String conversationId);

    List<ConversationMember> findByUserId(String userId);

    @Query("SELECT cm1.conversationId FROM ConversationMember cm1 "
            + "WHERE cm1.userId = :userA AND cm1.conversationId IN "
            + "(SELECT cm2.conversationId FROM ConversationMember cm2 WHERE cm2.userId = :userB)")
    List<String> findCommonConversationIds(@Param("userA") String userA, @Param("userB") String userB);

    @Modifying
    @Query("DELETE FROM ConversationMember cm WHERE cm.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") String conversationId);
}
