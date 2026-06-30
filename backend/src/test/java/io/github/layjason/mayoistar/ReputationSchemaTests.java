package io.github.layjason.mayoistar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 信誉积分变更记录相关数据库模式测试。
 *
 * <p>类职责：验证 H2 测试数据库包含信誉积分变更记录表和相关约束。
 *
 * <p>类不变量：测试仅查询内存数据库元数据，不写入业务数据，不连接外部数据库。
 */
@SpringBootTest
@ActiveProfiles("test")
class ReputationSchemaTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 验证信誉积分变更记录表存在并包含必要字段。
     *
     * <p>前置条件：Flyway V2 已在 H2 PostgreSQL 模式下执行迁移。
     *
     * <p>后置条件：reputation_records 表及其字段均存在。
     */
    @Test
    void reputationRecordsTableHasRequiredColumns() {
        assertEquals(1, columnCount("REPUTATION_RECORDS", "RECORD_ID"));
        assertEquals(1, columnCount("REPUTATION_RECORDS", "USER_ID"));
        assertEquals(1, columnCount("REPUTATION_RECORDS", "SCORE_CHANGE"));
        assertEquals(1, columnCount("REPUTATION_RECORDS", "REASON"));
        assertEquals(1, columnCount("REPUTATION_RECORDS", "SOURCE"));
        assertEquals(1, columnCount("REPUTATION_RECORDS", "REFERENCE_ID"));
        assertEquals(1, columnCount("REPUTATION_RECORDS", "CREATED_AT"));
    }

    /**
     * 验证 blacklists 表已添加自引用检查约束。
     *
     * <p>前置条件：Flyway V2 已执行迁移。
     *
     * <p>后置条件：ck_blacklists_self 约束存在。
     */
    @Test
    void blacklistTableHasSelfCheckConstraint() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE TABLE_NAME = 'BLACKLISTS' AND CONSTRAINT_NAME = 'CK_BLACKLISTS_SELF'
                """, Integer.class);
        assertEquals(1, count);
    }

    private Integer columnCount(String tableName, String columnName) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
    }
}
