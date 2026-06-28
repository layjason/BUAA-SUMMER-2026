import "@typespec/http";

using TypeSpec.Http;

namespace QujuApi.Ai;

@doc("AI 调用状态。")
enum AiCallStatus {
  succeeded,
  failed,
  rateLimited,
}

@doc("AI 活动策划请求，用户已登录且未超过调用频率，返回可编辑的结构化活动草案，不接收或返回模型供应商凭据。")
model ActivityPlanningRequest {
  @doc("用户输入的活动主题。")
  topic: string;

  @doc("用户选择或输入的活动类型。")
  activityType?: string;

  @doc("期望举办活动的城市。")
  city?: string;

  @doc("期望参与人数。")
  expectedParticipants?: int32;

  @doc("补充要求，例如风格、限制或偏好。")
  additionalRequirements?: string;
}

@doc("AI 生成的活动策划结果。")
model ActivityPlanningResult {
  @doc("AI 调用状态。")
  status: AiCallStatus;

  @doc("建议活动名称。")
  title?: string;

  @doc("建议活动标签。")
  tags: string[];

  @doc("建议活动简介。")
  introduction?: string;

  @doc("建议安全须知。")
  safetyNotice?: string;

  @doc("建议人数上限。")
  suggestedCapacity?: int32;

  @doc("建议报名截止时间。")
  suggestedRegistrationDeadline?: DateTimeString;

  @doc("AI 调用失败时可展示给用户的友好错误信息。")
  friendlyErrorMessage?: string;
}

@doc("AI 内容审核风险等级。")
enum AiRiskLevel {
  low,
  medium,
  high,
  uncertain,
}

@doc("AI 内容审核结果。")
model AiContentReviewResult {
  @doc("AI 调用状态。")
  status: AiCallStatus;

  @doc("AI 判断的内容风险等级。")
  riskLevel: AiRiskLevel;

  @doc("AI 建议的审核状态。")
  suggestedReviewStatus: ReviewStatus;

  @doc("AI 给出的风险或判断理由。")
  reasons: string[];

  @doc("AI 调用失败时可展示给用户的友好错误信息。")
  friendlyErrorMessage?: string;
}

@doc("AI 图片分类请求，mediaIds 指向已上传图片，返回建议标签供人工确认，最终标签以人工确认结果为准。")
model ImageClassificationRequest {
  @doc("需要进行图片分类的媒体文件标识列表。")
  mediaIds: EntityId[];
}

@doc("单张图片 AI 分类结果。")
model ImageClassificationItem {
  @doc("图片媒体文件标识。")
  mediaId: EntityId;

  @doc("AI 建议的图片标签。")
  suggestedTags: string[];

  @doc("AI 对分类结果的置信度。")
  confidence: float64;
}

@doc("AI 图片分类响应。")
model ImageClassificationResult {
  @doc("AI 调用状态。")
  status: AiCallStatus;

  @doc("逐图片分类结果。")
  items: ImageClassificationItem[];

  @doc("AI 调用失败时可展示给用户的友好错误信息。")
  friendlyErrorMessage?: string;
}

@route("/ai")
@tag("AI 辅助")
interface AiOperations {
  @post
  @route("/activity-plans")
  @doc("生成活动策划草案，调用方已登录且未超出使用量，返回结构化草案供用户逐项修改，AI 输出不能直接替代提交审核。")
  generateActivityPlan(
    @body body: ActivityPlanningRequest,
  ): APIResponse<ActivityPlanningResult>;

  @post
  @route("/image-classifications")
  @doc("分类活动或总结图片，图片已上传，返回建议标签，标签需由人工确认后才成为最终标签。")
  classifyImages(
    @body body: ImageClassificationRequest,
  ): APIResponse<ImageClassificationResult>;
}
