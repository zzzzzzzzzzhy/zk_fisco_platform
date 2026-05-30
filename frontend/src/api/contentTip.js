import request from './request'

/**
 * 内容打赏相关API
 */
export default {
  /**
   * 创建打赏
   */
  createTip(data) {
    return request({
      url: '/content-tips',
      method: 'post',
      data
    })
  },

  /**
   * 获取内容的打赏记录
   */
  getTipsByContent(contentType, contentId) {
    return request({
      url: `/content-tips/content/${contentType}/${contentId}`,
      method: 'get'
    })
  },

  /**
   * 获取创作者收到的打赏记录
   */
  getTipsByCreator(creatorId, limit = 10) {
    return request({
      url: `/content-tips/creator/${creatorId}`,
      method: 'get',
      params: { limit }
    })
  },

  /**
   * 获取内容的总打赏金额
   */
  getTotalTipsByContent(contentType, contentId) {
    return request({
      url: `/content-tips/content/${contentType}/${contentId}/total`,
      method: 'get'
    })
  },

  /**
   * 获取创作者的总打赏金额
   */
  getTotalTipsByCreator(creatorId) {
    return request({
      url: `/content-tips/creator/${creatorId}/total`,
      method: 'get'
    })
  },

  /**
   * 获取用户的打赏统计
   */
  getUserTipStats(userId) {
    return request({
      url: `/content-tips/user/${userId}/stats`,
      method: 'get'
    })
  }
}
