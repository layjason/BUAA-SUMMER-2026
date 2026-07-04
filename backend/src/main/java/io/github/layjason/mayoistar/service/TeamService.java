package io.github.layjason.mayoistar.service;

import static io.github.layjason.mayoistar.exception.ErrorCodes.BLACKLIST_RELATION_EXISTS;
import static io.github.layjason.mayoistar.exception.ErrorCodes.DUPLICATE_TEAM_JOIN_REQUEST;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_ACTIVITY_NOT_VISIBLE;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_FILE_DUPLICATE;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_FULL;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_JOIN_REQUEST_STATE_INVALID;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_LEADER_CANNOT_LEAVE;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_MEDIA_NOT_FOUND;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_MEMBER_ALREADY_EXISTS;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_MEMBER_NOT_FOUND;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_NAME_UNAVAILABLE;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_NOT_VISIBLE;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_PERMISSION_DENIED;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_ROLE_CHANGE_INVALID;
import static io.github.layjason.mayoistar.exception.ErrorCodes.TEAM_UNAVAILABLE;

import io.github.layjason.mayoistar.api.activities.ActivityDtos;
import io.github.layjason.mayoistar.api.common.CommonDtos;
import io.github.layjason.mayoistar.api.common.PageResult;
import io.github.layjason.mayoistar.api.social.SocialDtos;
import io.github.layjason.mayoistar.entity.activities.Activity;
import io.github.layjason.mayoistar.entity.activities.ActivityImage;
import io.github.layjason.mayoistar.entity.activities.ActivityReviewStatus;
import io.github.layjason.mayoistar.entity.activities.ActivityRuntimeStatus;
import io.github.layjason.mayoistar.entity.chat.Conversation;
import io.github.layjason.mayoistar.entity.chat.ConversationKind;
import io.github.layjason.mayoistar.entity.chat.ConversationMember;
import io.github.layjason.mayoistar.entity.common.MediaAccessPolicy;
import io.github.layjason.mayoistar.entity.common.MediaFile;
import io.github.layjason.mayoistar.entity.common.MediaUsage;
import io.github.layjason.mayoistar.entity.identity.User;
import io.github.layjason.mayoistar.entity.social.Team;
import io.github.layjason.mayoistar.entity.social.TeamJoinMode;
import io.github.layjason.mayoistar.entity.social.TeamJoinRequest;
import io.github.layjason.mayoistar.entity.social.TeamJoinRequestStatus;
import io.github.layjason.mayoistar.entity.social.TeamMediaFile;
import io.github.layjason.mayoistar.entity.social.TeamMember;
import io.github.layjason.mayoistar.entity.social.TeamMemberRole;
import io.github.layjason.mayoistar.entity.social.TeamStatus;
import io.github.layjason.mayoistar.exception.BusinessException;
import io.github.layjason.mayoistar.repository.ActivityRepository;
import io.github.layjason.mayoistar.repository.BlacklistRepository;
import io.github.layjason.mayoistar.repository.ConversationMemberRepository;
import io.github.layjason.mayoistar.repository.ConversationRepository;
import io.github.layjason.mayoistar.repository.MediaFileRepository;
import io.github.layjason.mayoistar.repository.TeamJoinRequestRepository;
import io.github.layjason.mayoistar.repository.TeamMediaFileRepository;
import io.github.layjason.mayoistar.repository.TeamMemberRepository;
import io.github.layjason.mayoistar.repository.TeamPointRecordRepository;
import io.github.layjason.mayoistar.repository.TeamRepository;
import io.github.layjason.mayoistar.repository.UserRepository;
import io.github.layjason.mayoistar.repository.activities.ActivityImageRepository;
import io.github.layjason.mayoistar.service.media.MediaAccessService;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 小队服务，管理小队的创建、搜索、加入/退出、成员管理和积分榜。
 *
 * <p>类职责：封装兴趣小队的所有业务逻辑，包括生命周期管理、权限校验和群聊同步。
 *
 * <p>类不变量：小队解散后仅更新状态，不删除数据。队长始终有且只有一个，队长转让时必须指定新队长。
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamJoinRequestRepository teamJoinRequestRepository;
    private final TeamPointRecordRepository teamPointRecordRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final BlacklistRepository blacklistRepository;
    private final UserRepository userRepository;
    private final MediaFileRepository mediaFileRepository;
    private final TeamMediaFileRepository teamMediaFileRepository;
    private final MediaAccessService mediaAccessService;
    private final ActivityRepository activityRepository;
    private final ActivityImageRepository activityImageRepository;

    // ========================================
    // 小队创建
    // ========================================

    /**
     * 创建兴趣小队并自动生成群聊，创建者自动成为队长。
     *
     * <p>前置条件：{@code request.name} 在全平台唯一，{@code request.capacity} 为正数。
     *
     * <p>后置条件：创建 Team、Conversation，创建者以 leader 身份加入。
     *
     * @param creatorId 创建者用户 ID
     * @param request   创建请求
     * @return 小队资料
     * @throws BusinessException 名称已占用时抛出
     */
    public SocialDtos.TeamProfile createTeam(String creatorId, SocialDtos.TeamCreateRequest request) {
        if (teamRepository.existsByName(request.getName())) {
            log.warn("小队名称已被占用: name={}", request.getName());
            throw new BusinessException(TEAM_NAME_UNAVAILABLE, "Team name is already taken");
        }

        if (request.getCapacity() == null || request.getCapacity() <= 0) {
            throw new BusinessException(TEAM_NAME_UNAVAILABLE, "Team capacity must be positive");
        }

        Instant now = Instant.now();
        String teamId = UUID.randomUUID().toString();
        String conversationId = UUID.randomUUID().toString();

        Conversation conversation = Conversation.builder()
                .conversationId(conversationId)
                .kind(ConversationKind.team)
                .title(request.getName())
                .avatarMediaId(request.getAvatarMediaId())
                .createdAt(now)
                .updatedAt(now)
                .build();
        conversationRepository.save(conversation);

        Team team = Team.builder()
                .teamId(teamId)
                .name(request.getName())
                .tags(request.getTags())
                .joinMode(request.getJoinMode())
                .capacity(request.getCapacity())
                .description(request.getDescription())
                .avatarMediaId(request.getAvatarMediaId())
                .status(TeamStatus.active)
                .creatorId(creatorId)
                .leaderId(creatorId)
                .chatId(conversationId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        teamRepository.save(team);

        TeamMember leader = TeamMember.builder()
                .memberId(UUID.randomUUID().toString())
                .teamId(teamId)
                .userId(creatorId)
                .role(TeamMemberRole.leader)
                .points(0)
                .joinedAt(now)
                .build();
        teamMemberRepository.save(leader);

        ConversationMember convMember = ConversationMember.builder()
                .memberId(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .userId(creatorId)
                .joinedAt(now)
                .build();
        conversationMemberRepository.save(convMember);

        log.info("小队创建成功: teamId={}, name={}, creatorId={}", teamId, request.getName(), creatorId);
        return toTeamProfile(team, 1);
    }

    // ========================================
    // 小队搜索与详情
    // ========================================

    /**
     * 按关键词和标签搜索小队，已解散小队不出现在结果中。
     *
     * <p>前置条件：无。
     *
     * <p>后置条件：返回按创建时间倒序的分页结果。
     *
     * @param keyword  名称关键词
     * @param tags     兴趣标签列表
     * @param page     页码
     * @param pageSize 每页数量
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<SocialDtos.TeamProfile> searchTeams(
            @Nullable String keyword, @Nullable List<String> tags, int page, int pageSize) {
        var dbPage = teamRepository.findAll(
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(cb.notEqual(root.get("status"), TeamStatus.dissolved));
                    predicates.add(cb.notEqual(root.get("status"), TeamStatus.disabled));

                    if (keyword != null && !keyword.isBlank()) {
                        predicates.add(cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%"));
                    }

                    return cb.and(predicates.toArray(new Predicate[0]));
                },
                PageRequest.of(page - 1, pageSize));

        List<Team> filteredTeams = hasTags(tags) ? filterByTags(dbPage.getContent(), tags) : dbPage.getContent();

        var items = filteredTeams.stream()
                .map(team -> toTeamProfile(team, (int) teamMemberRepository.countByTeamId(team.getTeamId())))
                .collect(Collectors.toList());

        long total = hasTags(tags) ? filterByTagsCount(keyword, tags) : dbPage.getTotalElements();
        int totalPages = (int) Math.ceil((double) total / pageSize);

        return new PageResult<>(items, total, dbPage.getNumber() + 1, dbPage.getSize(), totalPages);
    }

    private boolean hasTags(@Nullable List<String> tags) {
        return tags != null && !tags.isEmpty();
    }

    private List<Team> filterByTags(List<Team> teams, List<String> tags) {
        return teams.stream()
                .filter(team -> {
                    List<String> teamTags = team.getTags();
                    return teamTags != null && tags.stream().anyMatch(teamTags::contains);
                })
                .collect(Collectors.toList());
    }

    private long filterByTagsCount(@Nullable String keyword, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return teamRepository.count((root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();
                predicates.add(cb.notEqual(root.get("status"), TeamStatus.dissolved));
                predicates.add(cb.notEqual(root.get("status"), TeamStatus.disabled));

                if (keyword != null && !keyword.isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%"));
                }

                return cb.and(predicates.toArray(new Predicate[0]));
            });
        }

        List<Team> allMatching = teamRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.notEqual(root.get("status"), TeamStatus.dissolved));
            predicates.add(cb.notEqual(root.get("status"), TeamStatus.disabled));

            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        });

        return filterByTags(allMatching, tags).size();
    }

    /**
     * 获取小队详情。
     *
     * <p>前置条件：{@code teamId} 对应的小队未被解散。
     *
     * <p>后置条件：返回含成员数的小队资料。
     *
     * @param teamId 小队 ID
     * @return 小队资料
     * @throws BusinessException 小队不存在或已解散
     */
    @Transactional(readOnly = true)
    public SocialDtos.TeamProfile getTeam(String teamId) {
        Team team = findVisibleTeam(teamId);
        int memberCount = (int) teamMemberRepository.countByTeamId(teamId);
        return toTeamProfile(team, memberCount);
    }

    // ========================================
    // 加入与退出
    // ========================================

    /**
     * 加入或申请加入小队。
     *
     * <p>前置条件：小队未满员、未解散、无黑名单关系，用户尚未是成员。
     *
     * <p>后置条件：公开小队直接加入；审核小队创建待处理申请。
     *
     * @param teamId  小队 ID
     * @param userId  用户 ID
     * @param request 加入请求（含附言）
     * @return 入队申请（公开小队状态为 accepted）
     * @throws BusinessException 小队不可见、已满、黑名单关系或重复加入
     */
    public SocialDtos.TeamJoinRequest joinTeam(String teamId, String userId, SocialDtos.JoinTeamRequest request) {
        Team team = findVisibleTeam(teamId);

        if (team.getStatus() != TeamStatus.active) {
            throw new BusinessException(TEAM_UNAVAILABLE, "Team is not available for joining");
        }

        long memberCount = teamMemberRepository.countByTeamId(teamId);
        if (memberCount >= team.getCapacity()) {
            log.warn("小队已满: teamId={}, capacity={}", teamId, team.getCapacity());
            throw new BusinessException(TEAM_FULL, "Team is full");
        }

        if (blacklistRepository.existsByBlockerIdAndBlockedUserId(userId, team.getCreatorId())
                || blacklistRepository.existsByBlockerIdAndBlockedUserId(team.getCreatorId(), userId)) {
            throw new BusinessException(BLACKLIST_RELATION_EXISTS, "Blacklist relationship exists");
        }

        if (teamMemberRepository.findByTeamIdAndUserId(teamId, userId).isPresent()) {
            throw new BusinessException(TEAM_MEMBER_ALREADY_EXISTS, "User is already a team member");
        }

        Instant now = Instant.now();

        if (team.getJoinMode() == TeamJoinMode.publicJoin) {
            addMemberToTeam(team, userId, TeamMemberRole.member, now);
            SocialDtos.TeamJoinRequest result = new SocialDtos.TeamJoinRequest();
            result.setRequestId(UUID.randomUUID().toString());
            result.setTeamId(teamId);
            result.setUserId(userId);
            result.setMessage(request.getMessage());
            result.setStatus(TeamJoinRequestStatus.accepted);
            result.setCreatedAt(now.toString());
            log.info("用户直接加入公开小队: teamId={}, userId={}", teamId, userId);
            return result;
        }

        if (teamJoinRequestRepository
                .findByTeamIdAndUserIdAndStatus(teamId, userId, TeamJoinRequestStatus.pending)
                .isPresent()) {
            throw new BusinessException(DUPLICATE_TEAM_JOIN_REQUEST, "A pending join request already exists");
        }

        TeamJoinRequest joinRequest = TeamJoinRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .teamId(teamId)
                .userId(userId)
                .message(request.getMessage())
                .status(TeamJoinRequestStatus.pending)
                .createdAt(now)
                .build();
        teamJoinRequestRepository.save(joinRequest);

        log.info("入队申请已创建: requestId={}, teamId={}, userId={}", joinRequest.getRequestId(), teamId, userId);
        return toTeamJoinRequestDto(joinRequest);
    }

    /**
     * 退出小队。
     *
     * <p>前置条件：{@code userId} 是 {@code teamId} 小队的成员且不是队长。
     *
     * <p>后置条件：删除成员记录和群聊成员记录。
     *
     * @param teamId 小队 ID
     * @param userId 用户 ID
     * @throws BusinessException 小队不可见、非成员或为队长
     */
    public void leaveTeam(String teamId, String userId) {
        Team team = findVisibleTeam(teamId);

        TeamMember member = teamMemberRepository
                .findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> {
                    log.warn("用户不是小队成员: teamId={}, userId={}", teamId, userId);
                    return new BusinessException(TEAM_MEMBER_NOT_FOUND, "User is not a team member");
                });

        if (member.getRole() == TeamMemberRole.leader) {
            log.warn("队长尝试退出小队: teamId={}, userId={}", teamId, userId);
            throw new BusinessException(TEAM_LEADER_CANNOT_LEAVE, "Team leader cannot leave the team");
        }

        teamMemberRepository.delete(member);

        if (team.getChatId() != null) {
            conversationMemberRepository.deleteByConversationIdAndUserId(team.getChatId(), userId);
        }

        log.info("用户退出小队: teamId={}, userId={}", teamId, userId);
    }

    // ========================================
    // 成员管理
    // ========================================

    /**
     * 分页获取小队成员列表。
     *
     * <p>前置条件：小队存在且可见。
     *
     * <p>后置条件：返回含昵称、角色、积分的分页列表。
     *
     * @param teamId   小队 ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 分页结果
     * @throws BusinessException 小队不可见
     */
    @Transactional(readOnly = true)
    public PageResult<SocialDtos.TeamMember> listTeamMembers(String teamId, int page, int pageSize) {
        findVisibleTeam(teamId);

        var memberPage = teamMemberRepository.findByTeamId(teamId, PageRequest.of(page - 1, pageSize));

        List<SocialDtos.TeamMember> items =
                memberPage.getContent().stream().map(this::toTeamMemberDto).collect(Collectors.toList());

        return new PageResult<>(
                items,
                memberPage.getTotalElements(),
                memberPage.getNumber() + 1,
                memberPage.getSize(),
                memberPage.getTotalPages());
    }

    /**
     * 分页获取入队申请列表，仅队长和管理员可查看。
     *
     * <p>前置条件：{@code viewerId} 是队长或管理员。
     *
     * <p>后置条件：返回按状态筛选的分页结果。
     *
     * @param teamId   小队 ID
     * @param viewerId 查看者 ID
     * @param status   状态筛选条件
     * @param page     页码
     * @param pageSize 每页数量
     * @return 分页结果
     * @throws BusinessException 权限不足
     */
    @Transactional(readOnly = true)
    public PageResult<SocialDtos.TeamJoinRequest> listTeamJoinRequests(
            String teamId, String viewerId, @Nullable TeamJoinRequestStatus status, int page, int pageSize) {
        requireTeamAdmin(teamId, viewerId);

        var requestPage = status != null
                ? teamJoinRequestRepository.findByTeamIdAndStatus(teamId, status, PageRequest.of(page - 1, pageSize))
                : teamJoinRequestRepository.findByTeamId(teamId, PageRequest.of(page - 1, pageSize));

        List<SocialDtos.TeamJoinRequest> items = requestPage.getContent().stream()
                .map(this::toTeamJoinRequestDto)
                .collect(Collectors.toList());

        return new PageResult<>(
                items,
                requestPage.getTotalElements(),
                requestPage.getNumber() + 1,
                requestPage.getSize(),
                requestPage.getTotalPages());
    }

    /**
     * 审核入队申请，同意时将申请人加入小队和群聊。
     *
     * <p>前置条件：{@code reviewerId} 是队长或管理员，申请状态为 pending，小队未满员。
     *
     * <p>后置条件：申请状态已更新，同意时成员加入小队和群聊。
     *
     * @param teamId     小队 ID
     * @param requestId  申请 ID
     * @param reviewerId 审核人 ID
     * @param accepted   是否同意
     * @return 更新后的申请
     * @throws BusinessException 权限不足、申请状态无效或小队已满
     */
    public SocialDtos.TeamJoinRequest decideTeamJoinRequest(
            String teamId, String requestId, String reviewerId, Boolean accepted) {
        requireTeamAdmin(teamId, reviewerId);

        TeamJoinRequest joinRequest = teamJoinRequestRepository
                .findById(requestId)
                .orElseThrow(() -> {
                    log.warn("入队申请不存在: requestId={}", requestId);
                    return new BusinessException(TEAM_JOIN_REQUEST_STATE_INVALID, "Join request not found");
                });

        if (!joinRequest.getTeamId().equals(teamId)) {
            throw new BusinessException(TEAM_JOIN_REQUEST_STATE_INVALID, "Join request does not belong to this team");
        }

        if (joinRequest.getStatus() != TeamJoinRequestStatus.pending) {
            throw new BusinessException(TEAM_JOIN_REQUEST_STATE_INVALID, "Join request is not pending");
        }

        Instant now = Instant.now();

        if (Boolean.TRUE.equals(accepted)) {
            long memberCount = teamMemberRepository.countByTeamId(teamId);
            Team team = teamRepository
                    .findByIdWithLock(teamId)
                    .filter(t -> t.getStatus() != TeamStatus.dissolved)
                    .orElseThrow(() -> {
                        log.warn("小队不可见: teamId={}", teamId);
                        return new BusinessException(TEAM_NOT_VISIBLE, "Team is not visible");
                    });
            if (memberCount >= team.getCapacity()) {
                throw new BusinessException(TEAM_FULL, "Team is full, cannot accept more members");
            }

            joinRequest.setStatus(TeamJoinRequestStatus.accepted);
            addMemberToTeam(team, joinRequest.getUserId(), TeamMemberRole.member, now);
            log.info("入队申请已通过: requestId={}, userId={}", requestId, joinRequest.getUserId());
        } else {
            joinRequest.setStatus(TeamJoinRequestStatus.rejected);
            log.info("入队申请已拒绝: requestId={}, userId={}", requestId, joinRequest.getUserId());
        }

        teamJoinRequestRepository.save(joinRequest);
        return toTeamJoinRequestDto(joinRequest);
    }

    /**
     * 调整小队成员角色，队长转让必须保证小队始终有且只有一个队长。
     *
     * <p>前置条件：{@code operatorId} 是队长，{@code memberId} 是小队成员。
     *
     * <p>后置条件：成员角色已更新。若转让队长，原队长变为普通成员。
     *
     * @param teamId     小队 ID
     * @param memberId   成员用户 ID
     * @param operatorId 操作人 ID（必须是队长）
     * @param newRole    新角色
     * @return 更新后的成员信息
     * @throws BusinessException 权限不足、成员不存在或无效操作
     */
    public SocialDtos.TeamMember updateTeamMemberRole(
            String teamId, String memberId, String operatorId, TeamMemberRole newRole) {
        TeamMember operator = teamMemberRepository
                .findByTeamIdAndUserId(teamId, operatorId)
                .orElseThrow(() -> {
                    log.warn("操作人不是小队成员: teamId={}, userId={}", teamId, operatorId);
                    return new BusinessException(TEAM_MEMBER_NOT_FOUND, "Operator is not a team member");
                });

        if (operator.getRole() != TeamMemberRole.leader) {
            log.warn("非队长尝试修改成员角色: teamId={}, operatorId={}", teamId, operatorId);
            throw new BusinessException(TEAM_PERMISSION_DENIED, "Only the team leader can modify member roles");
        }

        TeamMember target = teamMemberRepository
                .findByTeamIdAndUserId(teamId, memberId)
                .orElseThrow(() -> {
                    log.warn("目标成员不存在: teamId={}, userId={}", teamId, memberId);
                    return new BusinessException(TEAM_MEMBER_NOT_FOUND, "Target member not found");
                });

        if (target.getRole() == newRole) {
            throw new BusinessException(TEAM_ROLE_CHANGE_INVALID, "Member already has this role");
        }

        if (newRole == TeamMemberRole.leader) {
            operator.setRole(TeamMemberRole.member);
            teamMemberRepository.save(operator);

            target.setRole(TeamMemberRole.leader);
            teamMemberRepository.save(target);

            Team team = findVisibleTeam(teamId);
            team.setLeaderId(memberId);
            team.setUpdatedAt(Instant.now());
            teamRepository.save(team);

            log.info("队长转让成功: teamId={}, oldLeader={}, newLeader={}", teamId, operatorId, memberId);
        } else if (newRole == TeamMemberRole.admin || newRole == TeamMemberRole.member) {
            target.setRole(newRole);
            teamMemberRepository.save(target);
            log.info("成员角色更新: teamId={}, userId={}, newRole={}", teamId, memberId, newRole);
        } else {
            throw new BusinessException(TEAM_ROLE_CHANGE_INVALID, "Invalid role: " + newRole);
        }

        return toTeamMemberDto(target);
    }

    // ========================================
    // 解散小队
    // ========================================

    /**
     * 解散小队，仅队长可操作。解散后小队的群聊和活动停止使用。
     *
     * <p>前置条件：{@code operatorId} 是小队的队长，小队状态为 active。
     *
     * <p>后置条件：小队状态变更为 dissolved。
     *
     * @param teamId     小队 ID
     * @param operatorId 操作人 ID
     * @throws BusinessException 权限不足或小队不可见
     */
    public void dissolveTeam(String teamId, String operatorId) {
        Team team = findVisibleTeam(teamId);

        TeamMember member = teamMemberRepository
                .findByTeamIdAndUserId(teamId, operatorId)
                .orElseThrow(() -> {
                    log.warn("操作人不是小队成员: teamId={}, userId={}", teamId, operatorId);
                    return new BusinessException(TEAM_MEMBER_NOT_FOUND, "Team membership is required");
                });

        if (member.getRole() != TeamMemberRole.leader) {
            log.warn("非队长尝试解散小队: teamId={}, userId={}", teamId, operatorId);
            throw new BusinessException(TEAM_PERMISSION_DENIED, "Only the team leader can dissolve the team");
        }

        team.setStatus(TeamStatus.dissolved);
        team.setUpdatedAt(Instant.now());
        teamRepository.save(team);

        log.info("小队已解散: teamId={}, name={}", teamId, team.getName());
    }

    // ========================================
    // 积分榜
    // ========================================

    /**
     * 获取小队积分榜，按积分降序排列。
     *
     * <p>前置条件：小队存在且可见。
     *
     * <p>后置条件：返回含排名、昵称和积分的分页结果。
     *
     * @param teamId   小队 ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 分页结果
     * @throws BusinessException 小队不可见
     */
    @Transactional(readOnly = true)
    public PageResult<SocialDtos.TeamPointRankItem> getTeamPointRanks(String teamId, int page, int pageSize) {
        findVisibleTeam(teamId);

        var dbPage = teamMemberRepository.findByTeamId(
                teamId,
                PageRequest.of(
                        page - 1,
                        pageSize,
                        org.springframework.data.domain.Sort.by("points").descending()));

        List<String> userIds =
                dbPage.getContent().stream().map(TeamMember::getUserId).collect(Collectors.toList());
        Map<String, String> nicknameMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getUserId, User::getNickname, (a, b) -> a));

        int rankOffset = (page - 1) * pageSize;
        List<SocialDtos.TeamPointRankItem> items = new ArrayList<>();
        for (int i = 0; i < dbPage.getContent().size(); i++) {
            TeamMember member = dbPage.getContent().get(i);

            SocialDtos.TeamPointRankItem item = new SocialDtos.TeamPointRankItem();
            item.setRank(rankOffset + i + 1);
            item.setUserId(member.getUserId());
            item.setNickname(nicknameMap.getOrDefault(member.getUserId(), "未知用户"));
            item.setPoints(member.getPoints());
            items.add(item);
        }

        return new PageResult<>(items, dbPage.getTotalElements(), page, pageSize, dbPage.getTotalPages());
    }

    // ========================================
    // 私有辅助方法
    // ========================================

    /**
     * 查找存在且可见的小队（含 disabled 状态，不含已解散）。
     *
     * <p>前置条件：{@code teamId} 非空。
     *
     * <p>后置条件：返回非 dissolved 状态的小队，否则抛出异常。
     */
    private Team findVisibleTeam(String teamId) {
        Team team = teamRepository.findById(teamId).orElseThrow(() -> {
            log.warn("小队不存在: teamId={}", teamId);
            return new BusinessException(TEAM_NOT_VISIBLE, "Team not found");
        });

        if (team.getStatus() == TeamStatus.dissolved) {
            throw new BusinessException(TEAM_NOT_VISIBLE, "Team has been dissolved");
        }

        return team;
    }

    /**
     * 要求操作人是小队的队长或管理员。
     *
     * <p>前置条件：{@code teamId} 对应的小队存在。
     *
     * <p>后置条件：角色通过则返回成员记录，否则抛出权限异常。
     */
    private TeamMember requireTeamAdmin(String teamId, String userId) {
        return teamMemberRepository
                .findByTeamIdAndUserId(teamId, userId)
                .filter(m -> m.getRole() == TeamMemberRole.leader || m.getRole() == TeamMemberRole.admin)
                .orElseThrow(() -> {
                    log.warn("用户缺少小队管理权限: teamId={}, userId={}", teamId, userId);
                    return new BusinessException(TEAM_PERMISSION_DENIED, "Team admin permission is required");
                });
    }

    /**
     * 将用户加入小队并同步加入群聊。
     *
     * <p>前置条件：小队存在，用户尚未是成员。
     *
     * <p>后置条件：创建 TeamMember 和 ConversationMember 记录。
     */
    private void addMemberToTeam(Team team, String userId, TeamMemberRole role, Instant now) {
        TeamMember member = TeamMember.builder()
                .memberId(UUID.randomUUID().toString())
                .teamId(team.getTeamId())
                .userId(userId)
                .role(role)
                .points(0)
                .joinedAt(now)
                .build();
        teamMemberRepository.save(member);

        if (team.getChatId() != null) {
            boolean alreadyInChat =
                    conversationMemberRepository.existsByConversationIdAndUserId(team.getChatId(), userId);
            if (!alreadyInChat) {
                ConversationMember convMember = ConversationMember.builder()
                        .memberId(UUID.randomUUID().toString())
                        .conversationId(team.getChatId())
                        .userId(userId)
                        .joinedAt(now)
                        .build();
                conversationMemberRepository.save(convMember);
                log.info("用户加入群聊: conversationId={}, userId={}", team.getChatId(), userId);
            }
        }
    }

    // ========================================
    // 群文件与相册
    // ========================================

    /**
     * 上传群文件，将文件关联到小队，并将访问策略从默认 owner 提升为 teamMember。
     *
     * <p>前置条件：{@code userId} 是小队成员，文件实体已保存。
     *
     * <p>后置条件：小队-媒体关联已建立，访问策略更新为 teamMember，小队所有成员均可通过签名 URL
     * 访问该文件。
     *
     * @param teamId  小队 ID
     * @param userId  上传者 ID
     * @param mediaId 已保存的媒体文件 ID
     * @return 更新后的媒体文件 DTO（含 teamMember 策略签名 URL）
     * @throws BusinessException 非小队成员
     */
    public CommonDtos.MediaFile uploadTeamFile(String teamId, String userId, UUID mediaId) {
        requireTeamMember(teamId, userId);

        MediaFile file = mediaFileRepository
                .findById(mediaId)
                .orElseThrow(() -> new BusinessException(TEAM_MEDIA_NOT_FOUND, "Media file not found"));

        if (!file.getUploadedBy().equals(userId)) {
            log.warn("调用者不是文件上传者: mediaId={}, userId={}, uploadedBy={}", mediaId, userId, file.getUploadedBy());
            throw new BusinessException(TEAM_MEDIA_NOT_FOUND, "Media file not found");
        }

        // 同名文件去重：在校验通过前不更新访问策略，避免缓存与数据库状态不一致
        if (teamMediaFileRepository.existsByTeamIdAndFileNameAndUsage(
                teamId, file.getFileName(), MediaUsage.teamFile)) {
            throw new BusinessException(TEAM_FILE_DUPLICATE, "A file with the same name already exists in this team");
        }

        TeamMediaFile teamMedia = TeamMediaFile.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .mediaId(mediaId)
                .build();
        teamMediaFileRepository.save(teamMedia);

        mediaAccessService.updateAccessPolicy(mediaId, MediaAccessPolicy.teamMember, teamId, userId);

        log.info("群文件已上传: teamId={}, mediaId={}, uploadedBy={}", teamId, mediaId, userId);
        return toMediaFileDto(file);
    }

    /**
     * 分页查看群文件列表。
     *
     * <p>前置条件：{@code userId} 是小队成员。
     *
     * <p>后置条件：返回该小队的群文件分页列表。
     */
    @Transactional(readOnly = true)
    public PageResult<CommonDtos.MediaFile> listTeamFiles(String teamId, String userId, int page, int pageSize) {
        requireTeamMember(teamId, userId);

        var tmPage = teamMediaFileRepository.findByTeamIdAndMediaUsage(
                teamId, MediaUsage.teamFile, PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id")));
        List<UUID> mediaIds =
                tmPage.getContent().stream().map(TeamMediaFile::getMediaId).collect(Collectors.toList());

        List<MediaFile> files = mediaIds.isEmpty() ? List.of() : mediaFileRepository.findByMediaIdIn(mediaIds);
        Map<UUID, MediaFile> fileMap = files.stream().collect(Collectors.toMap(MediaFile::getMediaId, f -> f));

        List<CommonDtos.MediaFile> items = mediaIds.stream()
                .map(fileMap::get)
                .filter(f -> f != null && f.getDeletedAt() == null && f.getUsage() == MediaUsage.teamFile)
                .map(this::toMediaFileDto)
                .collect(Collectors.toList());

        return new PageResult<>(
                items, tmPage.getTotalElements(), tmPage.getNumber() + 1, tmPage.getSize(), tmPage.getTotalPages());
    }

    /**
     * 批量软删除群文件，仅队长和管理员可操作。
     *
     * <p>前置条件：{@code userId} 是队长或管理员，指定文件均属于该小队。
     *
     * <p>后置条件：关联记录已删除，媒体文件标记为软删除，旧签名 URL 因 accessVersion
     * 递增而失效。
     */
    public void deleteTeamFiles(String teamId, String userId, List<UUID> mediaIds) {
        requireTeamAdmin(teamId, userId);

        List<TeamMediaFile> teamMedias = teamMediaFileRepository.findByMediaIdInAndTeamId(mediaIds, teamId);
        if (teamMedias.size() != mediaIds.size()) {
            throw new BusinessException(TEAM_MEDIA_NOT_FOUND, "Some media files do not belong to this team");
        }

        teamMediaFileRepository.deleteAll(teamMedias);
        List<MediaFile> files = mediaFileRepository.findByMediaIdIn(mediaIds);
        for (MediaFile file : files) {
            if (file.getDeletedAt() == null) {
                mediaAccessService.softDelete(file.getMediaId());
            }
        }
        log.info("群文件已软删除: teamId={}, count={}", teamId, mediaIds.size());
    }

    /**
     * 上传小队相册图片，将访问策略从默认 owner 提升为 teamMember。
     *
     * <p>前置条件：{@code userId} 是小队成员，图片实体已保存。
     *
     * <p>后置条件：小队-媒体关联已建立，usage 更新为 teamAlbum，访问策略更新为
     * teamMember，小队所有成员均可访问。
     */
    public CommonDtos.MediaFile uploadTeamAlbumImage(String teamId, String userId, UUID mediaId) {
        requireTeamMember(teamId, userId);

        MediaFile file = mediaFileRepository
                .findById(mediaId)
                .orElseThrow(() -> new BusinessException(TEAM_MEDIA_NOT_FOUND, "Media file not found"));

        if (!file.getUploadedBy().equals(userId)) {
            log.warn("调用者不是文件上传者: mediaId={}, userId={}, uploadedBy={}", mediaId, userId, file.getUploadedBy());
            throw new BusinessException(TEAM_MEDIA_NOT_FOUND, "Media file not found");
        }

        file.setUsage(MediaUsage.teamAlbum);
        mediaFileRepository.save(file);

        TeamMediaFile teamMedia = TeamMediaFile.builder()
                .id(UUID.randomUUID())
                .teamId(teamId)
                .mediaId(mediaId)
                .build();
        teamMediaFileRepository.save(teamMedia);

        mediaAccessService.updateAccessPolicy(mediaId, MediaAccessPolicy.teamMember, teamId, userId);

        log.info("小队相册图片已上传: teamId={}, mediaId={}, uploadedBy={}", teamId, mediaId, userId);
        return toMediaFileDto(file);
    }

    /**
     * 分页查看小队相册图片。
     *
     * <p>前置条件：{@code userId} 是小队成员。
     *
     * <p>后置条件：返回该小队的相册图片分页列表。
     */
    @Transactional(readOnly = true)
    public PageResult<CommonDtos.MediaFile> listTeamAlbumImages(String teamId, String userId, int page, int pageSize) {
        requireTeamMember(teamId, userId);

        var tmPage = teamMediaFileRepository.findByTeamIdAndMediaUsage(
                teamId, MediaUsage.teamAlbum, PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id")));
        List<UUID> mediaIds =
                tmPage.getContent().stream().map(TeamMediaFile::getMediaId).collect(Collectors.toList());

        List<MediaFile> files = mediaIds.isEmpty() ? List.of() : mediaFileRepository.findByMediaIdIn(mediaIds);
        Map<UUID, MediaFile> fileMap = files.stream().collect(Collectors.toMap(MediaFile::getMediaId, f -> f));

        List<CommonDtos.MediaFile> items = mediaIds.stream()
                .map(fileMap::get)
                .filter(f -> f != null && f.getDeletedAt() == null && f.getUsage() == MediaUsage.teamAlbum)
                .map(this::toMediaFileDto)
                .collect(Collectors.toList());

        return new PageResult<>(
                items, tmPage.getTotalElements(), tmPage.getNumber() + 1, tmPage.getSize(), tmPage.getTotalPages());
    }

    /**
     * 批量软删除小队相册图片，仅队长和管理员可操作。
     *
     * <p>前置条件：{@code userId} 是队长或管理员，指定图片均属于该小队相册。
     *
     * <p>后置条件：关联记录已删除，媒体文件标记为软删除，旧签名 URL 因 accessVersion
     * 递增而失效。
     */
    public void deleteTeamAlbumImages(String teamId, String userId, List<UUID> mediaIds) {
        requireTeamAdmin(teamId, userId);

        List<TeamMediaFile> teamMedias = teamMediaFileRepository.findByMediaIdInAndTeamId(mediaIds, teamId);
        if (teamMedias.size() != mediaIds.size()) {
            throw new BusinessException(TEAM_MEDIA_NOT_FOUND, "Some media files do not belong to this team");
        }

        teamMediaFileRepository.deleteAll(teamMedias);
        List<MediaFile> files = mediaFileRepository.findByMediaIdIn(mediaIds);
        for (MediaFile file : files) {
            if (file.getDeletedAt() == null) {
                mediaAccessService.softDelete(file.getMediaId());
            }
        }
        log.info("小队相册图片已软删除: teamId={}, count={}", teamId, mediaIds.size());
    }

    // ========================================
    // 队内活动
    // ========================================

    /**
     * 创建队内活动，仅队长和管理员可操作。
     *
     * <p>前置条件：{@code userId} 是 {@code teamId} 小队的队长或管理员。
     *
     * <p>后置条件：活动已创建并关联到小队。
     */
    public ActivityDtos.ActivityDetail createTeamActivity(
            String teamId, String userId, ActivityDtos.ActivityUpsertRequest request) {
        requireTeamAdmin(teamId, userId);

        CommonDtos.LocationInfo location = request.getLocation();
        Instant now = Instant.now();

        Activity activity = Activity.builder()
                .activityId(UUID.randomUUID().toString())
                .organizerId(userId)
                .teamId(teamId)
                .title(request.getTitle())
                .tags(request.getTags() != null ? List.copyOf(request.getTags()) : List.of())
                .introduction(request.getIntroduction())
                .startAt(parseInstant(request.getStartAt()))
                .endAt(parseInstant(request.getEndAt()))
                .pointLon(
                        location != null && location.getPoint() != null
                                ? location.getPoint().getLongitude()
                                : null)
                .pointLat(
                        location != null && location.getPoint() != null
                                ? location.getPoint().getLatitude()
                                : null)
                .city(location != null ? location.getCity() : null)
                .address(location != null ? location.getAddress() : null)
                .placeName(location != null ? location.getPlaceName() : null)
                .safetyNotice(request.getSafetyNotice())
                .capacity(request.getCapacity())
                .feeAmount(request.getFeeAmount())
                .feeDescription(request.getFeeDescription())
                .minAge(request.getMinAge())
                .registrationDeadline(parseInstant(request.getRegistrationDeadline()))
                .reviewStatus(ActivityReviewStatus.approved)
                .runtimeStatus(ActivityRuntimeStatus.notStarted)
                .createdAt(now)
                .updatedAt(now)
                .build();
        activityRepository.save(activity);

        if (request.getImageIds() != null && !request.getImageIds().isEmpty()) {
            for (int i = 0; i < request.getImageIds().size(); i++) {
                ActivityImage image = ActivityImage.builder()
                        .imageId(UUID.randomUUID().toString())
                        .activityId(activity.getActivityId())
                        .mediaId(request.getImageIds().get(i))
                        .sortOrder(i)
                        .build();
                activityImageRepository.save(image);
            }
        }

        log.info("队内活动创建成功: activityId={}, teamId={}, title={}", activity.getActivityId(), teamId, request.getTitle());
        return toActivityDetail(activity);
    }

    /**
     * 分页查看队内活动列表，仅小队成员可查看。
     *
     * <p>前置条件：{@code userId} 是小队成员。
     *
     * <p>后置条件：返回该小队的活动摘要分页列表。
     */
    @Transactional(readOnly = true)
    public PageResult<ActivityDtos.ActivitySummary> listTeamActivities(
            String teamId, String userId, int page, int pageSize) {
        requireTeamMember(teamId, userId);

        var activityPage = activityRepository.findByTeamId(
                teamId, PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<ActivityDtos.ActivitySummary> items =
                activityPage.getContent().stream().map(this::toActivitySummary).collect(Collectors.toList());

        return new PageResult<>(
                items,
                activityPage.getTotalElements(),
                activityPage.getNumber() + 1,
                activityPage.getSize(),
                activityPage.getTotalPages());
    }

    /**
     * 查看队内活动详情，仅小队成员可查看。
     *
     * <p>前置条件：活动属于小队，调用者是小队成员。
     *
     * <p>后置条件：返回完整活动详情。
     */
    @Transactional(readOnly = true)
    public ActivityDtos.ActivityDetail getTeamActivity(String teamId, String activityId, String userId) {
        requireTeamMember(teamId, userId);

        Activity activity = activityRepository
                .findById(activityId)
                .orElseThrow(() -> new BusinessException(TEAM_ACTIVITY_NOT_VISIBLE, "Activity not found"));

        if (activity.getTeamId() == null || !activity.getTeamId().equals(teamId)) {
            throw new BusinessException(TEAM_ACTIVITY_NOT_VISIBLE, "Activity does not belong to this team");
        }

        return toActivityDetail(activity);
    }

    // ========================================
    // 私有辅助方法（续）
    // ========================================

    /**
     * 要求操作人是小队的成员（任意角色）。
     */
    private TeamMember requireTeamMember(String teamId, String userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId).orElseThrow(() -> {
            log.warn("调用方不是小队成员: teamId={}, userId={}", teamId, userId);
            return new BusinessException(TEAM_MEMBER_NOT_FOUND, "Team membership is required");
        });
    }

    private ActivityDtos.ActivityDetail toActivityDetail(Activity entity) {
        ActivityDtos.ActivityDetail dto = new ActivityDtos.ActivityDetail();
        dto.setActivityId(entity.getActivityId());
        dto.setTitle(entity.getTitle());
        dto.setTags(entity.getTags());
        dto.setIntroduction(entity.getIntroduction());
        dto.setStartAt(entity.getStartAt() != null ? entity.getStartAt().toString() : null);
        dto.setEndAt(entity.getEndAt() != null ? entity.getEndAt().toString() : null);
        dto.setSafetyNotice(entity.getSafetyNotice());
        dto.setCapacity(entity.getCapacity());
        dto.setFeeAmount(entity.getFeeAmount());
        dto.setReviewStatus(entity.getReviewStatus());
        dto.setRuntimeStatus(entity.getRuntimeStatus());
        dto.setOrganizerId(entity.getOrganizerId());
        dto.setRegistrationDeadline(
                entity.getRegistrationDeadline() != null
                        ? entity.getRegistrationDeadline().toString()
                        : null);

        if (entity.getPointLon() != null && entity.getPointLat() != null) {
            CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
            point.setLongitude(entity.getPointLon());
            point.setLatitude(entity.getPointLat());

            CommonDtos.LocationInfo loc = new CommonDtos.LocationInfo();
            loc.setPoint(point);
            loc.setCity(entity.getCity());
            loc.setAddress(entity.getAddress());
            loc.setPlaceName(entity.getPlaceName());
            dto.setLocation(loc);
        }

        List<ActivityImage> images =
                activityImageRepository.findByActivityIdOrderBySortOrderAsc(entity.getActivityId());
        if (!images.isEmpty()) {
            List<UUID> mediaIds = images.stream().map(ActivityImage::getMediaId).collect(Collectors.toList());
            Map<UUID, MediaFile> mediaMap = mediaFileRepository.findByMediaIdIn(mediaIds).stream()
                    .collect(Collectors.toMap(MediaFile::getMediaId, m -> m));
            dto.setImages(images.stream()
                    .map(img -> mediaMap.get(img.getMediaId()))
                    .filter(m -> m != null)
                    .map(this::toMediaFileDto)
                    .collect(Collectors.toList()));
        } else {
            dto.setImages(List.of());
        }
        dto.setRegisteredCount(0);
        dto.setWaitingCount(0);
        dto.setManualReviewRequired(false);
        dto.setReviewRecords(List.of());

        return dto;
    }

    private ActivityDtos.ActivitySummary toActivitySummary(Activity entity) {
        ActivityDtos.ActivitySummary dto = new ActivityDtos.ActivitySummary();
        dto.setActivityId(entity.getActivityId());
        dto.setTitle(entity.getTitle());
        dto.setTags(entity.getTags());
        dto.setStartAt(entity.getStartAt() != null ? entity.getStartAt().toString() : null);
        dto.setEndAt(entity.getEndAt() != null ? entity.getEndAt().toString() : null);
        dto.setFeeAmount(entity.getFeeAmount());
        dto.setReviewStatus(entity.getReviewStatus());
        dto.setRuntimeStatus(entity.getRuntimeStatus());
        dto.setCapacity(entity.getCapacity());
        dto.setRegisteredCount(0);

        if (entity.getPointLon() != null && entity.getPointLat() != null) {
            CommonDtos.GeoPoint point = new CommonDtos.GeoPoint();
            point.setLongitude(entity.getPointLon());
            point.setLatitude(entity.getPointLat());

            CommonDtos.LocationInfo loc = new CommonDtos.LocationInfo();
            loc.setPoint(point);
            loc.setCity(entity.getCity());
            loc.setAddress(entity.getAddress());
            loc.setPlaceName(entity.getPlaceName());
            dto.setLocation(loc);
        }

        return dto;
    }

    @Nullable
    private Instant parseInstant(String dateString) {
        if (dateString == null) return null;
        try {
            return Instant.parse(dateString);
        } catch (DateTimeParseException e) {
            log.warn("日期字符串解析失败: dateString={}", dateString, e);
            return null;
        }
    }

    /**
     * 将 Team 实体转换为 TeamProfile DTO。
     *
     * <p>前置条件：{@code team} 已持久化。
     *
     * <p>后置条件：返回包含成员数的 DTO。
     */
    private SocialDtos.TeamProfile toTeamProfile(Team team, int memberCount) {
        SocialDtos.TeamProfile dto = new SocialDtos.TeamProfile();
        dto.setTeamId(team.getTeamId());
        dto.setName(team.getName());
        dto.setTags(team.getTags());
        dto.setJoinMode(team.getJoinMode());
        dto.setCapacity(team.getCapacity());
        dto.setMemberCount(memberCount);
        dto.setDescription(team.getDescription());
        dto.setStatus(team.getStatus());
        dto.setCreatorId(team.getCreatorId());
        dto.setLeaderId(team.getLeaderId());
        dto.setChatId(team.getChatId());

        if (team.getAvatar() != null) {
            dto.setAvatar(toMediaFileDto(team.getAvatar()));
        }
        return dto;
    }

    private SocialDtos.TeamMember toTeamMemberDto(TeamMember entity) {
        SocialDtos.TeamMember dto = new SocialDtos.TeamMember();
        dto.setUserId(entity.getUserId());
        dto.setRole(entity.getRole());
        dto.setPoints(entity.getPoints());
        dto.setJoinedAt(entity.getJoinedAt().toString());

        userRepository.findById(entity.getUserId()).ifPresent(user -> dto.setNickname(user.getNickname()));

        return dto;
    }

    private SocialDtos.TeamJoinRequest toTeamJoinRequestDto(TeamJoinRequest entity) {
        SocialDtos.TeamJoinRequest dto = new SocialDtos.TeamJoinRequest();
        dto.setRequestId(entity.getRequestId());
        dto.setTeamId(entity.getTeamId());
        dto.setUserId(entity.getUserId());
        dto.setMessage(entity.getMessage());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt().toString());
        return dto;
    }

    private CommonDtos.MediaFile toMediaFileDto(MediaFile entity) {
        return mediaAccessService.toSignedDto(entity);
    }
}
