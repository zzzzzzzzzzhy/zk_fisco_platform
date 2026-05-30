<template>
  <div class="layout">
    <!-- 顶部导航 -->
    <header class="header">
      <div class="container header-content">
        <div class="logo" @click="$router.push('/')">
          <i class="el-icon-trophy"></i>
          <span>竞赛平台</span>
        </div>

        <!-- 移动端汉堡菜单按钮 -->
        <button class="mobile-menu-btn" @click="mobileMenuOpen = !mobileMenuOpen">
          <i :class="mobileMenuOpen ? 'el-icon-close' : 'el-icon-s-unfold'"></i>
        </button>

        <nav class="nav" :class="{ 'mobile-open': mobileMenuOpen }">
          <router-link to="/competitions" class="nav-item">
            <i class="el-icon-menu"></i>
            竞赛
          </router-link>
          <router-link to="/forum" class="nav-item">
            <i class="el-icon-chat-line-square"></i>
            社区论坛
          </router-link>
          <router-link to="/content-share" class="nav-item">
            <i class="el-icon-picture-outline-round"></i>
            内容分享
          </router-link>
          <router-link to="/coffee-mapping" class="nav-item">
            <i class="el-icon-s-shop"></i>
            咖啡映射
          </router-link>
          <router-link to="/governance" class="nav-item">
            <i class="el-icon-s-operation"></i>
            DAO 治理
          </router-link>
          <router-link v-if="isLoggedIn" to="/submissions" class="nav-item">
            <i class="el-icon-document"></i>
            我的提交
          </router-link>
          <router-link v-if="isLoggedIn && userRole === 'ADMIN'" to="/admin" class="nav-item">
            <i class="el-icon-setting"></i>
            管理后台
          </router-link>
        </nav>

        <div class="user-actions">
          <!-- 钱包状态同步按钮 (仅在开发环境显示) -->
          <el-button
            v-if="isDevelopment && showWalletSyncButton"
            type="text"
            icon="el-icon-refresh"
            @click="manualSyncWallet"
            size="small"
            style="margin-right: 10px; color: #409EFF;"
          >
            同步钱包
          </el-button>

          <template v-if="isLoggedIn">
            <el-dropdown @command="handleUserCommand">
              <span class="el-dropdown-link user-info">
                <i class="el-icon-user-solid"></i>
                {{ username }}
                <i class="el-icon-arrow-down el-icon--right"></i>
              </span>
              <el-dropdown-menu slot="dropdown">
                <el-dropdown-item command="profile">
                  <i class="el-icon-user"></i> 个人中心
                </el-dropdown-item>
                <el-dropdown-item command="wallet">
                  <i class="el-icon-wallet"></i> 我的钱包
                </el-dropdown-item>
                <el-dropdown-item v-if="userRole === 'ADMIN'" command="admin">
                  <i class="el-icon-setting"></i> 管理后台
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <i class="el-icon-switch-button"></i> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button size="small" @click="$router.push('/login')">登录</el-button>
            <el-button type="primary" size="small" @click="$router.push('/register')">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <!-- 主体内容 -->
    <main class="main-content">
      <router-view/>
    </main>

    <!-- 底部 -->
    <footer class="footer">
      <div class="container">
        <p>&copy; 2025 竞赛平台. All rights reserved.</p>
        <div class="footer-links">
          <a href="#">关于我们</a>
          <a href="#">联系方式</a>
          <a href="#">隐私政策</a>
          <a href="#">服务条款</a>
        </div>
      </div>
    </footer>

    <!-- 全局钱包状态修复工具 -->
    <WalletSyncFix />
  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex'
import WalletSyncFix from '@/components/Web3/WalletSyncFix.vue'
import { walletState, walletSync } from '@/store/wallet'

export default {
  name: 'MainLayout',
  components: {
    WalletSyncFix
  },
  data() {
    return {
      showWalletSyncButton: false,
      mobileMenuOpen: false
    }
  },
  computed: {
    ...mapGetters('user', ['isLoggedIn', 'username', 'userRole']),
    isDevelopment() {
      return process.env.NODE_ENV === 'development'
    }
  },
  watch: {
    // 监听路由变化，自动关闭移动端菜单
    '$route'() {
      this.mobileMenuOpen = false
    }
  },
  mounted() {
    // 检查是否显示钱包同步按钮
    this.checkWalletSyncButton()

    // 监听钱包状态变化
    this.syncUnsubscribe = walletSync.subscribe(() => {
      this.checkWalletSyncButton()
    })
  },
  beforeDestroy() {
    if (this.syncUnsubscribe) {
      this.syncUnsubscribe()
    }
  },
  methods: {
    ...mapActions('user', ['logout']),
    handleUserCommand(command) {
      if (command === 'logout') {
        this.$confirm('确定要退出登录吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => {
          this.logout()
          this.$message.success('退出登录成功')
          this.$router.push('/login')
        }).catch(() => {})
      } else if (command === 'profile') {
        this.$router.push('/profile')
      } else if (command === 'wallet') {
        this.$router.push('/profile?tab=wallet')
      } else if (command === 'admin') {
        this.$router.push('/admin')
      }
    },

    // 检查是否显示钱包同步按钮
    checkWalletSyncButton() {
      // 如果有地址但未连接，显示同步按钮
      this.showWalletSyncButton = walletState.address && !walletState.isConnected
    },

    // 手动同步钱包
    async manualSyncWallet() {
      try {
        this.$message.info('正在手动同步钱包状态...')

        // 方法1: 使用增强钱包管理器
        if (window.enhancedWalletManager) {
          await window.enhancedWalletManager.forceGlobalSync()
        }

        // 方法2: 触发全局同步事件
        await walletSync.emitChange('manual-sync', {
          address: walletState.address,
          timestamp: Date.now()
        })

        // 方法3: 使用调试工具
        if (window.Web3Debugger) {
          const result = await window.Web3Debugger.autoFixIssues()
          if (result.success) {
            console.log('🔧 调试工具修复结果:', result.fixes)
          }
        }

        // 等待状态更新
        setTimeout(() => {
          this.checkWalletSyncButton()

          if (!this.showWalletSyncButton) {
            this.$message.success('钱包状态同步成功！')
          } else {
            this.$message.warning('状态同步可能未完全成功，请查看控制台')
          }
        }, 1000)

      } catch (error) {
        console.error('手动同步失败:', error)
        this.$message.error(`同步失败: ${error.message}`)
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.header {
  background: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  position: sticky;
  top: 0;
  z-index: 100;

  .header-content {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 64px;
  }

  .logo {
    display: flex;
    align-items: center;
    font-size: 20px;
    font-weight: 700;
    color: #5b4cfa;
    cursor: pointer;
    transition: all 0.3s;

    i {
      font-size: 28px;
      margin-right: 8px;
    }

    &:hover {
      opacity: 0.8;
    }
  }

  .nav {
    display: flex;
    gap: 32px;

    .nav-item {
      display: flex;
      align-items: center;
      gap: 6px;
      color: #606266;
      text-decoration: none;
      font-size: 15px;
      font-weight: 500;
      transition: color 0.3s;

      i {
        font-size: 18px;
      }

      &:hover,
      &.router-link-active {
        color: #5b4cfa;
      }
    }
  }

  .user-actions {
    display: flex;
    align-items: center;
    gap: 12px;

    .user-info {
      display: flex;
      align-items: center;
      gap: 6px;
      cursor: pointer;
      padding: 8px 16px;
      border-radius: 8px;
      transition: all 0.3s;

      &:hover {
        background: #f5f7fa;
      }

      i.el-icon-user-solid {
        font-size: 18px;
        color: #5b4cfa;
      }
    }
  }
  
  /* 移动端汉堡菜单按钮（默认隐藏） */
  .mobile-menu-btn {
    display: none;
    background: none;
    border: none;
    font-size: 28px;
    color: #5b4cfa;
    cursor: pointer;
    padding: 8px;
    transition: all 0.3s;
    
    &:hover {
      opacity: 0.7;
    }
    
    &:active {
      transform: scale(0.95);
    }
  }
}

/* ============================================
   移动端响应式样式
   ============================================ */
@media (max-width: 768px) {
  .header {
    .header-content {
      height: 56px;
    }
    
    .logo {
      font-size: 16px;
      
      span {
        display: none; /* 隐藏文字，只保留图标 */
      }
      
      i {
        font-size: 24px;
        margin-right: 0;
      }
    }
    
    /* 显示汉堡菜单按钮 */
    .mobile-menu-btn {
      display: block;
    }
    
    /* 导航栏改为移动端下拉菜单 */
    .nav {
      position: fixed;
      top: 56px;
      left: 0;
      right: 0;
      background: white;
      flex-direction: column;
      gap: 0;
      padding: 0;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
      max-height: 0;
      overflow: hidden;
      transition: max-height 0.3s ease;
      
      &.mobile-open {
        max-height: 500px;
      }
      
      .nav-item {
        padding: 16px 20px;
        border-bottom: 1px solid #f0f0f0;
        
        &:hover {
          background: #f5f7fa;
        }
      }
    }
    
    /* 用户操作区域优化 */
    .user-actions {
      gap: 8px;
      
      .el-button {
        padding: 8px 12px;
        font-size: 13px;
      }
      
      .user-info {
        padding: 6px 12px;
        font-size: 14px;
      }
    }
  }
}

.main-content {
  flex: 1;
  padding: 40px 0;
}

.footer {
  background: #2c3e50;
  color: white;
  padding: 32px 0;
  margin-top: 60px;

  .container {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .footer-links {
    display: flex;
    gap: 24px;

    a {
      color: rgba(255, 255, 255, 0.8);
      text-decoration: none;
      transition: color 0.3s;

      &:hover {
        color: white;
      }
    }
  }
}
</style>
