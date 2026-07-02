package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.identity.IdentityDtos;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.identity.MerchantProfile;
import io.github.layjason.mayoistar.entity.identity.Qualification;
import io.github.layjason.mayoistar.entity.identity.QualificationStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.MerchantProfileRepository;
import io.github.layjason.mayoistar.repository.QualificationRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 商家资料服务。
 *
 * <p>类职责：处理商家资料的查询与更新、资质提交与审核、营业执照图片上传。
 *
 * <p>不变量：昵称修改前必须通过唯一性检查；资质在 approved 状态不可重新提交。
 */
@Slf4j
@Service
public class MerchantProfileService {

    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final QualificationRepository qualificationRepository;
    private final MediaFileRepository mediaFileRepository;
    private final MediaFileUploadService mediaFileUploadService;
    private final MediaAccessService mediaAccessService;

    /**
     * @param userRepository             用户数据访问
     * @param merchantProfileRepository  商家资料数据访问
     * @param qualificationRepository    资质审核数据访问
     * @param mediaFileRepository        媒体文件数据访问
     * @param mediaFileUploadService     媒体文件上传服务
     */
    public MerchantProfileService(
            UserRepository userRepository,
            MerchantProfileRepository merchantProfileRepository,
            QualificationRepository qualificationRepository,
            MediaFileRepository mediaFileRepository,
            MediaFileUploadService mediaFileUploadService,
            MediaAccessService mediaAccessService) {
        this.userRepository = userRepository;
        this.merchantProfileRepository = merchantProfileRepository;
        this.qualificationRepository = qualificationRepository;
        this.mediaFileRepository = mediaFileRepository;
        this.mediaFileUploadService = mediaFileUploadService;
        this.mediaAccessService = mediaAccessService;
    }

    /**
     * 获取商家资料。
     *
     * <p>前置条件：userId 对应有效商家用户。
     *
     * <p>后置条件：返回 MerchantProfile DTO，含账号状态和资质审核状态。
     *
     * @param userId 用户 ID
     * @return 商家资料
     */
    public IdentityDtos.MerchantProfile getMerchantProfile(String userId) {
        User user = requireMerchant(userId);
        MerchantProfile profile = merchantProfileRepository
                .findByUserId(userId)
                .orElseThrow(() -> new BusinessException(10008, "User kind is not allowed for this operation"));

        return buildMerchantProfile(user, profile);
    }

    /**
     * 更新商家资料。
     *
     * <p>前置条件：userId 对应商家用户。nickname 若修改则必须唯一。avatarMediaId 若提供则必须存在。
     *
     * <p>后置条件：资料部分更新。昵称若更改则同步到 User 实体。
     *
     * @param userId  用户 ID
     * @param request 资料更新请求
     * @return 更新后的商家资料
     */
    @Transactional
    public IdentityDtos.MerchantProfile updateMerchantProfile(
            String userId, IdentityDtos.UpdateMerchantProfileRequest request) {
        User user = requireMerchant(userId);

        MerchantProfile profile = merchantProfileRepository
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
        if (request.getMerchantName() != null) {
            profile.setMerchantName(request.getMerchantName());
        }
        if (request.getInterestedActivityFields() != null) {
            profile.setInterestedActivityFields(request.getInterestedActivityFields());
        }

        Instant now = Instant.now();
        user.setUpdatedAt(now);
        profile.setUpdatedAt(now);
        userRepository.save(user);
        merchantProfileRepository.save(profile);

        log.info("商家资料更新成功: userId={}", userId);
        return buildMerchantProfile(user, profile);
    }

    /**
     * 提交商家资质申请。
     *
     * <p>前置条件：userId 对应商家用户。资质状态不能为 pending 或 approved。licenseMediaIds 非空且存在且用途为 merchantLicense。
     *
     * <p>后置条件：若已有 rejected 的资质则更新，否则创建新的 Qualification 记录，状态变为 pending。
     *
     * @param userId  用户 ID
     * @param request 资质提交请求
     */
    @Transactional
    public void submitQualification(String userId, IdentityDtos.QualificationSubmitRequest request) {
        requireMerchant(userId);

        if (request.getLicenseMediaIds() == null || request.getLicenseMediaIds().isEmpty()) {
            throw new BusinessException(10010, "Media file is unavailable");
        }

        // 所有 license media 必须存在且用途正确
        for (UUID mediaId : request.getLicenseMediaIds()) {
            MediaFile media = mediaFileRepository
                    .findById(mediaId)
                    .orElseThrow(() -> new BusinessException(10010, "Media file is unavailable"));
            if (media.getUsage() != MediaUsage.merchantLicense) {
                throw new BusinessException(10000, "Media usage is invalid");
            }
        }

        // 检查是否有 pending 或 approved 的资质，不能重复提交
        if (qualificationRepository.existsByUserIdAndStatus(userId, QualificationStatus.pending)) {
            throw new BusinessException(10009, "Merchant qualification has already been submitted");
        }
        if (qualificationRepository.existsByUserIdAndStatus(userId, QualificationStatus.approved)) {
            throw new BusinessException(10009, "Merchant qualification has already been submitted");
        }

        Instant now = Instant.now();

        // 若存在 rejected 或 not_submitted 的资质则复用，否则创建新记录
        Qualification qualification = qualificationRepository
                .findByUserId(userId)
                .orElse(Qualification.builder()
                        .qualificationId(UUID.randomUUID().toString())
                        .userId(userId)
                        .createdAt(now)
                        .build());

        qualification.setStatus(QualificationStatus.pending);
        qualification.setLicenseMediaIds(request.getLicenseMediaIds());
        qualification.setSubmittedAt(now);
        qualification.setReviewedAt(null);
        qualification.setRejectReason(null);
        qualification.setReviewerId(null);
        qualificationRepository.save(qualification);

        log.info("商家资质提交成功: userId={}, qualificationId={}", userId, qualification.getQualificationId());
    }

    /**
     * 上传商家营业执照或营业凭证图片。
     *
     * <p>前置条件：file 为有效的图片文件（JPG/PNG），大小不超过 10 MB。
     *
     * <p>后置条件：文件存储到 RustFS 对象存储，元数据存入 media_files 表，usage 为 merchantLicense。
     *
     * @param userId 上传者用户 ID
     * @param file   上传的文件
     * @return 媒体文件元数据
     */
    @Transactional
    public CommonDtos.MediaFile uploadLicense(String userId, MultipartFile file) {
        return mediaFileUploadService.upload(userId, file, MediaUsage.merchantLicense);
    }

    /**
     * 校验用户是否为商家，如果不是则抛出异常。
     *
     * <p>前置条件：userId 非空。
     *
     * <p>后置条件：若用户不存在抛出 BusinessException(10003)；若用户非商家抛出 BusinessException(10008)；否则返回 User。
     *
     * @param userId 用户 ID
     * @return 商家用户实体
     */
    private User requireMerchant(String userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(10003, "Email or password is invalid"));
        if (user.getKind() != UserKind.merchant) {
            throw new BusinessException(10008, "User kind is not allowed for this operation");
        }
        return user;
    }

    /**
     * 组装商家资料 DTO。
     *
     * <p>前置条件：user.kind == merchant。
     *
     * <p>后置条件：返回填充完整的 MerchantProfile。
     */
    private IdentityDtos.MerchantProfile buildMerchantProfile(User user, MerchantProfile profile) {
        IdentityDtos.MerchantProfile dto = new IdentityDtos.MerchantProfile();
        dto.setUserId(user.getUserId());
        dto.setNickname(user.getNickname());
        dto.setMerchantName(profile.getMerchantName());
        dto.setInterestedActivityFields(
                profile.getInterestedActivityFields() != null ? profile.getInterestedActivityFields() : List.of());
        dto.setAccountStatus(user.getAccountStatus());

        // 头像
        if (profile.getAvatarMediaId() != null) {
            mediaFileRepository.findById(profile.getAvatarMediaId()).ifPresent(avatar -> {
                dto.setAvatar(mediaAccessService.toSignedDto(avatar));
            });
        }

        // 资质信息
        Qualification qualification =
                qualificationRepository.findByUserId(user.getUserId()).orElse(null);
        if (qualification != null) {
            dto.setQualificationStatus(qualification.getStatus());
            IdentityDtos.QualificationDetail detail = new IdentityDtos.QualificationDetail();
            detail.setStatus(qualification.getStatus());
            if (qualification.getSubmittedAt() != null) {
                detail.setSubmittedAt(qualification.getSubmittedAt().toString());
            }
            if (qualification.getReviewedAt() != null) {
                detail.setReviewedAt(qualification.getReviewedAt().toString());
            }
            detail.setRejectReason(qualification.getRejectReason());

            // 将 licenseMediaIds 转换为 URL 列表
            if (qualification.getLicenseMediaIds() != null
                    && !qualification.getLicenseMediaIds().isEmpty()) {
                List<String> urls = new ArrayList<>();
                for (UUID licenseId : qualification.getLicenseMediaIds()) {
                    mediaFileRepository
                            .findById(licenseId)
                            .ifPresentOrElse(
                                    mf -> urls.add(
                                            mediaAccessService.toSignedDto(mf).getSignedUrl()),
                                    () -> urls.add(""));
                }
                detail.setLicenseImageUrls(urls);
            }
            dto.setQualification(detail);
        } else {
            dto.setQualificationStatus(QualificationStatus.not_submitted);
        }

        return dto;
    }
}
