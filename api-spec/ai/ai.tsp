import "@typespec/http";

using TypeSpec.Http;

namespace QujuApi.Ai;

@doc("AI 调用状态。")
enum AiCallStatus {
  succeeded,
  failed,
  rateLimited,
}

@doc("分类任务状态。")
enum ClassifyTaskStatus {
  pending,
  succeeded,
  failed,
  timeout,
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

@doc("AI 图片分类请求，mediaIds 指向已上传图片，任务异步提交，通过返回的 taskId 轮询结果。")
model ImageClassificationRequest {
  @doc("需要进行图片分类的媒体文件标识列表。")
  mediaIds: EntityId[];
}

@doc("图片分类任务提交响应，包含 taskId 供后续轮询。")
model ImageClassificationSubmitResponse {
  @doc("任务唯一标识，用于轮询 GET /ai/image-classifications/{taskId} 获取结果。")
  taskId: EntityId;

  @doc("任务初始状态：若全部图片已有缓存则为 succeeded，否则为 pending。")
  status: ClassifyTaskStatus;
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

@doc("图片分类任务查询响应。")
model ImageClassificationTaskQueryResponse {
  @doc("任务状态：pending 时仅返回状态，succeeded 时包含全部结果，failed/timeout 时包含 errorMessage。")
  status: ClassifyTaskStatus;

  @doc("逐图片分类结果（仅 succeeded 时非空）。")
  items?: ImageClassificationItem[];

  @doc("失败或超时时的错误信息。")
  errorMessage?: string;
}

@doc("按 mediaId 查询单张图片的分类缓存。")
model MediaClassificationResponse {
  @doc("图片媒体文件标识。")
  mediaId: EntityId;

  @doc("AI 建议的图片标签。")
  suggestedTags: string[];

  @doc("AI 对分类结果的置信度。")
  confidence: float64;

  @doc("分类完成时间（ISO-8601）。")
  classifiedAt: DateTimeString;
}

@route("/ai")
@tag("AI 辅助")
interface AiOperations {
  @post
  @route("/activity-plans")
  @doc("生成活动策划草案，调用方已登录且未超出使用量，返回结构化草案供用户逐项修改，AI 输出不能直接替代提交审核。")
  generateActivityPlan(@body body: ActivityPlanningRequest): APIResult<
    ActivityPlanningResult,

      | QujuApi.Errors.Ai.AiRateLimited
      | QujuApi.Errors.Ai.AiServiceUnavailable
      | QujuApi.Errors.Ai.AiOutputUnavailable
  >;

  @post
  @route("/image-classifications")
  @doc("提交图片分类任务（异步）。已上传的图片通过后台 GPU 集群分类，返回 taskId 供轮询 GET /ai/image-classifications/{taskId} 获取结果。已分类过的图片直接命中缓存无需重复推理。")
  classifyImages(@body body: ImageClassificationRequest): APIResult<
    ImageClassificationSubmitResponse,

      | QujuApi.Errors.Ai.AiRateLimited
      | QujuApi.Errors.Ai.AiServiceUnavailable
      | QujuApi.Errors.Ai.ImageMediaUnavailable
  >;

  @get
  @route("/image-classifications/{taskId}")
  @doc("查询图片分类任务结果。pending 时仅返回状态，succeeded 时返回所有 media（含缓存命中）的分类结果，failed/timeout 时包含错误信息。任务超时时间为 30 秒。")
  getClassifyTaskResult(
    @path
    @doc("图片分类任务 ID，由 POST /ai/image-classifications 返回。")
    taskId: EntityId,
  ): APIResult<
    ImageClassificationTaskQueryResponse,

      | QujuApi.Errors.Ai.AiTaskNotFound
      | QujuApi.Errors.Ai.AiTaskTimeout
  >;

  @get
  @route("/image-classifications/media/{mediaId}")
  @doc("按媒体 ID 查询单张图片的分类缓存。命中缓存时返回包括 classifiedAt 的完整分类结果，未命中时返回 30004。")
  getClassificationByMediaId(
    @path
    @doc("媒体文件 ID。")
    mediaId: EntityId,
  ): APIResult<
    MediaClassificationResponse,

      | QujuApi.Errors.Ai.AiTaskNotFound
  >;
}
