<template>
  <div v-if="showFixButton" class="wallet-sync-fix">
    <el-alert
      title="钱包状态异常"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 10px;"
    >
      <template slot="default">
        <p>检测到钱包连接状态不一致</p>
        <el-button type="text" @click="handleGlobalFix" style="padding: 0; margin-top: 5px;">
          🔧 一键修复所有钱包状态
        </el-button>
      </template>
    </el-alert>
  </div>
</template>

<script>
import { walletState, walletSync } from '@/store/wallet'
import walletStateManager from '@/utils/walletStateManager'

export default {
  name: 'WalletSyncFix',
  data() {
    return {
      showFixButton: false,
      lastCheckTime: 0
    }
  },
  computed: {
    hasAddressButNotConnected() {
      return walletState.address && !walletState.isConnected
    }
  },
  mounted() {
    // 订阅状态变化
    this.syncUnsubscribe = walletSync.subscribe((event) => {
      console.log('🔧 WalletSyncFix 收到状态事件:', event.detail.type)
      this.checkAndShowFix()
    })

    // 定期检查状态
    this.checkInterval = setInterval(() => {
      this.checkAndShowFix()
    }, 3000) // 每3秒检查一次

    // 初始检查
    this.checkAndShowFix()
  },
  beforeDestroy() {
    if (this.syncUnsubscribe) {
      this.syncUnsubscribe()
    }
    if (this.checkInterval) {
      clearInterval(this.checkInterval)
    }
  },
  methods: {
    checkAndShowFix() {
      const now = Date.now()

      // 防止频繁检查（至少间隔1秒）
      if (now - this.lastCheckTime < 1000) {
        return
      }
      this.lastCheckTime = now

      // 检查是否需要显示修复按钮
      const needFix = this.hasAddressButNotConnected

      if (needFix !== this.showFixButton) {
        this.showFixButton = needFix
        console.log('🔧 修复按钮状态更新:', needFix)
      }
    },

    async handleGlobalFix() {
      try {
        this.$message.info('正在使用统一状态管理器修复...')

        // 主要方法: 使用统一状态管理器
        console.log('🔧 使用统一状态管理器修复...')
        const success = await walletStateManager.forceSyncAll()

        if (success) {
          // 备用方法: 使用增强钱包管理器
          if (window.enhancedWalletManager) {
            console.log('🔧 使用增强钱包管理器进一步修复...')
            await window.enhancedWalletManager.forceGlobalSync()
          }

          // 触发全局同步事件
          await walletSync.emitChange('global-fix', {
            address: walletState.address,
            expectedState: true,
            timestamp: Date.now()
          })

          // 使用调试工具诊断
          if (window.Web3Debugger) {
            console.log('🔧 使用调试工具诊断...')
            const diagnosis = await window.Web3Debugger.generateDebugReport()
            console.log('📊 诊断结果:', diagnosis)

            // 如果有问题，尝试自动修复
            if (diagnosis.recommendations.some(r => r.level === 'error')) {
              const result = await window.Web3Debugger.autoFixIssues()
              if (result.success) {
                console.log('✅ 调试工具修复成功:', result.fixes)
              }
            }
          }
        }

        // 等待状态同步
        setTimeout(() => {
          this.checkAndShowFix()

          if (!this.showFixButton) {
            this.$message.success('钱包状态修复成功！')
          } else {
            this.$message.warning('修复可能未完全成功，请查看控制台日志')

            // 提供手动修复选项
            if (window.walletStateManager) {
              this.$message.warning('您可以尝试在控制台运行: walletStateManager.forceSyncAll()')
            }
          }
        }, 1500)

      } catch (error) {
        console.error('❌ 全局修复失败:', error)
        this.$message.error(`修复失败: ${error.message}`)
      }
    }
  }
}
</script>

<style scoped>
.wallet-sync-fix {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 2000;
  max-width: 300px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

@media (max-width: 768px) {
  .wallet-sync-fix {
    top: 10px;
    right: 10px;
    left: 10px;
    max-width: none;
  }
}
</style>