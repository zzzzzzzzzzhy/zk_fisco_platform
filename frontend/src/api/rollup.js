import request from './request'

export function fetchRollupBatches(params) {
  return request({
    url: '/rollup/batches',
    method: 'get',
    params
  })
}
