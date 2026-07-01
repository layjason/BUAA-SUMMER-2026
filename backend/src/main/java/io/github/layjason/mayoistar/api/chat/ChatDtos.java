package io.github.layjason.mayoistar.api.chat;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.entity.chat.ConversationKind;
import io.github.layjason.mayoistar.entity.chat.MessageKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/**
 * 即时通讯接口 DTO 集合。
 *
 * <p>类职责：提供与 TypeSpec 聊天接口请求/响应模型同名的普通 DTO。
 *
 * <p>类不变量：字段使用 camelCase，必填字段使用 Jakarta Validation 表达。
 */
public final class ChatDtos {

    private ChatDtos() {}

    /* ========== 请求 DTO ========== */

    @Data
    public static class SendMessageRequest {
        @NotNull
        private MessageKind kind;

        private String text;
        private String imageMediaId;
        private CommonDtos.LocationInfo location;
        private List<String> mentionedUserIds;
        private Boolean mentionAll;
    }

    @Data
    public static class ForwardMessageRequest {
        @NotEmpty
        private List<String> targetConversationIds;
    }

    @Data
    public static class MarkMessagesReadRequest {
        @NotEmpty
        private List<String> messageIds;
    }

    @Data
    public static class TeamAnnouncementRequest {
        @NotBlank
        private String content;
    }

    @Data
    public static class DeleteTeamFilesRequest {
        @NotEmpty
        private List<String> mediaIds;
    }

    @Data
    public static class DeleteTeamAlbumImagesRequest {
        @NotEmpty
        private List<String> mediaIds;
    }

    @Data
    public static class TeamPollCreateRequest {
        @NotBlank
        @Size(max = 200)
        private String title;

        @NotEmpty
        @Size(min = 2)
        private List<String> options;

        private String deadline;
    }

    @Data
    public static class VotePollRequest {
        @NotBlank
        @Size(max = 36)
        private String optionId;
    }

    /* ========== 响应 DTO ========== */

    @Data
    public static class ConversationSummary {
        private String conversationId;
        private ConversationKind kind;
        private String title;
        private CommonDtos.MediaFile avatar;
        private String lastMessagePreview;
        private Integer unreadCount;
        private String updatedAt;
    }

    @Data
    public static class ChatMessage {
        private String messageId;
        private String conversationId;
        private String senderId;
        private MessageKind kind;
        private String text;
        private CommonDtos.MediaFile image;
        private CommonDtos.LocationInfo location;
        private List<String> mentionedUserIds;
        private Boolean mentionAll;
        private String readStatus;
        private Boolean recalled;
        private String sentAt;
    }

    /**
     * 聊天实时事件负载标记接口，具体类型由 ChatRealtimeEvent.kind 区分。
     */
    public interface ChatRealtimeEventPayload {}

    @Data
    public static class MessageCreatedPayload implements ChatRealtimeEventPayload {
        private ChatMessage message;
        private Integer conversationUnreadCount;
    }

    @Data
    public static class MessageRecalledPayload implements ChatRealtimeEventPayload {
        private ChatMessage message;
    }

    @Data
    public static class MessageForwardedPayload implements ChatRealtimeEventPayload {
        private ChatMessage message;
        private Integer conversationUnreadCount;
    }

    @Data
    public static class ChatRealtimeEvent {
        private String kind;
        private String conversationId;
        private ChatRealtimeEventPayload payload;
        private String occurredAt;
    }

    @Data
    public static class TeamAnnouncement {
        private String announcementId;
        private String teamId;
        private String content;
        private String publisherId;
        private String publishedAt;
        private Boolean readByCurrentUser;
    }

    @Data
    public static class TeamPollOption {
        private String optionId;
        private String content;
        private Integer voteCount;
    }

    @Data
    public static class TeamPoll {
        private String pollId;
        private String teamId;
        private String title;
        private List<TeamPollOption> options;
        private String deadline;
        private String createdAt;
    }
}
