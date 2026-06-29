package io.github.layjason.mayoistar.api.chat;

import io.github.layjason.mayoistar.api.common.ApiResponse;
import io.github.layjason.mayoistar.api.common.DefaultApiResponseFactory;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final DefaultApiResponseFactory responseFactory;

    public ChatController(DefaultApiResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<Object>> listConversations(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/chat/conversations");
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<Object>> listMessages(
            @PathVariable String conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/chat/conversations/" + conversationId + "/messages");
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<Object>> sendMessage(
            @PathVariable String conversationId, @Valid @RequestBody ChatDtos.SendMessageRequest request) {
        return responseFactory.success("POST", "/chat/conversations/" + conversationId + "/messages");
    }

    @PostMapping(value = "/media/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Object>> uploadChatImage(
            @RequestPart(value = "file", required = false) MultipartFile file) {
        return responseFactory.success("POST", "/chat/media/images");
    }

    @PostMapping("/messages/read")
    public ResponseEntity<ApiResponse<Object>> markMessagesRead(
            @Valid @RequestBody ChatDtos.MarkMessagesReadRequest request) {
        return responseFactory.success("POST", "/chat/messages/read");
    }

    @PostMapping("/messages/{messageId}/forward")
    public ResponseEntity<ApiResponse<Object>> forwardMessage(
            @PathVariable String messageId, @Valid @RequestBody ChatDtos.ForwardMessageRequest request) {
        return responseFactory.success("POST", "/chat/messages/" + messageId + "/forward");
    }

    @PostMapping("/messages/{messageId}/recall")
    public ResponseEntity<ApiResponse<Object>> recallMessage(@PathVariable String messageId) {
        return responseFactory.success("POST", "/chat/messages/" + messageId + "/recall");
    }

    @PostMapping(value = "/teams/{teamId}/album-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Object>> uploadTeamAlbumImage(
            @PathVariable String teamId, @RequestPart(value = "file", required = false) MultipartFile file) {
        return responseFactory.success("POST", "/chat/teams/" + teamId + "/album-images");
    }

    @GetMapping("/teams/{teamId}/album-images")
    public ResponseEntity<ApiResponse<Object>> listTeamAlbumImages(
            @PathVariable String teamId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/chat/teams/" + teamId + "/album-images");
    }

    @DeleteMapping("/teams/{teamId}/album-images")
    public ResponseEntity<ApiResponse<Object>> deleteTeamAlbumImages(
            @PathVariable String teamId, @Valid @RequestBody ChatDtos.DeleteTeamAlbumImagesRequest request) {
        return responseFactory.success("DELETE", "/chat/teams/" + teamId + "/album-images");
    }

    @PostMapping("/teams/{teamId}/announcements")
    public ResponseEntity<ApiResponse<Object>> publishAnnouncement(
            @PathVariable String teamId, @Valid @RequestBody ChatDtos.TeamAnnouncementRequest request) {
        return responseFactory.success("POST", "/chat/teams/" + teamId + "/announcements");
    }

    @PostMapping("/teams/{teamId}/announcements/{announcementId}/read")
    public ResponseEntity<ApiResponse<Object>> markAnnouncementRead(
            @PathVariable String teamId, @PathVariable String announcementId) {
        return responseFactory.success("POST", "/chat/teams/" + teamId + "/announcements/" + announcementId + "/read");
    }

    @PostMapping(value = "/teams/{teamId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Object>> uploadTeamFile(
            @PathVariable String teamId, @RequestPart(value = "file", required = false) MultipartFile file) {
        return responseFactory.success("POST", "/chat/teams/" + teamId + "/files");
    }

    @GetMapping("/teams/{teamId}/files")
    public ResponseEntity<ApiResponse<Object>> listTeamFiles(
            @PathVariable String teamId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return responseFactory.success("GET", "/chat/teams/" + teamId + "/files");
    }

    @DeleteMapping("/teams/{teamId}/files")
    public ResponseEntity<ApiResponse<Object>> deleteTeamFiles(
            @PathVariable String teamId, @Valid @RequestBody ChatDtos.DeleteTeamFilesRequest request) {
        return responseFactory.success("DELETE", "/chat/teams/" + teamId + "/files");
    }

    @PostMapping("/teams/{teamId}/polls")
    public ResponseEntity<ApiResponse<Object>> createPoll(
            @PathVariable String teamId, @Valid @RequestBody ChatDtos.TeamPollCreateRequest request) {
        return responseFactory.success("POST", "/chat/teams/" + teamId + "/polls");
    }

    @PostMapping("/teams/{teamId}/polls/{pollId}/votes")
    public ResponseEntity<ApiResponse<Object>> votePoll(
            @PathVariable String teamId,
            @PathVariable String pollId,
            @Valid @RequestBody ChatDtos.VotePollRequest request) {
        return responseFactory.success("POST", "/chat/teams/" + teamId + "/polls/" + pollId + "/votes");
    }

    @GetMapping("/ws/messages")
    public ResponseEntity<ApiResponse<Object>> connectMessageWebSocket() {
        return responseFactory.success("GET", "/chat/ws/messages");
    }
}
