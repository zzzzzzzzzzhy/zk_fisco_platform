import request from './request'

/**
 * 获取竞赛排行榜列表
 */
export function getLeaderboards(competitionId) {
  return request({
    url: `/leaderboards/competition/${competitionId}`,
    method: 'get'
  })
}

/**
 * 获取排行榜详情
 */
export function getLeaderboardById(id) {
  return request({
    url: `/leaderboards/${id}`,
    method: 'get'
  })
}

/**
 * 创建排行榜快照
 */
export function createLeaderboard(data) {
  return request({
    url: '/leaderboards',
    method: 'post',
    data
  })
}

/**
 * 冻结榜单
 */
export function freezeLeaderboard(id) {
  return request({
    url: `/leaderboards/${id}/freeze`,
    method: 'post'
  })
}

/**
 * 解冻榜单
 */
export function unfreezeLeaderboard(id) {
  return request({
    url: `/leaderboards/${id}/unfreeze`,
    method: 'post'
  })
}

/**
 * 确认榜单
 */
export function confirmLeaderboard(id, data) {
  return request({
    url: `/leaderboards/${id}/confirm`,
    method: 'post',
    data
  })
}

/**
 * 提交榜单异议
 */
export function submitAppeal(data) {
  return request({
    url: '/leaderboard-appeals',
    method: 'post',
    data
  })
}

/**
 * 获取榜单异议列表
 */
export function getAppeals(params) {
  return request({
    url: '/leaderboard-appeals',
    method: 'get',
    params
  })
}

/**
 * 审核异议
 */
export function reviewAppeal(id, data) {
  return request({
    url: `/leaderboard-appeals/${id}/review`,
    method: 'put',
    data
  })
}

/**
 * 获取榜单通知列表
 */
export function getNotifications(params) {
  return request({
    url: '/leaderboard-notifications',
    method: 'get',
    params
  })
}

/**
 * 标记通知已读
 */
export function markNotificationRead(id) {
  return request({
    url: `/leaderboard-notifications/${id}/read`,
    method: 'put'
  })
}
