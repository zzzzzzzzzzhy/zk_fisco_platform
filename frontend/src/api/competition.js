import request from './request'

/**
 * 获取竞赛列表
 */
export function getCompetitions(params) {
  return request({
    url: '/competitions',
    method: 'get',
    params
  })
}

/**
 * 获取竞赛详情
 */
export function getCompetitionById(id) {
  return request({
    url: `/competitions/${id}`,
    method: 'get'
  })
}

/**
 * 创建竞赛
 */
export function createCompetition(data) {
  return request({
    url: '/competitions',
    method: 'post',
    data
  })
}

/**
 * 报名竞赛
 */
export function registerCompetition(data) {
  return request({
    url: '/registrations',
    method: 'post',
    data
  })
}

/**
 * 检查用户是否已报名竞赛
 */
export function checkUserRegistration(userId, competitionId) {
  return request({
    url: '/registrations',
    method: 'get',
    params: { userId, competitionId }
  })
}

/**
 * 获取用户已报名的竞赛列表
 */
export function getMyRegisteredCompetitions(userId) {
  return request({
    url: '/registrations/my-competitions',
    method: 'get',
    params: { userId }
  })
}

/**
 * 获取进行中的竞赛
 */
export function getOngoingCompetitions() {
  return request({
    url: '/competitions/ongoing',
    method: 'get'
  })
}

/**
 * 更新竞赛
 */
export function updateCompetition(id, data) {
  return request({
    url: `/competitions/${id}`,
    method: 'put',
    data
  })
}

/**
 * 发布竞赛
 */
export function publishCompetition(id) {
  return request({
    url: `/competitions/${id}/publish`,
    method: 'post'
  })
}

/**
 * 删除竞赛
 */
export function deleteCompetition(id) {
  return request({
    url: `/competitions/${id}`,
    method: 'delete'
  })
}
