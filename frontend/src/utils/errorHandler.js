/**
 * 智能错误处理器
 * 将区块链/后端的技术错误转换为用户友好的提示
 */

import { Message } from 'element-ui'

/**
 * 错误类型和友好提示映射
 */
const ERROR_MESSAGES = {
  // ==========================================
  // 用户操作类错误
  // ==========================================
  'ACTION_REJECTED': {
    title: '操作已取消',
    message: '您取消了本次操作',
    type: 'info',
    showTip: false
  },
  
  'user rejected transaction': {
    title: '交易已取消',
    message: '您在 MetaMask 中取消了交易',
    type: 'info',
    showTip: false
  },
  
  'User denied transaction signature': {
    title: '签名已取消',
    message: '您取消了签名请求',
    type: 'info',
    showTip: false
  },
  
  // ==========================================
  // 余额/Gas 类错误
  // ==========================================
  'insufficient funds': {
    title: '余额不足',
    message: '您的钱包余额不足以支付 Gas 费用',
    type: 'error',
    solution: '请充值 POL 代币后重试'
  },
  
  'INSUFFICIENT_FUNDS': {
    title: '余额不足',
    message: '账户余额不足',
    type: 'error',
    solution: '请检查钱包余额'
  },
  
  'gas required exceeds allowance': {
    title: 'Gas 不足',
    message: 'Gas 费用超出限制',
    type: 'error',
    solution: '请增加 Gas Limit 或检查合约状态'
  },
  
  'out of gas': {
    title: 'Gas 耗尽',
    message: '交易执行时 Gas 耗尽',
    type: 'error',
    solution: '请增加 Gas Limit 后重试'
  },
  
  // ==========================================
  // 网络类错误
  // ==========================================
  'NETWORK_ERROR': {
    title: '网络错误',
    message: '无法连接到区块链网络',
    type: 'error',
    solution: '请检查网络连接或切换 RPC 节点'
  },
  
  'TIMEOUT': {
    title: '请求超时',
    message: '区块链请求超时',
    type: 'error',
    solution: '网络拥堵，请稍后重试'
  },
  
  'Failed to fetch': {
    title: '网络连接失败',
    message: '无法连接到服务器',
    type: 'error',
    solution: '请检查网络连接'
  },
  
  // ==========================================
  // 合约执行类错误
  // ==========================================
  'execution reverted': {
    title: '交易失败',
    message: '智能合约执行被回退',
    type: 'error',
    solution: '请检查交易参数是否正确'
  },
  
  'revert': {
    title: '合约执行失败',
    message: '智能合约拒绝了此交易',
    type: 'error',
    solution: '请检查操作条件是否满足'
  },
  
  'already cast': {
    title: '重复投票',
    message: '您已经投过票了',
    type: 'warning',
    showTip: false
  },
  
  'proposal not active': {
    title: '提案未激活',
    message: '该提案还未开始投票或已结束',
    type: 'warning',
    showTip: false
  },
  
  'insufficient balance': {
    title: '代币余额不足',
    message: 'WEE 代币余额不足',
    type: 'error',
    solution: '请先获取足够的 WEE 代币'
  },
  
  'already claimed': {
    title: '已经领取过',
    message: '您已经领取过此奖励',
    type: 'warning',
    showTip: false
  },
  
  'not authorized': {
    title: '权限不足',
    message: '您没有权限执行此操作',
    type: 'error',
    solution: '请使用有权限的账户'
  },
  
  // ==========================================
  // 认证类错误
  // ==========================================
  'No wallet detected': {
    title: '未检测到钱包',
    message: '请先安装 MetaMask 钱包',
    type: 'error',
    solution: '访问 metamask.io 安装钱包插件'
  },
  
  'Please connect wallet': {
    title: '钱包未连接',
    message: '请先连接 MetaMask 钱包',
    type: 'warning',
    solution: '点击右上角连接钱包'
  },
  
  'Wrong network': {
    title: '网络错误',
    message: '请切换到 Polygon 主网',
    type: 'error',
    solution: '在 MetaMask 中切换到 Polygon 网络'
  },
  
  // ==========================================
  // 后端API错误
  // ==========================================
  'Unauthorized': {
    title: '未授权',
    message: '登录已过期，请重新登录',
    type: 'error',
    solution: '点击右上角重新登录'
  },
  
  'Forbidden': {
    title: '禁止访问',
    message: '您没有权限访问此资源',
    type: 'error',
    showTip: false
  },
  
  'Not Found': {
    title: '资源不存在',
    message: '请求的资源不存在',
    type: 'error',
    showTip: false
  },
  
  'Internal Server Error': {
    title: '服务器错误',
    message: '服务器出现了问题',
    type: 'error',
    solution: '请稍后重试或联系管理员'
  }
}

/**
 * 智能错误处理器
 * @param {Error|string} error - 错误对象或错误消息
 * @param {Object} options - 配置选项
 * @returns {Object} 处理后的错误信息
 */
export function handleError(error, options = {}) {
  const {
    showMessage = true,  // 是否显示提示
    duration = 5000,     // 提示持续时间
    customMessage = null // 自定义消息
  } = options
  
  // 如果有自定义消息，直接使用
  if (customMessage) {
    if (showMessage) {
      Message.error(customMessage)
    }
    return { title: '错误', message: customMessage, type: 'error' }
  }
  
  // 获取错误消息
  let errorMessage = ''
  let errorCode = ''
  
  if (typeof error === 'string') {
    errorMessage = error
  } else if (error.message) {
    errorMessage = error.message
    errorCode = error.code
  } else if (error.reason) {
    errorMessage = error.reason
  } else {
    errorMessage = '未知错误'
  }
  
  console.error('原始错误:', error)
  
  // 匹配友好提示
  let friendlyError = matchFriendlyError(errorMessage, errorCode)
  
  // 显示提示
  if (showMessage) {
    showFriendlyMessage(friendlyError, duration)
  }
  
  return friendlyError
}

/**
 * 匹配友好的错误提示
 */
function matchFriendlyError(message, code) {
  // 将消息转为小写便于匹配
  const lowerMessage = (message || '').toLowerCase()
  
  // 优先匹配错误代码
  if (code) {
    // 检查数字代码
    if (code === 4001 || code === 'ACTION_REJECTED') {
      return ERROR_MESSAGES['ACTION_REJECTED']
    }
    // 检查字符串代码
    if (ERROR_MESSAGES[code]) {
      return ERROR_MESSAGES[code]
    }
  }
  
  // 按优先级匹配错误消息中的关键词
  const priorityKeys = [
    'user rejected',
    'User denied',
    'ACTION_REJECTED',
    'insufficient funds',
    'out of gas',
    'already cast',
    'already claimed',
    'proposal not active',
    'not authorized',
    'execution reverted',
    'No wallet detected',
    'Wrong network',
    'NETWORK_ERROR',
    'TIMEOUT'
  ]
  
  // 优先匹配高优先级关键词
  for (const key of priorityKeys) {
    if (lowerMessage.includes(key.toLowerCase())) {
      return ERROR_MESSAGES[key] || ERROR_MESSAGES[key.toLowerCase()]
    }
  }
  
  // 匹配其他关键词
  for (const [key, value] of Object.entries(ERROR_MESSAGES)) {
    if (lowerMessage.includes(key.toLowerCase())) {
      return value
    }
  }
  
  // 没有匹配到，返回简洁的通用错误
  return {
    title: '操作失败',
    message: '抱歉，操作未能完成',
    type: 'error',
    solution: '请稍后重试或联系客服',
    showTip: true
  }
}

/**
 * 显示友好的错误提示
 */
function showFriendlyMessage(errorInfo, duration) {
  const { title, message, type, solution, showTip = true } = errorInfo
  
  // 清理消息内容，移除技术细节
  const cleanMessage = cleanErrorMessage(message)
  const cleanTitle = cleanErrorMessage(title)
  
  // 构建完整的提示内容
  let fullMessage = `<strong>${cleanTitle}</strong><br/>${cleanMessage}`
  
  if (solution && showTip) {
    fullMessage += `<br/><span style="color: #909399; font-size: 13px;">💡 ${solution}</span>`
  }
  
  // 根据类型显示不同的提示
  const messageType = type === 'warning' ? 'warning' : 
                     type === 'info' ? 'info' : 'error'
  
  Message({
    dangerouslyUseHTMLString: true,
    message: fullMessage,
    type: messageType,
    duration: duration,
    showClose: true,
    customClass: 'friendly-error-message'
  })
}

/**
 * 清理错误消息，移除技术细节
 */
function cleanErrorMessage(message) {
  if (!message || typeof message !== 'string') {
    return '操作失败'
  }
  
  // 移除十六进制数据（0x 开头的长字符串）
  message = message.replace(/0x[a-fA-F0-9]{40,}/g, '[数据已隐藏]')
  
  // 移除 JSON 对象
  message = message.replace(/\{[^}]{50,}\}/g, '')
  
  // 移除括号中的技术细节
  message = message.replace(/\([^)]{50,}\)/g, '')
  
  // 移除多余的空格和换行
  message = message.replace(/\s+/g, ' ').trim()
  
  // 限制长度（最多 200 个字符）
  if (message.length > 200) {
    message = message.substring(0, 200) + '...'
  }
  
  return message
}

/**
 * 区块链交易错误处理（专用）
 */
export function handleTransactionError(error, customMessage) {
  console.error('交易错误:', error)
  
  // 提取错误信息
  let errorMsg = ''
  
  if (error.code === 'ACTION_REJECTED' || error.code === 4001) {
    // 用户取消交易
    return handleError('ACTION_REJECTED')
  }
  
  if (error.reason) {
    errorMsg = error.reason
  } else if (error.message) {
    errorMsg = error.message
  } else if (error.data && error.data.message) {
    errorMsg = error.data.message
  }
  
  // 处理并显示
  return handleError(errorMsg, { customMessage })
}

/**
 * API 请求错误处理（专用）
 */
export function handleApiError(error) {
  console.error('API 错误:', error)
  
  if (error.response) {
    const status = error.response.status
    const data = error.response.data
    
    // 根据 HTTP 状态码处理
    if (status === 401) {
      return handleError('Unauthorized')
    } else if (status === 403) {
      return handleError('Forbidden')
    } else if (status === 404) {
      return handleError('Not Found')
    } else if (status === 500) {
      return handleError('Internal Server Error')
    } else if (data && data.message) {
      return handleError(data.message)
    }
  } else if (error.request) {
    // 请求发出但没有收到响应
    return handleError('NETWORK_ERROR')
  }
  
  return handleError(error.message || '请求失败')
}

/**
 * 合约调用错误处理（专用）
 */
export function handleContractError(error) {
  console.error('合约错误:', error)
  
  // 尝试解析合约错误原因
  let reason = ''
  
  if (error.error && error.error.data && error.error.data.message) {
    reason = error.error.data.message
  } else if (error.data && error.data.message) {
    reason = error.data.message
  } else if (error.reason) {
    reason = error.reason
  } else if (error.message) {
    reason = error.message
  }
  
  // 提取 revert 原因
  const revertMatch = reason.match(/execution reverted: (.+)/)
  if (revertMatch) {
    reason = revertMatch[1]
  }
  
  return handleError(reason)
}

// 导出默认错误处理器
export default {
  handleError,
  handleTransactionError,
  handleApiError,
  handleContractError
}

