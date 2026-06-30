package io.github.layjason.mayoistar.service;

import io.github.layjason.mayoistar.api.admin.AdminDtos;
import io.github.layjason.mayoistar.entity.identity.MerchantProfile;
import io.github.layjason.mayoistar.entity.identity.Qualification;
import io.github.layjason.mayoistar.entity.identity.QualificationStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.AdminRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.MerchantProfileRepository;
import io.github.layjason.mayoistar.repository.QualificationRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 后台管理服务。
 *
 * <p>类职责：处理管理员对用户、商家、活动等的管理操作。
 */
@Slf4j
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final QualificationRepository qualificationRepository;
    private final AdminRepository adminRepository;
    private final MediaFileRepository mediaFileRepository;

    /**
     * @param userRepository             用户数据访问
     * @param merchantProfileRepository  商家资料数据访问
     * @param qualificationRepository    资质审核数据访问
     * @param adminRepository            管理员数据访问
     * @param mediaFileRepository        媒体文件数据访问
     */
    public AdminService(
            UserRepository userRepository,
            MerchantProfileRepository merchantProfileRepository,
            QualificationRepository qualificationRepository,
            AdminRepository adminRepository,
            MediaFileRepository mediaFileRepository) {
        this.userRepository = userRepository;
        this.merchantProfileRepository = merchantProfileRepository;
        this.qualificationRepository = qualificationRepository;
        this.adminRepository = adminRepository;
        this.mediaFileRepository = mediaFileRepository;
    }

    /**
     * 审核商家资质。
     *
     * <p>前置条件：merchantId 对应有效商家用户，且存在 pending 状态的资质审核记录。若驳回则 must 提供 reason。adminId 对应有效管理员。
     *
     * <p>后置条件：Qualification.status 变为 approved 或 rejected，设置 reviewedAt、reviewerId，若驳回则设置 rejectReason。
     *
     * @param merchantId 商家用户 ID
     * @param adminId    操作管理员 ID
     * @param request    审核请求
     * @return 更新后的商家资料
     */
    @Transactional
    public io.github.layjason.mayoistar.api.identity.IdentityDtos.MerchantProfile reviewMerchantQualification(
            String merchantId, String adminId, AdminDtos.MerchantReviewRequest request) {

        // 校验管理员存在
        adminRepository.findById(adminId).orElseThrow(() -> new BusinessException(401, "Admin not found"));

        // 校验商家存在
        User user = userRepository.findById(merchantId).orElseThrow(() -> new BusinessException(404, "User not found"));
        if (user.getKind() != UserKind.merchant) {
            throw new BusinessException(10008, "User kind is not allowed for this operation");
        }

        // 校验资质存在且为 pending 状态
        Qualification qualification = qualificationRepository
                .findByUserIdAndStatus(merchantId, QualificationStatus.pending)
                .orElseThrow(() -> new BusinessException(400, "Merchant review state is invalid"));

        // 驳回时必须填写原因
        if (Boolean.FALSE.equals(request.getApproved())
                && (request.getReason() == null || request.getReason().isBlank())) {
            throw new BusinessException(400, "Review reason is required");
        }

        Instant now = Instant.now();
        qualification.setStatus(request.getApproved() ? QualificationStatus.approved : QualificationStatus.rejected);
        qualification.setReviewedAt(now);
        qualification.setReviewerId(adminId);
        if (Boolean.FALSE.equals(request.getApproved())) {
            qualification.setRejectReason(request.getReason());
        }
        qualificationRepository.save(qualification);

        // 获取商家资料并构建响应
        MerchantProfile profile = merchantProfileRepository
                .findByUserId(merchantId)
                .orElseThrow(() -> new BusinessException(10008, "User kind is not allowed for this operation"));

        log.info("商家资质审核完成: merchantId={}, adminId={}, result={}", merchantId, adminId, qualification.getStatus());

        return buildMerchantProfileResponse(user, profile, qualification);
    }

    /**
     * 获取商家信息供管理员查看。
     *
     * <p>前置条件：merchantId 对应有效商家用户。
     *
     * <p>后置条件：返回完整的商家资料 DTO。
     *
     * @param merchantId 商家用户 ID
     * @return 商家资料
     */
    public io.github.layjason.mayoistar.api.identity.IdentityDtos.MerchantProfile getMerchantForAdmin(
            String merchantId) {
        User user = userRepository.findById(merchantId).orElseThrow(() -> new BusinessException(404, "User not found"));
        if (user.getKind() != UserKind.merchant) {
            throw new BusinessException(404, "User not found");
        }

        MerchantProfile profile = merchantProfileRepository
                .findByUserId(merchantId)
                .orElseThrow(() -> new BusinessException(10008, "User kind is not allowed for this operation"));

        Qualification qualification =
                qualificationRepository.findByUserId(merchantId).orElse(null);

        return buildMerchantProfileResponse(user, profile, qualification);
    }

    /**
     * 组装商家资料 DTO。
     */
    private io.github.layjason.mayoistar.api.identity.IdentityDtos.MerchantProfile buildMerchantProfileResponse(
            User user, MerchantProfile profile, Qualification qualification) {
        io.github.layjason.mayoistar.api.identity.IdentityDtos.MerchantProfile dto =
                new io.github.layjason.mayoistar.api.identity.IdentityDtos.MerchantProfile();
        dto.setUserId(user.getUserId());
        dto.setNickname(user.getNickname());
        dto.setMerchantName(profile.getMerchantName());
        dto.setInterestedActivityFields(
                profile.getInterestedActivityFields() != null
                        ? profile.getInterestedActivityFields()
                        : java.util.List.of());
        dto.setAccountStatus(user.getAccountStatus());

        if (qualification != null) {
            dto.setQualificationStatus(qualification.getStatus());
            io.github.layjason.mayoistar.api.identity.IdentityDtos.QualificationDetail detail =
                    new io.github.layjason.mayoistar.api.identity.IdentityDtos.QualificationDetail();
            detail.setStatus(qualification.getStatus());
            if (qualification.getSubmittedAt() != null) {
                detail.setSubmittedAt(qualification.getSubmittedAt().toString());
            }
            if (qualification.getReviewedAt() != null) {
                detail.setReviewedAt(qualification.getReviewedAt().toString());
            }
            detail.setRejectReason(qualification.getRejectReason());

            if (qualification.getLicenseMediaIds() != null
                    && !qualification.getLicenseMediaIds().isEmpty()) {
                java.util.List<String> urls = new java.util.ArrayList<>();
                for (String licenseId : qualification.getLicenseMediaIds()) {
                    mediaFileRepository
                            .findById(licenseId)
                            .ifPresentOrElse(
                                    mf -> urls.add(mf.getUrl() != null ? mf.getUrl() : ""), () -> urls.add(""));
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
