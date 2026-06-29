import "@typespec/http";
import "@typespec/openapi3";
import "./common/models.tsp";
import "./common/errors.tsp";
import "./identity/identity.tsp";
import "./activities/activities.tsp";
import "./ai/ai.tsp";
import "./social/social.tsp";
import "./chat/chat.tsp";
import "./admin/admin.tsp";

using TypeSpec.Http;

@service(#{ title: "迷星群聚平台 API" })
@useAuth(BearerAuth)
@doc("迷星群聚平台 API 契约。除二进制导出外，业务接口 HTTP 状态码固定为 200，响应体统一使用 APIResponse。")
namespace QujuApi;
