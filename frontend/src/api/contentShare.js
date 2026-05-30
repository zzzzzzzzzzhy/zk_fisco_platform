import request from './request'

export function getContentShareUploadUrl(params) {
  return request({
    url: '/content-shares/presigned-url',
    method: 'get',
    params
  })
}

export function uploadContentShareFile({ mediaType, file }) {
  const formData = new FormData()
  formData.append('mediaType', mediaType)
  formData.append('file', file)
  return request({
    url: '/content-shares/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 10 * 60 * 1000
  })
}

export function createContentShare(data) {
  return request({
    url: '/content-shares',
    method: 'post',
    data
  })
}

export function submitContentShareConsent(id, data) {
  return request({
    url: `/content-shares/${id}/consent`,
    method: 'post',
    data
  })
}

export function fetchContentShares(params) {
  return request({
    url: '/content-shares',
    method: 'get',
    params
  })
}

// New function for public content shares with admin-like filtering
export function fetchAllContentShares(params) {
  return request({
    url: '/content-shares',
    method: 'get',
    params: {
      ...params,
      includeAll: true // Flag to show all content (if backend supports)
    }
  })
}

export function getContentShareById(id) {
  return request({
    url: `/content-shares/${id}`,
    method: 'get'
  })
}

export function deleteContentShare(id) {
  return request({
    url: `/content-shares/${id}`,
    method: 'delete'
  })
}

export function adminListContentShares(params) {
  return request({
    url: '/content-shares/admin/list',
    method: 'get',
    params
  })
}

export function toggleContentVisibility(id, visibility) {
  return request({
    url: `/content-shares/${id}/visibility`,
    method: 'put',
    params: { visibility }
  })
}

export function reviewContentShare(id, status, reason) {
  return request({
    url: `/content-shares/${id}/review`,
    method: 'put',
    data: { status, reason }
  })
}

// Polygon 存证相关接口
export function getPolygonSignData(id) {
  return request({
    url: `/content-shares/${id}/polygon-sign-data`,
    method: 'get'
  })
}

export function submitPolygonProof(id, txHash) {
  return request({
    url: `/content-shares/${id}/polygon-proof`,
    method: 'post',
    data: { txHash }
  })
}

// 内容举报
export function reportContentShare(id, data) {
  return request({
    url: `/content-shares/${id}/report`,
    method: 'post',
    data
  })
}
