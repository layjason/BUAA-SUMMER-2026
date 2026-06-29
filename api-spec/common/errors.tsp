namespace QujuApi.Errors.Identity {
  @doc("10000：上传媒体用途不符合当前身份接口要求。")
  model InvalidMediaUsage
    extends APIErrorResponse<10000, "Media usage is invalid"> {}

  @doc("10001：注册邮箱已被占用。")
  model EmailAlreadyRegistered
    extends APIErrorResponse<10001, "Email is already registered"> {}

  @doc("10002：昵称已被占用或不可使用。")
  model NicknameUnavailable
    extends APIErrorResponse<10002, "Nickname is unavailable"> {}

  @doc("10003：登录凭据错误。")
  model InvalidCredentials
    extends APIErrorResponse<10003, "Email or password is invalid"> {}

  @doc("10004：账号尚未激活。")
  model AccountInactive
    extends APIErrorResponse<10004, "Account is inactive"> {}

  @doc("10005：账号已被封禁。")
  model AccountBanned extends APIErrorResponse<10005, "Account is banned"> {}

  @doc("10006：账号激活 token 无效、过期或已使用。")
  model InvalidActivationToken
    extends APIErrorResponse<10006, "Activation token is invalid"> {}

  @doc("10007：刷新 token 无效、过期或已撤销。")
  model InvalidRefreshToken
    extends APIErrorResponse<10007, "Refresh token is invalid"> {}

  @doc("10008：当前账号类型不允许执行该身份接口。")
  model UserKindMismatch
    extends APIErrorResponse<
      10008,
      "User kind is not allowed for this operation"
    > {}

  @doc("10009：商家资质申请状态不允许重复提交。")
  model MerchantQualificationAlreadySubmitted
    extends APIErrorResponse<
      10009,
      "Merchant qualification has already been submitted"
    > {}

  @doc("10010：引用的媒体文件不存在或不可用于当前账号。")
  model MediaFileUnavailable
    extends APIErrorResponse<10010, "Media file is unavailable"> {}

  @doc("10011：邮箱对应的账号不存在。")
  model EmailNotRegistered
    extends APIErrorResponse<10011, "Email is not registered"> {}

  @doc("10012：账号已经激活。")
  model AccountAlreadyActive
    extends APIErrorResponse<10012, "Account is already active"> {}

  @doc("10013：上传文件不是支持的 JPG 或 PNG 图像。")
  model ImageFormatInvalid
    extends APIErrorResponse<10013, "Image must be a JPG or PNG file"> {}

  @doc("10014：上传图像超过身份服务允许的大小。")
  model ImageTooLarge
    extends APIErrorResponse<10014, "Image file is too large"> {}

  @doc("10015：激活邮件重发过于频繁。")
  model ActivationEmailRateLimited
    extends APIErrorResponse<
      10015,
      "Activation email resend rate limit has been exceeded"
    > {}
}

namespace QujuApi.Errors.Activities {
  @doc("20000：活动图片媒体用途不符合活动接口要求。")
  model InvalidMediaUsage
    extends APIErrorResponse<20000, "Media usage is invalid"> {}

  @doc("20001：活动模板不存在。")
  model TemplateNotFound
    extends APIErrorResponse<20001, "Activity template does not exist"> {}

  @doc("20002：活动不存在、已下架或调用方不可见。")
  model ActivityNotVisible
    extends APIErrorResponse<20002, "Activity {activityId} is not visible"> {}

  @doc("20003：调用方不是活动发起人或管理员。")
  model ActivityPermissionDenied
    extends APIErrorResponse<20003, "Activity operation is not allowed"> {}

  @doc("20004：活动时间、截止时间或人数等业务字段非法。")
  model InvalidActivitySchedule
    extends APIErrorResponse<
      20004,
      "Activity schedule or capacity is invalid"
    > {}

  @doc("20005：活动当前状态不允许提交审核或修改。")
  model ActivityStateNotSubmittable
    extends APIErrorResponse<
      20005,
      "Activity state does not allow submission"
    > {}

  @doc("20006：活动已关闭报名。")
  model RegistrationClosed
    extends APIErrorResponse<20006, "Activity registration is closed"> {}

  @doc("20007：用户已经报名或已有有效候补记录。")
  model DuplicateRegistration
    extends APIErrorResponse<20007, "Registration already exists"> {}

  @doc("20008：用户信誉分不满足活动要求。")
  model ReputationInsufficient
    extends APIErrorResponse<20008, "User reputation is insufficient"> {}

  @doc("20009：用户年龄不满足活动要求。")
  model AgeRequirementNotMet
    extends APIErrorResponse<20009, "Age requirement is not met"> {}

  @doc("20010：活动安全须知未确认。")
  model SafetyNoticeNotAccepted
    extends APIErrorResponse<20010, "Safety notice must be accepted"> {}

  @doc("20011：当前用户没有可取消的报名或候补记录。")
  model RegistrationNotFound
    extends APIErrorResponse<20011, "Registration does not exist"> {}

  @doc("20012：候补确认窗口无效或已过期。")
  model WaitingConfirmationUnavailable
    extends APIErrorResponse<20012, "Waiting confirmation is unavailable"> {}

  @doc("20013：签到二维码无效或已过期。")
  model CheckInQrCodeInvalid
    extends APIErrorResponse<20013, "Check-in QR code is invalid"> {}

  @doc("20014：用户当前位置不满足签到位置校验。")
  model CheckInLocationInvalid
    extends APIErrorResponse<20014, "Check-in location is invalid"> {}

  @doc("20015：活动尚未结束，不能发布总结或评价。")
  model ActivityNotEnded
    extends APIErrorResponse<20015, "Activity has not ended"> {}

  @doc("20016：用户已经评价过该活动。")
  model DuplicateReview
    extends APIErrorResponse<20016, "Activity review already exists"> {}

  @doc("20017：活动媒体文件不存在或不可用于当前活动。")
  model MediaFileUnavailable
    extends APIErrorResponse<20017, "Media file is unavailable"> {}

  @doc("20018：上传文件不是支持的 JPG 或 PNG 图像。")
  model ImageFormatInvalid
    extends APIErrorResponse<20018, "Image must be a JPG or PNG file"> {}

  @doc("20019：上传图像超过活动服务允许的大小。")
  model ImageTooLarge
    extends APIErrorResponse<20019, "Image file is too large"> {}
}

namespace QujuApi.Errors.Ai {
  @doc("30000：AI 调用频率超过限制。")
  model AiRateLimited
    extends APIErrorResponse<30000, "AI rate limit has been exceeded"> {}

  @doc("30001：AI 服务暂不可用。")
  model AiServiceUnavailable
    extends APIErrorResponse<30001, "AI service is unavailable"> {}

  @doc("30002：AI 输出不可用或无法解析为契约结构。")
  model AiOutputUnavailable
    extends APIErrorResponse<30002, "AI output is unavailable"> {}

  @doc("30003：图片媒体文件不存在或不支持 AI 分类。")
  model ImageMediaUnavailable
    extends APIErrorResponse<30003, "Image media is unavailable"> {}
}

namespace QujuApi.Errors.Social {
  @doc("40000：目标用户不存在或不可见。")
  model UserNotVisible
    extends APIErrorResponse<40000, "User {userId} is not visible"> {}

  @doc("40001：当前用户与目标用户存在黑名单关系。")
  model BlacklistRelationExists
    extends APIErrorResponse<
      40001,
      "Blacklist relation blocks this operation"
    > {}

  @doc("40002：关注关系已存在。")
  model FollowAlreadyExists
    extends APIErrorResponse<40002, "Follow relation already exists"> {}

  @doc("40003：关注关系不存在。")
  model FollowNotFound
    extends APIErrorResponse<40003, "Follow relation does not exist"> {}

  @doc("40004：好友关系状态不允许当前操作。")
  model FriendshipStateInvalid
    extends APIErrorResponse<
      40004,
      "Friendship state does not allow this operation"
    > {}

  @doc("40005：好友申请状态不允许当前操作。")
  model FriendRequestStateInvalid
    extends APIErrorResponse<
      40005,
      "Friend request state does not allow this operation"
    > {}

  @doc("40006：重复好友申请。")
  model DuplicateFriendRequest
    extends APIErrorResponse<40006, "Friend request already exists"> {}

  @doc("40007：举报目标或举报内容不合法。")
  model UserReportInvalid
    extends APIErrorResponse<40007, "User report is invalid"> {}

  @doc("40008：小队名称已被占用。")
  model TeamNameUnavailable
    extends APIErrorResponse<40008, "Team name is unavailable"> {}

  @doc("40009：小队不存在、已解散或调用方不可见。")
  model TeamNotVisible
    extends APIErrorResponse<40009, "Team {teamId} is not visible"> {}

  @doc("40010：小队人数已满。")
  model TeamFull extends APIErrorResponse<40010, "Team is full"> {}

  @doc("40011：小队已解散或已停用。")
  model TeamUnavailable
    extends APIErrorResponse<40011, "Team is unavailable"> {}

  @doc("40012：用户已经是小队成员。")
  model TeamMemberAlreadyExists
    extends APIErrorResponse<40012, "Team member already exists"> {}

  @doc("40013：重复入队申请。")
  model DuplicateTeamJoinRequest
    extends APIErrorResponse<40013, "Team join request already exists"> {}

  @doc("40014：入队申请状态不允许当前操作。")
  model TeamJoinRequestStateInvalid
    extends APIErrorResponse<
      40014,
      "Team join request state does not allow this operation"
    > {}

  @doc("40015：调用方不是小队成员。")
  model TeamMemberNotFound
    extends APIErrorResponse<40015, "Team member does not exist"> {}

  @doc("40016：队长不能直接退出小队。")
  model TeamLeaderCannotLeave
    extends APIErrorResponse<40016, "Team leader cannot leave the team"> {}

  @doc("40017：小队角色变更不合法。")
  model TeamRoleChangeInvalid
    extends APIErrorResponse<40017, "Team role change is invalid"> {}

  @doc("40018：小队活动不存在或不属于该小队。")
  model TeamActivityNotVisible
    extends APIErrorResponse<40018, "Team activity is not visible"> {}

  @doc("40019：黑名单关系不存在。")
  model BlacklistRelationNotFound
    extends APIErrorResponse<40019, "Blacklist relation does not exist"> {}

  @doc("40020：调用方缺少小队管理权限。")
  model TeamPermissionDenied
    extends APIErrorResponse<40020, "Team operation is not allowed"> {}
}

namespace QujuApi.Errors.Chat {
  @doc("50000：上传媒体用途不符合聊天接口要求。")
  model InvalidMediaUsage
    extends APIErrorResponse<50000, "Media usage is invalid"> {}

  @doc("50001：小队不存在或调用方不可见。")
  model TeamNotVisible
    extends APIErrorResponse<50001, "Team {teamId} is not visible"> {}

  @doc("50002：调用方不是小队成员。")
  model TeamMemberRequired
    extends APIErrorResponse<50002, "Team membership is required"> {}

  @doc("50003：小队文件或相册媒体不存在。")
  model TeamMediaNotFound
    extends APIErrorResponse<50003, "Team media does not exist"> {}

  @doc("50004：会话不存在或调用方不可见。")
  model ConversationNotVisible
    extends APIErrorResponse<
      50004,
      "Conversation {conversationId} is not visible"
    > {}

  @doc("50005：调用方不是会话成员。")
  model ConversationMemberRequired
    extends APIErrorResponse<50005, "Conversation membership is required"> {}

  @doc("50006：消息内容与消息类型不匹配。")
  model MessageContentInvalid
    extends APIErrorResponse<
      50006,
      "Message content is invalid for its kind"
    > {}

  @doc("50007：消息不存在或调用方不可见。")
  model MessageNotVisible
    extends APIErrorResponse<50007, "Message {messageId} is not visible"> {}

  @doc("50008：调用方不是消息发送者。")
  model MessageSenderRequired
    extends APIErrorResponse<50008, "Message sender permission is required"> {}

  @doc("50009：消息已超过可撤回时间窗口。")
  model MessageRecallExpired
    extends APIErrorResponse<50009, "Message recall window has expired"> {}

  @doc("50010：转发目标会话不可用。")
  model ForwardTargetUnavailable
    extends APIErrorResponse<
      50010,
      "Forward target conversation is unavailable"
    > {}

  @doc("50011：调用方无权发布群公告。")
  model AnnouncementPermissionDenied
    extends APIErrorResponse<50011, "Announcement operation is not allowed"> {}

  @doc("50012：投票选项不满足业务规则。")
  model PollOptionsInvalid
    extends APIErrorResponse<50012, "Poll options are invalid"> {}

  @doc("50013：投票不存在、已截止或调用方不可参与。")
  model PollUnavailable
    extends APIErrorResponse<50013, "Poll is unavailable"> {}

  @doc("50014：调用方无权管理小队媒体文件。")
  model TeamMediaPermissionDenied
    extends APIErrorResponse<50014, "Team media operation is not allowed"> {}

  @doc("50015：上传文件不是支持的 JPG 或 PNG 图像。")
  model ImageFormatInvalid
    extends APIErrorResponse<50015, "Image must be a JPG or PNG file"> {}

  @doc("50016：上传图像超过即时通讯服务允许的大小。")
  model ImageTooLarge
    extends APIErrorResponse<50016, "Image file is too large"> {}
}

namespace QujuApi.Errors.Admin {
  @doc("60000：管理员登录凭据错误。")
  model InvalidAdminCredentials
    extends APIErrorResponse<60000, "Admin username or password is invalid"> {}

  @doc("60001：管理员旧密码错误。")
  model InvalidOldPassword
    extends APIErrorResponse<60001, "Old password is invalid"> {}

  @doc("60002：目标用户不存在。")
  model UserNotFound
    extends APIErrorResponse<60002, "User {userId} does not exist"> {}

  @doc("60003：用户已处于封禁状态。")
  model UserAlreadyBanned
    extends APIErrorResponse<60003, "User is already banned"> {}

  @doc("60004：用户未处于封禁状态。")
  model UserNotBanned extends APIErrorResponse<60004, "User is not banned"> {}

  @doc("60005：商家审核状态不允许当前操作。")
  model MerchantReviewStateInvalid
    extends APIErrorResponse<
      60005,
      "Merchant review state does not allow this operation"
    > {}

  @doc("60006：审核驳回、要求修改、下架或停用类操作缺少原因。")
  model ReviewReasonRequired
    extends APIErrorResponse<60006, "Review reason is required"> {}

  @doc("60007：举报记录不存在。")
  model UserReportNotFound
    extends APIErrorResponse<60007, "User report {reportId} does not exist"> {}

  @doc("60008：活动不存在。")
  model ActivityNotFound
    extends APIErrorResponse<60008, "Activity {activityId} does not exist"> {}

  @doc("60009：活动后台状态流转不合法。")
  model ActivityModerationStateInvalid
    extends APIErrorResponse<
      60009,
      "Activity moderation state does not allow this operation"
    > {}

  @doc("60010：小队不存在。")
  model TeamNotFound
    extends APIErrorResponse<60010, "Team {teamId} does not exist"> {}

  @doc("60011：小队后台状态流转不合法。")
  model TeamModerationStateInvalid
    extends APIErrorResponse<
      60011,
      "Team moderation state does not allow this operation"
    > {}
}
