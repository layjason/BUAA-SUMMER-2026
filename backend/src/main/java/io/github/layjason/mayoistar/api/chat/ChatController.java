package io.github.layjason.mayoistar.api.chat;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.common.SecurityUtils;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.service.ChatService;
import io.github.layjason.mayoistar.service.MediaFileUploadService;
import io.github.layjason.mayoistar.service.TeamService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    private final DefaultApiResponseFactory responseFactory;
    private final ChatService chatService;
    private final TeamService teamService;
    private final SecurityUtils securityUtils;
    private final MediaFileUploadService mediaFileUploadService;
    private final MediaFileRepository mediaFileRepository;

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
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.CommonDtos.MediaFile>> uploadChatImage(
            @RequestPart(value = "file") MultipartFile file) {
        return responseFactory.mediaFile(MediaUsage.chatImage);
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
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.CommonDtos.MediaFile>>
            uploadTeamAlbumImage(@PathVariable String teamId, @RequestPart(value = "file") MultipartFile file) {
        String userId = securityUtils.getCurrentUserId();
        UUID mediaId = saveMediaFile(file, MediaUsage.teamAlbum, userId);
        var result = teamService.uploadTeamAlbumImage(teamId, userId, mediaId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/teams/{teamId}/album-images")
    public ResponseEntity<ApiResponse<PageResult<io.github.layjason.mayoistar.api.common.CommonDtos.MediaFile>>>
            listTeamAlbumImages(
                    @PathVariable String teamId,
                    @RequestParam(required = false, defaultValue = "1") Integer page,
                    @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        String userId = securityUtils.getCurrentUserId();
        var result = teamService.listTeamAlbumImages(teamId, userId, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/teams/{teamId}/album-images")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> deleteTeamAlbumImages(
            @PathVariable String teamId, @Valid @RequestBody ChatDtos.DeleteTeamAlbumImagesRequest request) {
        String userId = securityUtils.getCurrentUserId();
        teamService.deleteTeamAlbumImages(teamId, userId, request.getMediaIds());
        return ResponseEntity.ok(ApiResponse.success(new io.github.layjason.mayoistar.api.common.EmptyData()));
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

    @PostMapping(value = "/teams/{teamId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.CommonDtos.MediaFile>> uploadTeamFile(
            @PathVariable String teamId, @RequestPart(value = "file") MultipartFile file) {
        String userId = securityUtils.getCurrentUserId();
        UUID mediaId = saveMediaFile(file, MediaUsage.teamFile, userId);
        var result = teamService.uploadTeamFile(teamId, userId, mediaId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/teams/{teamId}/files")
    public ResponseEntity<ApiResponse<PageResult<io.github.layjason.mayoistar.api.common.CommonDtos.MediaFile>>>
            listTeamFiles(
                    @PathVariable String teamId,
                    @RequestParam(required = false, defaultValue = "1") Integer page,
                    @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        String userId = securityUtils.getCurrentUserId();
        var result = teamService.listTeamFiles(teamId, userId, page, pageSize);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @DeleteMapping("/teams/{teamId}/files")
    public ResponseEntity<ApiResponse<io.github.layjason.mayoistar.api.common.EmptyData>> deleteTeamFiles(
            @PathVariable String teamId, @Valid @RequestBody ChatDtos.DeleteTeamFilesRequest request) {
        String userId = securityUtils.getCurrentUserId();
        teamService.deleteTeamFiles(teamId, userId, request.getMediaIds());
        return ResponseEntity.ok(ApiResponse.success(new io.github.layjason.mayoistar.api.common.EmptyData()));
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

    @GetMapping("/ws/messages")
    public ResponseEntity<ApiResponse<ChatDtos.ChatRealtimeEvent>> connectMessageWebSocket() {
        return responseFactory.chatRealtimeEvent();
    }

    private UUID saveMediaFile(MultipartFile file, MediaUsage usage, String userId) {
        UUID mediaId = UUID.randomUUID();
        MediaFile mediaFile = MediaFile.builder()
                .mediaId(mediaId)
                .fileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown")
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .sizeBytes(file.getSize())
                .usage(usage)
                .storagePath("uploads/" + mediaId)
                .uploadedBy(userId)
                .uploadedAt(Instant.now())
                .build();
        mediaFileRepository.save(mediaFile);
        return mediaId;
    }

    private String saveMediaFileUrl(MultipartFile file, MediaUsage usage, String userId) {
        return mediaFileUploadService.upload(userId, file, usage).getUrl();
    }
}
