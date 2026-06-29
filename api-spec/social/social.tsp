import "@typespec/http";

using TypeSpec.Http;

namespace QujuApi.Social;

@doc("好友申请来源。")
enum FriendRequestSource {
  @doc("通过个人资料页添加。")
  profile,

  @doc("通过活动参与者列表添加。")
  activityParticipants,

  @doc("通过兴趣小队添加。")
  team,

  @doc("通过扫描二维码添加。")
  qrCode,
}

@doc("好友申请状态。")
enum FriendRequestStatus {
  pending,
  accepted,
  rejected,
  canceled,
}

@doc("好友关系来源。")
enum FriendshipSource {
  manualRequest,
  mutualFollow,
}

@doc("举报处理状态。")
enum ReportStatus {
  pending,
  processing,
  resolved,
  rejected,
}

@doc("好友申请请求，双方不是好友且不存在黑名单关系，创建待处理申请，重复申请不会创建多条有效记录。")
model FriendRequestCreate {
  @doc("好友申请目标用户标识。")
  targetUserId: EntityId;

  @doc("好友申请发起来源。")
  source: FriendRequestSource;

  @doc("好友申请附言。")
  message?: string;
}

@doc("好友申请处理请求。")
model FriendRequestDecision {
  @doc("是否同意该好友申请。")
  accepted: boolean;
}

@doc("好友申请。")
model FriendRequest {
  @doc("好友申请标识。")
  requestId: EntityId;

  @doc("申请发起人用户标识。")
  requesterId: EntityId;

  @doc("申请接收人用户标识。")
  targetUserId: EntityId;

  @doc("好友申请来源。")
  source: FriendRequestSource;

  @doc("好友申请附言。")
  message?: string;

  @doc("好友申请状态。")
  status: FriendRequestStatus;

  @doc("申请创建时间。")
  createdAt: DateTimeString;
}

@doc("好友申请查询条件。")
model FriendRequestQuery extends PageQuery {
  @doc("好友申请状态筛选条件。")
  @query
  status?: FriendRequestStatus;
}

@doc("用户举报请求。")
model UserReportCreateRequest {
  @doc("被举报用户标识。")
  targetUserId: EntityId;

  @doc("举报理由文本。")
  reason: string;
}

@doc("用户举报记录。")
model UserReport {
  @doc("举报记录标识。")
  reportId: EntityId;

  @doc("举报人用户标识。")
  reporterUserId: EntityId;

  @doc("被举报用户标识。")
  targetUserId: EntityId;

  @doc("举报理由文本。")
  reason: string;

  @doc("举报处理状态。")
  status: ReportStatus;

  @doc("后台处理说明。")
  handlingNote?: string;

  @doc("举报创建时间。")
  createdAt: DateTimeString;

  @doc("举报处理时间。")
  handledAt?: DateTimeString;
}

@doc("用户举报查询条件。")
model UserReportQuery extends PageQuery {
  @doc("举报处理状态筛选条件。")
  @query
  status?: ReportStatus;
}

@doc("好友信息。")
model FriendItem {
  @doc("好友用户标识。")
  userId: EntityId;

  @doc("好友昵称。")
  nickname: string;

  @doc("好友头像。")
  avatar?: MediaFile;

  @doc("当前用户为该好友设置的备注。")
  remark?: string;

  @doc("当前用户为该好友设置的分组标签。")
  groupTags: string[];

  @doc("好友关系形成方式。")
  source: FriendshipSource;
}

@doc("好友备注更新请求，双方为好友，更新调用方侧备注与分组，不会修改对方资料。")
model FriendRemarkUpdate {
  @doc("新的好友备注。")
  remark?: string;

  @doc("新的好友分组标签。")
  groupTags?: string[];
}

@doc("关注关系结果。")
model FollowRelation {
  @doc("被关注或取消关注的目标用户标识。")
  targetUserId: EntityId;

  @doc("当前用户是否正在关注目标用户。")
  following: boolean;

  @doc("双方是否互相关注。")
  mutual: boolean;

  @doc("本次操作是否创建了好友关系。")
  friendshipCreated: boolean;
}

@doc("关注/粉丝列表项。")
model FollowItem {
  @doc("用户标识。")
  userId: EntityId;

  @doc("用户昵称。")
  nickname: string;

  @doc("用户头像。")
  avatar?: MediaFile;

  @doc("关注时间。")
  followedAt: DateTimeString;

  @doc("双方是否互相关注。")
  mutual: boolean;
}

@doc("黑名单项。")
model BlacklistItem {
  @doc("被屏蔽用户标识。")
  userId: EntityId;

  @doc("被屏蔽用户昵称。")
  nickname: string;

  @doc("被屏蔽用户头像。")
  avatar?: MediaFile;

  @doc("屏蔽时间。")
  blockedAt: DateTimeString;
}

@doc("小队加入方式。")
enum TeamJoinMode {
  publicJoin,
  approvalRequired,
}

@doc("小队成员角色。")
enum TeamMemberRole {
  leader,
  admin,
  member,
}

@doc("小队状态。")
enum TeamStatus {
  active,
  dissolved,
  disabled,
}

@doc("入队申请状态。")
enum TeamJoinRequestStatus {
  pending,
  accepted,
  rejected,
  canceled,
}

@doc("小队创建请求，名称可用且人数上限为正数，创建小队并自动生成群聊，创建者成为队长。")
model TeamCreateRequest {
  @doc("小队名称。")
  name: string;

  @doc("小队兴趣标签。")
  tags: string[];

  @doc("小队加入方式。")
  joinMode: TeamJoinMode;

  @doc("小队人数上限。")
  capacity: int32;

  @doc("小队简介。")
  description?: string;

  @doc("小队头像媒体文件标识。")
  avatarMediaId?: EntityId;
}

@doc("小队资料。")
model TeamProfile {
  @doc("小队标识。")
  teamId: EntityId;

  @doc("小队名称。")
  name: string;

  @doc("小队兴趣标签。")
  tags: string[];

  @doc("小队加入方式。")
  joinMode: TeamJoinMode;

  @doc("小队人数上限。")
  capacity: int32;

  @doc("当前成员数量。")
  memberCount: int32;

  @doc("小队简介。")
  description?: string;

  @doc("小队头像媒体文件。")
  avatar?: MediaFile;

  @doc("小队状态。")
  status: TeamStatus;

  @doc("队长用户标识。")
  leaderId: EntityId;

  @doc("小队群聊会话标识。")
  chatId: EntityId;
}

@doc("小队成员。")
model TeamMember {
  @doc("成员用户标识。")
  userId: EntityId;

  @doc("成员昵称。")
  nickname: string;

  @doc("成员头像。")
  avatar?: MediaFile;

  @doc("成员在小队中的角色。")
  role: TeamMemberRole;

  @doc("成员小队积分。")
  points: int32;

  @doc("成员加入小队时间。")
  joinedAt: DateTimeString;
}

@doc("加入小队申请。")
model TeamJoinRequest {
  @doc("入队申请标识。")
  requestId: EntityId;

  @doc("小队标识。")
  teamId: EntityId;

  @doc("申请人用户标识。")
  userId: EntityId;

  @doc("入队申请附言。")
  message?: string;

  @doc("入队申请状态。")
  status: TeamJoinRequestStatus;

  @doc("申请创建时间。")
  createdAt: DateTimeString;
}

@doc("入队申请查询条件。")
model TeamJoinRequestQuery extends PageQuery {
  @doc("入队申请状态筛选条件。")
  @query
  status?: TeamJoinRequestStatus;
}

@doc("加入小队请求，小队存在且未满员未解散，公开小队直接加入，审核小队创建申请，黑名单关系不得加入。")
model JoinTeamRequest {
  @doc("入队申请附言。")
  message?: string;
}

@doc("入队申请处理请求。")
model TeamJoinRequestDecision {
  @doc("是否同意该入队申请。")
  accepted: boolean;
}

@doc("小队积分项。")
model TeamPointRankItem {
  @doc("积分排名。")
  rank: int32;

  @doc("成员用户标识。")
  userId: EntityId;

  @doc("成员昵称。")
  nickname: string;

  @doc("成员积分。")
  points: int32;
}

@route("/social")
@tag("好友社群")
interface SocialOperations {
  @get
  @route("/profiles/{userId}")
  @doc("查看个人主页，用户存在且未被屏蔽，返回公开资料，不返回敏感账号信息。")
  getUserProfile(@path userId: EntityId): APIResult<
    QujuApi.Identity.PublicUserProfile,

      | QujuApi.Errors.Social.UserNotVisible
      | QujuApi.Errors.Social.BlacklistRelationExists
  >;

  @post
  @route("/follows/{targetUserId}")
  @doc("关注用户，目标用户存在且不存在黑名单关系，创建关注关系，互关时可升级为好友，手动添加好友不因单方取消关注自动删除。")
  followUser(@path targetUserId: EntityId): APIResult<
    FollowRelation,

      | QujuApi.Errors.Social.UserNotVisible
      | QujuApi.Errors.Social.BlacklistRelationExists
      | QujuApi.Errors.Social.FollowAlreadyExists
  >;

  @delete
  @route("/follows/{targetUserId}")
  @doc("取消关注用户，已关注目标用户，取消关注并同步互关关系，仅由互关形成的好友关系随互关解除。")
  unfollowUser(@path targetUserId: EntityId): APIResult<
    FollowRelation,
    QujuApi.Errors.Social.UserNotVisible | QujuApi.Errors.Social.FollowNotFound
  >;

  @get
  @route("/follows")
  @doc("获取我关注的用户列表，调用方已登录，分页返回已关注用户，按关注时间倒序。")
  listFollows(...PageQuery): APIResult<PageResult<FollowItem>>;

  @get
  @route("/followers")
  @doc("获取我的粉丝列表，调用方已登录，分页返回关注我的用户，按关注时间倒序。")
  listFollowers(...PageQuery): APIResult<PageResult<FollowItem>>;

  @post
  @route("/friend-requests")
  @doc("发送好友申请，双方不是好友且不存在黑名单关系，创建待处理申请，异常关系不可重复申请。")
  createFriendRequest(@body body: FriendRequestCreate): APIResult<
    FriendRequest,

      | QujuApi.Errors.Social.UserNotVisible
      | QujuApi.Errors.Social.BlacklistRelationExists
      | QujuApi.Errors.Social.FriendshipStateInvalid
      | QujuApi.Errors.Social.DuplicateFriendRequest
  >;

  @get
  @route("/friend-requests/sent")
  @doc("查看我发送的好友申请，返回申请状态和分页结果。")
  listSentFriendRequests(
    ...FriendRequestQuery,
  ): APIResult<PageResult<FriendRequest>>;

  @get
  @route("/friend-requests/received")
  @doc("查看我收到的好友申请，返回待处理和历史申请的分页结果。")
  listReceivedFriendRequests(
    ...FriendRequestQuery,
  ): APIResult<PageResult<FriendRequest>>;

  @post
  @route("/friend-requests/{requestId}/decision")
  @doc("处理好友申请，调用方为接收人且申请待处理，同意时建立好友关系，拒绝时结束申请，处理结果不可重复提交。")
  decideFriendRequest(
    @path requestId: EntityId,
    @body body: FriendRequestDecision,
  ): APIResult<FriendRequest, QujuApi.Errors.Social.FriendRequestStateInvalid>;

  @get
  @route("/friends")
  @doc("获取好友列表，调用方已登录，分页返回好友，删除好友后双方关系解除。")
  listFriends(...PageQuery): APIResult<PageResult<FriendItem>>;

  @patch
  @route("/friends/{userId}")
  @doc("更新好友备注和分组，双方为好友，更新调用方本地好友信息，不会影响对方看到的资料。")
  updateFriendRemark(
    @path userId: EntityId,
    @body body: FriendRemarkUpdate,
  ): APIResult<FriendItem, QujuApi.Errors.Social.FriendshipStateInvalid>;

  @delete
  @route("/friends/{userId}")
  @doc("删除好友，双方为好友，双向好友关系解除，历史消息不因此删除。")
  deleteFriend(@path userId: EntityId): APIResult<
    EmptyData,
    QujuApi.Errors.Social.FriendshipStateInvalid
  >;

  @get
  @route("/blacklist")
  @doc("查看黑名单列表，调用方已登录，分页返回已屏蔽用户信息。")
  listBlacklist(...PageQuery): APIResult<PageResult<BlacklistItem>>;

  @post
  @route("/blacklist/{targetUserId}")
  @doc("加入黑名单，目标用户存在，阻止关注、好友申请和小队加入等关系动作，黑名单关系优先于好友关系校验。")
  blockUser(@path targetUserId: EntityId): APIResult<
    EmptyData,

      | QujuApi.Errors.Social.UserNotVisible
      | QujuApi.Errors.Social.BlacklistRelationExists
  >;

  @delete
  @route("/blacklist/{targetUserId}")
  @doc("移出黑名单，目标用户已在黑名单，解除屏蔽，不会自动恢复历史好友关系。")
  unblockUser(@path targetUserId: EntityId): APIResult<
    EmptyData,
    QujuApi.Errors.Social.BlacklistRelationNotFound
  >;

  @post
  @route("/user-reports")
  @doc("举报用户，调用方填写被举报用户和举报理由，举报进入后台处理流程。")
  createUserReport(@body body: UserReportCreateRequest): APIResult<
    UserReport,

      | QujuApi.Errors.Social.UserNotVisible
      | QujuApi.Errors.Social.UserReportInvalid
  >;

  @get
  @route("/user-reports")
  @doc("查看我提交的用户举报，返回举报理由、处理状态和处理说明。")
  listMyUserReports(...UserReportQuery): APIResult<PageResult<UserReport>>;

  @post
  @route("/teams")
  @doc("创建兴趣小队，调用方已登录且名称合法，创建小队、队长成员和群聊，创建者自动为队长。")
  createTeam(@body body: TeamCreateRequest): APIResult<
    TeamProfile,
    QujuApi.Errors.Social.TeamNameUnavailable
  >;

  @get
  @route("/teams")
  @doc("搜索兴趣小队，查询条件通过 URL 传递，返回可发现小队，已解散小队不出现在发现列表。")
  searchTeams(
    @query keyword?: string,
    @query tags?: string[],
    ...PageQuery,
  ): APIResult<PageResult<TeamProfile>>;

  @post
  @route("/teams/{teamId}/join")
  @doc("加入兴趣小队，小队未满员、未解散且无黑名单关系，公开小队加入或审核小队创建申请，加入小队同步加入群聊。")
  joinTeam(@path teamId: EntityId, @body body: JoinTeamRequest): APIResult<
    TeamJoinRequest,

      | QujuApi.Errors.Social.TeamNotVisible
      | QujuApi.Errors.Social.TeamUnavailable
      | QujuApi.Errors.Social.TeamFull
      | QujuApi.Errors.Social.BlacklistRelationExists
      | QujuApi.Errors.Social.TeamMemberAlreadyExists
      | QujuApi.Errors.Social.DuplicateTeamJoinRequest
  >;

  @post
  @route("/teams/{teamId}/leave")
  @doc("退出兴趣小队，调用方为小队普通成员或管理员，退出小队并同步退出群聊，队长不可退出（需先转让队长或解散小队）。")
  leaveTeam(@path teamId: EntityId): APIResult<
    EmptyData,

      | QujuApi.Errors.Social.TeamNotVisible
      | QujuApi.Errors.Social.TeamMemberNotFound
      | QujuApi.Errors.Social.TeamLeaderCannotLeave
  >;

  @get
  @route("/teams/{teamId}/join-requests")
  @doc("查看小队入队申请列表，队长或管理员可按申请状态筛选。")
  listTeamJoinRequests(
    @path teamId: EntityId,
    ...TeamJoinRequestQuery,
  ): APIResult<
    PageResult<TeamJoinRequest>,

      | QujuApi.Errors.Social.TeamNotVisible
      | QujuApi.Errors.Social.TeamPermissionDenied
  >;

  @post
  @route("/teams/{teamId}/join-requests/{requestId}/decision")
  @doc("审核入队申请，调用方为队长或管理员，同意时加入小队和群聊，满员后不可继续同意申请。")
  decideTeamJoinRequest(
    @path teamId: EntityId,
    @path requestId: EntityId,
    @body body: TeamJoinRequestDecision,
  ): APIResult<
    TeamJoinRequest,

      | QujuApi.Errors.Social.TeamNotVisible
      | QujuApi.Errors.Social.TeamPermissionDenied
      | QujuApi.Errors.Social.TeamFull
      | QujuApi.Errors.Social.TeamJoinRequestStateInvalid
  >;

  @get
  @route("/teams/{teamId}/members")
  @doc("获取小队成员，调用方可查看该小队，返回成员与角色，角色只能为队长、管理员、普通成员。")
  listTeamMembers(@path teamId: EntityId, ...PageQuery): APIResult<
    PageResult<TeamMember>,

      | QujuApi.Errors.Social.TeamNotVisible
      | QujuApi.Errors.Social.TeamMemberNotFound
  >;

  @post
  @route("/teams/{teamId}/activities")
  @doc("创建队内活动，队长或管理员在小队内发布活动，活动字段复用普通活动创建结构。")
  createTeamActivity(
    @path teamId: EntityId,
    @body body: QujuApi.Activities.ActivityUpsertRequest,
  ): APIResult<
    QujuApi.Activities.ActivityDetail,

      | QujuApi.Errors.Social.TeamNotVisible
      | QujuApi.Errors.Social.TeamPermissionDenied
      | QujuApi.Errors.Activities.InvalidActivitySchedule
      | QujuApi.Errors.Activities.MediaFileUnavailable
  >;

  @get
  @route("/teams/{teamId}/activities")
  @doc("查看小队队内活动列表，小队成员可分页查看队内活动摘要。")
  listTeamActivities(@path teamId: EntityId, ...PageQuery): APIResult<
    PageResult<QujuApi.Activities.ActivitySummary>,

      | QujuApi.Errors.Social.TeamNotVisible
      | QujuApi.Errors.Social.TeamMemberNotFound
  >;

  @get
  @route("/teams/{teamId}/activities/{activityId}")
  @doc("查看队内活动详情，小队成员可查看指定队内活动的完整信息。")
  getTeamActivity(
    @path teamId: EntityId,
    @path activityId: EntityId,
  ): APIResult<
    QujuApi.Activities.ActivityDetail,

      | QujuApi.Errors.Social.TeamNotVisible
      | QujuApi.Errors.Social.TeamMemberNotFound
      | QujuApi.Errors.Social.TeamActivityNotVisible
  >;

  @patch
  @route("/teams/{teamId}/members/{memberId}/role")
  @doc("调整小队成员角色，调用方为队长，成员角色更新，队长身份转移必须保证小队始终有且只有一个队长。")
  updateTeamMemberRole(
    @path teamId: EntityId,
    @path memberId: EntityId,
    @body body: TeamMemberRoleUpdate,
  ): APIResult<
    TeamMember,

      | QujuApi.Errors.Social.TeamNotVisible
      | QujuApi.Errors.Social.TeamPermissionDenied
      | QujuApi.Errors.Social.TeamMemberNotFound
      | QujuApi.Errors.Social.TeamRoleChangeInvalid
  >;

  @delete
  @route("/teams/{teamId}")
  @doc("解散小队，调用方为队长，小队解散，群聊和队内活动停止使用，解散后不再显示于发现列表。")
  dissolveTeam(@path teamId: EntityId): APIResult<
    EmptyData,

      | QujuApi.Errors.Social.TeamNotVisible
      | QujuApi.Errors.Social.TeamPermissionDenied
  >;

  @get
  @route("/teams/{teamId}/points")
  @doc("查看小队积分榜，调用方可查看该小队，返回积分排名，排名由成员积分降序生成。")
  getTeamPointRanks(@path teamId: EntityId, ...PageQuery): APIResult<
    PageResult<TeamPointRankItem>,

      | QujuApi.Errors.Social.TeamNotVisible
      | QujuApi.Errors.Social.TeamMemberNotFound
  >;
}

@doc("小队成员角色更新请求。")
model TeamMemberRoleUpdate {
  @doc("调整后的成员角色。")
  role: TeamMemberRole;
}
