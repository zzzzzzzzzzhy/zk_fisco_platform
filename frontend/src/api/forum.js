import request from './request'

/**
 * 获取帖子列表
 */
export function getForumPosts(params) {
  return request({
    url: '/forum/posts',
    method: 'get',
    params
  })
}

/**
 * 创建帖子
 */
export function createForumPost(data) {
  return request({
    url: '/forum/posts',
    method: 'post',
    data
  })
}

/**
 * 获取帖子详情
 */
export function getForumPostDetail(id) {
  return request({
    url: `/forum/posts/${id}`,
    method: 'get'
  })
}

/**
 * 获取帖子评论
 */
export function getForumComments(postId, params) {
  return request({
    url: `/forum/posts/${postId}/comments`,
    method: 'get',
    params
  })
}

/**
 * 创建评论
 */
export function createForumComment(postId, data) {
  return request({
    url: `/forum/posts/${postId}/comments`,
    method: 'post',
    data
  })
}

export function submitCommentConsent(postId, commentId, data) {
  return request({
    url: `/forum/posts/${postId}/comments/${commentId}/consent`,
    method: 'post',
    data
  })
}

/**
 * 置顶帖子
 */
export function pinForumPost(id, pinned = true) {
  return request({
    url: `/forum/posts/${id}/pin`,
    method: 'post',
    params: { pinned }
  })
}

/**
 * 删除帖子
 */
export function deleteForumPost(id) {
  return request({
    url: `/forum/posts/${id}`,
    method: 'delete'
  })
}

/**
 * 根据内容分享ID获取关联帖子
 */
export function getPostsByContentShare(contentShareId) {
  return request({
    url: `/forum/posts/content-share/${contentShareId}`,
    method: 'get'
  })
}

/**
 * 创建与内容分享关联的帖子
 */
export function createContentSharePost(data) {
  return request({
    url: '/forum/posts/content-share',
    method: 'post',
    data
  })
}

/**
 * 删除评论
 */
export function deleteForumComment(postId, commentId) {
  return request({
    url: `/forum/posts/${postId}/comments/${commentId}`,
    method: 'delete'
  })
}

/**
 * 获取代币奖励历史（真实记录）
 */
export function getTokenRewardHistory(params) {
  return request({
    url: '/forum/token/reward-history',
    method: 'get',
    params
  })
}
