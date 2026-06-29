package io.github.layjason.mayoistar.api.chat;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * 即时通讯接口 DTO 占位集合。
 *
 * <p>类职责：提供与 TypeSpec 聊天接口请求模型同名的普通 DTO。
 *
 * <p>类不变量：字段使用 camelCase，必填字段使用 Jakarta Validation 表达。
 */
public final class ChatDtos {

    /**
     * 阻止实例化 DTO 命名空间类。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：外部无法创建该工具型容器类实例。
     *
     * <p>不变量：该构造函数不创建任何 DTO 对象。
     */
    private ChatDtos() {}

    @Data
    public static class SendMessageRequest {
        @NotNull
        private String kind;

        private String text;
        private String imageMediaId;
        private Object location;
        private List<String> mentionedUserIds;
        private Boolean mentionAll;
    }

    @Data
    public static class ForwardMessageRequest {
        @NotNull
        private List<String> targetConversationIds;
    }

    @Data
    public static class MarkMessagesReadRequest {
        @NotNull
        private List<String> messageIds;
    }

    @Data
    public static class TeamAnnouncementRequest {
        @NotNull
        private String content;
    }

    @Data
    public static class DeleteTeamFilesRequest {
        @NotNull
        private List<String> mediaIds;
    }

    @Data
    public static class DeleteTeamAlbumImagesRequest {
        @NotNull
        private List<String> mediaIds;
    }

    @Data
    public static class TeamPollCreateRequest {
        @NotNull
        private String title;

        @NotNull
        private List<String> options;

        private String deadline;
    }

    @Data
    public static class VotePollRequest {
        @NotNull
        private String optionId;
    }
}
