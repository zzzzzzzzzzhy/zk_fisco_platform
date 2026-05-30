// Web3 错误处理工具
export class Web3ErrorHandler {
  static getErrorMessage(error) {
    if (!error) return '未知错误'

    // MetaMask 常见错误码
    switch (error.code) {
      case 4001:
        return '用户拒绝了请求'
      case 4100:
        return '未授权的账户，请先连接钱包'
      case 4900:
        return '未解锁的账户，请先解锁 MetaMask'
      case 4901:
        return '用户拒绝了链切换请求'
      case 4902:
        return '该链未被添加到 MetaMask'
      case -32603:
        return '内部错误，请重试'
      case -32000:
        return '交易失败，请检查 gas 费用'
      case -32001:
        return '方法不支持'
      case -32002:
        return '请求已处理，请查看 MetaMask'
      case -32003:
        return '交易已拒绝'
      case -32004:
        return '方法参数无效'
      case -32005:
        return 'JSON-RPC 版本不支持'
      default:
        if (error.message) {
          return error.message
        }
        return `连接失败: ${error.toString()}`
    }
  }

  static isUserRejection(error) {
    return error.code === 4001 || error.message?.includes('User rejected')
  }

  static isNetworkError(error) {
    return error.code === 4901 || error.code === 4902 || error.message?.includes('network')
  }

  static isAccountError(error) {
    return error.code === 4100 || error.code === 4900 || error.message?.includes('account')
  }

  static handleConnectionError(error) {
    const message = this.getErrorMessage(error)

    if (this.isUserRejection(error)) {
      console.log('用户取消了连接:', message)
      return {
        success: false,
        error: message,
        isUserCancelled: true,
        type: 'user_rejection'
      }
    }

    if (this.isNetworkError(error)) {
      console.error('网络相关错误:', message)
      return {
        success: false,
        error: message,
        type: 'network_error'
      }
    }

    if (this.isAccountError(error)) {
      console.error('账户相关错误:', message)
      return {
        success: false,
        error: message,
        type: 'account_error'
      }
    }

    console.error('连接错误:', message)
    return {
      success: false,
      error: message,
      type: 'unknown_error'
    }
  }

  static handleTransactionError(error) {
    const message = this.getErrorMessage(error)

    if (this.isUserRejection(error)) {
      return {
        success: false,
        error: '用户取消了交易',
        type: 'user_rejection'
      }
    }

    // 检查是否是 gas 费用不足
    if (message.includes('insufficient funds') || message.includes('gas')) {
      return {
        success: false,
        error: '账户余额不足以支付 gas 费用',
        type: 'insufficient_funds'
      }
    }

    // 检查是否是 nonce 错误
    if (message.includes('nonce')) {
      return {
        success: false,
        error: '交易序号错误，请重试',
        type: 'nonce_error'
      }
    }

    return {
      success: false,
      error: message,
      type: 'transaction_error'
    }
  }
}

export default Web3ErrorHandler