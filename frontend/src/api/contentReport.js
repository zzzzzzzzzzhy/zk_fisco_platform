import request from './request'

export function fetchContentReports(params) {
  return request({
    url: '/content-reports',
    method: 'get',
    params
  })
}

export function handleContentReport(id, data) {
  return request({
    url: `/content-reports/${id}/handle`,
    method: 'put',
    data
  })
}


