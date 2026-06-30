package io.github.layjason.mayoistar;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityRepository;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockMultipartHttpServletRequestBuilder;

/**
 * API Controller 契约测试。
 *
 * <p>类职责：从 TypeSpec 生成的 OpenAPI 文件自动生成 MockMvc 请求，验证所有 Controller 方法均匹配契约。
 *
 * <p>类不变量：测试只访问内存 Spring 上下文，不调用外部服务，不修改真实业务数据。
 * 使用所有角色（admin、personal、merchant）的 Mock 用户，确保契约测试可访问所有端点，
 * 权限校验由专用权限测试覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(
        username = "test-user-id",
        authorities = {"ROLE_admin", "ROLE_personal", "ROLE_merchant"})
class ApiContractControllerTests {

    private static final String OPENAPI_SPEC = "../api-spec/tsp-output/openapi.yaml";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String MULTIPART_CONTENT_TYPE = "multipart/form-data";
    private static final String MULTIPART_BOUNDARY = "MayoiStarContractBoundary";
    private static final String CONTRACT_USER_ID = "test-user-id";
    private static final String CONTRACT_PASSWORD = "password-placeholder";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PersonalProfileRepository personalProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ActivityRepository activityRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 为 OpenAPI 中的每个 operation 生成一条契约测试。
     *
     * <p>前置条件：OpenAPI 文件存在且 Controller 已注册对应路径。
     *
     * <p>后置条件：每个 operation 的请求和响应均通过 OpenAPI validator 校验。
     *
     * <p>不变量：生成的请求只包含静态占位数据，不创建真实业务状态。
     *
     * @return 动态契约测试集合
     */
    @TestFactory
    Stream<DynamicTest> allOperationsMatchOpenApi() {
        OpenAPI openApi = readOpenApi();
        return openApi.getPaths().entrySet().stream()
                .flatMap(pathEntry -> operations(pathEntry.getValue()).stream()
                        .map(operationEntry -> dynamicTest(
                                operationEntry.method + " " + pathEntry.getKey(),
                                () -> assertOperation(
                                        openApi,
                                        operationEntry.method,
                                        pathEntry.getKey(),
                                        operationEntry.operation))));
    }

    /**
     * 执行单个 OpenAPI operation 的契约断言。
     *
     * <p>前置条件：method、pathTemplate 与 operation 来自同一个 OpenAPI 文档。
     *
     * <p>后置条件：MockMvc 响应状态为 200，响应体符合 OpenAPI 成功响应分支。
     *
     * <p>不变量：二进制导出接口只校验 octet-stream 类型，不生成真实导出文件。
     */
    private void assertOperation(OpenAPI openApi, String method, String pathTemplate, Operation operation)
            throws Exception {
        seedContractUser();
        AbstractMockHttpServletRequestBuilder<?> request = requestBuilder(openApi, method, pathTemplate, operation);
        if (pathTemplate.endsWith("/check-ins/export")) {
            mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM))
                    .andExpect(openApi().isValid(OPENAPI_SPEC));
            return;
        }
        mockMvc.perform(request).andExpect(status().isOk()).andExpect(openApi().isValid(OPENAPI_SPEC));
    }

    /**
     * 构造 MockMvc 请求。
     *
     * <p>前置条件：OpenAPI operation 已解析完成。
     *
     * <p>后置条件：返回携带必要 path、query、body 的请求构造器。
     *
     * <p>不变量：该方法不依赖 Controller 内部实现，只依据 OpenAPI Schema 生成请求。
     */
    private AbstractMockHttpServletRequestBuilder<?> requestBuilder(
            OpenAPI openApi, String method, String pathTemplate, Operation operation) throws Exception {
        String path = materializePath(pathTemplate);
        RequestBody requestBody = resolveRequestBody(openApi, operation.getRequestBody());
        boolean multipartBody = isMultipart(requestBody);
        AbstractMockHttpServletRequestBuilder<?> builder = createBuilder(method, path, multipartBody);
        addQueryParameters(openApi, builder, operation);
        addRequestBody(openApi, builder, requestBody);
        return builder;
    }

    /**
     * 创建与 HTTP 方法匹配的 MockMvc 请求构造器。
     *
     * <p>前置条件：method 是 OpenAPI 支持的 HTTP 方法。
     *
     * <p>后置条件：返回对应 MockMvc builder。
     *
     * <p>不变量：multipart 仅用于 OpenAPI 声明 multipart/form-data 的 POST 请求。
     */
    private AbstractMockHttpServletRequestBuilder<?> createBuilder(String method, String path, boolean multipartBody) {
        if (multipartBody) {
            return multipart(path);
        }
        return switch (method) {
            case "GET" -> get(path);
            case "POST" -> post(path);
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };
    }

    /**
     * 添加查询参数。
     *
     * <p>前置条件：operation 参数来自已解析 OpenAPI。
     *
     * <p>后置条件：所有 query 参数都有合法占位值。
     *
     * <p>不变量：path 参数已经写入 URL，不再重复加入 query。
     */
    private void addQueryParameters(
            OpenAPI openApi, AbstractMockHttpServletRequestBuilder<?> builder, Operation operation) {
        for (Parameter parameter :
                operation.getParameters() == null ? List.<Parameter>of() : operation.getParameters()) {
            Parameter resolved = resolveParameter(openApi, parameter);
            if ("query".equals(resolved.getIn())) {
                Object value = schemaValue(openApi, resolved.getSchema(), resolved.getName());
                if (value instanceof List<?> values) {
                    for (Object item : values) {
                        builder.param(resolved.getName(), String.valueOf(item));
                    }
                } else {
                    builder.param(resolved.getName(), String.valueOf(value));
                }
            }
        }
    }

    /**
     * 添加 JSON 或 multipart 请求体。
     *
     * <p>前置条件：requestBody 允许为空。
     *
     * <p>后置条件：有请求体的 operation 携带 OpenAPI Schema 所需字段。
     *
     * <p>不变量：生成的 body 只服务于契约测试，不代表业务样例。
     */
    private void addRequestBody(
            OpenAPI openApi, AbstractMockHttpServletRequestBuilder<?> builder, RequestBody requestBody)
            throws Exception {
        if (requestBody == null || requestBody.getContent() == null) {
            return;
        }
        if (requestBody.getContent().containsKey(MULTIPART_CONTENT_TYPE)) {
            addMultipartBody(openApi, builder, requestBody.getContent().get(MULTIPART_CONTENT_TYPE));
            return;
        }
        MediaType jsonMediaType = requestBody.getContent().get(JSON_CONTENT_TYPE);
        if (jsonMediaType != null) {
            Object value = schemaValue(openApi, jsonMediaType.getSchema(), "request");
            if (value instanceof Map<?, ?> body && hasField(body, "oldPassword")) {
                Map<String, Object> adjustedValue = new LinkedHashMap<>();
                body.forEach((key, mapValue) -> adjustedValue.put(String.valueOf(key), mapValue));
                adjustedValue.put("oldPassword", CONTRACT_PASSWORD);
                value = adjustedValue;
            }
            builder.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(value));
        }
    }

    /**
     * 添加 multipart 请求体。
     *
     * <p>前置条件：mediaType 来自 multipart/form-data content。
     *
     * <p>后置条件：每个属性都有表单字段或文件字段。
     *
     * <p>不变量：文件内容为固定字节，不写入磁盘，同时保留原始 body 供 OpenAPI validator 校验。
     */
    private void addMultipartBody(
            OpenAPI openApi, AbstractMockHttpServletRequestBuilder<?> builder, MediaType mediaType) {
        if (!(builder instanceof AbstractMockMultipartHttpServletRequestBuilder<?> multipartBuilder)) {
            throw new IllegalStateException("multipart 请求必须使用 Spring multipart builder");
        }
        StringBuilder rawBody = new StringBuilder();
        Schema<?> schema = resolveSchema(openApi, mediaType.getSchema());
        Map<String, Schema> properties = schema == null ? Map.of() : schema.getProperties();
        for (Map.Entry<String, Schema> entry : properties.entrySet()) {
            Schema<?> property = resolveSchema(openApi, entry.getValue());
            rawBody.append("--").append(MULTIPART_BOUNDARY).append("\r\n");
            if (property != null && "binary".equals(property.getFormat())) {
                multipartBuilder.file(new MockMultipartFile(
                        entry.getKey(),
                        "file.bin",
                        org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE,
                        new byte[] {'x'}));
                rawBody.append("Content-Disposition: form-data; name=\"")
                        .append(entry.getKey())
                        .append("\"; filename=\"file.bin\"\r\n")
                        .append("Content-Type: application/octet-stream\r\n\r\n")
                        .append("x\r\n");
            } else {
                String value = String.valueOf(schemaValue(openApi, property, entry.getKey()));
                multipartBuilder.param(entry.getKey(), value);
                rawBody.append("Content-Disposition: form-data; name=\"")
                        .append(entry.getKey())
                        .append("\"\r\n\r\n")
                        .append(value)
                        .append("\r\n");
            }
        }
        rawBody.append("--").append(MULTIPART_BOUNDARY).append("--\r\n");
        builder.contentType(org.springframework.http.MediaType.parseMediaType(
                        MULTIPART_CONTENT_TYPE + "; boundary=" + MULTIPART_BOUNDARY))
                .content(rawBody.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 根据 Schema 生成合法占位值。
     *
     * <p>前置条件：schema 可以为空或引用组件 Schema。
     *
     * <p>后置条件：返回可被 Jackson 序列化的合法占位值。
     *
     * <p>不变量：对象 Schema 只生成必填字段，避免引入契约未要求的额外语义。
     */
    private Object schemaValue(OpenAPI openApi, Schema<?> schema, String fieldName) {
        Schema<?> resolved = resolveSchema(openApi, schema);
        if (resolved == null) {
            return Map.of();
        }
        if (resolved.getEnum() != null && !resolved.getEnum().isEmpty()) {
            return resolved.getEnum().getFirst();
        }
        if (resolved.getAllOf() != null && !resolved.getAllOf().isEmpty()) {
            Map<String, Object> value = new LinkedHashMap<>();
            List<Object> scalarValues = new ArrayList<>();
            for (Schema<?> child : resolved.getAllOf()) {
                Object childValue = schemaValue(openApi, child, fieldName);
                if (childValue instanceof Map<?, ?> childMap) {
                    childMap.forEach((key, mapValue) -> value.put(String.valueOf(key), mapValue));
                } else {
                    scalarValues.add(childValue);
                }
            }
            return value.isEmpty() && !scalarValues.isEmpty() ? scalarValues.getFirst() : value;
        }
        if (resolved.getProperties() != null && !resolved.getProperties().isEmpty()) {
            Map<String, Object> value = new LinkedHashMap<>();
            List<String> required = resolved.getRequired() == null ? List.of() : resolved.getRequired();
            for (String name : required) {
                Schema<?> property = (Schema<?>) resolved.getProperties().get(name);
                value.put(name, schemaValue(openApi, property, name));
            }
            return value;
        }
        if ("array".equals(resolved.getType())) {
            return List.of(schemaValue(openApi, resolved.getItems(), fieldName));
        }
        return scalarValue(resolved, fieldName);
    }

    /**
     * 根据标量 Schema 生成占位值。
     *
     * <p>前置条件：schema 不表示对象或数组。
     *
     * <p>后置条件：返回与 type/format 兼容的 Java 值。
     *
     * <p>不变量：同一类型总是生成稳定值，避免测试不确定性。
     */
    private Object scalarValue(Schema<?> schema, String fieldName) {
        String type = schema.getType();
        String format = schema.getFormat();
        if ("integer".equals(type)) {
            return 1;
        }
        if ("number".equals(type)) {
            return 1.0;
        }
        if ("boolean".equals(type)) {
            return true;
        }
        if ("date-time".equals(format)) {
            return "2026-06-29T08:00:00Z";
        }
        if ("date".equals(format) || fieldName.toLowerCase().contains("birthday")) {
            return "2026-06-29";
        }
        return fieldName + "-placeholder";
    }

    /**
     * 将 OpenAPI 路径模板替换为测试路径。
     *
     * <p>前置条件：pathTemplate 使用 OpenAPI 的 {name} path 参数格式。
     *
     * <p>后置条件：返回 MockMvc 可请求的实际路径。
     *
     * <p>不变量：所有 path 参数使用同一个稳定占位后缀。
     */
    private String materializePath(String pathTemplate) {
        return pathTemplate.replaceAll("\\{[^/]+}", "placeholder");
    }

    /**
     * 读取 OpenAPI 文档。
     *
     * <p>前置条件：OPENAPI_SPEC 指向有效 OpenAPI 文件。
     *
     * <p>后置条件：返回已解析的 OpenAPI 对象。
     *
     * <p>不变量：解析失败会让测试失败，不静默跳过契约。
     */
    private OpenAPI readOpenApi() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result =
                new OpenAPIParser().readLocation(Path.of(OPENAPI_SPEC).toString(), null, options);
        if (result.getOpenAPI() == null) {
            throw new IllegalStateException("OpenAPI 解析失败：" + result.getMessages());
        }
        return result.getOpenAPI();
    }

    /**
     * 为依赖当前登录用户的契约接口准备稳定数据。
     *
     * <p>前置条件：测试使用 @WithMockUser(CONTRACT_USER_ID)，H2 数据库可写。
     *
     * <p>后置条件：数据库中存在个人用户及其资料，且密码重置为 CONTRACT_PASSWORD。
     *
     * <p>不变量：每个 operation 前都重置同一用户状态，避免动态测试之间互相污染。
     */
    private void seedContractUser() {
        Instant now = Instant.now();
        User user = userRepository
                .findById(CONTRACT_USER_ID)
                .orElseGet(() -> User.builder()
                        .userId(CONTRACT_USER_ID)
                        .email("contract-user@example.com")
                        .nickname("contract-user")
                        .kind(UserKind.personal)
                        .createdAt(now)
                        .build());
        user.setPasswordHash(passwordEncoder.encode(CONTRACT_PASSWORD));
        user.setKind(UserKind.personal);
        user.setAccountStatus(AccountStatus.active);
        user.setUpdatedAt(now);
        userRepository.save(user);

        final User managedUser = userRepository.findById(CONTRACT_USER_ID).orElseThrow();
        PersonalProfile profile = personalProfileRepository
                .findByUserId(CONTRACT_USER_ID)
                .orElseGet(() -> {
                    PersonalProfile p = new PersonalProfile();
                    p.setUser(managedUser);
                    return p;
                });
        profile.setUser(managedUser);
        profile.setInterestTags(List.of("contract"));
        profile.setReputationScore(100);
        profile.setUpdatedAt(now);
        personalProfileRepository.save(profile);

        // 为使用 placeholder 路径参数的 admin 端点准备种子活动
        activityRepository.findById("placeholder").orElseGet(() -> activityRepository.save(Activity.builder()
                .activityId("placeholder")
                .organizerId(CONTRACT_USER_ID)
                .title("契约测试活动")
                .tags(List.of("契约"))
                .introduction("契约测试活动简介")
                .startAt(now)
                .endAt(now.plusSeconds(7200))
                .pointLon(116.397)
                .pointLat(39.907)
                .city("北京")
                .address("契约测试地址")
                .placeName("契约测试地点")
                .safetyNotice("契约测试安全须知")
                .capacity(20)
                .registrationDeadline(now.plusSeconds(3600))
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .createdAt(now)
                .updatedAt(now)
                .build()));
    }

    /**
     * 判断生成的请求体是否包含指定字段。
     *
     * <p>前置条件：body 为根据 OpenAPI schema 生成的请求体 map。
     *
     * <p>后置条件：存在字段时返回 true。
     *
     * <p>不变量：只检查键名，不读取或修改字段值。
     */
    private boolean hasField(Map<?, ?> body, String fieldName) {
        return body.containsKey(fieldName);
    }

    /**
     * 枚举 PathItem 中声明的 operation。
     *
     * <p>前置条件：pathItem 来自 OpenAPI paths。
     *
     * <p>后置条件：返回包含 HTTP 方法和 operation 的列表。
     *
     * <p>不变量：只枚举当前契约使用的 GET/POST/PATCH/DELETE。
     */
    private List<OperationEntry> operations(PathItem pathItem) {
        List<OperationEntry> operations = new ArrayList<>();
        addOperation(operations, "GET", pathItem.getGet());
        addOperation(operations, "POST", pathItem.getPost());
        addOperation(operations, "PATCH", pathItem.getPatch());
        addOperation(operations, "DELETE", pathItem.getDelete());
        return operations;
    }

    /**
     * 加入非空 operation。
     *
     * <p>前置条件：operations 为可变列表。
     *
     * <p>后置条件：operation 非空时列表新增一项。
     *
     * <p>不变量：不改变 operation 内容。
     */
    private void addOperation(List<OperationEntry> operations, String method, Operation operation) {
        if (operation != null) {
            operations.add(new OperationEntry(method, operation));
        }
    }

    /**
     * 判断请求体是否为 multipart。
     *
     * <p>前置条件：requestBody 允许为空。
     *
     * <p>后置条件：返回 true 表示请求应按 multipart/form-data 生成。
     *
     * <p>不变量：判断只依赖 OpenAPI content type。
     */
    private boolean isMultipart(RequestBody requestBody) {
        return requestBody != null
                && requestBody.getContent() != null
                && requestBody.getContent().containsKey(MULTIPART_CONTENT_TYPE);
    }

    /**
     * 解析 Parameter 引用。
     *
     * <p>前置条件：parameter 来自 OpenAPI operation。
     *
     * <p>后置条件：返回实际 Parameter。
     *
     * <p>不变量：非引用参数原样返回。
     */
    private Parameter resolveParameter(OpenAPI openApi, Parameter parameter) {
        if (parameter.get$ref() == null) {
            return parameter;
        }
        return openApi.getComponents().getParameters().get(refName(parameter.get$ref()));
    }

    /**
     * 解析 RequestBody 引用。
     *
     * <p>前置条件：requestBody 允许为空。
     *
     * <p>后置条件：返回实际 RequestBody 或 null。
     *
     * <p>不变量：非引用请求体原样返回。
     */
    private RequestBody resolveRequestBody(OpenAPI openApi, RequestBody requestBody) {
        if (requestBody == null || requestBody.get$ref() == null) {
            return requestBody;
        }
        return openApi.getComponents().getRequestBodies().get(refName(requestBody.get$ref()));
    }

    /**
     * 解析 Schema 引用。
     *
     * <p>前置条件：schema 允许为空。
     *
     * <p>后置条件：返回实际 Schema 或 null。
     *
     * <p>不变量：非引用 Schema 原样返回。
     */
    private Schema<?> resolveSchema(OpenAPI openApi, Schema<?> schema) {
        if (schema == null || schema.get$ref() == null) {
            return schema;
        }
        return openApi.getComponents().getSchemas().get(refName(schema.get$ref()));
    }

    /**
     * 提取 OpenAPI 引用名称。
     *
     * <p>前置条件：ref 使用 JSON Pointer 形式。
     *
     * <p>后置条件：返回最后一级引用名称。
     *
     * <p>不变量：不解码或修改引用名称内容。
     */
    private String refName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    private record OperationEntry(String method, Operation operation) {
        private OperationEntry {
            Objects.requireNonNull(method);
            Objects.requireNonNull(operation);
        }
    }
}
