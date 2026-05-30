// 统一的钱包状态管理器
import { walletState, walletManager, walletSync } from '@/store/wallet'
import web3Service from '@/utils/web3'

class WalletStateManager {
  constructor() {
    this.isInitialized = false
    this.syncInProgress = false
    this.subscribers = []
    this.initPromise = null
  }

  // 初始化状态管理器
  async init() {
    if (this.isInitialized) {
      return this.initPromise
    }

    if (this.initPromise) {
      return this.initPromise
    }

    this.initPromise = this._doInit()
    return this.initPromise
  }

  async _doInit() {
    console.log('🚀 WalletStateManager 初始化...')

    try {
      // 等待 MetaMask 完全加载
      await this._waitForMetaMask()

      // 执行自动恢复连接
      await walletManager.autoRestoreConnection()

      // 强制同步状态
      await this.forceSyncAll()

      this.isInitialized = true
      console.log('✅ WalletStateManager 初始化完成')

      // 通知所有订阅者
      this._notifySubscribers('initialized', { state: walletState })

      return true
    } catch (error) {
      console.error('❌ WalletStateManager 初始化失败:', error)
      return false
    }
  }

  // 等待 MetaMask 加载
  async _waitForMetaMask() {
    const maxWait = 10000 // 最多等待10秒
    const startTime = Date.now()

    while (Date.now() - startTime < maxWait) {
      if (window.ethereum) {
        console.log('✅ MetaMask 已加载')
        return true
      }
      await new Promise(resolve => setTimeout(resolve, 100))
    }

    throw new Error('MetaMask 加载超时')
  }

  // 强制同步所有状态
  async forceSyncAll() {
    if (this.syncInProgress) {
      console.log('⏳ 同步正在进行中，跳过...')
      return
    }

    this.syncInProgress = true
    console.log('🔄 开始强制同步所有钱包状态...')

    try {
      // 1. 同步基础钱包状态
      await walletManager.syncWalletState()

      // 2. 检查 MetaMask 实际状态
      if (window.ethereum) {
        const accounts = await window.ethereum.request({ method: 'eth_accounts' })
        console.log('📋 MetaMask 当前账户:', accounts)

        if (accounts.length > 0 && !walletState.isConnected) {
          console.log('🔧 检测到状态不一致，进行修复...')

          // 尝试重新初始化 Web3 服务
          try {
            const result = await web3Service.connectMetaMask()
            if (result.success) {
              await walletManager.syncWalletState()
              console.log('✅ Web3 服务重新初始化成功')
            }
          } catch (error) {
            console.warn('⚠️ Web3 服务重新初始化失败，设置基础状态')
            // 设置基础状态
            walletState.address = accounts[0]
            walletState.isConnected = false
            walletState.error = error.message
          }
        }
      }

      // 3. 如果有地址但未连接，尝试修复
      if (walletState.address && !walletState.isConnected) {
        console.log('🔧 尝试修复连接状态...')
        await this._fixConnectionState()
      }

      // 4. 触发全局同步事件
      await walletSync.emitChange('force-sync-all', {
        state: walletState,
        timestamp: Date.now()
      })

      // 5. 通知所有订阅者
      this._notifySubscribers('synced', { state: walletState })

      console.log('✅ 强制同步完成')
      return true

    } catch (error) {
      console.error('❌ 强制同步失败:', error)
      return false
    } finally {
      this.syncInProgress = false
    }
  }

  // 修复连接状态
  async _fixConnectionState() {
    try {
      // 方法1: 重新同步状态
      await walletManager.syncWalletState()

      // 方法2: 检查 Web3 服务状态
      const web3Connected = web3Service.isWalletConnected()
      console.log('🔍 Web3 服务连接状态:', web3Connected)

      if (web3Connected && !walletState.isConnected) {
        console.log('🔧 Web3 服务已连接但全局状态未连接，修复状态...')
        walletState.isConnected = true
        walletState.error = null
      }

      // 方法3: 触发状态检查
      if (window.enhancedWalletManager) {
        await window.enhancedWalletManager.forceGlobalSync()
      }

    } catch (error) {
      console.error('❌ 修复连接状态失败:', error)
    }
  }

  // 订阅状态变化
  subscribe(callback) {
    const subscriber = {
      id: Date.now() + Math.random(),
      callback
    }

    this.subscribers.push(subscriber)

    console.log(`📡 新增订阅者: ${subscriber.id}, 总订阅者: ${this.subscribers.length}`)

    // 返回取消订阅函数
    return () => {
      const index = this.subscribers.findIndex(s => s.id === subscriber.id)
      if (index > -1) {
        this.subscribers.splice(index, 1)
        console.log(`🔌 取消订阅: ${subscriber.id}, 剩余订阅者: ${this.subscribers.length}`)
      }
    }
  }

  // 通知所有订阅者
  _notifySubscribers(type, data) {
    this.subscribers.forEach(subscriber => {
      try {
        subscriber.callback({ type, data })
      } catch (error) {
        console.error(`❌ 订阅者 ${subscriber.id} 回调失败:`, error)
      }
    })
  }

  // 获取当前状态快照
  getSnapshot() {
    return {
      isConnected: walletState.isConnected,
      address: walletState.address,
      network: walletState.network,
      balance: walletState.balance,
      mtkBalance: walletState.mtkBalance,
      error: walletState.error,
      lastUpdated: walletState.lastUpdated,
      web3ServiceConnected: web3Service.isWalletConnected(),
      hasMetaMask: !!window.ethereum
    }
  }

  // 诊断方法
  async diagnose() {
    console.log('🔍 开始钱包状态诊断...')

    const snapshot = this.getSnapshot()
    const issues = []

    // 检查 MetaMask
    if (!snapshot.hasMetaMask) {
      issues.push('MetaMask 未安装')
    }

    // 检查地址和连接状态一致性
    if (snapshot.address && !snapshot.isConnected) {
      issues.push('有地址但显示未连接')
    }

    // 检查 Web3 服务状态
    if (snapshot.web3ServiceConnected !== snapshot.isConnected) {
      issues.push('Web3 服务状态与全局状态不一致')
    }

    // 检查是否有错误
    if (snapshot.error) {
      issues.push(`存在错误: ${snapshot.error}`)
    }

    const diagnosis = {
      snapshot,
      issues,
      healthy: issues.length === 0,
      timestamp: new Date().toISOString()
    }

    console.log('📊 诊断结果:', diagnosis)
    return diagnosis
  }
}

// 创建全局实例
const walletStateManager = new WalletStateManager()

// 自动初始化
if (typeof window !== 'undefined') {
  // 暴露到全局
  window.walletStateManager = walletStateManager

  // 页面加载完成后自动初始化
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
      walletStateManager.init()
    })
  } else {
    // 如果已经加载完成，延迟一点时间初始化
    setTimeout(() => {
      walletStateManager.init()
    }, 500)
  }

  // 监听页面可见性变化，页面重新显示时同步状态
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && walletStateManager.isInitialized) {
      setTimeout(() => {
        walletStateManager.forceSyncAll()
      }, 1000)
    }
  })
}

export default walletStateManager