package io.github.layjason.mayoistar.service.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("MediaAccessService")
class MediaAccessServiceTest {

    private FileStorageService fileStorageService;
    private MediaAccessService mediaAccessService;

    @BeforeEach
    void setUp() {
        MediaAccessProperties properties = new MediaAccessProperties();
        properties.setSigningSecret("unit-test-secret");
        MediaFileRepository mediaFileRepository = mock(MediaFileRepository.class);
        fileStorageService = mock(FileStorageService.class);
        mediaAccessService = new MediaAccessService(
                properties,
                mediaFileRepository,
                fileStorageService,
                new InMemoryMediaAccessCache(),
                mock(ConversationMemberRepository.class),
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
