<template>
  <transition name="notification-slide">
    <div v-if="visible" class="transaction-notification" :class="statusClass">
      <div class="notification-content">
        <div class="icon-wrapper">
          <i v-if="status === 'pending'" class="el-icon-loading"></i>
          <i v-else-if="status === 'confirming'" class="el-icon-time"></i>
          <i v-else-if="status === 'success'" class="el-icon-circle-check"></i>
          <i v-else-if="status === 'error'" class="el-icon-circle-close"></i>
        </div>
        
        <div class="text-content">
          <div class="title">{{ title }}</div>
          <div class="message">{{ message }}</div>
          <div v-if="txHash" class="tx-hash">
            <a :href="explorerUrl" target="_blank" rel="noopener">
              查看交易 →
            </a>
          </div>
        </div>
        
        <div v-if="showClose" class="close-btn" @click="close">
          <i class="el-icon-close"></i>
        </div>
      </div>
      
      <!-- 进度条 -->
      <div v-if="status === 'pending' || status === 'confirming'" class="progress-bar">
        <div class="progress-fill" :style="{ width: progress + '%' }"></div>
      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: 'TransactionNotification',
  data() {
    return {
      visible: false,
      status: 'pending', // pending, confirming, success, error
      title: '',
      message: '',
      txHash: '',
      progress: 0,
      autoCloseTimer: null,
      progressTimer: null
    }
  },
  computed: {
    statusClass() {
      return `status-${this.status}`
    },
    showClose() {
      return this.status === 'success' || this.status === 'error'
    },
    explorerUrl() {
      if (!this.txHash) return '#'
      // Polygon 浏览器
      return `https://polygonscan.com/tx/${this.txHash}`
    }
  },
  methods: {
    show({ status = 'pending', title = '', message = '', txHash = '', duration = 0 }) {
      this.visible = true
      this.status = status
      this.title = title
      this.message = message
      this.txHash = txHash
      this.progress = 0
      
      // 清除之前的定时器
      this.clearTimers()
      
      // 如果是进行中状态，显示进度条动画
      if (status === 'pending' || status === 'confirming') {
        this.startProgress()
      }
      
      // 自动关闭
      if (duration > 0 && (status === 'success' || status === 'error')) {
        this.autoCloseTimer = setTimeout(() => {
          this.close()
        }, duration)
      }
    },
    
    update({ status, title, message, txHash }) {
      if (status) this.status = status
      if (title) this.title = title
      if (message) this.message = message
      if (txHash) this.txHash = txHash
      
      // 如果变为成功或失败，停止进度条并自动关闭
      if (status === 'success' || status === 'error') {
        this.progress = 100
        this.clearTimers()
        
        this.autoCloseTimer = setTimeout(() => {
          this.close()
        }, 5000)
      }
    },
    
    close() {
      this.visible = false
      this.clearTimers()
    },
    
    startProgress() {
      this.progress = 0
      this.progressTimer = setInterval(() => {
        if (this.progress < 90) {
          this.progress += Math.random() * 10
        }
      }, 500)
    },
    
    clearTimers() {
      if (this.autoCloseTimer) {
        clearTimeout(this.autoCloseTimer)
        this.autoCloseTimer = null
      }
      if (this.progressTimer) {
        clearInterval(this.progressTimer)
        this.progressTimer = null
      }
    }
  },
  beforeDestroy() {
    this.clearTimers()
  }
}
</script>

<style scoped lang="scss">
.transaction-notification {
  position: fixed;
  top: 80px;
  right: 20px;
  width: 380px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12),
              0 2px 8px rgba(0, 0, 0, 0.08);
  overflow: hidden;
  z-index: 3000;
  
  .notification-content {
    display: flex;
    align-items: flex-start;
    padding: 20px;
    gap: 16px;
  }
  
  .icon-wrapper {
    flex-shrink: 0;
    width: 48px;
    height: 48px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
    
    i {
      animation: icon-pulse 2s ease-in-out infinite;
    }
  }
  
  .text-content {
    flex: 1;
    min-width: 0;
    
    .title {
      font-size: 16px;
      font-weight: 600;
      margin-bottom: 6px;
      color: #303133;
    }
    
    .message {
      font-size: 14px;
      color: #606266;
      line-height: 1.5;
    }
    
    .tx-hash {
      margin-top: 8px;
      
      a {
        font-size: 13px;
        color: #667eea;
        text-decoration: none;
        font-weight: 500;
        
        &:hover {
          color: #5568d3;
          text-decoration: underline;
        }
      }
    }
  }
  
  .close-btn {
    flex-shrink: 0;
    width: 24px;
    height: 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    border-radius: 6px;
    color: #909399;
    transition: all 0.2s;
    
    &:hover {
      background: #f5f7fa;
      color: #606266;
    }
  }
  
  .progress-bar {
    height: 3px;
    background: rgba(0, 0, 0, 0.05);
    position: relative;
    overflow: hidden;
    
    .progress-fill {
      height: 100%;
      background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
      transition: width 0.3s ease;
      position: absolute;
      left: 0;
      top: 0;
    }
  }
  
  // 不同状态的样式
  &.status-pending,
  &.status-confirming {
    .icon-wrapper {
      background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
      color: #667eea;
    }
  }
  
  &.status-success {
    .icon-wrapper {
      background: linear-gradient(135deg, rgba(17, 153, 142, 0.1) 0%, rgba(56, 239, 125, 0.1) 100%);
      color: #11998e;
    }
    
    .progress-bar .progress-fill {
      background: linear-gradient(90deg, #11998e 0%, #38ef7d 100%);
    }
  }
  
  &.status-error {
    .icon-wrapper {
      background: linear-gradient(135deg, rgba(235, 51, 73, 0.1) 0%, rgba(244, 92, 67, 0.1) 100%);
      color: #eb3349;
    }
    
    .progress-bar .progress-fill {
      background: linear-gradient(90deg, #eb3349 0%, #f45c43 100%);
    }
  }
}

// 进入/离开动画
.notification-slide-enter-active,
.notification-slide-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.notification-slide-enter {
  opacity: 0;
  transform: translateX(100%) scale(0.95);
}

.notification-slide-leave-to {
  opacity: 0;
  transform: translateX(100%) scale(0.95);
}

// 图标脉冲动画
@keyframes icon-pulse {
  0%, 100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.1);
  }
}

// 移动端适配
@media (max-width: 768px) {
  .transaction-notification {
    width: calc(100vw - 40px);
    right: 20px;
    left: 20px;
  }
}
</style>

