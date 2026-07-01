package io.github.layjason.mayoistar.repository;

import io.github.layjason.mayoistar.entity.chat.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 会话数据访问层。
 *
 * <p>类职责：提供 Conversation 实体的 CRUD。
 */
public interface ConversationRepository extends JpaRepository<Conversation, String> {}
