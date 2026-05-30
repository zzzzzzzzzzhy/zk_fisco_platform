import request from './request'

/**
 * 获取预签名上传URL
 */
export function getPresignedUploadUrl(params) {
  return request({
    url: '/submissions/presigned-url',
    method: 'get',
    params
  })
}

/**
 * 创建提交记录
 */
export function createSubmission(data) {
  return request({
    url: '/submissions',
    method: 'post',
    data
  })
}

/**
 * 获取提交详情
 */
export function getSubmissionById(id) {
  return request({
    url: `/submissions/${id}`,
    method: 'get'
  })
}

/**
 * 获取我的提交记录
 */
export function getMySubmissions(params) {
  return request({
    url: '/submissions/my',
    method: 'get',
    params
  })
}
