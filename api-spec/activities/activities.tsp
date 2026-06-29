import "@typespec/http";

using TypeSpec.Http;

namespace QujuApi.Activities;

@doc("活动审核状态。")
enum ActivityReviewStatus {
  draft,
  pending,
  approved,
  rejected,
  changeRequired,
}

@doc("活动进行状态。")
enum ActivityRuntimeStatus {
  notStarted,
  registering,
  registrationClosed,
  ongoing,
  ended,
  takenDown,
}

@doc("报名状态。")
enum RegistrationStatus {
  registered,
  waiting,
  waitingConfirmation,
  canceled,
  checkedIn,
}

@doc("首页信息流类型。")
enum ActivityFeedTab {
  recommended,
  latest,
  nearby,
}

@doc("活动基础信息请求，地点来自地图选点，时间范围合法，可用于草稿保存或提交审核，费用字段只表达信息，不触发支付。")
model ActivityUpsertRequest {
  @doc("活动名称。")
  title: string;

  @doc("活动标签，用于分类、搜索和推荐。")
  tags: string[];

  @doc("活动简介。")
  introduction: string;

  @doc("活动开始时间。")
  startAt: DateTimeString;

  @doc("活动结束时间。")
  endAt: DateTimeString;

  @doc("活动地点信息。")
  location: LocationInfo;

  @doc("活动安全须知。")
  safetyNotice: string;

  @doc("活动人数上限。")
  capacity: int32;

  @doc("报名截止时间。")
  registrationDeadline: DateTimeString;

  @doc("活动费用金额，仅表示活动信息，不代表支付订单。")
  feeAmount?: float64;

  @doc("费用说明，例如免费、AA 或现场支付说明。")
  feeDescription?: string;

  @doc("参与者最低年龄要求。")
  minAge?: int32;

  @doc("活动图片媒体文件标识列表。")
  imageIds?: EntityId[];
}

@doc("活动摘要。")
model ActivitySummary {
  @doc("活动标识。")
  activityId: EntityId;

  @doc("活动名称。")
  title: string;

  @doc("活动标签。")
  tags: string[];

  @doc("活动开始时间。")
  startAt: DateTimeString;

  @doc("活动结束时间。")
  endAt: DateTimeString;

  @doc("活动地点。")
  location: LocationInfo;

  @doc("活动封面图片。")
  coverImage?: MediaFile;

  @doc("活动费用金额。")
  feeAmount?: float64;

  @doc("活动审核状态。")
  reviewStatus: ActivityReviewStatus;

  @doc("活动进行状态。")
  runtimeStatus: ActivityRuntimeStatus;

  @doc("已报名人数。")
  registeredCount: int32;

  @doc("活动人数上限。")
  capacity: int32;
}

@doc("活动详情。")
model ActivityDetail {
  ...ActivitySummary;

  @doc("活动完整简介。")
  introduction: string;

  @doc("活动安全须知全文。")
  safetyNotice: string;

  @doc("报名截止时间。")
  registrationDeadline: DateTimeString;

  @doc("活动发起人用户标识。")
  organizerId: EntityId;

  @doc("活动发起人展示名称。")
  organizerName: string;

  @doc("活动图片列表。")
  images: MediaFile[];

  @doc("当前候补人数。")
  waitingCount: int32;

  @doc("当前用户是否可以发起报名。")
  canRegister: boolean;
}

@doc("活动模板。")
model ActivityTemplate {
  @doc("模板标识。")
  templateId: EntityId;

  @doc("模板名称。")
  name: string;

  @doc("模板所属活动类型。")
  activityType: string;

  @doc("模板默认标签。")
  defaultTags: string[];

  @doc("模板默认活动简介。")
  defaultIntroduction: string;

  @doc("模板默认安全须知。")
  defaultSafetyNotice: string;

  @doc("模板默认人数上限。")
  defaultCapacity: int32;
}

@doc("活动搜索条件，GET 请求通过 URL 传递，返回匹配活动，多选活动类型使用重复 query 参数或逗号分隔由实现统一处理。")
model ActivitySearchQuery extends PageQuery {
  @doc("搜索关键词。")
  @query
  keyword?: string;

  @doc("活动类型筛选条件。")
  @query
  activityTypes?: string[];

  @doc("城市筛选条件。")
  @query
  city?: string;

  @doc("活动开始时间下界。")
  @query
  startAtFrom?: DateTimeString;

  @doc("活动开始时间上界。")
  @query
  startAtTo?: DateTimeString;

  @doc("最低费用筛选条件。")
  @query
  minFee?: float64;

  @doc("最高费用筛选条件。")
  @query
  maxFee?: float64;

  @doc("距离筛选中心点经度。")
  @query
  centerLongitude?: float64;

  @doc("距离筛选中心点纬度。")
  @query
  centerLatitude?: float64;

  @doc("距离筛选半径，单位米。")
  @query
  distanceMeters?: int32;
}

@doc("地图活动点位。")
model ActivityMapPoint {
  @doc("活动标识。")
  activityId: EntityId;

  @doc("活动名称。")
  title: string;

  @doc("地图点位坐标。")
  point: GeoPoint;

  @doc("活动进行状态。")
  runtimeStatus: ActivityRuntimeStatus;

  @doc("活动开始时间。")
  startAt: DateTimeString;
}

@doc("活动报名请求，活动存在且用户满足信誉、年龄等校验，生成正式报名或候补记录，满员时不得直接占用名额。")
model RegisterActivityRequest {
  @doc("参与者报名备注或必要报名信息。")
  participantNote?: string;

  @doc("是否已确认活动安全须知。")
  acceptedSafetyNotice: boolean;
}

@doc("活动报名结果。")
model RegistrationResult {
  @doc("报名记录标识。")
  registrationId: EntityId;

  @doc("活动标识。")
  activityId: EntityId;

  @doc("报名或候补状态。")
  status: RegistrationStatus;

  @doc("候补排位，正式报名时为空。")
  waitingRank?: int32;

  @doc("候补名额确认截止时间。")
  confirmationDeadline?: DateTimeString;
}

@doc("候补确认请求。")
model WaitingConfirmationRequest {
  @doc("是否确认接受候补释放的名额。")
  confirmed: boolean;
}

@doc("签到二维码。")
model CheckInQrCode {
  @doc("活动标识。")
  activityId: EntityId;

  @doc("签到二维码 token。")
  qrCodeToken: string;

  @doc("二维码过期时间。")
  expiresAt: DateTimeString;
}

@doc("扫码签到请求，二维码有效，报名记录进入已签到状态，开启位置校验时当前位置必须在活动地点附近。")
model CheckInRequest {
  @doc("扫码得到的签到二维码 token。")
  qrCodeToken: string;

  @doc("签到时用户当前位置，用于可选的位置校验。")
  currentLocation?: GeoPoint;
}

@doc("签到记录。")
model CheckInRecord {
  @doc("报名记录标识。")
  registrationId: EntityId;

  @doc("参与者用户标识。")
  userId: EntityId;

  @doc("参与者昵称。")
  nickname: string;

  @doc("当前报名或签到状态。")
  registrationStatus: RegistrationStatus;

  @doc("签到完成时间。")
  checkedInAt?: DateTimeString;
}

@doc("活动图文总结请求，活动已结束且调用方为发起人，生成活动总结，图片标签必须来自人工确认结果。")
model ActivitySummaryPostRequest {
  @doc("活动总结标题。")
  title: string;

  @doc("活动总结正文。")
  content: string;

  @doc("活动总结图片媒体文件标识列表。")
  imageIds: EntityId[];

  @doc("经人工确认的图片标签。")
  confirmedImageTags: ImageTagConfirmation[];
}

@doc("图片标签确认结果。")
model ImageTagConfirmation {
  @doc("图片媒体文件标识。")
  mediaId: EntityId;

  @doc("该图片最终确认的标签。")
  tags: string[];
}

@doc("活动图文总结。")
model ActivitySummaryPost {
  @doc("活动总结标识。")
  summaryId: EntityId;

  @doc("活动标识。")
  activityId: EntityId;

  @doc("活动总结标题。")
  title: string;

  @doc("活动总结正文。")
  content: string;

  @doc("活动总结图片列表。")
  images: MediaFile[];

  @doc("活动总结图片标签。")
  imageTags: ImageTagConfirmation[];

  @doc("活动总结发布时间。")
  createdAt: DateTimeString;
}

@doc("活动评价请求，调用方已签到且评价窗口未关闭，保存评价，每名参与者对同一活动仅评价一次。")
model ActivityReviewRequest {
  @doc("评分。")
  rating: int32;

  @doc("评价正文。")
  content?: string;

  @doc("评价标签。")
  tags?: string[];
}

@doc("活动评价。")
model ActivityReview {
  @doc("评价标识。")
  reviewId: EntityId;

  @doc("活动标识。")
  activityId: EntityId;

  @doc("评价用户标识。")
  userId: EntityId;

  @doc("评分。")
  rating: int32;

  @doc("评价正文。")
  content?: string;

  @doc("评价标签。")
  tags: string[];

  @doc("评价创建时间。")
  createdAt: DateTimeString;
}

@route("/activities")
@tag("活动")
interface ActivityOperations {
  @post
  @route("/media/images")
  @doc("上传活动图片，请求为 multipart/form-data，返回活动图片 mediaId，上传接口不创建活动。")
  uploadActivityImage(@multipartBody body: MediaUploadRequest): APIResult<
    MediaFile,

      | QujuApi.Errors.Activities.InvalidMediaUsage
      | QujuApi.Errors.Activities.ImageFormatInvalid
      | QujuApi.Errors.Activities.ImageTooLarge
  >;

  @get
  @route("/templates")
  @doc("获取活动模板，无，返回可用于快速创建的模板，模板不代表已创建活动。")
  listTemplates(...PageQuery): APIResult<PageResult<ActivityTemplate>>;

  @post
  @route("/drafts")
  @doc("保存活动草稿，调用方已登录，创建或更新草稿，草稿不会进入发现列表。")
  saveDraft(@body body: ActivityUpsertRequest): APIResult<
    ActivityDetail,

      | QujuApi.Errors.Activities.InvalidActivitySchedule
      | QujuApi.Errors.Activities.MediaFileUnavailable
  >;

  @patch
  @route("/drafts/{activityId}")
  @doc("更新活动草稿，调用方为草稿发起人，草稿内容更新，已提交审核的活动不能按草稿规则覆盖。")
  updateDraft(
    @path activityId: EntityId,
    @body body: ActivityUpsertRequest,
  ): APIResult<
    ActivityDetail,

      | QujuApi.Errors.Activities.ActivityNotVisible
      | QujuApi.Errors.Activities.ActivityPermissionDenied
      | QujuApi.Errors.Activities.InvalidActivitySchedule
      | QujuApi.Errors.Activities.ActivityStateNotSubmittable
      | QujuApi.Errors.Activities.MediaFileUnavailable
  >;

  @post
  @route("/templates/{templateId}/drafts")
  @doc("基于模板创建草稿，模板存在，生成可编辑草稿，用户必须确认后才可提交审核。")
  createDraftFromTemplate(@path templateId: EntityId): APIResult<
    ActivityDetail,
    QujuApi.Errors.Activities.TemplateNotFound
  >;

  @post
  @route("/{activityId}/clone")
  @doc("克隆历史活动，活动存在且调用方有权克隆，复制基础信息为新草稿，报名数据和评价不会被复制。")
  cloneActivity(@path activityId: EntityId): APIResult<
    ActivityDetail,

      | QujuApi.Errors.Activities.ActivityNotVisible
      | QujuApi.Errors.Activities.ActivityPermissionDenied
  >;

  @post
  @route("/{activityId}/submit")
  @doc("提交活动审核，活动信息完整，进入 AI 或人工审核流程，超过 50 人或 AI 不确定时进入人工审核。")
  submitActivity(@path activityId: EntityId): APIResult<
    ActivityDetail,

      | QujuApi.Errors.Activities.ActivityNotVisible
      | QujuApi.Errors.Activities.ActivityPermissionDenied
      | QujuApi.Errors.Activities.ActivityStateNotSubmittable
      | QujuApi.Errors.Activities.InvalidActivitySchedule
  >;

  @get
  @route("/feed")
  @doc("获取首页活动信息流，tab 指定推荐、最新或附近，返回分页活动摘要，GET 参数全部位于 URL。")
  getFeed(
    @query tab: ActivityFeedTab,
    ...ActivitySearchQuery,
  ): APIResult<PageResult<ActivitySummary>>;

  @get
  @route("/search")
  @doc("高级搜索活动，查询条件通过 URL 传递，返回符合条件的活动，距离筛选必须提供中心点。")
  searchActivities(
    ...ActivitySearchQuery,
  ): APIResult<PageResult<ActivitySummary>>;

  @get
  @route("/map")
  @doc("获取地图模式点位，提供地图视野或中心点范围，返回活动点位，点位只包含地图展示所需摘要。")
  getMapPoints(...ActivitySearchQuery): APIResult<ActivityMapPoint[]>;

  @get
  @route("/{activityId}")
  @doc("获取活动详情，活动存在且调用方可见，返回完整展示信息，下架活动仅管理员或发起人可见。")
  getActivity(@path activityId: EntityId): APIResult<
    ActivityDetail,
    QujuApi.Errors.Activities.ActivityNotVisible
  >;

  @post
  @route("/{activityId}/registrations")
  @doc("报名活动，用户通过名额、信誉、年龄和安全须知确认校验，生成报名或候补，候补成功仍需二次确认。")
  registerActivity(
    @path activityId: EntityId,
    @body body: RegisterActivityRequest,
  ): APIResult<
    RegistrationResult,

      | QujuApi.Errors.Activities.ActivityNotVisible
      | QujuApi.Errors.Activities.RegistrationClosed
      | QujuApi.Errors.Activities.DuplicateRegistration
      | QujuApi.Errors.Activities.ReputationInsufficient
      | QujuApi.Errors.Activities.AgeRequirementNotMet
  >;

  @post
  @route("/{activityId}/registrations/cancel")
  @doc("取消报名，调用方已报名且未过报名截止，释放名额并触发候补递补，已结束活动不可取消报名。")
  cancelRegistration(@path activityId: EntityId): APIResult<
    RegistrationResult,

      | QujuApi.Errors.Activities.ActivityNotVisible
      | QujuApi.Errors.Activities.RegistrationNotFound
      | QujuApi.Errors.Activities.RegistrationClosed
  >;

  @post
  @route("/{activityId}/waiting-confirmations")
  @doc("确认候补名额，调用方处于候补确认窗口，确认则转为报名，拒绝或超时则顺延，确认窗口过期后不可确认。")
  confirmWaitingSeat(
    @path activityId: EntityId,
    @body body: WaitingConfirmationRequest,
  ): APIResult<
    RegistrationResult,

      | QujuApi.Errors.Activities.ActivityNotVisible
      | QujuApi.Errors.Activities.WaitingConfirmationUnavailable
  >;

  @post
  @route("/{activityId}/check-in-qrcode")
  @doc("生成签到二维码，调用方为活动发起人且活动临近开始，返回短期有效二维码 token，二维码过期后不可签到。")
  createCheckInQrCode(@path activityId: EntityId): APIResult<
    CheckInQrCode,

      | QujuApi.Errors.Activities.ActivityNotVisible
      | QujuApi.Errors.Activities.ActivityPermissionDenied
  >;

  @post
  @route("/{activityId}/check-ins")
  @doc("扫码签到，二维码有效且用户已报名，记录签到时间，需要位置校验时必须处于活动地点附近。")
  checkIn(@path activityId: EntityId, @body body: CheckInRequest): APIResult<
    CheckInRecord,

      | QujuApi.Errors.Activities.ActivityNotVisible
      | QujuApi.Errors.Activities.RegistrationNotFound
      | QujuApi.Errors.Activities.CheckInQrCodeInvalid
      | QujuApi.Errors.Activities.CheckInLocationInvalid
  >;

  @get
  @route("/{activityId}/check-ins")
  @doc("查看签到列表，调用方为活动发起人或管理员，返回报名与签到状态，列表不返回用户敏感凭据。")
  listCheckIns(@path activityId: EntityId, ...PageQuery): APIResult<
    PageResult<CheckInRecord>,

      | QujuApi.Errors.Activities.ActivityNotVisible
      | QujuApi.Errors.Activities.ActivityPermissionDenied
  >;

  @get
  @route("/{activityId}/check-ins/export")
  @doc("导出签到数据，调用方为活动发起人或管理员，返回二进制表格文件，该接口允许不使用 APIResponse 包装。")
  exportCheckIns(@path activityId: EntityId): bytes;

  @post
  @route("/{activityId}/summaries")
  @doc("发布活动图文总结，活动已结束且调用方为发起人，发布总结，AI 图片标签必须经人工确认。")
  createSummary(
    @path activityId: EntityId,
    @body body: ActivitySummaryPostRequest,
  ): APIResult<
    ActivitySummaryPost,

      | QujuApi.Errors.Activities.ActivityNotVisible
      | QujuApi.Errors.Activities.ActivityPermissionDenied
      | QujuApi.Errors.Activities.ActivityNotEnded
      | QujuApi.Errors.Activities.MediaFileUnavailable
  >;

  @post
  @route("/{activityId}/reviews")
  @doc("评价活动，调用方已参与且评价入口仍有效，保存评价，每个用户仅能评价一次。")
  reviewActivity(
    @path activityId: EntityId,
    @body body: ActivityReviewRequest,
  ): APIResult<
    ActivityReview,

      | QujuApi.Errors.Activities.ActivityNotVisible
      | QujuApi.Errors.Activities.ActivityNotEnded
      | QujuApi.Errors.Activities.RegistrationNotFound
      | QujuApi.Errors.Activities.DuplicateReview
  >;
}
