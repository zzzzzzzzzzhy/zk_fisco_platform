<template>
  <div class="metamask-connect">
    <!-- 未连接状态 -->
    <div v-if="!isConnected" class="connect-wallet">
      <el-button
        type="primary"
        icon="el-icon-connection"
        @click="connectWallet"
        :loading="connecting"
        size="large"
        class="connect-btn"
      >
        {{ connecting ? '连接中...' : '连接 MetaMask 钱包' }}
      </el-button>

      <div class="wallet-info">
        <el-alert
          title="需要MetaMask钱包"
          type="info"
          :closable="false"
          show-icon
        >
          <template slot="default">
            <p>请安装并连接MetaMask钱包以使用Web3功能</p>
            <div class="install-links">
              <el-link
                href="https://metamask.io/download/"
                target="_blank"
                type="primary"
              >
                <i class="el-icon-download"></i>
                下载MetaMask
              </el-link>
            </div>
          </template>
        </el-alert>
      </div>
    </div>

    <!-- 已连接状态 -->
    <div v-else class="wallet-connected">
      <div class="wallet-header">
        <div class="wallet-address">
          <el-avatar :size="32" :src="getAddressAvatar(walletAddress)">
            <i class="el-icon-wallet"></i>
          </el-avatar>
          <div class="address-info">
            <div class="address">{{ formatAddress(walletAddress) }}</div>
            <div class="network">{{ currentNetwork }}</div>
          </div>
        </div>
        <el-button
          type="danger"
          size="mini"
          @click="disconnectWallet"
          plain
        >
          断开连接
        </el-button>
      </div>

      <!-- 代币余额信息 -->
      <div class="token-balance" v-if="showBalanceCard">
        <el-card class="balance-card" shadow="hover">
          <template #header>
            <div class="balance-header">
              <i class="el-icon-coin"></i>
              <span>代币余额</span>
              <el-button
                type="text"
                size="mini"
                @click="refreshBalance"
                :loading="refreshing"
              >
                <i class="el-icon-refresh"></i>
              </el-button>
            </div>
          </template>
          <div class="balance-content">
            <div class="balance-item">
              <div class="balance-label">WEE 余额</div>
              <div class="balance-value">{{ tokenBalance }} WEE</div>
            </div>
            <div class="balance-item">
              <div class="balance-label">连续签到</div>
              <div class="balance-value consecutive">
                <span v-if="consecutiveDays > 0">{{ consecutiveDays }} 天</span>
                <span v-else>暂未签到</span>
              </div>
            </div>
          </div>
        </el-card>
      </div>

      <!-- 网络状态指示器 -->
      <div class="network-status">
        <el-tooltip :content="`当前网络: ${currentNetwork}`" placement="top">
          <div class="status-indicator" :class="networkClass">
            <i class="el-icon-connection"></i>
            {{ isCorrectNetwork ? expectedNetworkName : '网络错误' }}
          </div>
        </el-tooltip>
      </div>
    </div>

    <!-- 开发环境调试工具 -->
    <div v-if="isDevelopment && !isConnected && walletAddress" class="debug-tools" style="margin-bottom: 15px;">
      <el-alert
        title="检测到状态异常"
        type="warning"
        :closable="false"
        show-icon
      >
        <template slot="default">
          <p>地址已显示但连接状态异常</p>
          <el-button type="text" @click="handleDebugAndFix" style="padding: 0;">
            🔧 一键修复
          </el-button>
        </template>
      </el-alert>
    </div>

    <!-- 错误提示 -->
    <el-dialog
      title="连接错误"
      :visible.sync="showErrorDialog"
      width="400px"
    >
      <div class="error-content">
        <i class="el-icon-warning error-icon"></i>
        <p>{{ errorMessage }}</p>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button @click="showErrorDialog = false">关闭</el-button>
        <el-button type="primary" @click="connectWallet">重试</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { walletState, walletManager, walletSync } from '@/store/wallet'
import web3Service from '@/utils/web3'
import walletStateManager from '@/utils/walletStateManager'

export default {
  name: 'MetaMaskConnect',
  props: {
    showBalanceCard: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return {
      refreshing: false,
      showErrorDialog: false,
      errorMessage: '',
      connecting: false,
      syncUnsubscribe: null // 用于取消订阅
    }
  },
  computed: {
    // 使用全局钱包状态
    isConnected() {
      return walletState.isConnected
    },
    isConnecting() {
      return walletState.isConnecting
    },
    walletAddress() {
      return walletState.address
    },
    currentNetwork() {
      return walletState.network
    },
    expectedNetworkName() {
      return web3Service.config?.chainName || ''
    },
    tokenBalance() {
      return walletState.mtkBalance || '0'
    },
    consecutiveDays() {
      return walletState.consecutiveDays || 0
    },
    isCorrectNetwork() {
      return this.currentNetwork === this.expectedNetworkName
    },
    networkClass() {
      return {
        'network-correct': this.isCorrectNetwork,
        'network-wrong': !this.isCorrectNetwork
      }
    },
    isDevelopment() {
      return process.env.NODE_ENV === 'development'
    }
  },
  async mounted() {
    console.log('🏗️ MetaMaskConnect 组件挂载...')

    // 订阅统一状态管理器
    this.stateUnsubscribe = walletStateManager.subscribe(async (event) => {
      console.log('📡 MetaMaskConnect 收到统一状态事件:', event.type)

      // 强制重新渲染
      this.$forceUpdate()

      // 如果状态同步完成，检查是否需要修复
      if (event.type === 'synced') {
        await this.checkAndFixState()
      }
    })

    // 订阅原有状态同步事件（保持兼容性）
    this.syncUnsubscribe = walletSync.subscribe(async (event) => {
      console.log('📡 收到状态同步事件:', event.detail.type)

      // 强制重新渲染，确保状态同步
      this.$forceUpdate()

      // 如果是强制同步事件，执行本地状态修复
      if (event.detail.type === 'force-sync') {
        await this.handleForceSync()
      }
    })

    // 等待状态管理器初始化
    setTimeout(async () => {
      try {
        console.log('🔄 等待状态管理器初始化...')
        await walletStateManager.init()

        // 再次检查状态
        await this.checkAndFixState()
      } catch (error) {
        console.error('❌ 状态管理器初始化失败:', error)
      }
    }, 3000) // 增加等待时间，确保状态管理器先初始化
  },

  beforeDestroy() {
    // 组件销毁时取消订阅
    if (this.syncUnsubscribe) {
      this.syncUnsubscribe()
      console.log('🔌 已取消状态同步订阅')
    }
    if (this.stateUnsubscribe) {
      this.stateUnsubscribe()
      console.log('🔌 已取消统一状态订阅')
    }
  },
  methods: {
    // 检查和修复状态
    async checkAndFixState() {
      try {
        // 如果状态异常，使用统一状态管理器修复
        if (!walletState.isConnected && walletState.address) {
          console.log('🔧 MetaMaskConnect 检测到状态异常，使用统一管理器修复...')
          await walletStateManager.forceSyncAll()
        }
      } catch (error) {
        console.error('❌ 状态检查修复失败:', error)
      }
    },
    // 强制同步状态的修复方法
    async forceSyncState() {
      console.log('🔧 执行强制状态同步...')

      try {
        // 检查 MetaMask 当前状态
        if (!window.ethereum) {
          console.log('❌ MetaMask 不可用')
          return
        }

        const accounts = await window.ethereum.request({ method: 'eth_accounts' })
        console.log('📋 MetaMask 账户:', accounts)

        if (accounts.length > 0) {
          const currentAccount = accounts[0]

          // 如果地址不匹配，更新地址
          if (walletState.address.toLowerCase() !== currentAccount.toLowerCase()) {
            console.log('📝 更新钱包地址:', currentAccount)
            walletState.address = currentAccount
          }

          // 尝试重新初始化 Web3 服务
          try {
            console.log('🔄 重新初始化 Web3 服务...')
            const result = await web3Service.connectMetaMask()

            if (result.success) {
              console.log('✅ Web3 服务重新初始化成功')
              await walletManager.syncWalletState()
              walletManager.saveWalletState()
            } else {
              console.warn('⚠️ Web3 服务重新初始化失败，但设置基本状态')
              // 设置基本状态
              walletState.address = currentAccount
              walletState.isConnected = true
              walletManager.saveWalletState()
            }
          } catch (web3Error) {
            console.error('❌ Web3 服务初始化失败:', web3Error)
            // 设置基本状态，让用户知道有问题但可以看到地址
            walletState.address = currentAccount
            walletState.isConnected = false
            walletState.error = web3Error.message
          }
        } else {
          console.log('❌ MetaMask 没有连接账户')
          walletManager.resetWalletState()
        }
      } catch (error) {
        console.error('❌ 强制同步失败:', error)
      }
    },

    async connectWallet() {
      this.connecting = true
      try {
        const result = await walletManager.connectWallet()

        if (result && result.success) {
          await this.refreshBalance()
          this.$message.success('MetaMask连接成功!')

          // 触发连接成功事件
          this.$emit('connected', {
            address: walletState.address
          })
        } else {
          const errorType = result?.type || 'unknown'
          const errorMessage = result?.error || walletState.error || '连接失败'

          // 根据错误类型显示不同的消息
          if (errorType === 'user_rejection') {
            this.$message.warning('您已取消连接')
          } else if (errorType === 'network_error') {
            const expectedNetwork = this.expectedNetworkName || '目标网络'
            this.$message.error(`网络切换失败，请手动切换到 ${expectedNetwork}`)
          } else if (errorType === 'account_error') {
            this.$message.error('账户错误，请检查 MetaMask 状态')
          } else {
            this.errorMessage = errorMessage
            this.showErrorDialog = true
          }
        }
      } catch (error) {
        console.error('连接钱包异常:', error)
        this.errorMessage = `连接异常: ${error.message || error}`
        this.showErrorDialog = true
      } finally {
        this.connecting = false
      }
    },

    disconnectWallet() {
      walletManager.disconnect()

      this.$message.info('已断开MetaMask连接')

      // 触发断开连接事件
      this.$emit('disconnected')
    },

    async refreshBalance() {
      if (!this.isConnected) return

      this.refreshing = true
      try {
        // 使用全局钱包管理器刷新余额
        await walletManager.refreshBalances()

        // 触发余额更新事件
        this.$emit('balance-updated', {
          balance: this.tokenBalance,
          consecutiveDays: this.consecutiveDays
        })
      } catch (error) {
        console.error('刷新余额失败:', error)
        this.$message.error('刷新余额失败: ' + error.message)
      } finally {
        this.refreshing = false
      }
    },

    
    async checkConnectionStatus() {
      try {
        if (!window.ethereum) {
          return
        }

        const accounts = await window.ethereum.request({ method: 'eth_accounts' })
        if (accounts.length > 0) {
          // 同步全局钱包状态
          await walletManager.syncWalletState()
          await this.refreshBalance()
        }
      } catch (error) {
        console.error('检查连接状态失败:', error)
      }
    },

    formatAddress(address) {
      if (!address) return ''
      return `${address.slice(0, 6)}...${address.slice(-4)}`
    },

    getAddressAvatar(address) {
      // address 未准备好时使用默认头像，避免 slice 报错，同时避免依赖外部网络资源
      const palette = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399']
      const safeAddress = address || ''
      const lastChar = safeAddress.slice(-1) || '0'
      const index = parseInt(lastChar, 16) % palette.length
      const bgColor = palette[index]
      const text = (safeAddress ? safeAddress.slice(2, 4) : 'MM').toUpperCase()
      const svg = `
        <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64">
          <rect width="64" height="64" rx="12" fill="${bgColor}"/>
          <text x="50%" y="50%" alignment-baseline="central" text-anchor="middle" font-size="26" font-family="Arial" fill="#fff">${text}</text>
        </svg>`
      return `data:image/svg+xml;base64,${window.btoa(svg)}`
    },

    // 处理强制同步事件
    async handleForceSync() {
      console.log('🔄 处理强制同步事件...')

      try {
        // 简单的状态检查和修复
        if (!walletState.isConnected && walletState.address) {
          console.log('🔧 强制同步检测到状态异常，进行修复...')
          await this.forceSyncState()
        }
      } catch (error) {
        console.error('❌ 强制同步处理失败:', error)
      }
    },

    // 处理调试和修复
    async handleDebugAndFix() {
      try {
        this.$message.info('正在调试和修复连接问题...')

        // 在开发环境下使用调试工具
        if (this.isDevelopment && window.Web3Debugger) {
          const result = await window.Web3Debugger.debugAndFix()
          if (result.success) {
            this.$message.success(`修复成功: ${result.fixes.join(', ')}`)
            // 刷新状态
            await this.forceSyncState()
            await this.refreshBalance()

            // 触发全局状态同步
            await walletSync.emitChange('debug-fix', { result })
          } else {
            this.$message.error(`修复失败: ${result.error}`)
          }
        } else {
          // 使用基础修复方法
          await this.forceSyncState()
          this.$message.success('已执行基础修复')

          // 触发全局状态同步
          await walletSync.emitChange('basic-fix', {})
        }
      } catch (error) {
        console.error('调试修复失败:', error)
        this.$message.error('修复失败，请重试')
      }
    }
  }
}
</script>

<style scoped>
.metamask-connect {
  width: 100%;
}

.connect-wallet {
  text-align: center;
  padding: 20px;
}

.connect-btn {
  margin-bottom: 20px;
  font-size: 16px;
  padding: 12px 24px;
}

.wallet-info {
  max-width: 400px;
  margin: 0 auto;
}

.install-links {
  margin-top: 10px;
}

.wallet-connected {
  width: 100%;
}

.wallet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 20px;
}

.wallet-address {
  display: flex;
  align-items: center;
  gap: 12px;
}

.address-info {
  text-align: left;
}

.address {
  font-family: 'Courier New', monospace;
  font-weight: bold;
  color: #303133;
}

.network {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.token-balance {
  margin-bottom: 15px;
}

.balance-card {
  border: 1px solid #dcdfe6;
}

.balance-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.balance-content {
  display: flex;
  justify-content: space-around;
  padding: 10px 0;
}

.balance-item {
  text-align: center;
}

.balance-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 5px;
}

.balance-value {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.balance-value.consecutive {
  color: #67C23A;
}

.network-status {
  text-align: center;
}

.status-indicator {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 12px;
}

.network-correct {
  background: #f0f9ff;
  color: #67C23A;
  border: 1px solid #67C23A;
}

.network-wrong {
  background: #fef0f0;
  color: #F56C6C;
  border: 1px solid #F56C6C;
}

.error-content {
  text-align: center;
  padding: 20px 0;
}

.error-icon {
  font-size: 48px;
  color: #F56C6C;
  margin-bottom: 15px;
}

.error-content p {
  color: #606266;
  line-height: 1.6;
}

.dialog-footer {
  text-align: right;
}
</style>
