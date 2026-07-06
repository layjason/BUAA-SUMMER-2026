package io.github.layjason.mayoistar.service.activities;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.entity.activities.ActivityTemplate;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.exception.ErrorCodes;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityTemplateRepository;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 活动模板后台管理服务。
 *
 * <p>类职责：维护平台级活动模板，提供管理员创建、更新、删除模板的事务边界。
 *
 * <p>类不变量：模板为平台全局资源，不绑定创建者；模板封面若存在，则必须是未删除的 activityImage 媒体并对所有用户公开可见。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityTemplateAdminService {

    private final ActivityTemplateRepository activityTemplateRepository;
    private final MediaFileRepository mediaFileRepository;
    private final ActivityDraftMapper activityDraftMapper;
    private final MediaAccessService mediaAccessService;

    /**
     * 创建平台级活动模板。
     *
     * <p>前置条件：调用方已通过管理员权限校验；请求字段已通过 Controller 层 Bean Validation；
     * 若传入默认封面媒体，则媒体存在、未删除且用途为 activityImage。
     *
     * <p>后置条件：新增一条活动模板记录；默认封面媒体被设置为 publicAccess/publicVisible；返回新模板详情。
     *
     * <p>不变量：模板不记录个人创建者，创建后对所有用户可见。
     *
     * @param request 模板创建请求
     * @return 新建模板详情
     */
    @Transactional
    public ActivityDtos.ActivityTemplate createTemplate(ActivityDtos.ActivityTemplateUpsertRequest request) {
        ActivityTemplate template = ActivityTemplate.builder()
                .templateId(UUID.randomUUID().toString())
                .build();
        MediaFile coverImage = applyTemplateFields(template, request);
        ActivityTemplate savedTemplate = activityTemplateRepository.save(template);
        log.info("管理员已创建活动模板，templateId={}", savedTemplate.getTemplateId());
        return activityDraftMapper.toTemplate(savedTemplate, coverImage);
    }

    /**
     * 更新平台级活动模板。
     *
     * <p>前置条件：调用方已通过管理员权限校验；templateId 对应模板存在；请求字段已通过 Controller 层 Bean Validation；
     * 若传入默认封面媒体，则媒体存在、未删除且用途为 activityImage。
     *
     * <p>后置条件：目标模板的基础信息被请求整体覆盖；默认封面媒体被设置为 publicAccess/publicVisible；返回更新后模板详情。
     *
     * <p>不变量：templateId 不变；已基于旧模板生成的活动草稿不会被回写修改。
     *
     * @param templateId 模板 ID
     * @param request 模板更新请求
     * @return 更新后模板详情
     */
    @Transactional
    public ActivityDtos.ActivityTemplate updateTemplate(
            String templateId, ActivityDtos.ActivityTemplateUpsertRequest request) {
        ActivityTemplate template = findTemplate(templateId);
        MediaFile coverImage = applyTemplateFields(template, request);
        ActivityTemplate savedTemplate = activityTemplateRepository.save(template);
        log.info("管理员已更新活动模板，templateId={}", sanitizeForLog(templateId));
        return activityDraftMapper.toTemplate(savedTemplate, coverImage);
    }

    /**
     * 删除平台级活动模板。
     *
     * <p>前置条件：调用方已通过管理员权限校验；templateId 对应模板存在。
     *
     * <p>后置条件：目标模板被删除，不再出现在用户模板列表。
     *
     * <p>不变量：已基于该模板创建的草稿与活动不被删除或修改。
     *
     * @param templateId 模板 ID
     */
    @Transactional
    public void deleteTemplate(String templateId) {
        ActivityTemplate template = findTemplate(templateId);
        activityTemplateRepository.delete(template);
        log.info("管理员已删除活动模板，templateId={}", sanitizeForLog(templateId));
    }

    /**
     * 将模板请求字段应用到实体。
     *
     * <p>前置条件：template 非空；request 已通过基础字段校验；默认封面媒体 ID 可为空。
     *
     * <p>后置条件：实体基础字段与请求一致；封面媒体关联和封面访问策略已同步。
     *
     * <p>不变量：不修改 templateId。
     *
     * @param template 目标模板实体
     * @param request 模板请求
     * @return 已校验并公开化的封面媒体，或 null
     */
    private MediaFile applyTemplateFields(
            ActivityTemplate template, ActivityDtos.ActivityTemplateUpsertRequest request) {
        MediaFile coverImage = resolveCoverImage(request.getDefaultCoverImageMediaId());
        template.setName(request.getName());
        template.setActivityType(request.getActivityType());
        template.setDefaultTags(List.copyOf(request.getDefaultTags()));
        template.setDefaultIntroduction(request.getDefaultIntroduction());
        template.setDefaultSafetyNotice(request.getDefaultSafetyNotice());
        template.setDefaultCapacity(request.getDefaultCapacity());
        template.setDefaultCoverImageMediaId(coverImage == null ? null : coverImage.getMediaId());
        return coverImage;
    }

    /**
     * 解析并公开模板封面媒体。
     *
     * <p>前置条件：coverImageMediaId 可为空；非空时必须指向未删除的 activityImage 媒体。
     *
     * <p>后置条件：非空媒体被升级为 publicAccess/publicVisible，并返回对应实体；空 ID 返回 null。
     *
     * <p>不变量：不会创建或删除媒体文件。
     *
     * @param coverImageMediaId 默认封面媒体 ID
     * @return 可用的默认封面媒体，或 null
     */
    private MediaFile resolveCoverImage(UUID coverImageMediaId) {
        if (coverImageMediaId == null) {
            return null;
        }
        MediaFile mediaFile = mediaFileRepository
                .findById(coverImageMediaId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .filter(candidate -> candidate.getUsage() == MediaUsage.activityImage)
                .orElseThrow(
                        () -> new BusinessException(ErrorCodes.MEDIA_FILE_UNAVAILABLE, "Media file is unavailable"));
        mediaAccessService.overrideAccessPolicy(
                coverImageMediaId, MediaAccessPolicy.publicAccess, "", MediaVisibility.publicVisible);
        return mediaFileRepository.findById(coverImageMediaId).orElse(mediaFile);
    }

    private ActivityTemplate findTemplate(String templateId) {
        return activityTemplateRepository
                .findById(templateId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCodes.TEMPLATE_NOT_FOUND, "Activity template does not exist"));
    }

    private String sanitizeForLog(String value) {
        return value.replace("\n", "\\n").replace("\r", "\\r");
    }
}
