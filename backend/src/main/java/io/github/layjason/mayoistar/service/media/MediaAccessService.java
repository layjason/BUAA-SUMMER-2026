package io.github.layjason.mayoistar.service.media;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.config.MediaAccessProperties;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.ConversationMemberRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.service.storage.FileStorageService;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 媒体签名访问服务。
 *
 * <p>类职责：负责媒体签名 URL 生成、签名校验、下载前访问控制、访问快照缓存和文件流读取。
 *
 * <p>不变量：下载必须同时满足签名未篡改、未过期、accessVersion 匹配、资源未软删除和访问策略允许。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaAccessService {

    private final MediaAccessProperties properties;
    private final MediaFileRepository mediaFileRepository;
    private final FileStorageService fileStorageService;
    private final MediaAccessCache mediaAccessCache;
    private final ConversationMemberRepository conversationMemberRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ActivityRepository activityRepository;
    private final MediaRefreshRateLimiter mediaRefreshRateLimiter;
    private final Clock clock = Clock.systemUTC();

    /**
     * 为媒体实体签发访问 URL。
     *
     * <p>前置条件：mediaFile 未被软删除。
     *
     * <p>后置条件：返回包含当前 accessVersion、访问策略和作用域的签名 URL。
     *
     * @param mediaFile 媒体实体
     * @return 签名访问地址
     */
    public SignedMediaAccess sign(MediaFile mediaFile) {
        MediaAccessDescriptor descriptor = toDescriptor(mediaFile);
        if (descriptor.deletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file is not found");
        }
        mediaAccessCache.put(descriptor);
        return sign(descriptor);
    }

    /**
     * 校验签名 URL 并读取媒体文件。
     *
     * <p>前置条件：请求参数来自签名 URL，mediaId 非空。
     *
     * <p>后置条件：若签名和权限均有效，返回对象存储输入流；否则抛出对应 HTTP 异常。
     *
     * <p>不变量：私有资源必须存在有效认证；管理员仍必须提供有效签名 URL。
     *
     * @param mediaId        媒体标识
     * @param exp            URL 过期时间戳
     * @param accessVersion  URL 中的访问版本
     * @param policy         URL 中的访问策略
     * @param scope          URL 中的访问作用域
     * @param signature      URL 签名
     * @param authentication 当前认证信息，可为空
     * @return 文件输入流
     */
    public InputStream openSignedContent(
            UUID mediaId,
            long exp,
            long accessVersion,
            MediaAccessPolicy policy,
            @Nullable String scope,
            String signature,
            @Nullable Authentication authentication) {
        Instant expiresAt = Instant.ofEpochSecond(exp);
        if (!expiresAt.isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SignatureExpired");
        }
        if (!verifySignature(mediaId, exp, accessVersion, policy, normalizeScope(scope), signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission is denied");
        }

        MediaAccessDescriptor descriptor = loadDescriptor(mediaId);
        if (descriptor.deletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file is not found");
        }
        if (descriptor.accessVersion() != accessVersion
                || descriptor.policy() != policy
                || !normalizeScope(descriptor.scope()).equals(normalizeScope(scope))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission is denied");
        }
        assertAccessAllowed(descriptor, authentication);
        try {
            return fileStorageService.retrieve(descriptor.storagePath());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file is not found", e);
        }
    }

    /**
     * 将媒体实体转换为 DTO 并填充签名 URL。
     *
     * <p>前置条件：mediaFile 非空。
     *
     * <p>后置条件：DTO 不暴露长期 url，填充 signedUrl、signedUrlExpiresAt 和 visibility。
     *
     * @param mediaFile 媒体实体
     * @return 媒体 DTO
     */
    public CommonDtos.MediaFile toSignedDto(MediaFile mediaFile) {
        CommonDtos.MediaFile result = new CommonDtos.MediaFile();
        result.setMediaId(mediaFile.getMediaId());
        result.setFileName(mediaFile.getFileName());
        result.setContentType(mediaFile.getContentType());
        result.setSizeBytes(mediaFile.getSizeBytes());
        result.setUsage(mediaFile.getUsage());
        result.setVisibility(mediaFile.getVisibility());
        result.setUploadedAt(mediaFile.getUploadedAt().toString());
        if (mediaFile.getDeletedAt() == null) {
            SignedMediaAccess signed = sign(mediaFile);
            result.setSignedUrl(signed.signedUrl());
            result.setSignedUrlExpiresAt(signed.expiresAt().toString());
        }
        return result;
    }

    /**
     * 批量刷新媒体签名 URL。
     *
     * <p>前置条件：mediaIds 非空且数量不超过配置上限。
     *
     * <p>后置条件：返回当前调用方有权访问的媒体签名 URL；任一资源无权访问时抛出 HTTP 错误。
     *
     * <p>不变量：刷新限流在权限校验和签名生成前执行。
     *
     * @param mediaIds        媒体标识列表
     * @param authentication  当前认证信息，可为空
     * @param clientIp        客户端 IP
     * @return 批量签名 URL 响应
     */
    public CommonDtos.BatchSignedUrlResponse batchSign(
            List<UUID> mediaIds, @Nullable Authentication authentication, String clientIp) {
        List<UUID> distinctMediaIds = new LinkedHashSet<>(mediaIds).stream().toList();
        if (distinctMediaIds.size() > properties.getBatchSizeLimit()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many media IDs");
        }
        mediaRefreshRateLimiter.check(rateKey(authentication, clientIp), distinctMediaIds.size());

        List<CommonDtos.SignedMediaUrl> items = distinctMediaIds.stream()
                .map(mediaId -> {
                    MediaAccessDescriptor descriptor = loadDescriptor(mediaId);
                    if (descriptor.deletedAt() != null) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file is not found");
                    }
                    assertAccessAllowed(descriptor, authentication);
                    SignedMediaAccess signed = sign(descriptor);
                    CommonDtos.SignedMediaUrl item = new CommonDtos.SignedMediaUrl();
                    item.setMediaId(mediaId);
                    item.setSignedUrl(signed.signedUrl());
                    item.setSignedUrlExpiresAt(signed.expiresAt().toString());
                    return item;
                })
                .toList();

        CommonDtos.BatchSignedUrlResponse response = new CommonDtos.BatchSignedUrlResponse();
        response.setItems(items);
        return response;
    }

    /**
     * 软删除媒体文件。
     *
     * <p>前置条件：调用方为上传者或管理员。
     *
     * <p>后置条件：deletedAt 非空，accessVersion 递增，缓存中的旧快照失效。
     *
     * @param mediaId        媒体标识
     * @param authentication 当前认证信息
     */
    @Transactional
    public void softDelete(UUID mediaId, Authentication authentication) {
        MediaFile mediaFile = mediaFileRepository
                .findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file is not found"));
        assertAccessAllowed(toDescriptor(mediaFile), authentication);
        mediaFile.setDeletedAt(clock.instant());
        mediaFile.setAccessVersion(mediaFile.getAccessVersion() + 1);
        mediaFileRepository.save(mediaFile);
        mediaAccessCache.put(toDescriptor(mediaFile));
        log.info("媒体文件已软删除: mediaId={}, accessVersion={}", mediaId, mediaFile.getAccessVersion());
    }

    public MediaAccessDescriptor loadDescriptor(UUID mediaId) {
        return mediaAccessCache.get(mediaId).orElseGet(() -> {
            MediaFile mediaFile = mediaFileRepository
                    .findById(mediaId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file is not found"));
            MediaAccessDescriptor descriptor = toDescriptor(mediaFile);
            mediaAccessCache.put(descriptor);
            return descriptor;
        });
    }

    private SignedMediaAccess sign(MediaAccessDescriptor descriptor) {
        Duration ttl = ttlFor(descriptor);
        Instant expiresAt = clock.instant().plus(ttl);
        long exp = expiresAt.getEpochSecond();
        String scope = normalizeScope(descriptor.scope());
        String signature =
                createSignature(descriptor.mediaId(), exp, descriptor.accessVersion(), descriptor.policy(), scope);
        String signedUrl = "/media/"
                + descriptor.mediaId()
                + "?exp="
                + exp
                + "&v="
                + descriptor.accessVersion()
                + "&policy="
                + descriptor.policy().name()
                + "&scope="
                + urlEncode(scope)
                + "&sig="
                + urlEncode(signature);
        return new SignedMediaAccess(signedUrl, expiresAt);
    }

    private Duration ttlFor(MediaAccessDescriptor descriptor) {
        if (descriptor.visibility() != MediaVisibility.publicVisible) {
            return properties.getPrivateTtl();
        }
        if (descriptor.policy() == MediaAccessPolicy.publicAccess && descriptor.uploadedBy() != null) {
            if (descriptor.storagePath().startsWith(MediaUsage.avatar.name() + "/")) {
                return properties.getPublicAvatarTtl();
            }
        }
        return properties.getPublicActivityImageTtl();
    }

    private MediaAccessDescriptor toDescriptor(MediaFile mediaFile) {
        return new MediaAccessDescriptor(
                mediaFile.getMediaId(),
                mediaFile.getStoragePath(),
                mediaFile.getContentType(),
                mediaFile.getFileName(),
                mediaFile.getSizeBytes(),
                mediaFile.getVisibility(),
                mediaFile.getAccessPolicy(),
                mediaFile.getAccessScopeId(),
                mediaFile.getAccessVersion(),
                mediaFile.getDeletedAt(),
                mediaFile.getUploadedBy());
    }

    private void assertAccessAllowed(MediaAccessDescriptor descriptor, @Nullable Authentication authentication) {
        if (descriptor.policy() == MediaAccessPolicy.publicAccess
                && descriptor.visibility() == MediaVisibility.publicVisible) {
            return;
        }
        if (isAnonymous(authentication)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        String userId = String.valueOf(authentication.getPrincipal());
        if (isAdmin(authentication)) {
            return;
        }
        boolean allowed =
                switch (descriptor.policy()) {
                    case owner -> userId.equals(descriptor.uploadedBy()) || userId.equals(descriptor.scope());
                    case conversationMember ->
                        conversationMemberRepository.existsByConversationIdAndUserId(descriptor.scope(), userId);
                    case teamMember ->
                        teamMemberRepository
                                .findByTeamIdAndUserId(descriptor.scope(), userId)
                                .isPresent();
                    case activityOwner ->
                        activityRepository
                                .findById(descriptor.scope())
                                .map(Activity::getOrganizerId)
                                .filter(userId::equals)
                                .isPresent();
                    case adminOnly -> false;
                    case publicAccess -> descriptor.visibility() == MediaVisibility.publicVisible;
                };
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission is denied");
        }
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_admin"::equals);
    }

    private boolean isAnonymous(@Nullable Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || "anonymousUser".equals(String.valueOf(authentication.getPrincipal()));
    }

    private String rateKey(@Nullable Authentication authentication, String clientIp) {
        if (!isAnonymous(authentication)) {
            return "rate:media-refresh:user:" + authentication.getPrincipal();
        }
        return "rate:media-refresh:ip:" + clientIp;
    }

    private boolean verifySignature(
            UUID mediaId, long exp, long accessVersion, MediaAccessPolicy policy, String scope, String signature) {
        String expected = createSignature(mediaId, exp, accessVersion, policy, scope);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    private String createSignature(UUID mediaId, long exp, long accessVersion, MediaAccessPolicy policy, String scope) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = mediaId + "\n" + exp + "\n" + accessVersion + "\n" + policy.name() + "\n" + scope;
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("媒体签名生成失败", e);
        }
    }

    private static String normalizeScope(@Nullable String scope) {
        return scope == null ? "" : scope;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
