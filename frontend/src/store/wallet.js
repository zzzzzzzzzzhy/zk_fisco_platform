import { reactive } from 'vue'
import web3Service from '@/utils/web3'

// 全局钱包状态
const walletState = reactive({
  isConnected: false,
  isConnecting: false,
  address: '',
  network: '',
  balance: '0',
  tokenBalance: '0',
  mtkBalance: '0',
  consecutiveDays: 0,
  totalRewarded: '0', // 累计获得的所有奖励（包含签到+评论+发帖等）
  error: null,
  lastUpdated: null
})

// 全局钱包管理器
const walletManager = {
  state: walletState,

  // 连接钱包
  async connectWallet() {
    if (walletState.isConnecting) {
      console.log('钱包连接中，请稍候...')
      return { success: false, error: '正在连接中，请稍候' }
    }

    try {
      walletState.isConnecting = true
      walletState.error = null

      const result = await web3Service.connectMetaMask()

      if (result.success) {
        // 同步钱包状态
        await this.syncWalletState()

        // 保存连接状态到本地存储
        this.saveWalletState()

        console.log('钱包连接成功:', walletState.address)
        return result
      } else {
        walletState.error = result.error
        return result
      }
    } catch (error) {
      console.error('钱包连接失败:', error)
      walletState.error = error.message
      return { success: false, error: error.message, type: 'unexpected_error' }
    } finally {
      walletState.isConnecting = false
    }
  },

  // 断开钱包
  disconnect() {
    web3Service.disconnect()
    this.resetWalletState()
    this.saveWalletState() // 也要保存断开状态
    console.log('钱包已断开连接')
  },

  // 同步钱包状态
  async syncWalletState() {
    try {
      console.log('🔄 同步钱包状态...')

      const web3State = web3Service.getState()
      const serviceConnected = web3Service.isWalletConnected()

      console.log('📊 Web3 服务状态:', {
        isConnected: web3State.isConnected,
        address: web3State.address,
        serviceConnected: serviceConnected
      })

      // 使用 Web3 服务的实际连接状态
      walletState.isConnected = serviceConnected && !!web3State.address
      walletState.address = web3State.address || ''
      walletState.balance = web3State.balance || '0'

      // 异步获取实际网络信息
      if (walletState.isConnected) {
        try {
          const fullState = await web3Service.getFullState()
          walletState.network = fullState.network || ''

          // 尝试获取余额
          try {
            const balance = await web3Service.getBalance()
            walletState.balance = balance || '0'
          } catch (balanceError) {
            console.warn('⚠️ 获取余额失败:', balanceError)
            walletState.balance = '0'
          }

          // 立即获取奖励信息
          try {
            const rewardInfo = await web3Service.getUserRewardInfo()
            walletState.consecutiveDays = rewardInfo.consecutiveDays || 0
            walletState.totalRewarded = rewardInfo.totalRewarded || '0'
            console.log('🎁 奖励信息获取成功:', {
              consecutiveDays: walletState.consecutiveDays,
              totalRewarded: walletState.totalRewarded
            })
          } catch (rewardError) {
            console.warn('⚠️ 获取奖励信息失败:', rewardError)
            walletState.consecutiveDays = 0
            walletState.totalRewarded = '0'
          }

          console.log('✅ 钱包状态同步完成:', {
            isConnected: walletState.isConnected,
            address: walletState.address,
            network: walletState.network,
            balance: walletState.balance
          })
        } catch (error) {
          console.error('获取网络信息失败:', error)
          walletState.network = web3State.network || ''
        }
      } else {
        walletState.network = ''
        console.log('❌ 钱包未连接')
      }

      walletState.lastUpdated = new Date().toISOString()
    } catch (error) {
      console.error('❌ 同步钱包状态失败:', error)
      walletState.error = error.message
      walletState.lastUpdated = new Date().toISOString()
    }
  },

  // 重置钱包状态
  resetWalletState() {
    walletState.isConnected = false
    walletState.isConnecting = false
    walletState.address = ''
    walletState.network = ''
    walletState.balance = '0'
    walletState.tokenBalance = '0'
    walletState.mtkBalance = '0'
    walletState.consecutiveDays = 0
    walletState.totalRewarded = '0'
    walletState.error = null
    walletState.lastUpdated = new Date().toISOString()

    // 清除持久化存储
    localStorage.removeItem('wallet_connected')
    localStorage.removeItem('wallet_address')
  },

  // 保存钱包连接状态到本地存储
  saveWalletState() {
    try {
      if (walletState.isConnected && walletState.address) {
        localStorage.setItem('wallet_connected', 'true')
        localStorage.setItem('wallet_address', walletState.address)
      } else {
        localStorage.removeItem('wallet_connected')
        localStorage.removeItem('wallet_address')
      }
    } catch (error) {
      console.warn('保存钱包状态失败:', error)
    }
  },

  // 从本地存储加载钱包状态
  loadPersistedWalletState() {
    try {
      const isConnected = localStorage.getItem('wallet_connected') === 'true'
      const address = localStorage.getItem('wallet_address')

      if (isConnected && address) {
        // 设置临时状态，稍后通过MetaMask验证
        walletState.address = address
        return true
      }
    } catch (error) {
      console.warn('加载持久化钱包状态失败:', error)
    }
    return false
  },

  // 自动恢复钱包连接
  async autoRestoreConnection() {
    if (!window.ethereum || walletState.isConnecting || walletState.isConnected) {
      return walletState.isConnected
    }

    try {
      console.log('🔄 开始自动恢复钱包连接...')

      // 检查是否有保存的连接状态
      const hasPersistedState = this.loadPersistedWalletState()
      if (!hasPersistedState) {
        console.log('❌ 没有找到持久化的连接状态')
        return false
      }

      // 检查MetaMask当前连接的账户
      let accounts
      try {
        accounts = await window.ethereum.request({ method: 'eth_accounts' })
      } catch (error) {
        console.error('❌ 获取账户失败:', error)
        this.resetWalletState()
        this.saveWalletState()
        return false
      }

      if (accounts.length === 0) {
        // MetaMask没有连接账户，清除持久化状态
        console.log('❌ MetaMask没有连接账户')
        this.resetWalletState()
        this.saveWalletState()
        return false
      }

      const currentAccount = accounts[0].toLowerCase()

      // 如果没有保存的地址或者地址不匹配，使用当前地址
      if (!walletState.address || walletState.address.toLowerCase() !== currentAccount) {
        console.log('📝 更新钱包地址:', currentAccount)
        walletState.address = currentAccount
      }

      // 尝试初始化 web3 服务
      try {
        console.log('🔧 初始化 Web3 服务...')
        const result = await web3Service.connectMetaMask()

        if (result && result.success) {
          // 同步钱包状态
          await this.syncWalletState()

          // 确保连接状态正确设置
          walletState.isConnected = true
          this.saveWalletState()

          console.log('✅ 自动恢复钱包连接成功')
          return true
        } else {
          console.warn('⚠️ Web3 服务连接失败，但设置基础状态')
          // 即使web3初始化失败，也设置基础状态
          walletState.address = currentAccount
          walletState.isConnected = true
          this.saveWalletState()
          return true
        }
      } catch (web3Error) {
        console.error('❌ Web3服务初始化失败:', web3Error)
        // 设置基础状态，让用户可以手动重试
        walletState.address = currentAccount
        walletState.isConnected = false  // 明确设置为false
        walletState.error = web3Error.message
        this.saveWalletState()
        return false
      }
    } catch (error) {
      console.error('❌ 自动恢复连接失败:', error)
      this.resetWalletState()
      this.saveWalletState()
      return false
    }
  },

  // 刷新余额
  async refreshBalances() {
    if (!walletState.isConnected || !walletState.address) {
      return
    }

    try {
      // 刷新原生余额
      const balance = await web3Service.getBalance()
      walletState.balance = balance || '0'

      // 刷新WEE余额
      const mtkBalance = await web3Service.getWEEBalance()
      walletState.mtkBalance = mtkBalance || '0'

      // 刷新连续签到天数
      const consecutiveDays = await web3Service.getConsecutiveDays()
      walletState.consecutiveDays = consecutiveDays || 0

      walletState.lastUpdated = new Date().toISOString()
    } catch (error) {
      // 合约不可用（如未启动本地链）时静默处理，显示默认值
      walletState.mtkBalance = walletState.mtkBalance || '0'
      walletState.balance = walletState.balance || '0'
    }
  },

  // 检查今日是否可以签到
  async canCheckinToday() {
    if (!walletState.isConnected) {
      return false
    }

    try {
      return await web3Service.hasDailyCheckinToday()
    } catch (error) {
      console.error('检查签到状态失败:', error)
      return false
    }
  },

  // 初始化钱包状态监听
  initWalletListeners() {
    // 防止重复初始化监听器
    if (this.listenersInitialized) {
      return
    }
    this.listenersInitialized = true

    // 监听账户变化
    web3Service.onAccountChanged((address) => {
      console.log('账户变化:', address)
      this.syncWalletState()
    })

    // 监听网络变化
    web3Service.onNetworkChanged(async (chainId) => {
      console.log('网络变化:', chainId)
      await this.syncWalletState()
    })

    // 监听余额变化
    web3Service.onBalanceUpdated((balance) => {
      console.log('余额变化:', balance)
      walletState.balance = balance || '0'
    })

    // 监听代币余额变化
    web3Service.onBalanceUpdated(async (balance, consecutiveDays) => {
      console.log('代币余额变化:', balance, '连续天数:', consecutiveDays)
      walletState.tokenBalance = balance || '0'
      walletState.consecutiveDays = consecutiveDays || 0

      // 同时获取累计奖励信息
      try {
        const rewardInfo = await web3Service.getUserRewardInfo()
        walletState.totalRewarded = rewardInfo.totalRewarded || '0'
        console.log('累计奖励已更新:', walletState.totalRewarded)
      } catch (error) {
        console.warn('获取累计奖励信息失败:', error)
      }
    })

    // 定期刷新状态 - 防止重复创建定时器
    if (!this.refreshInterval) {
      this.refreshInterval = setInterval(() => {
        if (walletState.isConnected) {
          this.refreshBalances()
        }
      }, 30000) // 每30秒刷新一次
    }
  }
}

// 导出全局钱包状态和管理器
export {
  walletState,
  walletManager
}

// 全局状态同步事件
const stateSyncEvent = new EventTarget()

// 状态同步工具
export const walletSync = {
  // 订阅状态变化
  subscribe(callback) {
    stateSyncEvent.addEventListener('state-changed', callback)
    return () => {
      stateSyncEvent.removeEventListener('state-changed', callback)
    }
  },

  // 触发状态同步事件
  async emitChange(type = 'full', data = {}) {
    const event = new CustomEvent('state-changed', {
      detail: { type, data, timestamp: Date.now() }
    })
    stateSyncEvent.dispatchEvent(event)

    // 短暂延迟后再次触发，确保所有组件都能收到
    setTimeout(() => {
      stateSyncEvent.dispatchEvent(event)
    }, 100)
  }
}

// 增强钱包管理器 - 直接修改原有的 walletManager，而不是创建新对象
const originalConnectWallet = walletManager.connectWallet
const originalDisconnect = walletManager.disconnect
const originalSyncWalletState = walletManager.syncWalletState

// 增强方法
walletManager.connectWallet = async function() {
  const result = await originalConnectWallet.call(this)
  await walletSync.emitChange('connect', { result })
  return result
}

walletManager.disconnect = function() {
  originalDisconnect.call(this)
  walletSync.emitChange('disconnect')
}

walletManager.syncWalletState = async function() {
  await originalSyncWalletState.call(this)
  await walletSync.emitChange('sync', { state: walletState })
}

// 添加强制全局同步方法
walletManager.forceGlobalSync = async function() {
  console.log('🌐 执行全局状态同步...')
  try {
    await this.syncWalletState()
    await walletSync.emitChange('force-sync', { state: walletState })
  } catch (error) {
    console.error('❌ 全局状态同步失败:', error)
  }
}

// 自动初始化监听器和恢复连接
if (typeof window !== 'undefined') {
  walletManager.initWalletListeners()

  // 延迟执行自动恢复，确保MetaMask完全加载
  setTimeout(async () => {
    try {
      await walletManager.autoRestoreConnection()
      // 恢复完成后触发全局同步
      setTimeout(() => {
        walletSync.emitChange('auto-restore', { state: walletState })
      }, 500)
    } catch (error) {
      console.log('自动恢复连接失败，等待手动连接:', error.message)
    }
  }, 1000)

  // 监听 MetaMask 账户变化
  if (window.ethereum) {
    window.ethereum.on('accountsChanged', async (accounts) => {
      console.log('👥 MetaMask 账户变化:', accounts)
      await walletManager.forceGlobalSync()
    })

    window.ethereum.on('chainChanged', async (chainId) => {
      console.log('🌐 MetaMask 网络变化:', chainId)
      await walletManager.forceGlobalSync()
    })
  }

  // 暴露全局工具到 window
  window.walletSync = walletSync
  window.enhancedWalletManager = walletManager  // 直接引用增强后的 walletManager
}