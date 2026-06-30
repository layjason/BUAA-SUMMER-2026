package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.InterestTagRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户资料服务。
 *
 * <p>类职责：处理个人资料的查询与更新、昵称唯一性校验、兴趣标签获取、头像上传。
 *
 * <p>不变量：昵称修改前必须通过唯一性检查。
 */
@Slf4j
@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final PersonalProfileRepository personalProfileRepository;
    private final InterestTagRepository interestTagRepository;
    private final MediaFileRepository mediaFileRepository;

    /**
     * @param userRepository              用户数据访问
     * @param personalProfileRepository   个人资料数据访问
     * @param interestTagRepository       兴趣标签数据访问
     * @param mediaFileRepository         媒体文件数据访问
     */
    public UserProfileService(
            UserRepository userRepository,
            PersonalProfileRepository personalProfileRepository,
            InterestTagRepository interestTagRepository,
            MediaFileRepository mediaFileRepository) {
        this.userRepository = userRepository;
        this.personalProfileRepository = personalProfileRepository;
        this.interestTagRepository = interestTagRepository;
        this.mediaFileRepository = mediaFileRepository;
    }

    /**
     * 获取当前用户的公开资料。
     *
     * <p>前置条件：userId 对应有效用户，且为个人用户。
     *
     * <p>后置条件：返回 PublicUserProfile，包含昵称、头像、性别、生日、签名、兴趣标签、信誉分。
     *
     * @param userId 用户 ID
     * @return 用户公开资料
     */
    public IdentityDtos.PublicUserProfile getProfile(String userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(10003, "Email or password is invalid"));
        if (user.getKind() != UserKind.personal) {
            throw new BusinessException(10008, "User kind is not allowed for this operation");
        }

        PersonalProfile profile = personalProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new BusinessException(10008, "User kind is not allowed for this operation"));

        return buildPublicProfile(user, profile);
    }

    /**
     * 更新个人资料。
     *
     * <p>前置条件：userId 对应个人用户。nickname 若修改则必须唯一。avatarMediaId 若提供则必须存在。
     *
     * <p>后置条件：资料部分更新。昵称若更改则同步到 User 实体。
     *
     * @param userId  用户 ID
     * @param request 资料更新请求
     * @return 更新后的公开资料
     */
    @Transactional
    public IdentityDtos.PublicUserProfile updateProfile(
            String userId, IdentityDtos.UpdatePersonalProfileRequest request) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(10003, "Email or password is invalid"));
        if (user.getKind() != UserKind.personal) {
            throw new BusinessException(10008, "User kind is not allowed for this operation");
        }

        PersonalProfile profile = personalProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new BusinessException(10008, "User kind is not allowed for this operation"));

        if (request.getNickname() != null) {
            if (!request.getNickname().equals(user.getNickname())) {
                if (userRepository.existsByNickname(request.getNickname())) {
                    throw new BusinessException(10002, "Nickname is unavailable");
                }
                user.setNickname(request.getNickname());
            }
        }
        if (request.getAvatarMediaId() != null) {
            if (!mediaFileRepository.existsById(request.getAvatarMediaId())) {
                throw new BusinessException(10010, "Media file is unavailable");
            }
            profile.setAvatarMediaId(request.getAvatarMediaId());
        }
        if (request.getGender() != null) {
            profile.setGender(request.getGender());
        }
        if (request.getBirthday() != null) {
            profile.setBirthday(request.getBirthday());
        }
        if (request.getSignature() != null) {
            profile.setSignature(request.getSignature());
        }
        if (request.getInterestTags() != null) {
            profile.setInterestTags(request.getInterestTags());
        }

        Instant now = Instant.now();
        user.setUpdatedAt(now);
        profile.setUpdatedAt(now);
        userRepository.save(user);
        personalProfileRepository.save(profile);

        log.info("个人资料更新成功: userId={}", userId);
        return buildPublicProfile(user, profile);
    }

    /**
     * 校验昵称是否可用。
     *
     * <p>前置条件：nickname 非空。
     *
     * <p>后置条件：返回可用性结果。用户名在黑名单或已占用时不可用。
     *
     * @param nickname 待校验昵称
     * @return 昵称可用性
     */
    public IdentityDtos.NicknameAvailability checkNickname(String nickname) {
        boolean available = !userRepository.existsByNickname(nickname);
        IdentityDtos.NicknameAvailability result = new IdentityDtos.NicknameAvailability();
        result.setNickname(nickname);
        result.setAvailable(available);
        return result;
    }

    /**
     * 获取系统预定义兴趣标签。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回所有兴趣标签，按名称排序。
     *
     * @return 兴趣标签列表
     */
    public List<IdentityDtos.InterestTagItem> getInterestTags() {
        return interestTagRepository.findAllByOrderByName().stream()
                .map(tag -> {
                    IdentityDtos.InterestTagItem item = new IdentityDtos.InterestTagItem();
                    item.setName(tag.getName());
                    return item;
                })
                .toList();
    }

    /**
     * 上传用户头像。
     *
     * <p>前置条件：file 为有效的图片文件（JPG/PNG），大小不超过限制。
     *
     * <p>后置条件：文件存储到本地文件系统，元数据存入 media_files 表。
     *
     * @param userId 上传者用户 ID
     * @param file   上传的文件
     * @return 媒体文件元数据
     */
    @Transactional
    public CommonDtos.MediaFile uploadAvatar(String userId, MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png"))) {
            throw new BusinessException(10013, "Image must be a JPG or PNG file");
        }

        long maxSize = 5 * 1024 * 1024L; // 5 MB
        if (file.getSize() > maxSize) {
            throw new BusinessException(10014, "Image file is too large");
        }

        String mediaId = UUID.randomUUID().toString();
        String originalFilename =
                Optional.ofNullable(file.getOriginalFilename()).orElse("avatar.png");
        String storagePath = "avatars/" + userId + "/" + mediaId + "_" + originalFilename;

        try {
            java.nio.file.Path dir = java.nio.file.Path.of("uploads/avatars/" + userId);
            java.nio.file.Files.createDirectories(dir);
            file.transferTo(dir.resolve(mediaId + "_" + originalFilename).toFile());
        } catch (Exception e) {
            log.error("头像文件写入失败: userId={}", userId, e);
            throw new RuntimeException("文件上传失败", e);
        }

        Instant now = Instant.now();
        MediaFile mediaFile = MediaFile.builder()
                .mediaId(mediaId)
                .fileName(originalFilename)
                .contentType(contentType)
                .sizeBytes(file.getSize())
                .usage(MediaUsage.avatar)
                .storagePath(storagePath)
                .uploadedBy(userId)
                .uploadedAt(now)
                .build();
        mediaFileRepository.save(mediaFile);

        log.info("头像上传成功: mediaId={}, userId={}", mediaId, userId);

        CommonDtos.MediaFile result = new CommonDtos.MediaFile();
        result.setMediaId(mediaId);
        result.setFileName(originalFilename);
        result.setContentType(contentType);
        result.setSizeBytes(file.getSize());
        result.setUsage(MediaUsage.avatar);
        result.setUploadedAt(now.toString());
        return result;
    }

    /**
     * 组装公开资料 DTO。
     *
     * <p>前置条件：user.kind == personal。
     *
     * <p>后置条件：返回填充完整的 PublicUserProfile。
     */
    private IdentityDtos.PublicUserProfile buildPublicProfile(User user, PersonalProfile profile) {
        IdentityDtos.PublicUserProfile dto = new IdentityDtos.PublicUserProfile();
        dto.setUserId(user.getUserId());
        dto.setNickname(user.getNickname());
        dto.setGender(profile.getGender());
        dto.setBirthday(profile.getBirthday());
        dto.setSignature(profile.getSignature());
        dto.setInterestTags(profile.getInterestTags() != null ? profile.getInterestTags() : List.of());
        // TODO: 信誉分评定标准待确定，当前返回默认值
        dto.setReputationScore(profile.getReputationScore() != null ? profile.getReputationScore() : 100);
        dto.setKind(user.getKind());
        return dto;
    }
}
