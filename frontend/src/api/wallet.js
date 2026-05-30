import request from './request'

/**
 * 获取钱包余额
 */
export function getWalletBalance(params) {
  return request({
    url: '/wallet/balance',
    method: 'get',
    params
  })
}

/**
 * 获取交易流水
 */
export function getTransactions(params) {
  return request({
    url: '/wallet/transactions',
    method: 'get',
    params
  })
}
