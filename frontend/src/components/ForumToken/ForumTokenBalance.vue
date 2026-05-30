<template>
  <div class="forum-token-balance">
    <!-- MetaMask连接组件 -->
    <MetaMaskConnect
      :show-balance-card="false"
      @connected="onWalletConnected"
      @disconnected="onWalletDisconnected"
      @balance-updated="onBalanceUpdated"
    />

    <!-- 滑块验证码组件 -->
    <SliderCaptcha
      ref="captchaComponent"
      @success="onCaptchaSuccess"
      @cancel="onCaptchaCancel"
    />

    <!-- 连接钱包后的代币功能 -->
    <div v-if="walletConnected" class="token-features">
      <el-card class="token-main-card" shadow="hover">
        <div class="token-card-body">
          <div class="token-summary">
            <div class="token-header">
              <div class="token-title">
                <div class="token-icon">
                  <i class="el-icon-coin"></i>
                </div>
                <div class="token-info">
                  <h2>WEE 代币</h2>
                  <p class="contract-info">
                    合约地址: {{ shortContractAddress }}
                  </p>
                </div>
              </div>
              <div class="balance-display">
                <div class="balance-amount">
                  <span class="amount-value">{{ currentBalance }}</span>
                  <span class="amount-label">WEE</span>
                </div>
                <div class="network-indicator" :class="networkClass">
                  <i class="el-icon-connection"></i>
                  {{ currentNetwork }}
                </div>
              </div>
            </div>

            <div class="token-stats">
              <div class="stat-pill">
                <div class="pill-icon">
                  <i class="el-icon-calendar-check"></i>
                </div>
                <div class="pill-details">
                  <p class="pill-label">连续签到</p>
                  <p class="pill-value">{{ consecutiveDays }} 天</p>
                </div>
              </div>
              <div class="stat-pill">
                <div class="pill-icon">
                  <i class="el-icon-gift"></i>
                </div>
                <div class="pill-details">
                  <p class="pill-label">累计奖励</p>
                  <p class="pill-value">{{ totalRewards }} WEE</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-card>

      <!-- 操作按钮组 -->
      <el-card class="actions-card" shadow="hover">
        <template #header>
          <div class="actions-header">
            <i class="el-icon-setting"></i>
            <span>代币操作</span>
          </div>
        </template>

        <div class="actions-grid">
          <el-button
            type="primary"
            class="action-btn checkin-btn"
            @click="performDailyCheckin"
            :disabled="!canCheckin || checkingIn"
            :loading="checkingIn"
            size="large"
          >
            <div class="btn-content">
              <i class="el-icon-date"></i>
              <span>{{ canCheckin ? '每日签到' : '今日已签到' }}</span>
              <div class="reward-hint">+{{ rewardConfig.dailyCheckinReward }} WEE</div>
            </div>
          </el-button>

          <el-button
            type="info"
            class="action-btn guide-btn"
            @click="showUsageDialog = true"
            size="large"
          >
            <div class="btn-content">
              <i class="el-icon-question"></i>
              <span>使用指南</span>
              <div class="reward-hint">了解如何获得WEE</div>
            </div>
          </el-button>

          <el-button
            type="warning"
            class="action-btn"
            @click="showRewardHistory"
            size="large"
          >
            <div class="btn-content">
              <i class="el-icon-notebook-2"></i>
              <span>代币账单</span>
              <div class="reward-hint reward-hint--placeholder"></div>
            </div>
          </el-button>

          <el-button
            type="info"
            class="action-btn"
            @click="showTokenUsage"
            size="large"
          >
            <div class="btn-content">
              <i class="el-icon-shopping-cart-2"></i>
              <span>使用代币</span>
              <div class="reward-hint reward-hint--placeholder"></div>
            </div>
          </el-button>
        </div>
      </el-card>

      <!-- 实时状态卡片 -->
      <el-row :gutter="20" class="status-cards">
        <el-col :span="12">
          <el-card class="status-card" shadow="hover">
            <div class="status-content">
              <div class="status-icon success">
                <i class="el-icon-circle-check"></i>
              </div>
              <div class="status-info">
                <div class="status-title">今日签到</div>
                <div class="status-value">{{ hasCheckedInToday ? '已完成' : '待签到' }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="status-card" shadow="hover">
            <div class="status-content">
              <div class="status-icon info">
                <i class="el-icon-trophy"></i>
              </div>
              <div class="status-info">
                <div class="status-title">签到进度</div>
                <div class="status-value">{{ checkinProgress }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="rollup-card" shadow="hover">
        <div class="rollup-header">
          <div class="rollup-title">
            <i class="el-icon-collection-tag"></i>
            奖励批次上链
          </div>
          <el-button type="text" size="mini" @click="loadRollupBatches">刷新</el-button>
        </div>
        <el-table :data="rollupBatches" size="mini" v-loading="rollupLoading">
          <el-table-column prop="typeLabel" label="类型" width="120" />
          <el-table-column prop="windowLabel" label="批次窗口" />
          <el-table-column prop="statusLabel" label="状态" width="140">
            <template #default="{ row }">
              <el-tag :type="row.statusTag" size="mini">{{ row.statusLabel }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 未连接钱包提示 -->
    <el-card v-else class="connect-prompt" shadow="hover">
      <div class="connect-content">
        <div class="connect-icon">
          <i class="el-icon-connection"></i>
        </div>
        <h3>连接钱包启用代币功能</h3>
        <p>连接MetaMask钱包以查看代币余额、每日签到并获得发帖奖励</p>
      </div>
    </el-card>

    <!-- 交易确认对话框 -->
    <el-dialog
      title="交易确认"
      :visible.sync="showTransactionDialog"
      width="450px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
    >
      <div class="transaction-content">
        <div class="transaction-icon">
          <i class="el-icon-loading" v-if="transactionPending"></i>
          <i class="el-icon-circle-check" v-else-if="transactionSuccess"></i>
          <i class="el-icon-circle-close" v-else></i>
        </div>
        <h3>{{ transactionTitle }}</h3>
        <p>{{ transactionMessage }}</p>
        <div class="transaction-hash" v-if="transactionSuccess">
          <span>已提交签名，等待批次上链后系统自动发放奖励</span>
        </div>
      </div>
      <span slot="footer" class="dialog-footer">
        <el-button
          v-if="!transactionPending && !transactionSuccess"
          @click="showTransactionDialog = false"
        >
          关闭
        </el-button>
        <el-button
          v-if="transactionSuccess"
          type="primary"
          @click="showTransactionDialog = false"
        >
          完成
        </el-button>
      </span>
    </el-dialog>

    <!-- 代币账单对话框 -->
    <el-dialog
      title="WEE 代币账单"
      :visible.sync="showHistoryDialog"
      width="600px"
    >
      <div class="history-content">
        <el-table :data="rewardHistory" stripe>
          <el-table-column prop="date" label="时间" width="160">
            <template #default="{ row }">
              <span>{{ formatDate(row.date) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="type" label="类型" width="120">
            <template #default="{ row }">
              <el-tag :type="getRewardTypeColor(row.type)" size="mini">
                {{ row.type }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="amount" label="变动数量" width="120">
            <template #default="{ row }">
              <span
                class="reward-amount"
                :class="row.amount.startsWith('-') ? 'reward-amount--negative' : 'reward-amount--positive'"
              >
                {{ row.amount.startsWith('-') ? row.amount : '+' + row.amount }} WEE
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'success' ? 'success' : 'warning'" size="mini">
                {{ row.status === 'success' ? '成功' : '处理中' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>

    <!-- 代币使用说明对话框 -->
    <el-dialog
      title="WEE 代币使用指南"
      :visible.sync="showUsageDialog"
      width="700px"
    >
      <div class="usage-content">
        <div class="usage-section">
          <h4><i class="el-icon-coin"></i> 获得方式</h4>
          <div class="usage-list">
            <div class="usage-item">
              <span class="icon">📝</span>
              <span class="text">发布帖子获得 {{ rewardConfig.postReward }} WEE</span>
            </div>
            <div class="usage-item">
              <span class="icon">💬</span>
              <span class="text">发表评论获得 {{ rewardConfig.commentReward }} WEE</span>
            </div>
            <div class="usage-item">
              <span class="icon">📅</span>
              <span class="text">每日签到获得 {{ rewardConfig.dailyCheckinReward }} WEE</span>
            </div>
            <div class="usage-item">
              <span class="icon">🔥</span>
              <span class="text">连续签到额外获得 {{ rewardConfig.consecutiveBonus }} WEE</span>
            </div>
            <div class="usage-item">
              <span class="icon">🖼️</span>
              <span class="text">分享图片奖励 {{ rewardConfig.contentImageReward }} WEE</span>
            </div>
            <div class="usage-item">
              <span class="icon">🎬</span>
              <span class="text">分享视频奖励 {{ rewardConfig.contentVideoReward }} WEE</span>
            </div>
          </div>
        </div>

        <div class="usage-section">
          <h4><i class="el-icon-shopping-cart-2"></i> 使用场景</h4>
          <div class="usage-list">
            <div class="usage-item">
              <span class="icon">📌</span>
              <span class="text">内容置顶 24 小时 · 50 WEE</span>
            </div>
            <div class="usage-item">
              <span class="icon">📚</span>
              <span class="text">购买竞赛资料和数据集</span>
            </div>
            <div class="usage-item">
              <span class="icon">🎯</span>
              <span class="text">享受竞赛报名优惠</span>
            </div>
            <div class="usage-item">
              <span class="icon">🏆</span>
              <span class="text">兑换竞赛纪念品</span>
            </div>
            <div class="usage-item">
              <span class="icon">🎓</span>
              <span class="text">解锁高级课程内容</span>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import MetaMaskConnect from '@/components/Web3/MetaMaskConnect.vue'
import SliderCaptcha from '@/components/SliderCaptcha.vue'
import web3Service from '@/utils/web3'
import { walletState, walletManager, walletSync } from '@/store/wallet'
import walletStateManager from '@/utils/walletStateManager'
import { getTokenRewardHistory } from '@/api/forum'
import { fetchRollupBatches } from '@/api/rollup'

export default {
  name: 'ForumTokenBalance',
  components: {
    MetaMaskConnect,
    SliderCaptcha
  },
  data() {
    return {
      // 验证码相关
      captchaToken: null,
      pendingAction: null, // 'checkin', 'post', 'comment', 'pin'
      transactionPending: false,
      transactionSuccess: false,
      showTransactionDialog: false,
      showHistoryDialog: false,
      showUsageDialog: false,
      transactionTitle: '',
      transactionMessage: '',
      transactionHash: '',
      checkingIn: false,
      claimingReward: false,
      canCheckin: false,
      hasCheckedInToday: false,
      rewardConfig: {
        postReward: '0',
        commentReward: '0',
        dailyCheckinReward: '0',
        featuredPostReward: '0',
        consecutiveBonus: '0',
        contentImageReward: '0',
        contentVideoReward: '0'
      },
      // 奖励历史记录（从后端真实读取）
      rewardHistory: [],
      rollupBatches: [],
      rollupLoading: false,
      syncUnsubscribe: null, // 用于取消订阅
      stateUnsubscribe: null // 用于取消统一状态订阅
    }
  },
  computed: {
    // 使用全局钱包状态
    walletConnected() {
      return walletState.isConnected
    },
    currentBalance() {
      return walletState.mtkBalance || '0'
    },
    consecutiveDays() {
      return walletState.consecutiveDays || 0
    },
    currentNetwork() {
      return walletState.network || ''
    },
    expectedNetworkName() {
      return web3Service.config?.chainName || ''
    },
    shortContractAddress() {
      const address = this.mtkTokenAddress
      if (!address) {
        return '--'
      }
      return `${address.slice(0, 6)}...${address.slice(-4)}`
    },
    shortTransactionHash() {
      if (!this.transactionHash) return ''
      return `${this.transactionHash.slice(0, 6)}...${this.transactionHash.slice(-4)}`
    },
    networkClass() {
      const expectedNetwork = this.expectedNetworkName
      if (!expectedNetwork) {
        return {
          'network-correct': false,
          'network-wrong': true
        }
      }
      return {
        'network-correct': this.currentNetwork === expectedNetwork,
        'network-wrong': this.currentNetwork !== expectedNetwork
      }
    },
    checkinProgress() {
      return `${this.consecutiveDays}/30 天`
    },
    totalRewards() {
      // 从链上获取真实的累计奖励（包含所有类型：签到+评论+发帖等）
      if (this.walletConnected && walletState.totalRewarded !== undefined) {
        return walletState.totalRewarded || '0'
      }
      // 如果链上数据不可用，使用估算值
      const checkinRewards = this.consecutiveDays * parseInt(this.rewardConfig.dailyCheckinReward || '0')
      return checkinRewards.toString()
    },

    mtkTokenAddress() {
      try {
        const config = web3Service.config
        return config.mtkTokenAddress
      } catch (error) {
        console.error('获取WEE地址失败:', error)
        return ''
      }
    },
  },
  mounted() {
    console.log('🏗️ ForumTokenBalance 组件挂载...')

    // 订阅统一状态管理器
    this.stateUnsubscribe = walletStateManager.subscribe(async (event) => {
      console.log('📡 ForumTokenBalance 收到统一状态事件:', event.type)

      // 强制重新渲染
      this.$forceUpdate()

      // 如果连接状态变化，加载初始数据
      if (event.type === 'synced' && this.walletConnected) {
        this.loadInitialData()
      }
    })

    // 订阅原有状态同步事件（保持兼容性）
    this.syncUnsubscribe = walletSync.subscribe(async (event) => {
      console.log('📡 ForumTokenBalance 收到状态同步事件:', event.detail.type)

      // 强制重新渲染，确保状态同步
      this.$forceUpdate()

      // 如果连接状态变化，加载初始数据
      if (event.detail.type === 'connect' && this.walletConnected) {
        this.loadInitialData()
      }
    })

    // 添加奖励更新事件监听
    this.rewardUpdateListener = async (event) => {
      console.log('🎉 收到奖励更新事件:', event.detail)
      await this.updateRewardStatus()
    }
    window.addEventListener('reward-updated', this.rewardUpdateListener)

    // 等待状态管理器初始化
    setTimeout(async () => {
      try {
        console.log('🔄 等待状态管理器初始化...')
        await walletStateManager.init()

        // 检查连接状态，如果已连接则加载数据
        if (this.walletConnected) {
          this.loadInitialData()
        } else {
          console.log('🔧 ForumTokenBalance 钱包未连接，等待用户操作')
        }
      } catch (error) {
        console.error('❌ 状态管理器初始化失败:', error)
      }
    }, 3000) // 确保在 MetaMaskConnect 之后初始化
  },

  beforeDestroy() {
    // 组件销毁时取消订阅
    if (this.syncUnsubscribe) {
      this.syncUnsubscribe()
      console.log('🔌 ForumTokenBalance 已取消状态同步订阅')
    }
    if (this.stateUnsubscribe) {
      this.stateUnsubscribe()
      console.log('🔌 ForumTokenBalance 已取消统一状态订阅')
    }

    // 移除奖励更新事件监听
    if (this.rewardUpdateListener) {
      window.removeEventListener('reward-updated', this.rewardUpdateListener)
      console.log('🔌 ForumTokenBalance 已移除奖励更新监听')
    }
  },
  methods: {
    onWalletConnected({ address }) {
      this.$message.success(`钱包连接成功: ${address.slice(0, 6)}...${address.slice(-4)}`)
      this.loadInitialData()
    },

    onWalletDisconnected() {
      this.resetData()
    },

    onBalanceUpdated() {
      // 余额已经通过全局状态自动更新，这里可以做额外处理
    },

    async loadInitialData() {
      try {
        // 获取奖励配置 - 从合约动态获取
        const config = await web3Service.getRewardConfig()
        if (config) {
          this.rewardConfig = config
        }

        // 检查签到状态 - 直接通过合约检查
        if (web3Service.isConnected) {
          const canCheckin = await web3Service.canCheckinToday()
          this.hasCheckedInToday = !canCheckin
          this.canCheckin = canCheckin
        } else {
          // 钱包未连接时的默认状态
          this.hasCheckedInToday = false
          this.canCheckin = false
        }

        // 刷新余额
        await this.refreshBalance()
        await this.loadRollupBatches()
      } catch (error) {
        console.error('加载初始数据失败:', error)
        this.$message.error('加载数据失败: ' + error.message)
      }
    },

    async refreshBalance() {
      try {
        // 使用全局钱包管理器刷新余额
        await walletManager.refreshBalances()
      } catch (error) {
        console.error('刷新余额失败:', error)
      }
    },

    // 立即更新奖励状态（用于签到后实时更新）
    async updateRewardStatus() {
      try {
        console.log('🔄 立即更新奖励状态...')
        if (web3Service.isConnected) {
          const rewardInfo = await web3Service.getUserRewardInfo()

          // 更新全局钱包状态
          walletState.consecutiveDays = rewardInfo.consecutiveDays || 0
          walletState.totalRewarded = rewardInfo.totalRewarded || '0'

          // 同时更新本地状态以确保同步
          this.consecutiveDays = rewardInfo.consecutiveDays || 0

          console.log('✅ 奖励状态更新完成:', {
            consecutiveDays: rewardInfo.consecutiveDays,
            totalRewarded: rewardInfo.totalRewarded
          })

          // 强制组件重新渲染
          this.$forceUpdate()
        }
      } catch (error) {
        console.error('❌ 更新奖励状态失败:', error)
      }
    },

    async performDailyCheckin() {
      // 显示验证码
      this.pendingAction = 'checkin'
      this.$refs.captchaComponent.show()
    },

    // 验证码成功回调
    async onCaptchaSuccess(token) {
      this.captchaToken = token

      // 根据待处理的操作执行相应的逻辑
      if (this.pendingAction === 'checkin') {
        await this.executeDailyCheckin()
      }
      // 其他操作可以在这里添加
    },

    // 验证码取消回调
    onCaptchaCancel() {
      this.pendingAction = null
      this.$message.info('已取消操作')
    },

    // 实际执行签到逻辑
    async executeDailyCheckin() {
      this.checkingIn = true
      this.showTransactionDialog = true
      this.transactionPending = true
      this.transactionSuccess = false
      this.transactionTitle = '每日签到'
      this.transactionMessage = '正在提交签名，请稍候...'

      try {
        // 调用后端签名 API（传入验证码令牌）
        const result = await web3Service.dailyCheckin(this.captchaToken)

        if (result.success) {
          this.transactionPending = false
          this.transactionSuccess = true
          this.transactionHash = ''
          this.transactionMessage = '签名已提交，等待批次上链后系统自动发放奖励'

          // 更新状态
          this.hasCheckedInToday = true
          this.canCheckin = false
          this.consecutiveDays += 1

          // 等待批次上链后再领取，不在这里强制刷新链上余额

          // 清空验证码令牌
          this.captchaToken = null
          this.pendingAction = null

          // 为保证「WEE 代币账单」与后端数据库完全一致，这里不再本地拼一条记录，
          // 而是等用户打开账单对话框时统一从后端读取真实数据

          this.$message.success('签到签名已提交！')
        } else {
          this.transactionPending = false
          this.transactionMessage = '签到失败: ' + result.error
          this.$message.error('签到失败: ' + result.error)
        }
      } catch (error) {
        this.transactionPending = false
        this.transactionMessage = '签到失败: ' + error.message
        this.$message.error('签到失败: ' + error.message)
      } finally {
        this.checkingIn = false
        // 如果交易失败，2秒后自动关闭对话框
        if (!this.transactionSuccess && !this.transactionPending) {
          setTimeout(() => {
            this.showTransactionDialog = false
          }, 2000)
        }
      }
    },

    async claimPostReward() {
      // 检查是否为管理员功能（已禁用，但保留逻辑）
      this.$message.warning('发帖奖励功能仅限管理员使用，请通过发布内容自动获得奖励')
      return

      /* eslint-disable no-unreachable */
      this.claimingReward = true
      this.showTransactionDialog = true
      this.transactionPending = true
      this.transactionSuccess = false
      this.transactionTitle = '发帖奖励'
      this.transactionMessage = '正在处理发帖奖励，请等待交易确认...'

      try {
        const result = await web3Service.rewardPostCreation()

        if (result.success) {
          this.transactionPending = false
          this.transactionSuccess = true
          this.transactionHash = result.transactionHash
          this.transactionMessage = '发帖奖励发放成功！'

          // 立即更新奖励状态（包括累计奖励）
          await this.updateRewardStatus()

          // 刷新余额
          await this.refreshBalance()

          // 不再本地添加历史记录，保持与后端账单数据完全同步

          this.$message.success('发帖奖励发放成功！')
        } else {
          this.transactionPending = false
          this.transactionMessage = '奖励发放失败: ' + result.error
          this.$message.error('奖励发放失败: ' + result.error)
        }
      } catch (error) {
        this.transactionPending = false
        this.transactionMessage = '奖励发放失败: ' + error.message
        this.$message.error('奖励发放失败: ' + error.message)
      } finally {
        this.claimingReward = false
        // 如果交易失败，2秒后自动关闭对话框
        if (!this.transactionSuccess && !this.transactionPending) {
          setTimeout(() => {
            this.showTransactionDialog = false
          }, 2000)
        }
      }
    },

    async loadRollupBatches() {
      this.rollupLoading = true
      try {
        const { data } = await fetchRollupBatches({ size: 20 })
        const batches = Array.isArray(data) ? data : []
        this.rollupBatches = batches.map(item => {
          const meta = item.metadata || {}
          const windowLabel = this.formatWindow(meta.windowStart, meta.windowEnd)
          const typeLabel = this.mapRollupType(item.bizType)
          const statusLabel = this.mapRollupStatus(item.status)
          const statusTag = this.mapRollupStatusTag(item.status)
          return {
            ...item,
            windowLabel: windowLabel || '待生成',
            typeLabel,
            statusLabel,
            statusTag
          }
        })
      } catch (error) {
        console.error('加载批次信息失败:', error)
        this.$message.error('加载批次信息失败')
      } finally {
        this.rollupLoading = false
      }
    },

    async showRewardHistory() {
      try {
        const { data } = await getTokenRewardHistory({ current: 1, size: 20 })
        const records = (data && data.records) || []
        // 记录一份本地可显示的时间字段：优先后端 createTime，没有就用当前时间兜底
        this.rewardHistory = records.map(item => ({
          ...item,
          date: item.createTime || item.create_time || new Date(),
          type: this.mapRewardType(item.rewardType),
          status: 'success'
        }))
      } catch (error) {
        console.error('加载奖励历史失败:', error)
        this.$message.error('加载奖励历史失败: ' + (error.message || '未知错误'))
      } finally {
        this.showHistoryDialog = true
      }
    },

    showTokenUsage() {
      this.showUsageDialog = true
    },

    resetData() {
      this.currentBalance = '0'
      this.consecutiveDays = 0
      this.canCheckin = false
      this.hasCheckedInToday = false
    },

    getRewardTypeColor(type) {
      const colorMap = {
        '每日签到': 'success',
        '发帖奖励': 'primary',
        '评论奖励': 'info',
        '精华帖子': 'warning',
        '内容分享-图片': 'success',
        '内容分享-视频': 'success'
      }
      return colorMap[type] || 'info'
    },

    // 将后端 rewardType 映射为前端展示文案
    mapRewardType(code) {
      switch (code) {
        case 'DAILY_CHECKIN':
          return '每日签到'
        case 'POST':
          return '发帖奖励'
        case 'COMMENT':
          return '评论奖励'
        case 'FEATURED_POST':
          return '精华帖子'
        case 'CONTENT_IMAGE':
          return '内容分享-图片'
        case 'CONTENT_VIDEO':
          return '内容分享-视频'
        default:
          return code || '其他奖励'
      }
    },

    mapRollupType(bizType) {
      switch (bizType) {
        case 'CONTENT_SHARE_ROLLUP':
          return '内容分享'
        case 'COMMENT_ROLLUP':
          return '评论'
        case 'CHECKIN_ROLLUP':
          return '签到'
        default:
          return bizType || '未知'
      }
    },

    mapRollupStatus(status) {
      if (status === 2) return '已上链，系统自动发放'
      if (status === 3) return '上链失败'
      return '待上链'
    },

    mapRollupStatusTag(status) {
      if (status === 2) return 'success'
      if (status === 3) return 'danger'
      return 'warning'
    },

    formatWindow(start, end) {
      if (!start || !end) return ''
      return `${start} ~ ${end}`
    },

    formatDate(date) {
      if (!date) return ''

      // 大多数情况下后端会给字符串，如 "2025-11-26T13:54:44" 或 "2025-11-26 13:54:44"
      let d
      if (date instanceof Date) {
        d = date
      } else if (typeof date === 'string') {
        const s = date.replace(' ', 'T')
        d = new Date(s)
      } else {
        d = new Date(date)
      }

      if (isNaN(d.getTime())) {
        // 解析失败，直接显示原始值，避免空白
        return String(date)
      }

      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      const hh = String(d.getHours()).padStart(2, '0')
      const mm = String(d.getMinutes()).padStart(2, '0')
      const ss = String(d.getSeconds()).padStart(2, '0')
      return `${m}/${day} ${hh}:${mm}:${ss}`
    },

  }
}
</script>

<style scoped>
.forum-token-balance {
  width: 100%;
}

.token-features {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 主要卡片样式 */
/* 头部卡片重新布局 */
.token-main-card {
  border: none;
  border-radius: 24px;
  overflow: hidden;
}

.token-main-card :deep(.el-card__body) {
  padding: 0;
}

.token-card-body {
  width: 100%;
  min-height: 180px;
  background: linear-gradient(120deg, #6157ff 0%, #885bff 40%, #c26bff 100%);
  color: #fff;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.token-summary {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 8px;
}

.token-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.token-title {
  display: flex;
  align-items: center;
  gap: 15px;
  flex: 1 1 auto;
  min-width: 200px;
}

.token-icon {
  width: 56px;
  height: 56px;
  background: rgba(255, 255, 255, 0.18);
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
}

.token-info h2 {
  margin: 0 0 4px 0;
  font-size: 20px;
  font-weight: 600;
}

.contract-info {
  margin: 0;
  font-size: 12px;
  opacity: 0.85;
  font-family: 'Courier New', monospace;
}

.balance-display {
  text-align: right;
  min-width: 220px;
  flex: 0 0 auto;
}

.balance-amount {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.amount-value {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.1;
  letter-spacing: -0.5px;
  word-break: break-all;
}

.amount-label {
  font-size: 14px;
  opacity: 0.85;
}

.network-indicator {
  margin-top: 8px;
  padding: 4px 10px;
  border-radius: 14px;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(255, 255, 255, 0.15);
}

.network-correct {
  color: #c8f7dc;
}

.network-wrong {
  color: #ffe1e1;
}

.token-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 18px;
  margin-top: 12px;
}

.stat-pill {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 22px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.18);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.25);
}

.pill-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.pill-details {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pill-label {
  margin: 0;
  font-size: 13px;
  opacity: 0.85;
}

.pill-value {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  text-align: right;
}

/* 操作按钮 */
.actions-card {
  border: none;
}

.actions-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.actions-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 15px;
  grid-auto-rows: minmax(100px, auto);
  width: 100%;
  box-sizing: border-box;
}

.action-btn {
  width: 100%;
  height: 100%;
  padding: 0 !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  border-radius: 12px !important;
  border: 2px solid transparent !important;
  color: #fff !important;
  transition: all 0.3s ease;
  box-sizing: border-box !important;
  margin: 0 !important;
  float: none !important;
  position: static !important;
}

.action-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.15);
}

.btn-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 0;
  height: 100%;
  width: 100%;
  text-align: center;
  padding: 12px;
}

.btn-content i {
  font-size: 20px;
}

.reward-hint {
  font-size: 11px;
  background: rgba(255, 255, 255, 0.2);
  padding: 2px 6px;
  border-radius: 4px;
  white-space: nowrap;
}

.reward-hint--placeholder {
  display: none;
}

.checkin-btn {
  background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%);
}

.reward-btn {
  background: linear-gradient(135deg, #2196F3 0%, #1976D2 100%);
}

/* 专门修复奖励历史按钮的对齐问题 */
.el-button--warning {
  margin: 0 !important;
  padding: 0 !important;
  border: none !important;
  float: none !important;
  vertical-align: top !important;
}

.guide-btn {
  background: linear-gradient(135deg, #FF9800 0%, #F57C00 100%);
}

/* 状态卡片 */
.status-cards {
  margin-top: 20px;
}

.rollup-card {
  margin: 20px 0;
}

.rollup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.rollup-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-card {
  border: none;
  cursor: default;
}

.status-content {
  display: flex;
  align-items: center;
  gap: 15px;
}

.status-icon {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.status-icon.success {
  background: #E8F5E8;
  color: #67C23A;
}

.status-icon.info {
  background: #ECF5FF;
  color: #409EFF;
}

.status-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 2px;
}

.status-value {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

/* 连接提示 */
.connect-prompt {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  border: none;
  color: white;
  text-align: center;
  padding: 40px 20px;
}

.connect-icon {
  font-size: 48px;
  margin-bottom: 20px;
}

.connect-content h3 {
  margin: 0 0 10px 0;
  font-size: 20px;
}

.connect-content p {
  margin: 0;
  opacity: 0.9;
}

/* 交易对话框 */
.transaction-content {
  text-align: center;
  padding: 20px 0;
}

.transaction-icon {
  font-size: 48px;
  margin-bottom: 20px;
}

.transaction-icon .el-icon-loading {
  animation: rotate 2s linear infinite;
}

.transaction-content h3 {
  margin: 0 0 10px 0;
  font-size: 18px;
}

.transaction-content p {
  margin: 0 0 15px 0;
  color: #606266;
}

.transaction-hash {
  margin-top: 15px;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
  word-break: break-all;
}

/* 历史记录 */
.history-content {
  max-height: 400px;
  overflow-y: auto;
}

.reward-amount {
  font-weight: 600;
}

.reward-amount--positive {
  color: #67C23A;
}

.reward-amount--negative {
  color: #F56C6C;
}

/* 使用说明 */
.usage-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 30px;
}

.usage-section h4 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 15px;
  color: #303133;
}

.usage-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.usage-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
}

.usage-item .icon {
  font-size: 18px;
  width: 24px;
  text-align: center;
}

.usage-item .text {
  color: #606266;
  font-size: 14px;
}

@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 响应式设计 */
@media (max-width: 768px) {
  .token-header {
    flex-direction: column;
    gap: 20px;
    text-align: center;
  }

  .balance-display {
    text-align: center;
  }

  .token-stats {
    grid-template-columns: 1fr;
  }

  .actions-grid {
    grid-template-columns: 1fr;
    grid-auto-rows: auto;
  }

  .pill-details {
    flex-direction: column;
    align-items: flex-start;
  }

  .pill-value {
    width: 100%;
    text-align: left;
  }

  .usage-content {
    grid-template-columns: 1fr;
  }
}
</style>
