package io.github.layjason.mayoistar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.config.TestContentReviewConfiguration;
import io.github.layjason.mayoistar.config.TestSecurityConfiguration;
import io.github.layjason.mayoistar.config.TestStorageConfiguration;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityImage;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityTemplate;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.common.MediaVisibility;
import io.github.layjason.mayoistar.entity.identity.AccountStatus;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.identity.UserKind;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.ActivityReviewRecordRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.repository.TeamRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityImageRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityTemplateRepository;
import io.github.layjason.mayoistar.service.activities.ActivityDraftService;
import io.github.layjason.mayoistar.service.activities.ActivityTemplateAdminService;
import io.github.layjason.mayoistar.service.ai.ContentReviewRisk;
import io.github.layjason.mayoistar.service.ai.ContentReviewScanResult;
import io.github.layjason.mayoistar.service.storage.FileStorageService;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestSecurityConfiguration.class, TestStorageConfiguration.class, TestContentReviewConfiguration.class})
class ActivityDraftServiceTests {

    private static final UUID IMAGE_A_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID IMAGE_B_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private ActivityDraftService activityDraftService;

    @Autowired
    private ActivityTemplateAdminService activityTemplateAdminService;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private ActivityImageRepository activityImageRepository;

    @Autowired
    private ActivityTemplateRepository activityTemplateRepository;

    @Autowired
    private ActivityReviewRecordRepository activityReviewRecordRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestContentReviewConfiguration.FakeContentReviewClient contentReviewClient;

    @AfterEach
    void tearDown() {
        contentReviewClient.reset();
        activityReviewRecordRepository.deleteAll();
        activityImageRepository.deleteAll();
        activityRepository.deleteAll();
        activityTemplateRepository.deleteAll();
        mediaFileRepository.deleteAll();
        teamMemberRepository.deleteAll();
        teamRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void saveDraftShouldPersistDraftAndImages() {
        User organizer = saveUser("user-a");
        MediaFile image = saveMediaFile(IMAGE_A_ID, organizer.getUserId());

        ActivityDtos.ActivityDraftUpsertRequest request = createDraftRequest(List.of(image.getMediaId()));
        ActivityDtos.ActivityDraftDetail draftDetail = activityDraftService.saveDraft(organizer.getUserId(), request);

        Activity savedActivity =
                activityRepository.findById(draftDetail.getActivityId()).orElseThrow();
        assertThat(savedActivity.getOrganizerId()).isEqualTo(organizer.getUserId());
        assertThat(savedActivity.getReviewStatus()).isEqualTo(ActivityReviewStatus.draft);
        assertThat(savedActivity.getRuntimeStatus()).isEqualTo(ActivityRuntimeStatus.notStarted);
        assertThat(draftDetail.getImages()).hasSize(1);
        assertThat(draftDetail.getImages().getFirst().getMediaId()).isEqualTo(image.getMediaId());
    }

    @Test
    void saveDraftShouldUpgradeImageToActivityOwner() {
        User organizer = saveUser("user-a");
        MediaFile image = saveMediaFile(IMAGE_A_ID, organizer.getUserId());

        ActivityDtos.ActivityDraftDetail draftDetail =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of(image.getMediaId())));

        MediaFile upgraded = mediaFileRepository.findById(image.getMediaId()).orElseThrow();
        assertThat(upgraded.getAccessPolicy()).isEqualTo(MediaAccessPolicy.activityOwner);
        assertThat(upgraded.getAccessScopeId()).isEqualTo(draftDetail.getActivityId());
        assertThat(upgraded.getAccessVersion()).isEqualTo(2L);
        assertThat(upgraded.getDeletedAt()).isNull();
    }

    @Test
    void updateDraftShouldSoftDeleteRemovedImage() {
        User organizer = saveUser("user-a");
        MediaFile image = saveMediaFile(IMAGE_A_ID, organizer.getUserId());
        ActivityDtos.ActivityDraftDetail created =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of(image.getMediaId())));

        activityDraftService.updateDraft(organizer.getUserId(), created.getActivityId(), createDraftRequest(List.of()));

        MediaFile removed = mediaFileRepository.findById(image.getMediaId()).orElseThrow();
        assertThat(removed.getDeletedAt()).isNotNull();
        assertThat(removed.getAccessVersion()).isEqualTo(3L);
        assertThat(activityImageRepository.findByActivityIdOrderBySortOrderAsc(created.getActivityId()))
                .isEmpty();
    }

    @Test
    void saveDraftShouldPersistPartialRequestWithoutExposingPlaceholders() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftUpsertRequest request = new ActivityDtos.ActivityDraftUpsertRequest();
        request.setTitle("未完成的活动草稿");

        ActivityDtos.ActivityDraftDetail draft = activityDraftService.saveDraft(organizer.getUserId(), request);

        Activity savedActivity =
                activityRepository.findById(draft.getActivityId()).orElseThrow();
        assertThat(savedActivity.getTitle()).isEqualTo("未完成的活动草稿");
        assertThat(savedActivity.getStartAt()).isEqualTo(Instant.EPOCH);
        assertThat(savedActivity.getEndAt()).isEqualTo(Instant.EPOCH);
        assertThat(savedActivity.getCapacity()).isZero();
        assertThat(draft.getTitle()).isEqualTo("未完成的活动草稿");
        assertThat(draft.getStartAt()).isNull();
        assertThat(draft.getEndAt()).isNull();
        assertThat(draft.getCapacity()).isNull();
    }

    @Test
    void submitActivityShouldRejectDraftWithInternalPlaceholders() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftUpsertRequest request = createDraftRequest(List.of());
        request.setStartAt(null);
        request.setEndAt(null);
        request.setCapacity(null);
        ActivityDtos.ActivityDraftDetail draft = activityDraftService.saveDraft(organizer.getUserId(), request);

        assertThatThrownBy(() -> activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动结束时间必须晚于开始时间");
    }

    @Test
    void updateDraftShouldMergePartialRequestWithoutClearingExistingFields() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail created =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        ActivityDtos.ActivityDraftUpsertRequest request = new ActivityDtos.ActivityDraftUpsertRequest();
        request.setTitle("周末羽毛球（更新版）");
        request.setIntroduction("更新后的活动简介");
        request.setCapacity(30);

        ActivityDtos.ActivityDraftDetail updated =
                activityDraftService.updateDraft(organizer.getUserId(), created.getActivityId(), request);

        Activity savedActivity =
                activityRepository.findById(updated.getActivityId()).orElseThrow();
        assertThat(savedActivity.getTitle()).isEqualTo("周末羽毛球（更新版）");
        assertThat(savedActivity.getIntroduction()).isEqualTo("更新后的活动简介");
        assertThat(savedActivity.getCapacity()).isEqualTo(30);
        assertThat(savedActivity.getStartAt()).isEqualTo(Instant.parse("2026-07-02T10:00:00Z"));
        assertThat(savedActivity.getEndAt()).isEqualTo(Instant.parse("2026-07-02T12:00:00Z"));
        assertThat(updated.getStartAt()).isEqualTo("2026-07-02T10:00:00Z");
    }

    @Test
    void draftFlowShouldSupportIncrementalSaveUpdateAndSubmit() {
        User organizer = saveUser("user-a");
        MediaFile imageA = saveMediaFile(IMAGE_A_ID, organizer.getUserId());
        MediaFile imageB = saveMediaFile(IMAGE_B_ID, organizer.getUserId());

        ActivityDtos.ActivityDraftUpsertRequest titleOnlyRequest = new ActivityDtos.ActivityDraftUpsertRequest();
        titleOnlyRequest.setTitle("先占一个标题");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), titleOnlyRequest);
        assertThat(draft.getTitle()).isEqualTo("先占一个标题");
        assertThat(draft.getStartAt()).isNull();
        assertThat(draft.getEndAt()).isNull();
        assertThat(draft.getCapacity()).isNull();

        ActivityDtos.ActivityDraftUpsertRequest textPatch = new ActivityDtos.ActivityDraftUpsertRequest();
        textPatch.setTags(List.of("运动", "羽毛球"));
        textPatch.setIntroduction("第一次补充活动介绍");
        textPatch.setSafetyNotice("请穿防滑运动鞋");
        ActivityDtos.ActivityDraftDetail textUpdated =
                activityDraftService.updateDraft(organizer.getUserId(), draft.getActivityId(), textPatch);
        assertThat(textUpdated.getTitle()).isEqualTo("先占一个标题");
        assertThat(textUpdated.getTags()).containsExactly("运动", "羽毛球");
        assertThat(textUpdated.getStartAt()).isNull();
        assertThat(textUpdated.getCapacity()).isNull();

        ActivityDtos.ActivityDraftUpsertRequest schedulePatch = new ActivityDtos.ActivityDraftUpsertRequest();
        schedulePatch.setStartAt("2026-07-04T02:00:00Z");
        schedulePatch.setEndAt("2026-07-04T04:00:00Z");
        schedulePatch.setRegistrationDeadline("2026-07-03T12:00:00Z");
        schedulePatch.setLocation(createLocation("北京", "海淀体育馆", "三号场"));
        schedulePatch.setRequireLocationCheck(true);
        ActivityDtos.ActivityDraftDetail scheduleUpdated =
                activityDraftService.updateDraft(organizer.getUserId(), draft.getActivityId(), schedulePatch);
        assertThat(scheduleUpdated.getStartAt()).isEqualTo("2026-07-04T02:00:00Z");
        assertThat(scheduleUpdated.getEndAt()).isEqualTo("2026-07-04T04:00:00Z");
        assertThat(scheduleUpdated.getRegistrationDeadline()).isEqualTo("2026-07-03T12:00:00Z");
        assertThat(scheduleUpdated.getLocation().getAddress()).isEqualTo("海淀体育馆");
        assertThat(scheduleUpdated.getRequireLocationCheck()).isTrue();
        assertThat(scheduleUpdated.getCapacity()).isNull();

        ActivityDtos.ActivityDraftUpsertRequest capacityAndImagePatch = new ActivityDtos.ActivityDraftUpsertRequest();
        capacityAndImagePatch.setCapacity(12);
        capacityAndImagePatch.setFeeDescription("场地费 AA");
        capacityAndImagePatch.setMinAge(16);
        capacityAndImagePatch.setImageIds(List.of(imageA.getMediaId()));
        ActivityDtos.ActivityDraftDetail imageUpdated =
                activityDraftService.updateDraft(organizer.getUserId(), draft.getActivityId(), capacityAndImagePatch);
        assertThat(imageUpdated.getCapacity()).isEqualTo(12);
        assertThat(imageUpdated.getImages())
                .extracting(CommonDtos.MediaFile::getMediaId)
                .containsExactly(imageA.getMediaId());

        ActivityDtos.ActivityDraftUpsertRequest noImagePatch = new ActivityDtos.ActivityDraftUpsertRequest();
        noImagePatch.setTitle("周末羽毛球");
        ActivityDtos.ActivityDraftDetail imagePreserved =
                activityDraftService.updateDraft(organizer.getUserId(), draft.getActivityId(), noImagePatch);
        assertThat(imagePreserved.getImages())
                .extracting(CommonDtos.MediaFile::getMediaId)
                .containsExactly(imageA.getMediaId());
        assertThat(mediaFileRepository
                        .findById(imageA.getMediaId())
                        .orElseThrow()
                        .getDeletedAt())
                .isNull();

        ActivityDtos.ActivityDraftUpsertRequest clearImagePatch = new ActivityDtos.ActivityDraftUpsertRequest();
        clearImagePatch.setImageIds(List.of());
        ActivityDtos.ActivityDraftDetail imageCleared =
                activityDraftService.updateDraft(organizer.getUserId(), draft.getActivityId(), clearImagePatch);
        assertThat(imageCleared.getImages()).isEmpty();
        assertThat(mediaFileRepository
                        .findById(imageA.getMediaId())
                        .orElseThrow()
                        .getDeletedAt())
                .isNotNull();

        ActivityDtos.ActivityDraftUpsertRequest finalPatch = new ActivityDtos.ActivityDraftUpsertRequest();
        finalPatch.setImageIds(List.of(imageB.getMediaId()));
        ActivityDtos.ActivityDraftDetail readyDraft =
                activityDraftService.updateDraft(organizer.getUserId(), draft.getActivityId(), finalPatch);
        assertThat(readyDraft.getImages())
                .extracting(CommonDtos.MediaFile::getMediaId)
                .containsExactly(imageB.getMediaId());

        ActivityDtos.ActivityDetail submitted =
                activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId());
        assertThat(submitted.getReviewStatus()).isEqualTo(ActivityReviewStatus.approved);
        assertThat(submitted.getTitle()).isEqualTo("周末羽毛球");
        assertThat(submitted.getCapacity()).isEqualTo(12);
        assertThat(submitted.getImages())
                .extracting(CommonDtos.MediaFile::getMediaId)
                .containsExactly(imageB.getMediaId());
    }

    @Test
    void saveDraftShouldRejectBindingOthersMedia() {
        User organizer = saveUser("user-a");
        saveUser("user-b");
        MediaFile othersImage = saveMediaFile(IMAGE_A_ID, "user-b");

        assertThatThrownBy(() -> activityDraftService.saveDraft(
                        organizer.getUserId(), createDraftRequest(List.of(othersImage.getMediaId()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在不可用的活动图片");
    }

    @Test
    void updateDraftShouldRejectNonOwner() {
        User organizer = saveUser("user-a");
        saveUser("user-b");
        ActivityDtos.ActivityDraftDetail created =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));

        assertThatThrownBy(() -> activityDraftService.updateDraft(
                        "user-b", created.getActivityId(), createDraftRequest(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问其他用户的活动草稿");
    }

    @Test
    void saveDraftShouldRejectInvalidSchedule() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftUpsertRequest request = createDraftRequest(List.of());
        request.setStartAt("2026-07-02T10:00:00Z");
        request.setEndAt("2026-07-02T09:00:00Z");

        assertThatThrownBy(() -> activityDraftService.saveDraft(organizer.getUserId(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动结束时间必须晚于开始时间");
    }

    @Test
    void listDraftsShouldOnlyReturnCurrentUsersDrafts() {
        User organizer = saveUser("user-a");
        User otherOrganizer = saveUser("user-b");
        activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        activityDraftService.saveDraft(otherOrganizer.getUserId(), createDraftRequest(List.of()));

        assertThat(activityDraftService.listDrafts(organizer.getUserId(), 1, 20).getItems())
                .hasSize(1);
    }

    @Test
    void listTemplatesShouldReturnPersistedTemplates() {
        saveTemplate("template-a", "桌游模板", "社交", List.of("桌游", "轻松"));
        saveTemplate("template-b", "徒步模板", "户外", List.of("徒步"));

        ActivityDtos.ActivityTemplate firstTemplate =
                activityDraftService.listTemplates(1, 1).getItems().getFirst();

        assertThat(firstTemplate.getTemplateId()).isEqualTo("template-a");
        assertThat(firstTemplate.getName()).isEqualTo("桌游模板");
        assertThat(firstTemplate.getActivityType()).isEqualTo("社交");
        assertThat(firstTemplate.getDefaultTags()).containsExactly("桌游", "轻松");
        assertThat(activityDraftService.listTemplates(1, 1).getTotal()).isEqualTo(2);
    }

    @Test
    void createTemplateShouldPersistTemplateAndPublishCoverImage() {
        User adminUploader = saveUser("admin-uploader");
        MediaFile coverImage = saveMediaFile(IMAGE_A_ID, adminUploader.getUserId());

        ActivityDtos.ActivityTemplate created =
                activityTemplateAdminService.createTemplate(createTemplateRequest(coverImage.getMediaId()));

        ActivityTemplate savedTemplate =
                activityTemplateRepository.findById(created.getTemplateId()).orElseThrow();
        assertThat(savedTemplate.getName()).isEqualTo("城市探索模板");
        assertThat(savedTemplate.getActivityType()).isEqualTo("城市探索");
        assertThat(savedTemplate.getDefaultTags()).containsExactly("探索", "拍照");
        assertThat(savedTemplate.getDefaultCoverImageMediaId()).isEqualTo(coverImage.getMediaId());
        assertThat(created.getDefaultCoverImage()).isNotNull();
        assertThat(created.getDefaultCoverImage().getMediaId()).isEqualTo(coverImage.getMediaId());

        MediaFile publishedCover =
                mediaFileRepository.findById(coverImage.getMediaId()).orElseThrow();
        assertThat(publishedCover.getAccessPolicy()).isEqualTo(MediaAccessPolicy.publicAccess);
        assertThat(publishedCover.getVisibility()).isEqualTo(MediaVisibility.publicVisible);
        assertThat(publishedCover.getAccessVersion()).isEqualTo(2L);
    }

    @Test
    void updateTemplateShouldOverwriteFieldsAndClearCoverImage() {
        User adminUploader = saveUser("admin-uploader");
        MediaFile coverImage = saveMediaFile(IMAGE_A_ID, adminUploader.getUserId());
        ActivityTemplate template = activityTemplateRepository.save(ActivityTemplate.builder()
                .templateId("template-a")
                .name("旧模板")
                .activityType("旧类型")
                .defaultTags(List.of("旧标签"))
                .defaultIntroduction("旧介绍")
                .defaultSafetyNotice("旧安全须知")
                .defaultCapacity(8)
                .defaultCoverImageMediaId(coverImage.getMediaId())
                .build());
        ActivityDtos.ActivityTemplateUpsertRequest request = createTemplateRequest(null);
        request.setName("更新后的模板");
        request.setDefaultCapacity(18);

        ActivityDtos.ActivityTemplate updated =
                activityTemplateAdminService.updateTemplate(template.getTemplateId(), request);

        ActivityTemplate savedTemplate =
                activityTemplateRepository.findById(template.getTemplateId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("更新后的模板");
        assertThat(savedTemplate.getName()).isEqualTo("更新后的模板");
        assertThat(savedTemplate.getDefaultCapacity()).isEqualTo(18);
        assertThat(savedTemplate.getDefaultCoverImageMediaId()).isNull();
        assertThat(updated.getDefaultCoverImage()).isNull();
    }

    @Test
    void deleteTemplateShouldRemoveTemplate() {
        saveTemplate("template-a", "桌游模板", "社交", List.of("桌游", "轻松"));

        activityTemplateAdminService.deleteTemplate("template-a");

        assertThat(activityTemplateRepository.findById("template-a")).isEmpty();
    }

    @Test
    void updateTemplateShouldRejectMissingTemplate() {
        assertThatThrownBy(() -> activityTemplateAdminService.updateTemplate("missing", createTemplateRequest(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(20001);
    }

    @Test
    void createTemplateShouldRejectUnavailableCoverImage() {
        User adminUploader = saveUser("admin-uploader");
        MediaFile avatar = mediaFileRepository.save(MediaFile.builder()
                .mediaId(IMAGE_A_ID)
                .fileName("avatar.png")
                .contentType("image/png")
                .sizeBytes(1L)
                .usage(MediaUsage.avatar)
                .storagePath("/tmp/avatar.png")
                .uploadedBy(adminUploader.getUserId())
                .uploadedAt(Instant.now())
                .build());

        assertThatThrownBy(
                        () -> activityTemplateAdminService.createTemplate(createTemplateRequest(avatar.getMediaId())))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(20017);
    }

    @Test
    void createDraftFromTemplateShouldPersistEditableDraft() {
        User organizer = saveUser("user-a");
        saveTemplate("template-a", "桌游模板", "社交", List.of("桌游", "轻松"));

        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.createDraftFromTemplate(organizer.getUserId(), "template-a");

        Activity savedActivity =
                activityRepository.findById(draft.getActivityId()).orElseThrow();
        assertThat(savedActivity.getOrganizerId()).isEqualTo(organizer.getUserId());
        assertThat(savedActivity.getReviewStatus()).isEqualTo(ActivityReviewStatus.draft);
        assertThat(draft.getTitle()).isEqualTo("桌游模板");
        assertThat(draft.getTags()).containsExactly("桌游", "轻松");
        assertThat(draft.getIntroduction()).isEqualTo("模板介绍");
        assertThat(draft.getSafetyNotice()).isEqualTo("模板安全须知");
        assertThat(draft.getCapacity()).isEqualTo(12);
        assertThat(draft.getImages()).isEmpty();
    }

    @Test
    void createDraftFromTemplateShouldRejectMissingTemplate() {
        User organizer = saveUser("user-a");

        assertThatThrownBy(() -> activityDraftService.createDraftFromTemplate(organizer.getUserId(), "missing"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(20001);
    }

    @Test
    void cloneActivityShouldCopyActivityFieldsAndImagesAsDraft() {
        User organizer = saveUser("user-a");
        MediaFile sourceImage = saveMediaFile(IMAGE_A_ID, organizer.getUserId());
        Activity source = saveSubmittedActivity(organizer.getUserId(), "原活动");
        saveActivityImage(source.getActivityId(), sourceImage.getMediaId(), 0);
        when(fileStorageService.retrieve(sourceImage.getStoragePath()))
                .thenReturn(new ByteArrayInputStream(new byte[] {1}));
        when(fileStorageService.store(anyString(), any(), anyString(), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ActivityDtos.ActivityDraftDetail cloned =
                activityDraftService.cloneActivity(organizer.getUserId(), source.getActivityId());

        Activity savedClone =
                activityRepository.findById(cloned.getActivityId()).orElseThrow();
        assertThat(savedClone.getActivityId()).isNotEqualTo(source.getActivityId());
        assertThat(savedClone.getReviewStatus()).isEqualTo(ActivityReviewStatus.draft);
        assertThat(savedClone.getRuntimeStatus()).isEqualTo(ActivityRuntimeStatus.notStarted);
        assertThat(savedClone.getTitle()).isEqualTo(source.getTitle());
        assertThat(savedClone.getTags()).containsExactlyElementsOf(source.getTags());
        assertThat(savedClone.getRequireLocationCheck()).isEqualTo(source.getRequireLocationCheck());
        assertThat(cloned.getImages()).hasSize(1);
        assertThat(cloned.getImages().getFirst().getMediaId()).isNotEqualTo(sourceImage.getMediaId());

        MediaFile clonedImage = mediaFileRepository
                .findById(cloned.getImages().getFirst().getMediaId())
                .orElseThrow();
        assertThat(clonedImage.getUploadedBy()).isEqualTo(organizer.getUserId());
        assertThat(clonedImage.getAccessPolicy()).isEqualTo(MediaAccessPolicy.activityOwner);
        assertThat(clonedImage.getAccessScopeId()).isEqualTo(savedClone.getActivityId());
        assertThat(activityImageRepository.findByActivityIdOrderBySortOrderAsc(source.getActivityId()))
                .hasSize(1);
    }

    @Test
    void cloneActivityShouldRejectNonOrganizer() {
        saveUser("user-a");
        saveUser("user-b");
        Activity source = saveSubmittedActivity("user-a", "原活动");

        assertThatThrownBy(() -> activityDraftService.cloneActivity("user-b", source.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(20003);
    }

    @Test
    void submitActivityShouldAutoApproveLowRiskActivity() {
        User organizer = saveUser("user-a");
        MediaFile image = saveMediaFile(IMAGE_A_ID, organizer.getUserId());
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of(image.getMediaId())));

        ActivityDtos.ActivityDetail detail =
                activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId());

        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.approved);
        assertThat(detail.getAiContentReview()).isNotNull();
        assertThat(detail.getAiContentReview().getRiskLevel()).isEqualTo("low");
        assertThat(detail.getOrganizerName()).isEqualTo(organizer.getNickname());
        assertThat(detail.getReviewRecords()).hasSize(1);
        assertThat(detail.getReviewRecords().getFirst().getResult())
                .isEqualTo(io.github.layjason.mayoistar.entity.common.ReviewStatus.approved);

        Activity savedActivity =
                activityRepository.findById(draft.getActivityId()).orElseThrow();
        assertThat(savedActivity.getReviewStatus()).isEqualTo(ActivityReviewStatus.approved);
        assertThat(savedActivity.getManualReviewRequired()).isFalse();
        assertThat(savedActivity.getAiContentReviewJson()).contains("\"riskLevel\":\"low\"");

        MediaFile publishedImage =
                mediaFileRepository.findById(image.getMediaId()).orElseThrow();
        assertThat(publishedImage.getAccessPolicy()).isEqualTo(MediaAccessPolicy.publicAccess);
        assertThat(publishedImage.getVisibility()).isEqualTo(MediaVisibility.publicVisible);
        assertThat(publishedImage.getAccessScopeId()).isEqualTo("");
        assertThat(detail.getImages()).hasSize(1);
        assertThat(detail.getImages().getFirst().getVisibility()).isEqualTo(MediaVisibility.publicVisible);
    }

    @Test
    void submitActivityShouldTriggerManualReviewForLargeCapacity() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftUpsertRequest request = createDraftRequest(List.of());
        request.setCapacity(60);
        ActivityDtos.ActivityDraftDetail draft = activityDraftService.saveDraft(organizer.getUserId(), request);

        ActivityDtos.ActivityDetail detail =
                activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId());

        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.pending);
        assertThat(detail.getManualReviewRequired()).isTrue();
        Activity savedActivity =
                activityRepository.findById(draft.getActivityId()).orElseThrow();
        assertThat(savedActivity.getManualReviewRequired()).isTrue();
    }

    @Test
    void submitActivityShouldTriggerManualReviewForTextRisk() {
        contentReviewClient.setTextResult(
                new ContentReviewScanResult(ContentReviewRisk.review, List.of("文本命中广告引流风险"), null));
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));

        ActivityDtos.ActivityDetail detail =
                activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId());

        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.pending);
        assertThat(detail.getManualReviewRequired()).isTrue();
        assertThat(detail.getAiContentReview().getRiskLevel()).isEqualTo("uncertain");
        assertThat(detail.getReviewRecords().getFirst().getReason()).contains("文本命中广告引流风险");
    }

    @Test
    void submitActivityShouldTriggerManualReviewForImageRisk() {
        contentReviewClient.setImageResult(
                new ContentReviewScanResult(ContentReviewRisk.block, List.of("图片命中违规标识"), null));
        User organizer = saveUser("user-a");
        MediaFile image = saveMediaFile(IMAGE_A_ID, organizer.getUserId());
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of(image.getMediaId())));

        ActivityDtos.ActivityDetail detail =
                activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId());

        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.pending);
        assertThat(detail.getManualReviewRequired()).isTrue();
        assertThat(detail.getAiContentReview().getRiskLevel()).isEqualTo("high");
        assertThat(detail.getAiContentReview().getSuggestedReviewStatus())
                .isEqualTo(io.github.layjason.mayoistar.entity.common.ReviewStatus.rejected);
    }

    @Test
    void submitActivityShouldTriggerManualReviewWhenAiReviewFails() {
        contentReviewClient.setTextResult(ContentReviewScanResult.failed("阿里云文本内容审核失败"));
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));

        ActivityDtos.ActivityDetail detail =
                activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId());

        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.pending);
        assertThat(detail.getManualReviewRequired()).isTrue();
        assertThat(detail.getAiContentReview().getStatus()).isEqualTo("failed");
        assertThat(detail.getAiContentReview().getFriendlyErrorMessage()).contains("阿里云文本内容审核失败");
    }

    @Test
    void submitActivityShouldRejectNonOwner() {
        User organizer = saveUser("user-a");
        saveUser("user-b");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));

        assertThatThrownBy(() -> activityDraftService.submitActivity("user-b", draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权操作其他用户的活动");
    }

    @Test
    void submitActivityShouldRejectWhenNotFound() {
        saveUser("user-a");

        assertThatThrownBy(() -> activityDraftService.submitActivity("user-a", "non-existent-id"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not visible");
    }

    @Test
    void submitActivityShouldRejectWhenAlreadyPending() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId());

        assertThatThrownBy(() -> activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已审核通过");
    }

    @Test
    void submitActivityShouldRejectWhenRejected() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setReviewStatus(ActivityReviewStatus.rejected);
        activityRepository.save(activity);

        assertThatThrownBy(() -> activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被驳回");
    }

    @Test
    void submitActivityShouldRejectWhenMissingTitle() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        // 将标题设为空白，模拟校验边界情况（DB 不允许 NULL，但允许空白字符串）
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setTitle("  ");
        activityRepository.save(activity);

        assertThatThrownBy(() -> activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动名称不能为空");
    }

    @Test
    void submitActivityShouldRejectWhenMissingTags() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setTags(null);
        activityRepository.save(activity);

        assertThatThrownBy(() -> activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动标签不能为空");
    }

    @Test
    void submitActivityShouldRejectWhenMissingLocation() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setPointLon(null);
        activity.setPointLat(null);
        activity.setCity(null);
        activity.setAddress(null);
        activityRepository.save(activity);

        assertThatThrownBy(() -> activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动地点信息不完整");
    }

    @Test
    void submitActivityShouldRejectWhenInvalidSchedule() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        // 直接修改实体将结束时间设为早于开始时间，模拟边界情况
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setEndAt(Instant.parse("2026-07-01T10:00:00Z")); // 早于 startAt 2026-07-02T10:00:00Z
        activityRepository.save(activity);

        assertThatThrownBy(() -> activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活动结束时间必须晚于开始时间");
    }

    @Test
    void submitActivityShouldAllowResubmitFromChangeRequired() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setReviewStatus(ActivityReviewStatus.changeRequired);
        activityRepository.save(activity);

        ActivityDtos.ActivityDetail detail =
                activityDraftService.submitActivity(organizer.getUserId(), draft.getActivityId());

        assertThat(detail.getReviewStatus()).isEqualTo(ActivityReviewStatus.approved);
    }

    @Test
    void getDraftShouldAllowChangeRequiredStatus() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setReviewStatus(ActivityReviewStatus.changeRequired);
        activityRepository.save(activity);

        ActivityDtos.ActivityDraftDetail result =
                activityDraftService.getDraft(organizer.getUserId(), draft.getActivityId());

        assertThat(result.getActivityId()).isEqualTo(draft.getActivityId());
        assertThat(result.getReviewStatus()).isEqualTo(ActivityReviewStatus.changeRequired);
    }

    @Test
    void updateDraftShouldAllowChangeRequiredStatus() {
        User organizer = saveUser("user-a");
        ActivityDtos.ActivityDraftDetail draft =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity = activityRepository.findById(draft.getActivityId()).orElseThrow();
        activity.setReviewStatus(ActivityReviewStatus.changeRequired);
        activityRepository.save(activity);

        ActivityDtos.ActivityDraftUpsertRequest request = createDraftRequest(List.of());
        request.setTitle("修改后标题");
        ActivityDtos.ActivityDraftDetail result =
                activityDraftService.updateDraft(organizer.getUserId(), draft.getActivityId(), request);

        assertThat(result.getTitle()).isEqualTo("修改后标题");
    }

    @Test
    void listDraftsShouldIncludeChangeRequiredActivities() {
        User organizer = saveUser("user-a");
        // 创建一条 draft 状态的草稿
        activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        // 创建第二条草稿并设为 changeRequired
        ActivityDtos.ActivityDraftDetail draft2 =
                activityDraftService.saveDraft(organizer.getUserId(), createDraftRequest(List.of()));
        Activity activity2 = activityRepository.findById(draft2.getActivityId()).orElseThrow();
        activity2.setReviewStatus(ActivityReviewStatus.changeRequired);
        activityRepository.save(activity2);

        assertThat(activityDraftService.listDrafts(organizer.getUserId(), 1, 20).getItems())
                .hasSize(2);
    }

    private User saveUser(String userId) {
        return userRepository.save(User.builder()
                .userId(userId)
                .email(userId + "@example.com")
                .nickname("nickname-" + userId)
                .passwordHash("hashed-password")
                .kind(UserKind.personal)
                .accountStatus(AccountStatus.active)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private MediaFile saveMediaFile(UUID mediaId, String userId) {
        return mediaFileRepository.save(MediaFile.builder()
                .mediaId(mediaId)
                .fileName("cover.png")
                .contentType("image/png")
                .sizeBytes(1L)
                .usage(MediaUsage.activityImage)
                .storagePath("/tmp/" + mediaId)
                .uploadedBy(userId)
                .uploadedAt(Instant.now())
                .build());
    }

    private Activity saveSubmittedActivity(String organizerId, String title) {
        return activityRepository.save(Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(organizerId)
                .title(title)
                .tags(List.of("社交", "桌游"))
                .introduction("原活动简介")
                .startAt(Instant.parse("2026-07-02T10:00:00Z"))
                .endAt(Instant.parse("2026-07-02T12:00:00Z"))
                .pointLon(116.397)
                .pointLat(39.907)
                .city("北京")
                .address("海淀区某街道")
                .placeName("活动中心")
                .safetyNotice("原活动安全须知")
                .capacity(8)
                .feeDescription("AA")
                .minAge(18)
                .registrationDeadline(Instant.parse("2026-07-01T12:00:00Z"))
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.ended)
                .requireLocationCheck(true)
                .manualReviewRequired(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private ActivityImage saveActivityImage(String activityId, UUID mediaId, int sortOrder) {
        return activityImageRepository.save(ActivityImage.builder()
                .imageId(UUID.randomUUID().toString())
                .activityId(activityId)
                .mediaId(mediaId)
                .sortOrder(sortOrder)
                .build());
    }

    private ActivityTemplate saveTemplate(String templateId, String name, String activityType, List<String> tags) {
        return activityTemplateRepository.save(ActivityTemplate.builder()
                .templateId(templateId)
                .name(name)
                .activityType(activityType)
                .defaultTags(tags)
                .defaultIntroduction("模板介绍")
                .defaultSafetyNotice("模板安全须知")
                .defaultCapacity(12)
                .build());
    }

    private ActivityDtos.ActivityTemplateUpsertRequest createTemplateRequest(UUID defaultCoverImageMediaId) {
        ActivityDtos.ActivityTemplateUpsertRequest request = new ActivityDtos.ActivityTemplateUpsertRequest();
        request.setName("城市探索模板");
        request.setActivityType("城市探索");
        request.setDefaultTags(List.of("探索", "拍照"));
        request.setDefaultIntroduction("沿着城市线索完成探索任务");
        request.setDefaultSafetyNotice("注意交通安全，结伴行动");
        request.setDefaultCapacity(20);
        request.setDefaultCoverImageMediaId(defaultCoverImageMediaId);
        return request;
    }

    private ActivityDtos.ActivityDraftUpsertRequest createDraftRequest(List<UUID> imageIds) {
        ActivityDtos.ActivityDraftUpsertRequest request = new ActivityDtos.ActivityDraftUpsertRequest();
        request.setTitle("桌游局");
        request.setTags(List.of("社交", "桌游"));
        request.setIntroduction("一起玩桌游");
        request.setStartAt("2026-07-02T10:00:00Z");
        request.setEndAt("2026-07-02T12:00:00Z");
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setLongitude(116.397);
        point.setLatitude(39.907);
        CommonDtos.LocationInfo location = new CommonDtos.LocationInfo();
        location.setPoint(point);
        location.setCity("北京");
        location.setAddress("海淀区某街道");
        location.setPlaceName("活动中心");
        request.setLocation(location);
        request.setSafetyNotice("注意人身安全");
        request.setCapacity(8);
        request.setRegistrationDeadline("2026-07-01T12:00:00Z");
        request.setFeeDescription("AA");
        request.setMinAge(18);
        request.setImageIds(imageIds);
        return request;
    }

    private CommonDtos.LocationInfo createLocation(String city, String address, String placeName) {
        CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
        point.setLongitude(116.397);
        point.setLatitude(39.907);
        CommonDtos.LocationInfo location = new CommonDtos.LocationInfo();
        location.setPoint(point);
        location.setCity(city);
        location.setAddress(address);
        location.setPlaceName(placeName);
        return location;
    }
}
