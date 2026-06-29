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

  @doc("表情消息编码。")
  emojiCode?: string;

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

@doc("消息转发请求，原消息存在且调用方可见，目标会话生成新消息，转发不会修改原消息。")
model ForwardMessageRequest {
  @doc("转发目标会话标识列表。")
  targetConversationIds: EntityId[];
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

      | QujuApi.Errors.Chat.InvalidMediaUsage
      | QujuApi.Errors.Chat.ImageFormatInvalid
      | QujuApi.Errors.Chat.ImageTooLarge
  >;

  @post
  @route("/teams/{teamId}/files")
  @doc("上传群文件，请求为 multipart/form-data，调用方必须是小队成员，返回已关联到该小队的文件 mediaId。")
  uploadTeamFile(
    @path teamId: EntityId,
    @multipartBody body: MediaUploadRequest,
  ): APIResult<
    MediaFile,

      | QujuApi.Errors.Chat.InvalidMediaUsage
      | QujuApi.Errors.Chat.TeamNotVisible
      | QujuApi.Errors.Chat.TeamMemberRequired
  >;

  @post
  @route("/teams/{teamId}/album-images")
  @doc("上传小队相册图片，请求为 multipart/form-data，调用方必须是小队成员，返回已关联到该小队相册的图片 mediaId。")
  uploadTeamAlbumImage(
    @path teamId: EntityId,
    @multipartBody body: MediaUploadRequest,
  ): APIResult<
    MediaFile,

      | QujuApi.Errors.Chat.InvalidMediaUsage
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
  @route("/teams/{teamId}/album-images/{mediaId}")
  @doc("删除小队相册图片，队长或管理员可删除指定相册图片。")
  deleteTeamAlbumImage(
    @path teamId: EntityId,
    @path mediaId: EntityId,
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
  @route("/teams/{teamId}/files/{mediaId}")
  @doc("删除群文件，队长或管理员可删除指定群文件。")
  deleteTeamFile(@path teamId: EntityId, @path mediaId: EntityId): APIResult<
    EmptyData,

      | QujuApi.Errors.Chat.TeamNotVisible
      | QujuApi.Errors.Chat.TeamMediaNotFound
      | QujuApi.Errors.Chat.TeamMediaPermissionDenied
  >;

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
  >;

  @post
  @route("/messages/{messageId}/read")
  @doc("标记消息已读，调用方为消息接收方，消息读取状态更新，发送者不能替接收方标记已读。")
  markMessageRead(@path messageId: EntityId): APIResult<
    ChatMessage,

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
}
