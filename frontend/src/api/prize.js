import request from './request'

// ==================== 奖金池管理 ====================

/**
 * 创建奖金池
 */
export function createPrizePool(data) {
  return request({
    url: '/prize-pools',
    method: 'post',
    data
  })
}

/**
 * 获取奖金池详情
 */
export function getPrizePoolById(id) {
  return request({
    url: `/prize-pools/${id}`,
    method: 'get'
  })
}

/**
 * 获取竞赛的奖金池
 */
export function getCompetitionPrizePool(competitionId) {
  return request({
    url: `/prize-pools/competition/${competitionId}`,
    method: 'get'
  })
}

/**
 * 锁定奖金池
 */
export function lockPrizePool(id) {
  return request({
    url: `/prize-pools/${id}/lock`,
    method: 'post'
  })
}

/**
 * 解锁奖金池
 */
export function unlockPrizePool(id) {
  return request({
    url: `/prize-pools/${id}/unlock`,
    method: 'post'
  })
}

// ==================== 奖金分配 ====================

/**
 * 创建奖金分配
 */
export function createPrizeAllocation(data) {
  return request({
    url: '/prize-allocations',
    method: 'post',
    data
  })
}

/**
 * 批量创建奖金分配
 */
export function batchCreateAllocations(data) {
  return request({
    url: '/prize-allocations/batch',
    method: 'post',
    data
  })
}

/**
 * 获取奖金分配详情
 */
export function getPrizeAllocationById(id) {
  return request({
    url: `/prize-allocations/${id}`,
    method: 'get'
  })
}

/**
 * 获取用户的奖金分配
 */
export function getUserPrizeAllocations(userId, params) {
  return request({
    url: `/prize-allocations/user/${userId}`,
    method: 'get',
    params
  })
}

/**
 * 获取奖金池的分配列表
 */
export function getPoolAllocations(poolId, params) {
  return request({
    url: `/prize-allocations/pool/${poolId}`,
    method: 'get',
    params
  })
}

// ==================== 风控管理 ====================

/**
 * 风控检查
 */
export function checkRisk(data) {
  return request({
    url: '/risk-control/check',
    method: 'post',
    data
  })
}

/**
 * 人工审核
 */
export function reviewRisk(id, data) {
  return request({
    url: `/risk-control/${id}/review`,
    method: 'post',
    data
  })
}

/**
 * 获取风控记录
 */
export function getRiskRecordByAllocationId(allocationId) {
  return request({
    url: `/risk-control/allocation/${allocationId}`,
    method: 'get'
  })
}

/**
 * 获取风控统计
 */
export function getRiskStatistics(params) {
  return request({
    url: '/risk-control/statistics',
    method: 'get',
    params
  })
}

// ==================== 批次发放 ====================

/**
 * 创建发放批次
 */
export function createDisbursementBatch(data) {
  return request({
    url: '/disbursements/batches',
    method: 'post',
    data
  })
}

/**
 * 获取批次详情
 */
export function getDisbursementBatchById(id) {
  return request({
    url: `/disbursements/batches/${id}`,
    method: 'get'
  })
}

/**
 * 执行批量发放
 */
export function executeBatch(id) {
  return request({
    url: `/disbursements/batches/${id}/execute`,
    method: 'post'
  })
}

/**
 * 获取批次列表
 */
export function getDisbursementBatches(params) {
  return request({
    url: '/disbursements/batches',
    method: 'get',
    params
  })
}

// ==================== KYC管理 ====================

/**
 * 提交KYC信息
 */
export function submitKyc(data) {
  return request({
    url: '/kyc',
    method: 'post',
    data
  })
}

/**
 * 获取用户KYC状态
 */
export function getUserKyc(userId) {
  return request({
    url: `/kyc/user/${userId}`,
    method: 'get'
  })
}

/**
 * 审核KYC
 */
export function reviewKyc(id, data) {
  return request({
    url: `/kyc/${id}/review`,
    method: 'put',
    data
  })
}
