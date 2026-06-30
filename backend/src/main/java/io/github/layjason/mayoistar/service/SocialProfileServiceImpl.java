package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.identity.PersonalProfile;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.BlacklistRepository;
import io.github.layjason.mayoistar.repository.PersonalProfileRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.util.Collections;
import org.springframework.stereotype.Service;

/**
 * 社交公开资料服务实现。
 *
 * <p>类职责：读取用户公开资料，并应用黑名单可见性规则。
 *
 * <p>不变量：读取资料不修改数据库状态。
 */
@Service
public class SocialProfileServiceImpl implements SocialProfileService {

    private final UserRepository userRepository;
    private final PersonalProfileRepository personalProfileRepository;
    private final BlacklistRepository blacklistRepository;

    public SocialProfileServiceImpl(
            UserRepository userRepository,
            PersonalProfileRepository personalProfileRepository,
            BlacklistRepository blacklistRepository) {
        this.userRepository = userRepository;
        this.personalProfileRepository = personalProfileRepository;
        this.blacklistRepository = blacklistRepository;
    }

    /**
     * 获取目标用户公开资料。
     *
     * <p>前置条件：currentUserId 为当前登录用户，targetUserId 为待查看用户。
     *
     * <p>后置条件：返回公开资料 DTO；若目标用户不存在或对方已拉黑当前用户，则抛出业务异常。
     */
    @Override
    public IdentityDtos.PublicUserProfile getUserProfile(String currentUserId, String targetUserId) {
        User targetUser = userRepository
                .findById(targetUserId)
                .orElseThrow(() -> new BusinessException(40000, "User " + targetUserId + " is not visible"));
        if (blacklistRepository.existsByBlockerIdAndBlockedUserId(targetUserId, currentUserId)) {
            throw new BusinessException(40001, "Blacklist relation blocks this operation");
        }

        PersonalProfile profile = personalProfileRepository
                .findByUserId(targetUserId)
                .orElse(PersonalProfile.builder().userId(targetUserId).build());
        return toPublicUserProfile(targetUser, profile);
    }

    private IdentityDtos.PublicUserProfile toPublicUserProfile(User user, PersonalProfile profile) {
        IdentityDtos.PublicUserProfile dto = new IdentityDtos.PublicUserProfile();
        dto.setUserId(user.getUserId());
        dto.setNickname(user.getNickname());
        dto.setGender(profile.getGender());
        dto.setBirthday(profile.getBirthday());
        dto.setSignature(profile.getSignature());
        dto.setInterestTags(profile.getInterestTags() != null ? profile.getInterestTags() : Collections.emptyList());
        dto.setReputationScore(profile.getReputationScore() != null ? profile.getReputationScore() : 100);
        dto.setKind(user.getKind());
        if (profile.getAvatar() != null) {
            dto.setAvatar(toMediaFileDto(profile.getAvatar()));
        }
        return dto;
    }

    private CommonDtos.MediaFile toMediaFileDto(MediaFile mediaFile) {
        CommonDtos.MediaFile dto = new CommonDtos.MediaFile();
        dto.setMediaId(mediaFile.getMediaId());
        dto.setFileName(mediaFile.getFileName());
        dto.setContentType(mediaFile.getContentType());
        dto.setSizeBytes(mediaFile.getSizeBytes());
        dto.setUsage(mediaFile.getUsage());
        dto.setUrl(mediaFile.getUrl());
        dto.setUploadedAt(mediaFile.getUploadedAt().toString());
        return dto;
    }
}
