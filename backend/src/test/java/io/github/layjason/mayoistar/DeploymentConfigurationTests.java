package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 部署配置文件测试。
 *
 * <p>类职责：验证 Docker Compose、环境变量模板与 Spring Profile 配置之间的关键约定保持一致。
 *
 * <p>类不变量：测试只读取仓库内的文本配置文件，不启动容器，不连接外部数据库。
 */
class DeploymentConfigurationTests {

    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath();

    /**
     * 验证生产 Compose 使用 prod Profile 并构建后端镜像。
     *
     * <p>前置条件：仓库根目录存在生产 Docker Compose 文件。
     *
     * <p>后置条件：生产 Compose 明确声明后端构建上下文与 prod Spring Profile。
     *
     * <p>不变量：测试不解析或执行 Compose 文件，只检查稳定的部署契约文本。
     */
    @Test
    void productionComposeBuildsBackendWithProdProfile() throws IOException {
        var compose = Files.readString(PROJECT_ROOT.resolve("dokcer-compose.yaml"));

        assertThat(compose).contains("backend:", "build:", "SPRING_PROFILES_ACTIVE: prod");
        assertThat(compose).contains("postgres:", "POSTGRES_DB: ${POSTGRES_DB}");
    }

    /**
     * 验证本地 Compose 只包含数据库支持组件。
     *
     * <p>前置条件：仓库根目录存在本地 Docker Compose 文件。
     *
     * <p>后置条件：本地 Compose 暴露 PostgreSQL 端口，且不声明后端 Java 服务。
     *
     * <p>不变量：测试不启动容器，不占用本地端口。
     */
    @Test
    void localComposeOnlyProvidesPostgresSupport() throws IOException {
        var compose = Files.readString(PROJECT_ROOT.resolve("docker-compose-local.yaml"));

        assertThat(compose).contains("postgres:", "\"${POSTGRES_PORT:-5432}:5432\"");
        assertThat(compose).doesNotContain("backend:");
    }

    /**
     * 验证 Spring 配置可从 .env 文件读取数据库配置。
     *
     * <p>前置条件：仓库包含基础、dev、prod 三份 Spring 配置文件。
     *
     * <p>后置条件：基础配置导入 .env，dev 与 prod Profile 均引用 PostgreSQL 环境变量。
     *
     * <p>不变量：测试不创建 .env 文件，不改变当前进程环境变量。
     */
    @Test
    void springProfilesImportDatabaseSettingsFromDotenv() throws IOException {
        var application = Files.readString(PROJECT_ROOT.resolve("src/main/resources/application.yaml"));
        var dev = Files.readString(PROJECT_ROOT.resolve("src/main/resources/application-dev.yaml"));
        var prod = Files.readString(PROJECT_ROOT.resolve("src/main/resources/application-prod.yaml"));

        assertThat(application).contains("optional:file:.env[.properties]");
        assertThat(dev).contains("jdbc:postgresql://", "${POSTGRES_DB:mayoistar}", "${POSTGRES_USER:mayoistar}");
        assertThat(prod).contains("jdbc:postgresql://", "${POSTGRES_DB:mayoistar}", "${POSTGRES_USER}");
    }

    /**
     * 验证环境变量模板覆盖 Compose 与 Spring Profile 需要的键。
     *
     * <p>前置条件：仓库根目录存在 .env.example 文件。
     *
     * <p>后置条件：模板包含数据库名、用户名、密码、端口与后端端口配置。
     *
     * <p>不变量：测试不读取真实 .env，不暴露任何本地敏感配置。
     */
    @Test
    void dotenvExampleContainsRequiredDatabaseKeys() throws IOException {
        var example = Files.readString(PROJECT_ROOT.resolve(".env.example"));

        assertThat(example)
                .contains(
                        "POSTGRES_DB=",
                        "POSTGRES_USER=",
                        "POSTGRES_PASSWORD=",
                        "POSTGRES_PORT=",
                        "MAYOISTAR_SERVER_PORT=");
    }
}
