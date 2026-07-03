package io.github.layjason.mayoistar.service.media;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.config.MediaAccessProperties;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaFile;
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
import java.util.Base64;
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
     * @param accessVersion  URL 中的访问版本
     * @param policy         URL 中的访问策略
     * @param scope          URL 中的访问作用域
     * @param signature      URL 签名，可为空
     * @param authentication 当前认证信息，可为空
     * @return 文件输入流
     */
    public InputStream openSignedContent(
            UUID mediaId,
            long accessVersion,
            MediaAccessPolicy policy,
            @Nullable String scope,
            @Nullable String signature,
            @Nullable Authentication authentication) {
        MediaAccessDescriptor descriptor = loadDescriptor(mediaId);
        if (descriptor.deletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file is not found");
        }
        if (!verifySignature(mediaId, accessVersion, policy, normalizeScope(scope), signature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission is denied");
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
     * <p>后置条件：DTO 不暴露长期 url，填充 signedUrl 和 visibility。
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
        }
        return result;
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

    /**
     * 软删除媒体文件（不含权限校验），递增访问版本使旧签名 URL 立即失效。
     *
     * <p>前置条件：调用方需自行确保删除权限（如已通过团队管理权限检查）。
     *
     * <p>后置条件：deletedAt 非空，accessVersion 递增，缓存中的旧快照失效。
     *
     * @param mediaId 媒体标识
     */
    @Transactional
    public void softDelete(UUID mediaId) {
        MediaFile mediaFile = mediaFileRepository
                .findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file is not found"));
        mediaFile.setDeletedAt(clock.instant());
        mediaFile.setAccessVersion(mediaFile.getAccessVersion() + 1);
        mediaFileRepository.save(mediaFile);
        mediaAccessCache.put(toDescriptor(mediaFile));
        log.info("媒体文件已软删除: mediaId={}, accessVersion={}", mediaId, mediaFile.getAccessVersion());
    }

    /**
     * 更新媒体文件的访问策略和作用域，递增访问版本使旧签名 URL 失效，并刷新缓存。
     *
     * <p>前置条件：mediaId 对应有效的媒体文件，且 {@code callerUserId} 为该文件的上传者。
     *
     * <p>后置条件：accessPolicy 和 accessScopeId 已更新，accessVersion 递增，旧签名 URL
     * 因版本不匹配而失效，缓存中的快照已刷新。
     *
     * @param mediaId      媒体标识
     * @param policy       新的访问策略
     * @param scope        新的访问作用域
     * @param callerUserId 调用者用户 ID，必须为文件上传者
     */
    @Transactional
    public void updateAccessPolicy(UUID mediaId, MediaAccessPolicy policy, String scope, String callerUserId) {
        MediaFile mediaFile = mediaFileRepository
                .findById(mediaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file not found"));
        if (!mediaFile.getUploadedBy().equals(callerUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Permission is denied");
        }
        mediaFile.setAccessPolicy(policy);
        mediaFile.setAccessScopeId(scope);
        mediaFile.setAccessVersion(mediaFile.getAccessVersion() + 1);
        mediaFileRepository.save(mediaFile);
        mediaAccessCache.put(toDescriptor(mediaFile));
        log.info(
                "媒体访问策略已更新: mediaId={}, policy={}, scope={}, accessVersion={}",
                mediaId,
                policy,
                scope,
                mediaFile.getAccessVersion());
    }

    /**
     * 为新的访问作用域创建媒体文件副本，底层存储不复制，仅创建新的元数据记录。
     *
     * <p>前置条件：source 非空且未被软删除。
     *
     * <p>后置条件：新的 MediaFile 已持久化，缓存中已有对应快照。
     *
     * @param source     原始媒体文件实体
     * @param uploadedBy 新记录的上传者（转发者）
     * @param policy     新的访问策略
     * @param scope      新的访问作用域
     * @return 新创建的 MediaFile 实体
     */
    @Transactional
    public MediaFile copyForScope(MediaFile source, String uploadedBy, MediaAccessPolicy policy, String scope) {
        MediaFile copy = MediaFile.builder()
                .mediaId(UUID.randomUUID())
                .fileName(source.getFileName())
                .contentType(source.getContentType())
                .sizeBytes(source.getSizeBytes())
                .usage(source.getUsage())
                .storagePath(source.getStoragePath())
                .visibility(source.getVisibility())
                .accessPolicy(policy)
                .accessScopeId(scope)
                .accessVersion(1L)
                .uploadedBy(uploadedBy)
                .build();
        mediaFileRepository.save(copy);
        mediaAccessCache.put(toDescriptor(copy));
        log.info(
                "媒体文件副本已创建: originalMediaId={}, newMediaId={}, policy={}, scope={}",
                source.getMediaId(),
                copy.getMediaId(),
                policy,
                scope);
        return copy;
    }

    /**
     * 从缓存或数据库加载媒体访问描述符。
     *
     * <p>前置条件：mediaId 非空。
     *
     * <p>后置条件：缓存命中时直接返回；未命中时从数据库加载并回写缓存。
     *
     * @param mediaId 媒体标识
     * @return 媒体访问描述符
     */
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
        String scope = normalizeScope(descriptor.scope());
        String signature =
                createSignature(descriptor.mediaId(), descriptor.accessVersion(), descriptor.policy(), scope);
        String signedUrl = "/media/"
                + descriptor.mediaId()
                + "?v="
                + descriptor.accessVersion()
                + "&policy="
                + descriptor.policy().name()
                + "&scope="
                + urlEncode(scope)
                + "&sig="
                + urlEncode(signature);
        return new SignedMediaAccess(signedUrl);
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

    private boolean verifySignature(
            UUID mediaId, long accessVersion, MediaAccessPolicy policy, String scope, @Nullable String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = createSignature(mediaId, accessVersion, policy, scope);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    private String createSignature(UUID mediaId, long accessVersion, MediaAccessPolicy policy, String scope) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = mediaId + "\n" + accessVersion + "\n" + policy.name() + "\n" + scope;
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
