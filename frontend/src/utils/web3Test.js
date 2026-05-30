// Web3 连接测试工具
import web3Service from './web3'
import Web3ErrorHandler from './web3ErrorHandler'

export class Web3Tester {
  // 测试基础连接
  static async testBasicConnection() {
    console.log('🧪 开始测试基础 Web3 连接...')

    try {
      // 检查 MetaMask 是否安装
      if (!window.ethereum) {
        return {
          success: false,
          error: 'MetaMask 未安装',
          suggestion: '请先安装 MetaMask 浏览器扩展'
        }
      }

      // 检查是否已解锁
      const accounts = await window.ethereum.request({ method: 'eth_accounts' })
      if (accounts.length === 0) {
        return {
          success: false,
          error: 'MetaMask 未解锁或未连接账户',
          suggestion: '请解锁 MetaMask 并连接账户'
        }
      }

      // 检查网络
      const chainId = await window.ethereum.request({ method: 'eth_chainId' })
      const expectedChainId = '0x13882' // 80002 in hex

      if (chainId !== expectedChainId) {
        return {
          success: false,
          error: `网络不正确，当前: ${chainId}，期望: ${expectedChainId}`,
          suggestion: '请切换到 Polygon Amoy 测试网 (Chain ID: 80002)'
        }
      }

      return {
        success: true,
        message: '基础连接检查通过',
        data: {
          accounts,
          chainId: parseInt(chainId, 16)
        }
      }
    } catch (error) {
      const errorInfo = Web3ErrorHandler.handleConnectionError(error)
      return {
        success: false,
        error: errorInfo.error,
        type: errorInfo.type,
        suggestion: this.getSuggestion(errorInfo.type)
      }
    }
  }

  // 测试完整连接流程
  static async testFullConnection() {
    console.log('🧪 开始测试完整 Web3 连接流程...')

    try {
      const result = await web3Service.connectMetaMask()

      if (result.success) {
        // 验证连接状态
        const isConnected = web3Service.isWalletConnected()
        const address = web3Service.currentAddress

        // 测试获取余额
        try {
          const balance = await web3Service.getBalance()
          return {
            success: true,
            message: '完整连接测试成功',
            data: {
              isConnected,
              address,
              balance
            }
          }
        } catch (balanceError) {
          return {
            success: true,
            message: '连接成功，但获取余额失败',
            data: {
              isConnected,
              address,
              balanceError: balanceError.message
            }
          }
        }
      } else {
        return result
      }
    } catch (error) {
      return {
        success: false,
        error: error.message,
        type: 'unexpected_error'
      }
    }
  }

  // 测试合约交互
  static async testContractInteraction() {
    console.log('🧪 开始测试合约交互...')

    try {
      const isConnected = web3Service.isWalletConnected()
      if (!isConnected) {
        return {
          success: false,
          error: '钱包未连接',
          suggestion: '请先连接钱包'
        }
      }

      // 测试获取 MTK 余额
      try {
        const mtkBalance = await web3Service.getMTKBalance()
        console.log('✅ MTK 余额查询成功:', mtkBalance)

        return {
          success: true,
          message: '合约交互测试成功',
          data: {
            mtkBalance
          }
        }
      } catch (contractError) {
        console.error('❌ 合约交互失败:', contractError)
        return {
          success: false,
          error: `合约交互失败: ${contractError.message}`,
          suggestion: '请检查合约地址和网络配置'
        }
      }
    } catch (error) {
      return {
        success: false,
        error: error.message
      }
    }
  }

  // 获取错误建议
  static getSuggestion(errorType) {
    switch (errorType) {
      case 'user_rejection':
        return '您取消了操作，如果需要使用钱包功能，请重新尝试并授权'
      case 'network_error':
        return '请确保您的网络连接正常，并尝试切换到正确的网络'
      case 'account_error':
        return '请检查 MetaMask 是否已解锁并选择了正确的账户'
      default:
        return '请检查 MetaMask 是否正确安装和配置'
    }
  }

  // 运行所有测试
  static async runAllTests() {
    console.log('🚀 开始运行所有 Web3 测试...')

    const results = {
      basicConnection: await this.testBasicConnection(),
      fullConnection: null,
      contractInteraction: null
    }

    // 如果基础连接成功，继续其他测试
    if (results.basicConnection.success) {
      results.fullConnection = await this.testFullConnection()

      if (results.fullConnection?.success) {
        results.contractInteraction = await this.testContractInteraction()
      }
    }

    console.log('📊 测试结果:', results)
    return results
  }
}

export default Web3Tester