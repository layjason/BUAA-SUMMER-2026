package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.chat.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 会话数据访问层。
 *
 * <p>类职责：提供 Conversation 实体的 CRUD。
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {}
