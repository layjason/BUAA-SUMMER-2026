import "@typespec/http";

using TypeSpec.Http;

namespace QujuApi.Admin;

@doc("管理员登录请求，管理员账号由系统预置，认证成功后返回 JWT，后台不提供管理员注册。")
model AdminLoginRequest {
  @doc("管理员登录名。")
  username: string;

  @doc("管理员登录密码。")
  password: string;
}

@doc("管理员改密请求，调用方已登录且旧密码正确，密码更新并可使旧令牌失效，新密码只保存加盐哈希。")
model AdminChangePasswordRequest {
  @doc("当前密码。")
  oldPassword: string;

  @doc("新密码。")
  newPassword: string;
}

@doc("管理员登录响应，管理员账号由 admins 表管理，不返回 UserKind（管理员身份已隐含）。")
model AdminLoginResponse {
  @doc("管理员标识。")
  userId: EntityId;

  @doc("登录成功后签发的令牌对。")
  tokens: QujuApi.Identity.TokenPair;
}

@doc("用户管理查询条件。")
model AdminUserQuery extends PageQuery {
  @doc("按邮箱、昵称或商家名称搜索的关键词。")
  @query
  keyword?: string;

  @doc("用户类型筛选条件。")
  @query
  kind?: QujuApi.Identity.UserKind;

  @doc("账号状态筛选条件。")
  @query
  status?: QujuApi.Identity.AccountStatus;

  @doc("商家资质审核状态筛选条件，仅 kind=merchant 时有意义。")
  @query
  qualificationStatus?: QujuApi.Identity.QualificationStatus;
}

@doc("后台用户摘要。")
model AdminUserSummary {
  @doc("用户标识。")
  userId: EntityId;

  @doc("用户邮箱。")
  email: string;

  @doc("用户昵称或商家展示昵称。")
  nickname?: string;

  @doc("用户类型。")
  kind: QujuApi.Identity.UserKind;

  @doc("账号状态。")
  status: QujuApi.Identity.AccountStatus;

  @doc("商家资质审核状态。非商家用户为空。")
  qualificationStatus?: QujuApi.Identity.QualificationStatus;

  @doc("用户发布的活动数量。")
  activityCount: int32;

  @doc("用户创建的小队数量。")
  teamCount: int32;

  @doc("账号创建时间。")
  createdAt: DateTimeString;
}

@doc("后台用户详情。")
model AdminUserDetail {
  ...AdminUserSummary;

  @doc("当前封禁信息。用户未封禁时为空。")
  currentBanInfo?: AdminBanInfo;
}

@doc("后台用户当前封禁信息。")
model AdminBanInfo {
  @doc("封禁原因。")
  reason: string;

  @doc("封禁截止时间。")
  bannedUntil: DateTimeString;

  @doc("封禁记录创建时间。")
  createdAt: DateTimeString;

  @doc("操作管理员标识。")
  operatorId: EntityId;
}

@doc("封禁请求，目标用户存在且未被封禁，用户在期限内不能登录，必须填写封禁原因和期限。")
model BanUserRequest {
  @doc("封禁原因。")
  reason: string;

  @doc("封禁截止时间。")
  bannedUntil: DateTimeString;
}

@doc("商家审核请求，商家申请存在且待审核，更新商家审核状态，驳回时必须填写原因。")
model MerchantReviewRequest {
  @doc("是否通过商家审核。")
  approved: boolean;

  @doc("驳回原因或审核说明。")
  reason?: string;
}

@doc("活动后台查询条件。")
model AdminActivityQuery extends PageQuery {
  @doc("按活动名称或发起人搜索的关键词。")
  @query
  keyword?: string;

  @doc("活动审核状态筛选条件。")
  @query
  reviewStatus?: QujuApi.Activities.ActivityReviewStatus;

  @doc("活动进行状态筛选条件。")
  @query
  runtimeStatus?: QujuApi.Activities.ActivityRuntimeStatus;

  @doc("按活动发起人用户标识筛选。")
  @query
  organizerId?: EntityId;
}

@doc("下架或恢复活动请求，活动存在，活动状态按动作迁移，下架必须填写原因。")
model ActivityModerationRequest {
  @doc("活动下架或恢复说明。")
  reason: string;
}

@doc("小队后台查询条件。")
model AdminTeamQuery extends PageQuery {
  @doc("按小队名称或标签搜索的关键词。")
  @query
  keyword?: string;

  @doc("小队状态筛选条件。")
  @query
  status?: QujuApi.Social.TeamStatus;

  @doc("按小队创建者用户标识筛选。")
  @query
  creatorId?: EntityId;

  @doc("按当前队长用户标识筛选。")
  @query
  leaderId?: EntityId;

  @doc("按成员用户标识筛选。")
  @query
  memberUserId?: EntityId;
}

@doc("后台举报查询条件。")
model AdminReportQuery extends PageQuery {
  @doc("举报处理状态筛选条件。")
  @query
  status?: QujuApi.Social.ReportStatus;

  @doc("举报人用户标识筛选条件。")
  @query
  reporterUserId?: EntityId;

  @doc("被举报对象类型筛选条件。")
  @query
  targetType?: QujuApi.Social.ReportTargetType;

  @doc("被举报对象标识筛选条件。")
  @query
  targetId?: EntityId;
}

@doc("举报处理请求。")
model ReportDecisionRequest {
  @doc("更新后的举报处理状态。")
  status: QujuApi.Social.ReportStatus;

  @doc("后台处理说明。")
  handlingNote: string;
}

@doc("停用或恢复小队请求，小队存在，小队状态按动作迁移，停用必须填写原因。")
model TeamModerationRequest {
  @doc("小队停用或恢复说明。")
  reason: string;
}

@doc("后台治理动作。")
enum AdminModerationAction {
  banUser,
  unbanUser,
  reviewActivity,
  takeDownActivity,
  restoreActivity,
  disableTeam,
  restoreTeam,
  reviewMerchant,
  handleReport,
}

@doc("后台治理记录。")
model AdminModerationRecord {
  @doc("治理记录标识。")
  recordId: EntityId;

  @doc("治理动作。")
  action: AdminModerationAction;

  @doc("治理原因或说明。")
  reason: string;

  @doc("操作管理员标识。")
  operatorId: EntityId;

  @doc("治理记录创建时间。")
  createdAt: DateTimeString;
}

@doc("后台小队详情。")
model AdminTeamDetail {
  ...QujuApi.Social.TeamProfile;

  @doc("小队停用、恢复等后台治理记录。")
  moderationRecords: AdminModerationRecord[];
}

@route("/admin")
@tag("后台管理")
interface AdminOperations {
  @post
  @useAuth(NoAuth)
  @route("/auth/login")
  @doc("管理员登录，账号由系统预置且未禁用，返回后台 JWT，不提供管理员注册。")
  login(@body body: AdminLoginRequest): APIResult<
    AdminLoginResponse,
    QujuApi.Errors.Admin.InvalidAdminCredentials
  >;

  @post
  @route("/auth/password")
  @doc("管理员修改密码，旧密码正确，密码更新，密码只保存加盐哈希。")
  changePassword(@body body: AdminChangePasswordRequest): APIResult<
    EmptyData,
    QujuApi.Errors.Admin.InvalidOldPassword
  >;

  @get
  @route("/users")
  @doc("查询用户，管理员已登录，分页返回个人和商家用户，管理员不得修改用户资料和密码。")
  listUsers(...AdminUserQuery): APIResult<PageResult<AdminUserSummary>>;

  @get
  @route("/users/{userId}")
  @doc("获取后台用户详情，管理员查看用户基本信息、账号状态和当前封禁信息，不修改用户资料。")
  getUser(@path userId: EntityId): APIResult<
    AdminUserDetail,
    QujuApi.Errors.Admin.UserNotFound
  >;

  @get
  @route("/users/{userId}/activities")
  @doc("查询指定用户发布的活动，管理员按用户标识查看活动列表。")
  listUserActivities(@path userId: EntityId, ...PageQuery): APIResult<
    PageResult<QujuApi.Activities.ActivitySummary>,
    QujuApi.Errors.Admin.UserNotFound
  >;

  @get
  @route("/users/{userId}/teams")
  @doc("查询指定用户创建或参与的小队，管理员按用户标识查看小队列表。")
  listUserTeams(@path userId: EntityId, ...PageQuery): APIResult<
    PageResult<QujuApi.Social.TeamProfile>,
    QujuApi.Errors.Admin.UserNotFound
  >;

  @get
  @route("/reports")
  @doc("查询举报，管理员可按处理状态、举报人、被举报对象类型和被举报对象标识筛选。")
  listReports(
    ...AdminReportQuery,
  ): APIResult<PageResult<QujuApi.Social.Report>>;

  @post
  @route("/reports/{reportId}/decision")
  @doc("处理举报，管理员更新举报处理状态并填写处理说明。")
  decideReport(
    @path reportId: EntityId,
    @body body: ReportDecisionRequest,
  ): APIResult<QujuApi.Social.Report, QujuApi.Errors.Admin.ReportNotFound>;

  @post
  @route("/users/{userId}/ban")
  @doc("封禁用户，目标用户存在，用户在封禁期限内不能登录，必须记录封禁原因。")
  banUser(@path userId: EntityId, @body body: BanUserRequest): APIResult<
    AdminUserSummary,

      | QujuApi.Errors.Admin.UserNotFound
      | QujuApi.Errors.Admin.UserAlreadyBanned
      | QujuApi.Errors.Admin.ReviewReasonRequired
  >;

  @post
  @route("/users/{userId}/unban")
  @doc("解封用户，目标用户处于封禁状态，恢复正常登录和使用权限，原有账号数据不受影响。")
  unbanUser(@path userId: EntityId): APIResult<
    AdminUserSummary,
    QujuApi.Errors.Admin.UserNotFound | QujuApi.Errors.Admin.UserNotBanned
  >;

  @get
  @route("/merchants/{merchantId}")
  @doc("获取商家资料和资质详情，管理员用于查看营业执照或营业凭证、审核状态和驳回原因。")
  getMerchant(
    @path merchantId: EntityId,
  ): APIResponse<QujuApi.Identity.MerchantProfile>;

  @post
  @route("/merchants/{merchantId}/review")
  @doc("审核商家申请，商家处于待审核状态，通过或驳回商家身份，驳回必须填写原因。")
  reviewMerchant(
    @path merchantId: EntityId,
    @body body: MerchantReviewRequest,
  ): APIResult<
    QujuApi.Identity.MerchantProfile,

      | QujuApi.Errors.Admin.UserNotFound
      | QujuApi.Errors.Admin.MerchantReviewStateInvalid
      | QujuApi.Errors.Admin.ReviewReasonRequired
  >;

  @get
  @route("/activities")
  @doc("查询全部活动，管理员已登录，按状态筛选活动，可查询已下架活动。")
  listActivities(
    ...AdminActivityQuery,
  ): APIResult<PageResult<QujuApi.Activities.ActivitySummary>>;

  @get
  @route("/activities/{activityId}")
  @doc("获取后台活动详情，管理员用于查看完整活动内容、AI 风险提示、人工审核记录和处理原因。")
  getActivity(
    @path activityId: EntityId,
  ): APIResponse<QujuApi.Activities.ActivityDetail>;

  @post
  @route("/activities/{activityId}/review")
  @doc("管理员提交通过、驳回或要求修改的处理结果，驳回或要求修改必须填写原因。")
  reviewActivity(
    @path activityId: EntityId,
    @body body: ReviewDecisionRequest,
  ): APIResult<
    QujuApi.Activities.ActivityDetail,

      | QujuApi.Errors.Admin.ActivityNotFound
      | QujuApi.Errors.Admin.ActivityModerationStateInvalid
      | QujuApi.Errors.Admin.ReviewReasonRequired
  >;

  @post
  @route("/activities/{activityId}/take-down")
  @doc("下架活动，活动已发布且存在违规问题，活动从用户发现列表移除，必须填写下架原因。")
  takeDownActivity(
    @path activityId: EntityId,
    @body body: ActivityModerationRequest,
  ): APIResult<
    QujuApi.Activities.ActivityDetail,

      | QujuApi.Errors.Admin.ActivityNotFound
      | QujuApi.Errors.Admin.ActivityModerationStateInvalid
      | QujuApi.Errors.Admin.ReviewReasonRequired
  >;

  @post
  @route("/activities/{activityId}/restore")
  @doc("恢复活动，违规问题已处理，活动恢复可见，恢复动作保留历史下架记录。")
  restoreActivity(@path activityId: EntityId): APIResult<
    QujuApi.Activities.ActivityDetail,

      | QujuApi.Errors.Admin.ActivityNotFound
      | QujuApi.Errors.Admin.ActivityModerationStateInvalid
  >;

  @get
  @route("/teams")
  @doc("查询全部小队，管理员已登录，返回小队基础信息、状态和成员统计，管理员不得直接修改小队名称、标签、公告和成员身份。")
  listTeams(
    ...AdminTeamQuery,
  ): APIResult<PageResult<QujuApi.Social.TeamProfile>>;

  @get
  @route("/teams/{teamId}")
  @doc("获取后台小队详情，管理员查看小队基础信息和后台治理记录，不直接修改小队资料。")
  getTeam(@path teamId: EntityId): APIResult<
    AdminTeamDetail,
    QujuApi.Errors.Admin.TeamNotFound
  >;

  @get
  @route("/teams/{teamId}/members")
  @doc("查询后台小队成员，管理员查看成员信息和成员身份，不直接修改成员身份。")
  listTeamMembers(@path teamId: EntityId, ...PageQuery): APIResult<
    PageResult<QujuApi.Social.TeamMember>,
    QujuApi.Errors.Admin.TeamNotFound
  >;

  @get
  @route("/teams/{teamId}/activities")
  @doc("查询后台小队队内活动，管理员查看队内活动列表。")
  listTeamActivities(@path teamId: EntityId, ...PageQuery): APIResult<
    PageResult<QujuApi.Activities.ActivitySummary>,
    QujuApi.Errors.Admin.TeamNotFound
  >;

  @get
  @route("/teams/{teamId}/reports")
  @doc("查询后台小队相关举报，管理员查看 targetType=team 且 targetId 为该小队的举报记录。")
  listTeamReports(@path teamId: EntityId, ...PageQuery): APIResult<
    PageResult<QujuApi.Social.Report>,
    QujuApi.Errors.Admin.TeamNotFound
  >;

  @post
  @route("/teams/{teamId}/disable")
  @doc("停用小队，小队存在违规问题，禁止新增成员、活动和内容，历史信息仍可被原成员查看。")
  disableTeam(
    @path teamId: EntityId,
    @body body: TeamModerationRequest,
  ): APIResult<
    QujuApi.Social.TeamProfile,

      | QujuApi.Errors.Admin.TeamNotFound
      | QujuApi.Errors.Admin.TeamModerationStateInvalid
      | QujuApi.Errors.Admin.ReviewReasonRequired
  >;

  @post
  @route("/teams/{teamId}/restore")
  @doc("恢复小队，违规问题已处理，小队恢复正常功能，恢复动作保留历史停用记录。")
  restoreTeam(@path teamId: EntityId): APIResult<
    QujuApi.Social.TeamProfile,

      | QujuApi.Errors.Admin.TeamNotFound
      | QujuApi.Errors.Admin.TeamModerationStateInvalid
  >;
}
