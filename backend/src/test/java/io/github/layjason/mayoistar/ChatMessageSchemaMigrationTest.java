package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 聊天消息迁移脚本一致性测试。
 *
 * <p>类职责：验证 PostgreSQL 迁移脚本中 chat_messages.kind CHECK 约束
 * 与 Java MessageKind 枚举保持一致，不包含未实现的消息类型。
 *
 * <p>不变量：迁移脚本中的约束值必须与 Java 枚举值一一对应。
 */
class ChatMessageSchemaMigrationTest {

    /**
     * 验证 V1 迁移脚本中 chat_messages 的 CHECK 约束不包含 emoticon。
     *
     * <p>前置条件：V1__initial_schema.sql 文件存在。
     *
     * <p>后置条件：约束仅包含 text、image、location。
     */
    @Test
    @DisplayName("chat_messages CHECK 约束不包含 emoticon，与 MessageKind 枚举一致")
    void chatMessagesKindConstraintExcludesEmoticon() throws Exception {
        Path migrationPath = Paths.get("src/main/resources/db/migration/V1__initial_schema.sql");
        String content = Files.readString(migrationPath);

        assertThat(content)
                .as("V1 迁移脚本中 chat_messages.kind CHECK 约束不应包含 emoticon")
                .doesNotContain("'emoticon'");

        assertThat(content)
                .as("V1 迁移脚本中 chat_messages.kind CHECK 约束应包含 text、image、location")
                .contains("'text'", "'image'", "'location'");
    }

    /**
     * 验证 H2 测试迁移脚本中 chat_messages 的 CHECK 约束不包含 emoticon。
     *
     * <p>前置条件：测试迁移脚本存在。
     *
     * <p>后置条件：约束仅包含 text、image、location。
     */
    @Test
    @DisplayName("H2 测试迁移脚本中 chat_messages CHECK 约束不包含 emoticon")
    void h2ChatMessagesKindConstraintExcludesEmoticon() throws Exception {
        Path testMigrationPath = Paths.get("src/test/resources/db/V1__initial_schema.sql");
        String content = Files.readString(testMigrationPath);

        assertThat(content)
                .as("H2 测试迁移脚本中 chat_messages.kind CHECK 约束不应包含 emoticon")
                .doesNotContain("'emoticon'");

        assertThat(content)
                .as("H2 测试迁移脚本中 chat_messages.kind CHECK 约束应包含 text、image、location")
                .contains("'text'", "'image'", "'location'");
    }
}
