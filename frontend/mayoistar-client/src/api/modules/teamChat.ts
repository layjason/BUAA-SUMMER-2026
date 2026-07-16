/**
 * 小队群聊增强 API 模块
 *
 * 封装公告、投票、群文件、相册等 chat/teams 契约接口。
 */
import { get, post, put, del, upload } from '@/api/request'
import type { components } from '@/api/types/schema'

type TeamAnnouncementRequest = components['schemas']['Chat.TeamAnnouncementRequest']
type TeamPollCreateRequest = components['schemas']['Chat.TeamPollCreateRequest']
type VotePollRequest = components['schemas']['Chat.VotePollRequest']
type DeleteTeamFilesRequest = components['schemas']['Chat.DeleteTeamFilesRequest']
type DeleteTeamAlbumImagesRequest = components['schemas']['Chat.DeleteTeamAlbumImagesRequest']
type MediaFile = components['schemas']['MediaFile']

/** 发布群公告 */
export function publishAnnouncement(teamId: string, content: string) {
  const body: TeamAnnouncementRequest = { content }
  return post('/chat/teams/{teamId}/announcements', { path: { teamId }, body })
}

/** 群公告列表 */
export function listAnnouncements(teamId: string, page = 1, pageSize = 20) {
  return get('/chat/teams/{teamId}/announcements', {
    path: { teamId },
    query: { page, pageSize },
  })
}

/** 编辑群公告 */
export function updateAnnouncement(teamId: string, announcementId: string, content: string) {
  const body: TeamAnnouncementRequest = { content }
  return put('/chat/teams/{teamId}/announcements/{announcementId}', {
    path: { teamId, announcementId },
    body,
  })
}

/** 删除群公告 */
export function deleteAnnouncement(teamId: string, announcementId: string) {
  return del('/chat/teams/{teamId}/announcements/{announcementId}', {
    path: { teamId, announcementId },
  })
}

/** 标记公告已读 */
export function markAnnouncementRead(teamId: string, announcementId: string) {
  return post('/chat/teams/{teamId}/announcements/{announcementId}/read', {
    path: { teamId, announcementId },
  })
}

/** 创建群投票 */
export function createPoll(teamId: string, data: TeamPollCreateRequest) {
  return post('/chat/teams/{teamId}/polls', { path: { teamId }, body: data })
}

/** 群投票列表 */
export function listPolls(teamId: string, page = 1, pageSize = 20) {
  return get('/chat/teams/{teamId}/polls', { path: { teamId }, query: { page, pageSize } })
}

/** 群投票详情 */
export function getPoll(teamId: string, pollId: string) {
  return get('/chat/teams/{teamId}/polls/{pollId}', { path: { teamId, pollId } })
}

/** 参与投票 */
export function votePoll(teamId: string, pollId: string, optionId: string) {
  const body: VotePollRequest = { optionId }
  return post('/chat/teams/{teamId}/polls/{pollId}/votes', {
    path: { teamId, pollId },
    body,
  })
}

/** 上传群文件 */
export function uploadTeamFile(teamId: string, filePath: string): Promise<MediaFile> {
  return upload<MediaFile>(`/chat/teams/${teamId}/files`, filePath)
}

/** 群文件列表 */
export function listTeamFiles(teamId: string, page = 1, pageSize = 20) {
  return get('/chat/teams/{teamId}/files', { path: { teamId }, query: { page, pageSize } })
}

/** 删除群文件 */
export function deleteTeamFiles(teamId: string, mediaIds: string[]) {
  const body: DeleteTeamFilesRequest = { mediaIds }
  return del('/chat/teams/{teamId}/files', { path: { teamId }, body })
}

/** 上传相册图片 */
export function uploadTeamAlbumImage(teamId: string, filePath: string): Promise<MediaFile> {
  return upload<MediaFile>(`/chat/teams/${teamId}/album-images`, filePath)
}

/** 相册图片列表 */
export function listTeamAlbumImages(teamId: string, page = 1, pageSize = 20) {
  return get('/chat/teams/{teamId}/album-images', {
    path: { teamId },
    query: { page, pageSize },
  })
}

/** 删除相册图片 */
export function deleteTeamAlbumImages(teamId: string, mediaIds: string[]) {
  const body: DeleteTeamAlbumImagesRequest = { mediaIds }
  return del('/chat/teams/{teamId}/album-images', { path: { teamId }, body })
}
