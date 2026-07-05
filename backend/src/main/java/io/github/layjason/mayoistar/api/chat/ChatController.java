package io.github.layjason.mayoistar.api.chat;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.EmptyData;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.common.SecurityUtils;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.service.ChatService;
import io.github.layjason.mayoistar.service.MediaFileUploadService;
import io.github.layjason.mayoistar.service.TeamService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;
    private final TeamService teamService;
    private final SecurityUtils securityUtils;
    private final MediaFileUploadService mediaFileUploadService;

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<PageResult<ChatDtos.ConversationSummary>>> listConversations(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.listConversations(userId, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<PageResult<ChatDtos.ChatMessage>>> listMessages(
            @PathVariable String conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.listMessages(conversationId, userId, 1, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<ChatDtos.ChatMessage>> sendMessage(
            @PathVariable String conversationId, @Valid @RequestBody ChatDtos.SendMessageRequest request) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.sendMessage(conversationId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping(value = "/media/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CommonDtos.MediaFile>> uploadChatImage(
            @RequestPart(value = "file") MultipartFile file) {
        String userId = securityUtils.getCurrentUserId();
        var result = mediaFileUploadService.upload(userId, file, MediaUsage.chatImage);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/messages/read")
    public ResponseEntity<ApiResponse<List<ChatDtos.ChatMessage>>> markMessagesRead(
            @Valid @RequestBody ChatDtos.MarkMessagesReadRequest request) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.markMessagesRead(userId, request.getMessageIds());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/messages/{messageId}/forward")
    public ResponseEntity<ApiResponse<List<ChatDtos.ChatMessage>>> forwardMessage(
            @PathVariable String messageId, @Valid @RequestBody ChatDtos.ForwardMessageRequest request) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.forwardMessage(messageId, userId, request.getTargetConversationIds());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/messages/{messageId}/recall")
    public ResponseEntity<ApiResponse<ChatDtos.ChatMessage>> recallMessage(@PathVariable String messageId) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.recallMessage(messageId, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping(value = "/teams/{teamId}/album-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CommonDtos.MediaFile>> uploadTeamAlbumImage(
            @PathVariable String teamId, @RequestPart(value = "file") MultipartFile file) {
        String userId = securityUtils.getCurrentUserId();
        var uploaded = mediaFileUploadService.upload(userId, file, MediaUsage.teamAlbum);
        var result = teamService.uploadTeamAlbumImage(teamId, userId, uploaded.getMediaId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/teams/{teamId}/album-images")
    public ResponseEntity<ApiResponse<PageResult<CommonDtos.MediaFile>>> listTeamAlbumImages(
            @PathVariable String teamId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        String userId = securityUtils.getCurrentUserId();
        var result = teamService.listTeamAlbumImages(teamId, userId, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/teams/{teamId}/album-images")
    public ResponseEntity<ApiResponse<EmptyData>> deleteTeamAlbumImages(
            @PathVariable String teamId, @Valid @RequestBody ChatDtos.DeleteTeamAlbumImagesRequest request) {
        String userId = securityUtils.getCurrentUserId();
        teamService.deleteTeamAlbumImages(teamId, userId, request.getMediaIds());
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    @PostMapping("/teams/{teamId}/announcements")
    public ResponseEntity<ApiResponse<ChatDtos.TeamAnnouncement>> publishAnnouncement(
            @PathVariable String teamId, @Valid @RequestBody ChatDtos.TeamAnnouncementRequest request) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.publishAnnouncement(teamId, userId, request.getContent());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/teams/{teamId}/announcements/{announcementId}/read")
    public ResponseEntity<ApiResponse<ChatDtos.TeamAnnouncement>> markAnnouncementRead(
            @PathVariable String teamId, @PathVariable String announcementId) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.markAnnouncementRead(teamId, announcementId, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/teams/{teamId}/announcements")
    public ResponseEntity<ApiResponse<PageResult<ChatDtos.TeamAnnouncement>>> listAnnouncements(
            @PathVariable String teamId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.listAnnouncements(teamId, userId, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/teams/{teamId}/announcements/{announcementId}")
    public ResponseEntity<ApiResponse<ChatDtos.TeamAnnouncement>> getAnnouncement(
            @PathVariable String teamId, @PathVariable String announcementId) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.getAnnouncement(teamId, announcementId, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PutMapping("/teams/{teamId}/announcements/{announcementId}")
    public ResponseEntity<ApiResponse<ChatDtos.TeamAnnouncement>> updateAnnouncement(
            @PathVariable String teamId,
            @PathVariable String announcementId,
            @Valid @RequestBody ChatDtos.TeamAnnouncementUpdateRequest request) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.updateAnnouncement(teamId, announcementId, userId, request.getContent());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/teams/{teamId}/announcements/{announcementId}")
    public ResponseEntity<ApiResponse<EmptyData>> deleteAnnouncement(
            @PathVariable String teamId, @PathVariable String announcementId) {
        String userId = securityUtils.getCurrentUserId();
        chatService.deleteAnnouncement(teamId, announcementId, userId);
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    @PostMapping(value = "/teams/{teamId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CommonDtos.MediaFile>> uploadTeamFile(
            @PathVariable String teamId, @RequestPart(value = "file") MultipartFile file) {
        String userId = securityUtils.getCurrentUserId();
        var uploaded = mediaFileUploadService.upload(userId, file, MediaUsage.teamFile);
        var result = teamService.uploadTeamFile(teamId, userId, uploaded.getMediaId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/teams/{teamId}/files")
    public ResponseEntity<ApiResponse<PageResult<CommonDtos.MediaFile>>> listTeamFiles(
            @PathVariable String teamId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        String userId = securityUtils.getCurrentUserId();
        var result = teamService.listTeamFiles(teamId, userId, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/teams/{teamId}/files")
    public ResponseEntity<ApiResponse<EmptyData>> deleteTeamFiles(
            @PathVariable String teamId, @Valid @RequestBody ChatDtos.DeleteTeamFilesRequest request) {
        String userId = securityUtils.getCurrentUserId();
        teamService.deleteTeamFiles(teamId, userId, request.getMediaIds());
        return ResponseEntity.ok(ApiResponse.success(new EmptyData()));
    }

    @PostMapping("/teams/{teamId}/polls")
    public ResponseEntity<ApiResponse<ChatDtos.TeamPoll>> createPoll(
            @PathVariable String teamId, @Valid @RequestBody ChatDtos.TeamPollCreateRequest request) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.createPoll(teamId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/teams/{teamId}/polls/{pollId}/votes")
    public ResponseEntity<ApiResponse<ChatDtos.TeamPoll>> votePoll(
            @PathVariable String teamId,
            @PathVariable String pollId,
            @Valid @RequestBody ChatDtos.VotePollRequest request) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.votePoll(teamId, pollId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/teams/{teamId}/polls")
    public ResponseEntity<ApiResponse<PageResult<ChatDtos.TeamPoll>>> listPolls(
            @PathVariable String teamId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.listPolls(teamId, userId, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/teams/{teamId}/polls/{pollId}")
    public ResponseEntity<ApiResponse<ChatDtos.TeamPoll>> getPoll(
            @PathVariable String teamId, @PathVariable String pollId) {
        String userId = securityUtils.getCurrentUserId();
        var result = chatService.getPoll(teamId, pollId, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
