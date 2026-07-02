package io.github.layjason.mayoistar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 后台治理相关数据库模式测试。
 *
 * <p>类职责：验证 H2 测试数据库包含后台治理接口依赖的表和字段。
 *
 * <p>类不变量：测试仅查询内存数据库元数据，不写入业务数据，不连接外部数据库。
 */
class AdminSchemaTests extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证通用举报表包含目标类型和目标标识字段。
     *
     * <p>前置条件：Flyway 已在 H2 PostgreSQL 模式下执行初始化 SQL。
     *
     * <p>后置条件：reports.target_type 与 reports.target_id 均存在。
     *
     * <p>不变量：该断言只依赖数据库元数据，不依赖实体扫描顺序。
     */
    @Test
    void reportTableHasGenericTargetColumns() {
        assertEquals(1, columnCount("REPORTS", "TARGET_TYPE"));
        assertEquals(1, columnCount("REPORTS", "TARGET_ID"));
    }

    /**
     * 验证小队表持久化创建者字段。
     *
     * <p>前置条件：Flyway 已在 H2 PostgreSQL 模式下执行初始化 SQL。
     *
     * <p>后置条件：teams.creator_id 存在。
     *
     * <p>不变量：创建者字段独立于 leader_id，不因队长变更而被覆盖。
     */
    @Test
    void teamTableHasCreatorColumn() {
        assertEquals(1, columnCount("TEAMS", "CREATOR_ID"));
    }

    /**
     * 验证小队治理记录表包含治理动作和操作人字段。
     *
     * <p>前置条件：Flyway 已在 H2 PostgreSQL 模式下执行初始化 SQL。
     *
     * <p>后置条件：team_moderation_records.action 与 operator_id 均存在。
     *
     * <p>不变量：治理记录作为追加历史存在，不替代 teams.status 当前状态。
     */
    @Test
    void teamModerationRecordTableExists() {
        assertEquals(1, columnCount("TEAM_MODERATION_RECORDS", "ACTION"));
        assertEquals(1, columnCount("TEAM_MODERATION_RECORDS", "OPERATOR_ID"));
    }

    /**
     * 查询指定表字段在 H2 元数据中的数量。
     *
     * <p>前置条件：tableName 和 columnName 使用 H2 元数据中的大写名称。
     *
     * <p>后置条件：返回匹配字段数量。
     *
     * <p>不变量：该函数不拼接用户输入，参数仅来自测试常量。
     */
    private Integer columnCount(String tableName, String columnName) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
    }
}
