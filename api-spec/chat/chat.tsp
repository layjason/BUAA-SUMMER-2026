import "@typespec/http";

using TypeSpec.Http;

namespace QujuApi.Chat;

@doc("会话类型。")
enum ConversationKind {
  friend,
  team,
}

@doc("消息类型。")
enum MessageKind {
  text,
  image,
  location,
}

@doc("消息读取状态。")
enum MessageReadStatus {
  unread,
  read,
}

@doc("聊天实时事件类型。friendRequestCreated 事件通过 /queue/social-events 推送，不在此枚举内。")
enum ChatRealtimeEventKind {
  messageCreated,
  messageRecalled,
  messageForwarded,
}

@doc("会话摘要。")
model ConversationSummary {
  @doc("会话标识。")
  conversationId: EntityId;

  @doc("会话类型，好友会话或小队群聊。")
  kind: ConversationKind;

  @doc("会话标题。")
  title: string;

  @doc("会话头像。")
  avatar?: MediaFile;

  @doc("最后一条消息的摘要文本。")
  lastMessagePreview?: string;

  @doc("当前用户在该会话中的未读消息数。")
  unreadCount: int32;

  @doc("会话最后更新时间。")
  updatedAt: DateTimeString;
}

@doc("消息发送请求，调用方属于会话，创建消息并更新未读状态，不同消息类型只使用对应内容字段。")
model SendMessageRequest {
  @doc("待发送消息类型。")
  kind: MessageKind;

  @doc("文本消息内容（表情使用Unicode Emoji表示）。")
  text?: string;

  @doc("图片消息对应的媒体文件标识。")
  imageMediaId?: EntityId;

  @doc("位置共享消息中的地点信息。")
  location?: LocationInfo;

  @doc("被 @ 的用户标识列表。仅小队群聊中使用。")
  mentionedUserIds?: EntityId[];

  @doc("是否 @ 所有人。仅队长或管理员可用，普通成员传 true 时后端应拒绝。")
  mentionAll?: boolean;
}

@doc("聊天消息。")
model ChatMessage {
  @doc("消息标识。")
  messageId: EntityId;

  @doc("会话标识。")
  conversationId: EntityId;

  @doc("发送者用户标识。")
  senderId: EntityId;

  @doc("消息类型。")
  kind: MessageKind;

  @doc("文本消息内容。")
  text?: string;

  @doc("图片消息媒体文件。")
  image?: MediaFile;

  @doc("位置共享消息地点。")
  location?: LocationInfo;

  @doc("该消息 @ 的用户标识列表。")
  mentionedUserIds?: EntityId[];

  @doc("该消息是否 @ 所有人。")
  mentionAll?: boolean;

  @doc("消息读取状态。")
  readStatus: MessageReadStatus;

  @doc("消息是否已撤回。")
  recalled: boolean;

  @doc("消息发送时间。")
  sentAt: DateTimeString;
}

@doc("消息创建事件负载。")
model MessageCreatedPayload {
  @doc("新创建的聊天消息。")
  message: ChatMessage;

  @doc("当前用户在该会话中的最新未读消息数。")
  conversationUnreadCount: int32;
}

@doc("消息撤回事件负载。")
model MessageRecalledPayload {
  @doc("被撤回的聊天消息，其 recalled 字段为 true。")
  message: ChatMessage;
}

@doc("消息转发事件负载。")
model MessageForwardedPayload {
  @doc("转发产生的新聊天消息。")
  message: ChatMessage;

  @doc("当前用户在该会话中的最新未读消息数。")
  conversationUnreadCount: int32;
}

@doc("聊天实时事件，WebSocket 连接建立后由服务端推送，用于通知当前用户可见会话中的实时事件。")
model ChatRealtimeEvent {
  @doc("实时事件类型。")
  kind: ChatRealtimeEventKind;

  @doc("发生事件的会话标识。")
  conversationId: EntityId;

  @doc("事件负载，根据 kind 不同携带对应结构。kind=messageCreated 时 payload 为 MessageCreatedPayload，kind=messageRecalled 时为 MessageRecalledPayload，kind=messageForwarded 时为 MessageForwardedPayload。")
  payload:
    | MessageCreatedPayload
    | MessageRecalledPayload
    | MessageForwardedPayload;

  @doc("事件发生时间。")
  occurredAt: DateTimeString;
}

@doc("消息转发请求，原消息存在且调用方可见，目标会话生成新消息，转发不会修改原消息。")
model ForwardMessageRequest {
  @doc("转发目标会话标识列表。")
  targetConversationIds: EntityId[];
}

@doc("批量标记消息已读请求，调用方为消息接收方，所有消息均需对调用方可见。")
model MarkMessagesReadRequest {
  @doc("待标记为已读的消息标识列表。")
  messageIds: EntityId[];
}

@doc("群公告请求，调用方为队长或管理员，公告更新，普通成员不可发布群公告。")
model TeamAnnouncementRequest {
  @doc("群公告内容。")
  content: string;
}

@doc("群公告。")
model TeamAnnouncement {
  @doc("公告标识。")
  announcementId: EntityId;

  @doc("小队标识。")
  teamId: EntityId;

  @doc("公告内容。")
  content: string;

  @doc("发布者用户标识。")
  publisherId: EntityId;

  @doc("公告发布时间。")
  publishedAt: DateTimeString;

  @doc("当前用户是否已阅读该公告。")
  readByCurrentUser: boolean;
}

@doc("批量删除群文件请求，调用方为队长或管理员，所有媒体文件均需属于该小队。")
model DeleteTeamFilesRequest {
  @doc("待删除的群文件媒体标识列表。")
  mediaIds: EntityId[];
}

@doc("批量删除小队相册图片请求，调用方为队长或管理员，所有媒体文件均需属于该小队相册。")
model DeleteTeamAlbumImagesRequest {
  @doc("待删除的小队相册图片媒体标识列表。")
  mediaIds: EntityId[];
}

@doc("群投票创建请求，调用方为小队成员，创建可投票事项，选项至少两个。")
model TeamPollCreateRequest {
  @doc("投票标题。")
  title: string;

  @doc("投票选项文本列表。")
  options: string[];

  @doc("投票截止时间。")
  deadline?: DateTimeString;
}

@doc("群投票。")
model TeamPoll {
  @doc("投票标识。")
  pollId: EntityId;

  @doc("小队标识。")
  teamId: EntityId;

  @doc("投票标题。")
  title: string;

  @doc("投票选项及票数。")
  options: TeamPollOption[];

  @doc("投票截止时间。")
  deadline?: DateTimeString;

  @doc("投票创建时间。")
  createdAt: DateTimeString;
}

@doc("群投票选项。")
model TeamPollOption {
  @doc("投票选项标识。")
  optionId: EntityId;

  @doc("投票选项内容。")
  content: string;

  @doc("当前票数。")
  voteCount: int32;
}

@doc("投票请求。")
model VotePollRequest {
  @doc("用户选择的投票选项标识。")
  optionId: EntityId;
}

@route("/chat")
@tag("即时通讯")
interface ChatOperations {
  @post
  @route("/media/images")
  @doc("上传聊天图片，请求为 multipart/form-data，返回可发送的图片 mediaId，上传不等于发送消息。")
  uploadChatImage(@multipartBody body: MediaUploadRequest): APIResult<
    MediaFile,
    QujuApi.Errors.Chat.ImageFormatInvalid | QujuApi.Errors.Chat.ImageTooLarge
  >;

  @post
  @route("/teams/{teamId}/files")
  @doc("上传群文件，请求为 multipart/form-data，调用方必须是小队成员，返回已关联到该小队的文件 mediaId。")
  uploadTeamFile(
    @path teamId: EntityId,
    @multipartBody body: MediaUploadRequest,
  ): APIResult<
    MediaFile,
    QujuApi.Errors.Chat.TeamNotVisible | QujuApi.Errors.Chat.TeamMemberRequired
  >;

  @post
  @route("/teams/{teamId}/album-images")
  @doc("上传小队相册图片，请求为 multipart/form-data，调用方必须是小队成员，返回已关联到该小队相册的图片 mediaId。")
  uploadTeamAlbumImage(
    @path teamId: EntityId,
    @multipartBody body: MediaUploadRequest,
  ): APIResult<
    MediaFile,

      | QujuApi.Errors.Chat.TeamNotVisible
      | QujuApi.Errors.Chat.TeamMemberRequired
      | QujuApi.Errors.Chat.ImageFormatInvalid
      | QujuApi.Errors.Chat.ImageTooLarge
  >;

  @get
  @route("/teams/{teamId}/album-images")
  @doc("查看小队相册图片，小队成员可分页查看已上传到相册的图片。")
  listTeamAlbumImages(@path teamId: EntityId, ...PageQuery): APIResult<
    PageResult<MediaFile>,
    QujuApi.Errors.Chat.TeamNotVisible | QujuApi.Errors.Chat.TeamMemberRequired
  >;

  @delete
  @route("/teams/{teamId}/album-images")
  @doc("批量删除小队相册图片，队长或管理员可传入相册图片媒体标识列表删除指定图片。")
  deleteTeamAlbumImages(
    @path teamId: EntityId,
    @body body: DeleteTeamAlbumImagesRequest,
  ): APIResult<
    EmptyData,

      | QujuApi.Errors.Chat.TeamNotVisible
      | QujuApi.Errors.Chat.TeamMediaNotFound
      | QujuApi.Errors.Chat.TeamMediaPermissionDenied
  >;

  @get
  @route("/teams/{teamId}/files")
  @doc("查看群文件，小队成员可分页查看群文件列表。")
  listTeamFiles(@path teamId: EntityId, ...PageQuery): APIResult<
    PageResult<MediaFile>,
    QujuApi.Errors.Chat.TeamNotVisible | QujuApi.Errors.Chat.TeamMemberRequired
  >;

  @delete
  @route("/teams/{teamId}/files")
  @doc("批量删除群文件，队长或管理员可传入群文件媒体标识列表删除指定文件。")
  deleteTeamFiles(
    @path teamId: EntityId,
    @body body: DeleteTeamFilesRequest,
  ): APIResult<
    EmptyData,

      | QujuApi.Errors.Chat.TeamNotVisible
      | QujuApi.Errors.Chat.TeamMediaNotFound
      | QujuApi.Errors.Chat.TeamMediaPermissionDenied
  >;

  @get
  @route("/ws/messages")
  @doc("聊天消息实时 WebSocket 占位端点。实际调用时客户端以 Bearer Token 完成鉴权并发起 WebSocket Upgrade，连接建立后服务端按 ChatRealtimeEvent 推送当前用户可见会话中的新消息；普通 JSON API 统一响应规则不适用于升级后的 WebSocket 数据帧。")
  connectMessageWebSocket(): APIResult<ChatRealtimeEvent>;

  @get
  @route("/conversations")
  @doc("获取会话列表，调用方已登录，返回好友和小队会话，小队成员变动同步影响会话可见性。")
  listConversations(...PageQuery): APIResult<PageResult<ConversationSummary>>;

  @get
  @route("/conversations/{conversationId}/messages")
  @doc("获取会话消息，调用方属于会话，分页返回消息，撤回消息只展示撤回状态。")
  listMessages(@path conversationId: EntityId, ...PageQuery): APIResult<
    PageResult<ChatMessage>,

      | QujuApi.Errors.Chat.ConversationNotVisible
      | QujuApi.Errors.Chat.ConversationMemberRequired
  >;

  @post
  @route("/conversations/{conversationId}/messages")
  @doc("发送消息，调用方属于会话且消息内容合法，创建消息并通知接收方，位置消息必须包含完整地点信息。")
  sendMessage(
    @path conversationId: EntityId,
    @body body: SendMessageRequest,
  ): APIResult<
    ChatMessage,

      | QujuApi.Errors.Chat.ConversationNotVisible
      | QujuApi.Errors.Chat.ConversationMemberRequired
      | QujuApi.Errors.Chat.MessageContentInvalid
      | QujuApi.Errors.Chat.MediaReferenceInvalid
  >;

  @post
  @route("/messages/read")
  @doc("批量标记消息已读，调用方为消息接收方，消息读取状态更新，发送者不能替接收方标记已读。")
  markMessagesRead(@body body: MarkMessagesReadRequest): APIResult<
    ChatMessage[],

      | QujuApi.Errors.Chat.MessageNotVisible
      | QujuApi.Errors.Chat.ConversationMemberRequired
  >;

  @post
  @route("/messages/{messageId}/recall")
  @doc("撤回消息，调用方为发送者且发送未超过 2 分钟，消息进入撤回状态，撤回不删除审计记录。")
  recallMessage(@path messageId: EntityId): APIResult<
    ChatMessage,

      | QujuApi.Errors.Chat.MessageNotVisible
      | QujuApi.Errors.Chat.MessageSenderRequired
      | QujuApi.Errors.Chat.MessageRecallExpired
  >;

  @post
  @route("/messages/{messageId}/forward")
  @doc("转发消息，原消息可见且目标会话可发送，目标会话生成新消息，不会修改原消息。")
  forwardMessage(
    @path messageId: EntityId,
    @body body: ForwardMessageRequest,
  ): APIResult<
    ChatMessage[],

      | QujuApi.Errors.Chat.MessageNotVisible
      | QujuApi.Errors.Chat.ForwardTargetUnavailable
  >;

  @post
  @route("/teams/{teamId}/announcements")
  @doc("发布群公告，调用方为队长或管理员，小队公告更新并通知成员，普通成员不可发布公告。")
  publishAnnouncement(
    @path teamId: EntityId,
    @body body: TeamAnnouncementRequest,
  ): APIResult<
    TeamAnnouncement,

      | QujuApi.Errors.Chat.TeamNotVisible
      | QujuApi.Errors.Chat.AnnouncementPermissionDenied
  >;

  @get
  @route("/teams/{teamId}/announcements")
  @doc("查询群公告，调用方为小队成员，分页返回该小队的群公告列表。")
  listAnnouncements(@path teamId: EntityId, ...PageQuery): APIResult<
    PageResult<TeamAnnouncement>,
    QujuApi.Errors.Chat.TeamNotVisible | QujuApi.Errors.Chat.TeamMemberRequired
  >;

  @put
  @route("/teams/{teamId}/announcements/{announcementId}")
  @doc("编辑群公告，调用方为队长或管理员，仅限修改公告内容。")
  updateAnnouncement(
    @path teamId: EntityId,
    @path announcementId: EntityId,
    @body body: TeamAnnouncementRequest,
  ): APIResult<
    TeamAnnouncement,

      | QujuApi.Errors.Chat.TeamNotVisible
      | QujuApi.Errors.Chat.AnnouncementPermissionDenied
      | QujuApi.Errors.Chat.AnnouncementNotVisible
  >;

  @delete
  @route("/teams/{teamId}/announcements/{announcementId}")
  @doc("删除群公告，调用方为队长或管理员，公告删除后不可恢复。")
  deleteAnnouncement(
    @path teamId: EntityId,
    @path announcementId: EntityId,
  ): APIResult<
    EmptyData,

      | QujuApi.Errors.Chat.TeamNotVisible
      | QujuApi.Errors.Chat.AnnouncementPermissionDenied
      | QujuApi.Errors.Chat.AnnouncementNotVisible
  >;

  @post
  @route("/teams/{teamId}/announcements/{announcementId}/read")
  @doc("标记群公告已读，调用方为小队成员且公告可见，更新当前用户对该公告的已读状态并返回最新公告。")
  markAnnouncementRead(
    @path teamId: EntityId,
    @path announcementId: EntityId,
  ): APIResult<
    TeamAnnouncement,

      | QujuApi.Errors.Chat.TeamNotVisible
      | QujuApi.Errors.Chat.TeamMemberRequired
      | QujuApi.Errors.Chat.AnnouncementNotVisible
  >;

  @post
  @route("/teams/{teamId}/polls")
  @doc("创建群投票，调用方为小队成员，创建投票，投票选项至少两个。")
  createPoll(
    @path teamId: EntityId,
    @body body: TeamPollCreateRequest,
  ): APIResult<
    TeamPoll,

      | QujuApi.Errors.Chat.TeamNotVisible
      | QujuApi.Errors.Chat.TeamMemberRequired
      | QujuApi.Errors.Chat.PollOptionsInvalid
  >;

  @post
  @route("/teams/{teamId}/polls/{pollId}/votes")
  @doc("参与群投票，调用方为小队成员且投票未截止，记录投票选择，同一用户对同一投票只能保留一个选择。")
  votePoll(
    @path teamId: EntityId,
    @path pollId: EntityId,
    @body body: VotePollRequest,
  ): APIResult<
    TeamPoll,

      | QujuApi.Errors.Chat.TeamNotVisible
      | QujuApi.Errors.Chat.TeamMemberRequired
      | QujuApi.Errors.Chat.PollUnavailable
  >;

  @get
  @route("/teams/{teamId}/polls")
  @doc("查看群投票列表，调用方为小队成员，可分页查看小队中已创建的投票。")
  listPolls(@path teamId: EntityId, ...PageQuery): APIResult<
    PageResult<TeamPoll>,
    QujuApi.Errors.Chat.TeamNotVisible | QujuApi.Errors.Chat.TeamMemberRequired
  >;

  @get
  @route("/teams/{teamId}/polls/{pollId}")
  @doc("查看群投票结果，调用方为小队成员，返回投票详情及各选项当前票数。")
  getPoll(@path teamId: EntityId, @path pollId: EntityId): APIResult<
    TeamPoll,

      | QujuApi.Errors.Chat.TeamNotVisible
      | QujuApi.Errors.Chat.TeamMemberRequired
      | QujuApi.Errors.Chat.PollNotFound
  >;
}
