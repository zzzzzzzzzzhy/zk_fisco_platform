// Web3 调试工具
import { walletState, walletManager } from '@/store/wallet'
import web3Service from './web3'

export class Web3Debugger {
  // 生成完整的调试报告
  static async generateDebugReport() {
    console.log('🔍 生成 Web3 调试报告...')

    const report = {
      timestamp: new Date().toISOString(),
      environment: {
        userAgent: navigator.userAgent,
        hasMetaMask: !!window.ethereum,
        metaMaskVersion: window.ethereum?.isMetaMask ? 'MetaMask' : 'Other',
      },
      walletState: {
        isConnected: walletState.isConnected,
        isConnecting: walletState.isConnecting,
        address: walletState.address,
        network: walletState.network,
        balance: walletState.balance,
        error: walletState.error,
        lastUpdated: walletState.lastUpdated
      },
      web3Service: {
        isConnected: web3Service.isConnected,
        hasProvider: !!web3Service.provider,
        hasSigner: !!web3Service.signer,
        currentAddress: web3Service.currentAddress,
        isWalletConnected: web3Service.isWalletConnected()
      },
      metaMaskAccounts: null,
      networkInfo: null,
      recommendations: []
    }

    try {
      // 获取 MetaMask 账户信息
      if (window.ethereum) {
        const accounts = await window.ethereum.request({ method: 'eth_accounts' })
        report.metaMaskAccounts = {
          count: accounts.length,
          accounts: accounts,
          firstAccount: accounts[0] || null
        }

        // 获取网络信息
        const chainId = await window.ethereum.request({ method: 'eth_chainId' })
        report.networkInfo = {
          chainId: chainId,
          chainIdDecimal: parseInt(chainId, 16),
          expectedChainId: '0x13882', // 80002 in hex (Polygon Amoy)
          isCorrectNetwork: chainId === '0x13882'
        }
      }
    } catch (error) {
      report.error = error.message
    }

    // 生成建议
    report.recommendations = this.generateRecommendations(report)

    return report
  }

  // 根据状态生成建议
  static generateRecommendations(report) {
    const recommendations = []

    if (!report.environment.hasMetaMask) {
      recommendations.push({
        level: 'error',
        message: 'MetaMask 未安装',
        action: '请安装 MetaMask 浏览器扩展'
      })
    }

    if (report.metaMaskAccounts && report.metaMaskAccounts.count === 0) {
      recommendations.push({
        level: 'warning',
        message: 'MetaMask 未连接账户',
        action: '请解锁 MetaMask 并连接账户'
      })
    }

    if (report.networkInfo && !report.networkInfo.isCorrectNetwork) {
      recommendations.push({
        level: 'warning',
        message: '网络配置不正确',
        action: '请切换到 Polygon Amoy 测试网 (Chain ID: 80002)'
      })
    }

    if (report.walletState.address && !report.walletState.isConnected) {
      recommendations.push({
        level: 'error',
        message: '地址存在但连接状态异常',
        action: '这是状态同步问题，尝试断开重连'
      })
    }

    if (report.web3Service.currentAddress !== report.walletState.address) {
      recommendations.push({
        level: 'error',
        message: 'Web3 服务状态与全局状态不同步',
        action: '状态同步问题，需要重新连接'
      })
    }

    if (!report.web3Service.hasProvider || !report.web3Service.hasSigner) {
      recommendations.push({
        level: 'error',
        message: 'Web3 服务未正确初始化',
        action: '需要重新初始化 Web3 服务'
      })
    }

    if (recommendations.length === 0) {
      recommendations.push({
        level: 'success',
        message: '所有检查都通过了',
        action: '钱包连接正常'
      })
    }

    return recommendations
  }

  // 自动修复常见问题
  static async autoFixIssues() {
    console.log('🔧 尝试自动修复连接问题...')

    const report = await this.generateDebugReport()
    const fixes = []

    try {
      // 修复地址存在但连接状态异常的问题
      if (report.walletState.address && !report.walletState.isConnected) {
        console.log('🔧 修复连接状态问题...')
        await walletManager.syncWalletState()
        fixes.push('已同步钱包状态')
      }

      // 修复 Web3 服务状态不同步的问题
      if (report.web3Service.currentAddress !== report.walletState.address) {
        console.log('🔧 修复 Web3 服务同步问题...')

        if (report.metaMaskAccounts && report.metaMaskAccounts.count > 0) {
          const result = await web3Service.connectMetaMask()
          if (result.success) {
            await walletManager.syncWalletState()
            walletManager.saveWalletState()
            fixes.push('已重新初始化 Web3 服务')
          }
        }
      }

      // 尝试切换到正确网络
      if (report.networkInfo && !report.networkInfo.isCorrectNetwork) {
        console.log('🔧 尝试切换网络...')
        try {
          await web3Service.switchToCorrectNetwork()
          fixes.push('已切换到正确网络')
        } catch (error) {
          console.warn('网络切换失败:', error)
          fixes.push('网络切换失败，需要手动切换')
        }
      }

      console.log('✅ 自动修复完成:', fixes)
      return {
        success: true,
        fixes: fixes,
        newReport: await this.generateDebugReport()
      }
    } catch (error) {
      console.error('❌ 自动修复失败:', error)
      return {
        success: false,
        error: error.message,
        fixes: fixes
      }
    }
  }

  // 在控制台打印漂亮的报告
  static async printDebugReport() {
    const report = await this.generateDebugReport()

    console.group('📊 Web3 调试报告')
    console.log('🕒 时间:', report.timestamp)
    console.log('🌍 环境:', report.environment)
    console.log('💼 钱包状态:', report.walletState)
    console.log('🔧 Web3 服务:', report.web3Service)
    console.log('👥 MetaMask 账户:', report.metaMaskAccounts)
    console.log('🌐 网络信息:', report.networkInfo)

    console.group('💡 建议')
    report.recommendations.forEach((rec, index) => {
      const icon = rec.level === 'error' ? '❌' : rec.level === 'warning' ? '⚠️' : '✅'
      console.log(`${index + 1}. ${icon} ${rec.message}`)
      console.log(`   解决方案: ${rec.action}`)
    })
    console.groupEnd()

    console.groupEnd()

    return report
  }

  // 一键调试和修复
  static async debugAndFix() {
    console.log('🚀 开始一键调试和修复...')

    // 打印当前状态
    await this.printDebugReport()

    // 尝试自动修复
    const fixResult = await this.autoFixIssues()

    if (fixResult.success) {
      console.log('✅ 自动修复成功!')
      console.log('🔧 修复项目:', fixResult.fixes)

      // 打印修复后的状态
      console.log('📊 修复后的状态:')
      await this.printDebugReport()
    } else {
      console.error('❌ 自动修复失败:', fixResult.error)
      console.log('🔧 部分修复项目:', fixResult.fixes)
    }

    return fixResult
  }
}

// 在开发环境下自动暴露到全局
if (process.env.NODE_ENV === 'development') {
  window.Web3Debugger = Web3Debugger
  console.log('🔧 Web3Debugger 已暴露到全局，可在控制台使用:')
  console.log('  - Web3Debugger.printDebugReport()  // 打印调试报告')
  console.log('  - Web3Debugger.autoFixIssues()    // 自动修复')
  console.log('  - Web3Debugger.debugAndFix()      // 一键调试和修复')
}

export default Web3Debugger