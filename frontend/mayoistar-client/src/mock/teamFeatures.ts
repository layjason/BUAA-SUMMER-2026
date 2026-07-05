/**
 * 小队群聊增强能力 Mock 工作流
 *
 * 覆盖公告、投票、群文件、相册、积分榜与队内活动，对齐 api-spec chat/social 契约。
 */
import { getMockDb, nextId, persistMockDb } from './database'
import { MockBusinessError } from './workflow'
import type { MockPageResult, MockTeamAnnouncement, MockTeamPoll } from './types'
import type { components } from '@/api/types/schema'

type TeamAnnouncement = components['schemas']['Chat.TeamAnnouncement']
type TeamPoll = components['schemas']['Chat.TeamPoll']
type MediaFile = components['schemas']['MediaFile']
type TeamPointRankItem = components['schemas']['Social.TeamPointRankItem']
type TeamMemberRole = components['schemas']['Social.TeamMemberRole']

/** 分页切片 */
function paginate<T>(items: T[], page: number, pageSize: number): MockPageResult<T> {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const start = (page - 1) * pageSize
  return {
    items: items.slice(start, start + pageSize),
    total,
    page,
    pageSize,
    totalPages,
  }
}

/** 获取小队与成员校验 */
function requireTeamMember(teamId: number, userId: number) {
  const db = getMockDb()
  const team = db.teams.find((t) => t.id === teamId)
  if (!team) throw new MockBusinessError(40009, 'Team is not visible')
  const member = db.teamMembers.find((m) => m.teamId === teamId && m.userId === userId)
  if (!member) throw new MockBusinessError(40015, 'Team member does not exist')
  return { db, team, member }
}

/** 队长或管理员权限校验 */
function requireTeamManager(teamId: number, userId: number) {
  const ctx = requireTeamMember(teamId, userId)
  if (ctx.member.role !== 'leader' && ctx.member.role !== 'admin') {
    throw new MockBusinessError(40020, 'Team operation is not allowed')
  }
  return ctx
}

/** 小队是否允许新增内容 */
function assertTeamWritable(teamId: number) {
  const db = getMockDb()
  const team = db.teams.find((t) => t.id === teamId)
  if (!team || team.status !== 'active') {
    throw new MockBusinessError(40011, 'Team is unavailable')
  }
}

/** 构造媒体文件 DTO */
function teamMediaFile(
  mediaId: string,
  fileName: string,
  usage: 'teamFile' | 'teamAlbum',
  uploadedAt: string,
): MediaFile {
  return {
    mediaId,
    fileName,
    contentType: usage === 'teamAlbum' ? 'image/jpeg' : 'application/pdf',
    sizeBytes: 1024,
    usage,
    signedUrl: `https://picsum.photos/seed/${mediaId}/400/300`,
    uploadedAt,
  }
}

/** 公告 DTO */
function toAnnouncementDto(ann: MockTeamAnnouncement, userId: number): TeamAnnouncement {
  return {
    announcementId: String(ann.id),
    teamId: String(ann.teamId),
    content: ann.content,
    publisherId: String(ann.publisherId),
    publishedAt: ann.publishedAt,
    readByCurrentUser: ann.readByUserIds.includes(userId),
  }
}

/** 投票 DTO */
function toPollDto(poll: MockTeamPoll): TeamPoll {
  return {
    pollId: String(poll.id),
    teamId: String(poll.teamId),
    title: poll.title,
    options: poll.options.map((o) => ({
      optionId: String(o.id),
      content: o.content,
      voteCount: o.voteCount,
    })),
    deadline: poll.deadline,
    createdAt: poll.createdAt,
  }
}

/** 发布群公告 */
export function publishAnnouncement(
  teamId: number,
  userId: number,
  content: string,
): TeamAnnouncement {
  assertTeamWritable(teamId)
  requireTeamManager(teamId, userId)
  const db = getMockDb()
  const now = new Date().toISOString()
  const ann = {
    id: nextId('teamAnnouncements'),
    teamId,
    content,
    publisherId: userId,
    publishedAt: now,
    readByUserIds: [userId],
  }
  db.teamAnnouncements.push(ann)
  persistMockDb()
  return toAnnouncementDto(ann, userId)
}

/** 公告列表 */
export function listAnnouncements(
  teamId: number,
  userId: number,
  page: number,
  pageSize: number,
): MockPageResult<TeamAnnouncement> {
  requireTeamMember(teamId, userId)
  const db = getMockDb()
  const items = db.teamAnnouncements
    .filter((a) => a.teamId === teamId)
    .sort((a, b) => new Date(b.publishedAt).getTime() - new Date(a.publishedAt).getTime())
    .map((a) => toAnnouncementDto(a, userId))
  return paginate(items, page, pageSize)
}

/** 更新公告 */
export function updateAnnouncement(
  teamId: number,
  announcementId: number,
  userId: number,
  content: string,
): TeamAnnouncement {
  assertTeamWritable(teamId)
  requireTeamManager(teamId, userId)
  const db = getMockDb()
  const ann = db.teamAnnouncements.find((a) => a.id === announcementId && a.teamId === teamId)
  if (!ann) throw new MockBusinessError(50018, 'Announcement is not visible')
  ann.content = content
  persistMockDb()
  return toAnnouncementDto(ann, userId)
}

/** 删除公告 */
export function deleteAnnouncement(teamId: number, announcementId: number, userId: number): void {
  assertTeamWritable(teamId)
  requireTeamManager(teamId, userId)
  const db = getMockDb()
  const before = db.teamAnnouncements.length
  db.teamAnnouncements = db.teamAnnouncements.filter(
    (a) => !(a.id === announcementId && a.teamId === teamId),
  )
  if (db.teamAnnouncements.length === before) {
    throw new MockBusinessError(50018, 'Announcement is not visible')
  }
  persistMockDb()
}

/** 标记公告已读 */
export function markAnnouncementRead(
  teamId: number,
  announcementId: number,
  userId: number,
): TeamAnnouncement {
  const { db } = requireTeamMember(teamId, userId)
  const ann = db.teamAnnouncements.find((a) => a.id === announcementId && a.teamId === teamId)
  if (!ann) throw new MockBusinessError(50018, 'Announcement is not visible')
  if (!ann.readByUserIds.includes(userId)) {
    ann.readByUserIds.push(userId)
    persistMockDb()
  }
  return toAnnouncementDto(ann, userId)
}

/** 创建投票 */
export function createPoll(
  teamId: number,
  userId: number,
  title: string,
  options: string[],
  deadline?: string,
): TeamPoll {
  assertTeamWritable(teamId)
  requireTeamMember(teamId, userId)
  if (options.length < 2) throw new MockBusinessError(50012, 'Poll options are invalid')
  const db = getMockDb()
  const pollId = nextId('teamPolls')
  const poll = {
    id: pollId,
    teamId,
    title,
    options: options.map((content, idx) => ({
      id: pollId * 10 + idx + 1,
      content,
      voteCount: 0,
    })),
    deadline,
    createdAt: new Date().toISOString(),
    votes: [] as Array<{ userId: number; optionId: number }>,
  }
  db.teamPolls.push(poll)
  persistMockDb()
  return toPollDto(poll)
}

/** 投票列表 */
export function listPolls(
  teamId: number,
  userId: number,
  page: number,
  pageSize: number,
): MockPageResult<TeamPoll> {
  requireTeamMember(teamId, userId)
  const db = getMockDb()
  const items = db.teamPolls
    .filter((p) => p.teamId === teamId)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    .map(toPollDto)
  return paginate(items, page, pageSize)
}

/** 投票详情 */
export function getPoll(teamId: number, pollId: number, userId: number): TeamPoll {
  requireTeamMember(teamId, userId)
  const db = getMockDb()
  const poll = db.teamPolls.find((p) => p.id === pollId && p.teamId === teamId)
  if (!poll) throw new MockBusinessError(50019, 'Poll does not exist')
  return toPollDto(poll)
}

/** 参与投票 */
export function votePoll(
  teamId: number,
  pollId: number,
  userId: number,
  optionId: number,
): TeamPoll {
  assertTeamWritable(teamId)
  requireTeamMember(teamId, userId)
  const db = getMockDb()
  const poll = db.teamPolls.find((p) => p.id === pollId && p.teamId === teamId)
  if (!poll) throw new MockBusinessError(50019, 'Poll does not exist')
  if (poll.deadline && new Date(poll.deadline).getTime() < Date.now()) {
    throw new MockBusinessError(50013, 'Poll is unavailable')
  }
  const option = poll.options.find((o) => o.id === optionId)
  if (!option) throw new MockBusinessError(50013, 'Poll is unavailable')

  const existing = poll.votes.find((v) => v.userId === userId)
  if (existing) {
    if (existing.optionId !== optionId) {
      const old = poll.options.find((o) => o.id === existing.optionId)
      if (old) old.voteCount = Math.max(0, old.voteCount - 1)
      option.voteCount += 1
      existing.optionId = optionId
    }
  } else {
    poll.votes.push({ userId, optionId })
    option.voteCount += 1
  }
  persistMockDb()
  return toPollDto(poll)
}

/** 上传群文件（mock 仅记录元数据） */
export function uploadTeamFile(teamId: number, userId: number): MediaFile {
  assertTeamWritable(teamId)
  requireTeamMember(teamId, userId)
  const db = getMockDb()
  const mediaId = `media_team_file_${nextId('teamMedia')}`
  const now = new Date().toISOString()
  db.teamMedia.push({
    id: nextId('teamMedia'),
    teamId,
    mediaId,
    kind: 'file',
    fileName: '群文件.pdf',
    uploadedBy: userId,
    uploadedAt: now,
  })
  persistMockDb()
  return teamMediaFile(mediaId, '群文件.pdf', 'teamFile' as const, now)
}

/** 群文件列表 */
export function listTeamFiles(
  teamId: number,
  userId: number,
  page: number,
  pageSize: number,
): MockPageResult<MediaFile> {
  requireTeamMember(teamId, userId)
  const db = getMockDb()
  const items = db.teamMedia
    .filter((m) => m.teamId === teamId && m.kind === 'file')
    .sort((a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime())
    .map((m) => teamMediaFile(m.mediaId, m.fileName, 'teamFile' as const, m.uploadedAt))
  return paginate(items, page, pageSize)
}

/** 删除群文件 */
export function deleteTeamFiles(teamId: number, userId: number, mediaIds: string[]): void {
  assertTeamWritable(teamId)
  requireTeamManager(teamId, userId)
  const db = getMockDb()
  db.teamMedia = db.teamMedia.filter(
    (m) => !(m.teamId === teamId && m.kind === 'file' && mediaIds.includes(m.mediaId)),
  )
  persistMockDb()
}

/** 上传相册图片 */
export function uploadTeamAlbumImage(teamId: number, userId: number): MediaFile {
  assertTeamWritable(teamId)
  requireTeamMember(teamId, userId)
  const db = getMockDb()
  const mediaId = `media_team_album_${nextId('teamMedia')}`
  const now = new Date().toISOString()
  db.teamMedia.push({
    id: nextId('teamMedia'),
    teamId,
    mediaId,
    kind: 'album',
    fileName: 'album.jpg',
    uploadedBy: userId,
    uploadedAt: now,
  })
  persistMockDb()
  return teamMediaFile(mediaId, 'album.jpg', 'teamAlbum' as const, now)
}

/** 相册列表 */
export function listTeamAlbumImages(
  teamId: number,
  userId: number,
  page: number,
  pageSize: number,
): MockPageResult<MediaFile> {
  requireTeamMember(teamId, userId)
  const db = getMockDb()
  const items = db.teamMedia
    .filter((m) => m.teamId === teamId && m.kind === 'album')
    .sort((a, b) => new Date(b.uploadedAt).getTime() - new Date(a.uploadedAt).getTime())
    .map((m) => teamMediaFile(m.mediaId, m.fileName, 'teamAlbum' as const, m.uploadedAt))
  return paginate(items, page, pageSize)
}

/** 删除相册图片 */
export function deleteTeamAlbumImages(teamId: number, userId: number, mediaIds: string[]): void {
  assertTeamWritable(teamId)
  requireTeamManager(teamId, userId)
  const db = getMockDb()
  db.teamMedia = db.teamMedia.filter(
    (m) => !(m.teamId === teamId && m.kind === 'album' && mediaIds.includes(m.mediaId)),
  )
  persistMockDb()
}

/** 积分榜 */
export function getTeamPointRanks(
  teamId: number,
  userId: number,
  page: number,
  pageSize: number,
): MockPageResult<TeamPointRankItem> {
  const { db } = requireTeamMember(teamId, userId)
  const members = db.teamMembers.filter((m) => m.teamId === teamId)
  const ranked = members
    .map((m) => {
      const user = db.users.find((u) => u.id === m.userId)
      return {
        userId: String(m.userId),
        nickname: user?.nickname ?? '未知',
        points: m.points,
        rank: 0,
      }
    })
    .sort((a, b) => b.points - a.points)
    .map((item, idx) => ({ ...item, rank: idx + 1 }))
  return paginate(ranked, page, pageSize)
}

/** 根据会话 ID 反查小队 */
export function findTeamByConversationId(conversationId: number) {
  const db = getMockDb()
  return db.teams.find((t) => t.conversationId === conversationId) ?? null
}

/** 获取成员角色 */
export function getMemberRole(teamId: number, userId: number): TeamMemberRole | null {
  const db = getMockDb()
  const member = db.teamMembers.find((m) => m.teamId === teamId && m.userId === userId)
  return member?.role ?? null
}
