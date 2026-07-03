package io.github.layjason.mayoistar.service.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.config.MediaAccessProperties;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.ConversationMemberRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.service.storage.FileStorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("MediaAccessService")
class MediaAccessServiceTest {

    private FileStorageService fileStorageService;
    private MediaFileRepository mediaFileRepository;
    private ConversationMemberRepository conversationMemberRepository;
    private MediaAccessService mediaAccessService;

    @BeforeEach
    void setUp() {
        MediaAccessProperties properties = new MediaAccessProperties();
        properties.setSigningSecret("unit-test-secret");
        mediaFileRepository = mock(MediaFileRepository.class);
        fileStorageService = mock(FileStorageService.class);
        conversationMemberRepository = mock(ConversationMemberRepository.class);
        mediaAccessService = new MediaAccessService(
                properties,
                mediaFileRepository,
                fileStorageService,
                new InMemoryMediaAccessCache(),
                conversationMemberRepository,
                mock(TeamMemberRepository.class),
                mock(ActivityRepository.class));
    }

    @Test
    @DisplayName("公开资源签名后可匿名下载")
    void shouldOpenPublicMediaWithValidSignature() {
        UUID mediaId = UUID.randomUUID();
        MediaFile mediaFile =
                buildMediaFile(mediaId, MediaVisibility.publicVisible, MediaAccessPolicy.publicAccess, "");
        InputStream expectedStream = new ByteArrayInputStream("image-data".getBytes(StandardCharsets.UTF_8));
        when(fileStorageService.retrieve("avatar/user1/avatar.png")).thenReturn(expectedStream);
        when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.of(mediaFile));

        SignedMediaAccess signed = mediaAccessService.sign(mediaFile);
        Map<String, String> params = queryParams(signed.signedUrl());

        InputStream result = mediaAccessService.openSignedContent(
                mediaId,
                Long.parseLong(params.get("v")),
                MediaAccessPolicy.valueOf(params.get("policy")),
                params.get("scope"),
                params.get("sig"),
                null);

        assertThat(result).isSameAs(expectedStream);
    }

    @Test
    @DisplayName("私有资源匿名下载返回 401")
    void shouldRejectAnonymousPrivateMediaAccess() {
        UUID mediaId = UUID.randomUUID();
        MediaFile mediaFile = buildMediaFile(mediaId, MediaVisibility.privateVisible, MediaAccessPolicy.owner, "user1");
        when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.of(mediaFile));
        SignedMediaAccess signed = mediaAccessService.sign(mediaFile);
        Map<String, String> params = queryParams(signed.signedUrl());

        assertThatThrownBy(() -> mediaAccessService.openSignedContent(
                        mediaId,
                        Long.parseLong(params.get("v")),
                        MediaAccessPolicy.valueOf(params.get("policy")),
                        params.get("scope"),
                        params.get("sig"),
                        null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("conversationMember 策略：会话成员可下载")
    void shouldAllowConversationMemberAccess() {
        UUID mediaId = UUID.randomUUID();
        String conversationId = "conv-abc";
        MediaFile mediaFile = buildMediaFile(
                mediaId, MediaVisibility.privateVisible, MediaAccessPolicy.conversationMember, conversationId);
        InputStream expectedStream = new ByteArrayInputStream("chat-image".getBytes(StandardCharsets.UTF_8));
        when(fileStorageService.retrieve("avatar/user1/avatar.png")).thenReturn(expectedStream);
        when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.of(mediaFile));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, "user2"))
                .thenReturn(true);

        SignedMediaAccess signed = mediaAccessService.sign(mediaFile);
        Map<String, String> params = queryParams(signed.signedUrl());

        var auth = new TestingAuthenticationToken("user2", null);
        auth.setAuthenticated(true);

        InputStream result = mediaAccessService.openSignedContent(
                mediaId,
                Long.parseLong(params.get("v")),
                MediaAccessPolicy.valueOf(params.get("policy")),
                params.get("scope"),
                params.get("sig"),
                auth);

        assertThat(result).isSameAs(expectedStream);
    }

    @Test
    @DisplayName("conversationMember 策略：非会话成员返回 403")
    void shouldRejectNonConversationMemberAccess() {
        UUID mediaId = UUID.randomUUID();
        String conversationId = "conv-xyz";
        MediaFile mediaFile = buildMediaFile(
                mediaId, MediaVisibility.privateVisible, MediaAccessPolicy.conversationMember, conversationId);
        when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.of(mediaFile));
        when(conversationMemberRepository.existsByConversationIdAndUserId(conversationId, "stranger"))
                .thenReturn(false);

        SignedMediaAccess signed = mediaAccessService.sign(mediaFile);
        Map<String, String> params = queryParams(signed.signedUrl());

        var auth = new TestingAuthenticationToken("stranger", null);
        auth.setAuthenticated(true);

        assertThatThrownBy(() -> mediaAccessService.openSignedContent(
                        mediaId,
                        Long.parseLong(params.get("v")),
                        MediaAccessPolicy.valueOf(params.get("policy")),
                        params.get("scope"),
                        params.get("sig"),
                        auth))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode.value")
                .isEqualTo(403);
    }

    @Test
    @DisplayName("updateAccessPolicy：更新后签名 URL 反映新策略")
    void shouldUpdateAccessPolicyAndGenerateNewSignedUrl() {
        UUID mediaId = UUID.randomUUID();
        String conversationId = "conv-updated";
        MediaFile mediaFile = buildMediaFile(mediaId, MediaVisibility.privateVisible, MediaAccessPolicy.owner, "user1");
        when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.of(mediaFile));
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mediaAccessService.updateAccessPolicy(mediaId, MediaAccessPolicy.conversationMember, conversationId);

        // 验证保存时 entity 已更新
        ArgumentCaptor<MediaFile> captor = ArgumentCaptor.forClass(MediaFile.class);
        verify(mediaFileRepository).save(captor.capture());
        assertThat(captor.getValue().getAccessPolicy()).isEqualTo(MediaAccessPolicy.conversationMember);
        assertThat(captor.getValue().getAccessScopeId()).isEqualTo(conversationId);

        // 验证新签名 URL 包含更新的策略
        SignedMediaAccess signed = mediaAccessService.sign(mediaFile);
        assertThat(signed.signedUrl()).contains("policy=conversationMember");
        assertThat(signed.signedUrl()).contains("scope=" + conversationId);
    }

    @Test
    @DisplayName("copyForScope：创建的副本共享存储路径但具有独立的 accessPolicy")
    void shouldCopyMediaFileForNewScope() {
        UUID originalMediaId = UUID.randomUUID();
        MediaFile source =
                buildMediaFile(originalMediaId, MediaVisibility.privateVisible, MediaAccessPolicy.owner, "user1");
        String targetConversationId = "conv-target";

        ArgumentCaptor<MediaFile> captor = ArgumentCaptor.forClass(MediaFile.class);
        when(mediaFileRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        MediaFile copied = mediaAccessService.copyForScope(
                source, "user2", MediaAccessPolicy.conversationMember, targetConversationId);

        assertThat(copied.getMediaId()).isNotEqualTo(originalMediaId);
        assertThat(copied.getAccessPolicy()).isEqualTo(MediaAccessPolicy.conversationMember);
        assertThat(copied.getAccessScopeId()).isEqualTo(targetConversationId);
        assertThat(copied.getUploadedBy()).isEqualTo("user2");
        assertThat(copied.getStoragePath()).isEqualTo(source.getStoragePath());
        assertThat(copied.getContentType()).isEqualTo(source.getContentType());
        assertThat(copied.getSizeBytes()).isEqualTo(source.getSizeBytes());
    }

    private MediaFile buildMediaFile(
            UUID mediaId, MediaVisibility visibility, MediaAccessPolicy policy, String accessScopeId) {
        return MediaFile.builder()
                .mediaId(mediaId)
                .fileName("avatar.png")
                .contentType("image/png")
                .sizeBytes(100L)
                .usage(MediaUsage.avatar)
                .storagePath("avatar/user1/avatar.png")
                .visibility(visibility)
                .accessPolicy(policy)
                .accessScopeId(accessScopeId)
                .accessVersion(1L)
                .uploadedBy("user1")
                .uploadedAt(Instant.parse("2026-07-01T00:00:00Z"))
                .build();
    }

    private Map<String, String> queryParams(String signedUrl) {
        String query = URI.create(signedUrl).getQuery();
        return Arrays.stream(query.split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(part -> part[0], part -> URLDecoder.decode(part[1], StandardCharsets.UTF_8)));
    }
}
