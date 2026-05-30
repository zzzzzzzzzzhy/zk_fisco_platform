import request from './request'

/**
 * 内容置顶相关API
 */
export default {
  /**
   * 购买内容置顶
   */
  purchasePin(data) {
    return request({
      url: '/content-pins/purchase',
      method: 'post',
      data
    })
  },

  /**
   * 获取当前生效的置顶记录
   */
  getActivePins() {
    return request({
      url: '/content-pins/active',
      method: 'get'
    })
  },

  /**
   * 获取内容的置顶记录
   */
  getPinByContent(contentType, contentId) {
    return request({
      url: `/content-pins/content/${contentType}/${contentId}`,
      method: 'get'
    })
  },

  /**
   * 获取用户购买的置顶记录
   */
  getPinByUserId(userId) {
    return request({
      url: `/content-pins/user/${userId}`,
      method: 'get'
    })
  },

  /**
   * 检查内容是否置顶
   */
  isContentPinned(contentType, contentId) {
    return request({
      url: `/content-pins/check/${contentType}/${contentId}`,
      method: 'get'
    })
  }
}
