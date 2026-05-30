<template>
  <div class="admin-layout" :class="{ 'mobile-nav-open': isMobileMenuOpen }">
    <!-- 现代化顶部导航栏 -->
    <header class="admin-header">
      <div class="header-container">
        <!-- 左侧Logo和导航 -->
        <div class="header-left">
          <div class="logo-section">
            <!-- 汉堡菜单按钮 -->
            <button class="mobile-menu-btn" @click="toggleMobileMenu">
              <span class="menu-line" :class="{ 'is-active': isMobileMenuOpen }"></span>
              <span class="menu-line" :class="{ 'is-active': isMobileMenuOpen }"></span>
              <span class="menu-line" :class="{ 'is-active': isMobileMenuOpen }"></span>
            </button>

            <div class="branding" @click="goOverview">
              <i class="el-icon-setting"></i>
              <span class="branding-title">后台管理</span>
            </div>
            <el-tag size="mini" class="env-tag">{{ environmentTag }}</el-tag>
          </div>

          <!-- 桌面端主导航 -->
          <nav class="main-nav" :class="{ 'is-open': isMobileMenuOpen }">
            <router-link
              v-for="item in navigationLinks"
              :key="item.route"
              :to="item.route"
              class="nav-item"
              :class="{ 'is-active': isActiveNav(item.route) }"
              @click.native="closeMobileMenu"
            >
              <i :class="item.icon"></i>
              <span>{{ item.label }}</span>
            </router-link>
          </nav>
        </div>

        <!-- 中间搜索栏 -->
        <div class="header-center">
          <div class="search-container">
            <el-input
              v-model="searchKeyword"
              class="modern-search"
              placeholder="搜索功能、数据..."
              prefix-icon="el-icon-search"
              clearable
              @keyup.enter="handleSearch"
            />
          </div>
        </div>

        <!-- 右侧操作区 -->
        <div class="header-right">
          <!-- 功能按钮组 -->
          <div class="action-buttons">
            <el-tooltip content="返回前台" placement="bottom">
              <button class="action-btn" @click="goBack">
                <i class="el-icon-s-home"></i>
              </button>
            </el-tooltip>
            <el-tooltip content="刷新数据" placement="bottom">
              <button class="action-btn" @click="refreshContent">
                <i class="el-icon-refresh"></i>
              </button>
            </el-tooltip>
            <el-tooltip content="系统通知" placement="bottom">
              <button class="action-btn notification-btn">
                <i class="el-icon-bell"></i>
                <span class="notification-dot"></span>
              </button>
            </el-tooltip>
            <el-tooltip content="切换主题" placement="bottom">
              <button class="action-btn" @click="toggleTheme">
                <i class="el-icon-moon"></i>
              </button>
            </el-tooltip>
            <el-tooltip content="全屏" placement="bottom">
              <button class="action-btn" @click="toggleFullscreen">
                <i class="el-icon-full-screen"></i>
              </button>
            </el-tooltip>
          </div>

          <!-- 用户菜单 -->
          <el-dropdown @command="handleUserCommand">
            <span class="el-dropdown-link user-info">
              <i class="el-icon-user-solid"></i>
              {{ username || '管理员' }}
              <i class="el-icon-arrow-down el-icon--right"></i>
            </span>
            <el-dropdown-menu slot="dropdown">
              <el-dropdown-item command="profile">
                <i class="el-icon-user"></i> 个人中心
              </el-dropdown-item>
              <el-dropdown-item command="logout">
                <i class="el-icon-switch-button"></i> 退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </el-dropdown>
        </div>
      </div>
    </header>

    <div class="mobile-nav-overlay" v-if="isMobileMenuOpen" @click="toggleMobileMenu"></div>

    <!-- 现代化工作区 -->
      <section class="admin-workspace">
        <!-- 主要内容区 -->
        <main class="workspace-main">
          <div class="workspace-content">
            <router-view />
          </div>
        </main>

        <!-- 侧边栏 -->
        <aside class="workspace-side">
          <div class="sidebar-card">
            <h3 class="sidebar-title">
              <i class="el-icon-compass"></i>
              快捷导航
            </h3>
            <div class="sidebar-links">
              <a href="#" @click.prevent="goLeaderboards" class="sidebar-link">
                <div class="link-icon medal">
                  <i class="el-icon-medal"></i>
                </div>
                <div class="link-content">
                  <h4>排行榜管理</h4>
                  <p>配置更新排行榜及赛道</p>
                </div>
                <i class="el-icon-arrow-right link-arrow"></i>
              </a>

              <a href="#" @click.prevent="goContentShares" class="sidebar-link">
                <div class="link-icon content">
                  <i class="el-icon-picture"></i>
                </div>
                <div class="link-content">
                  <h4>内容分享管理</h4>
                  <p>管理用户分享的图片和视频</p>
                </div>
                <i class="el-icon-arrow-right link-arrow"></i>
              </a>

              <a href="#" @click.prevent="goAppeals" class="sidebar-link">
                <div class="link-icon appeal">
                  <i class="el-icon-warning"></i>
                </div>
                <div class="link-content">
                  <h4>异议处理</h4>
                  <p>跟进申诉和仲裁</p>
                </div>
                <i class="el-icon-arrow-right link-arrow"></i>
              </a>

              <a href="#" @click.prevent="goCreateCompetition" class="sidebar-link">
                <div class="link-icon create">
                  <i class="el-icon-folder-add"></i>
                </div>
                <div class="link-content">
                  <h4>创建新竞赛</h4>
                  <p>快速发起活动并设置奖励</p>
                </div>
                <i class="el-icon-arrow-right link-arrow"></i>
              </a>
            </div>
          </div>

          <div class="sidebar-card gradient-card">
            <div class="card-header">
              <i class="el-icon-cpu"></i>
              <h3>系统信息</h3>
            </div>
            <div class="card-content">
              <div class="stat-item">
                <span class="stat-label">系统状态</span>
                <span class="stat-value success">运行中</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">在线用户</span>
                <span class="stat-value">128</span>
              </div>
              <div class="stat-item">
                <span class="stat-label">今日操作</span>
                <span class="stat-value">456</span>
              </div>
            </div>
          </div>

          <div class="sidebar-card">
            <div class="card-header">
              <i class="el-icon-service"></i>
              <h3>帮助支持</h3>
            </div>
            <div class="card-content">
              <p>需要帮助？我们随时支持你处理竞赛、预算和安全事项。</p>
              <el-button type="primary" size="small" icon="el-icon-chat-dot-round">
                联系技术支持
              </el-button>
            </div>
          </div>
        </aside>
      </section>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'

export default {
  name: 'AdminLayout',
  data() {
    return {
      isMobileMenuOpen: false,
      searchKeyword: '',
      theme: 'light',
      navigationLinks: [
        { label: '竞赛管理', route: '/admin/competitions', icon: 'el-icon-trophy' },
        { label: '排行榜', route: '/admin/leaderboards', icon: 'el-icon-medal' },
        { label: '内容分享', route: '/admin/content-shares', icon: 'el-icon-picture' },
        { label: '举报管理', route: '/admin/content-reports', icon: 'el-icon-warning-outline' },
        { label: '异议处理', route: '/admin/appeals', icon: 'el-icon-warning-outline' },
        { label: '奖金管理', route: '/admin/prizes', icon: 'el-icon-money' },
        { label: 'Gas监控', route: '/admin/gas-monitoring', icon: 'el-icon-price-tag' }
      ],
      insightCards: [
        { label: '活跃竞赛', value: 12, icon: 'el-icon-data-analysis', variant: 'primary' },
        { label: '待审核事项', value: 5, icon: 'el-icon-document-checked', variant: 'warning' },
        { label: '奖金池余额', value: '¥3.2M', icon: 'el-icon-wallet', variant: 'success' }
      ]
    }
  },
  computed: {
    ...mapGetters('user', ['username', 'userRole']),
    displayName() {
      return this.username || '管理员'
    },
    userRoleLabel() {
      return this.userRole === 'ADMIN' ? '超级管理员' : '运营成员'
    },
    avatarInitial() {
      return this.displayName.charAt(0).toUpperCase()
    },
    environmentTag() {
      return process.env.VUE_APP_ENV || '生产环境'
    }
  },
  methods: {
    goOverview() {
      this.$router.push('/admin/competitions')
    },
    goBack() {
      this.$router.push('/')
    },
    goCreateCompetition() {
      this.$router.push('/admin/competitions/create')
    },
    goLeaderboards() {
      this.$router.push('/admin/leaderboards')
    },
    goAppeals() {
      this.$router.push('/admin/appeals')
    },
    goContentShares() {
      this.$router.push('/admin/content-shares')
    },
    handleSearch() {
      if (!this.searchKeyword) {
        this.$message.info('请输入要跳转的功能关键字')
        return
      }
      this.$message.success(`已记录关键字：${this.searchKeyword}`)
      this.searchKeyword = ''
    },
    refreshContent() {
      this.$emit('refresh')
      this.$message.success('已刷新页面数据')
    },
    toggleTheme() {
      this.theme = this.theme === 'light' ? 'dark' : 'light'
      document.body.classList.toggle('admin-theme-dark', this.theme === 'dark')
    },
    toggleMobileMenu() {
      this.isMobileMenuOpen = !this.isMobileMenuOpen
    },
    closeMobileMenu() {
      this.isMobileMenuOpen = false
    },
    handleUserCommand(command) {
      if (command === 'logout') {
        this.$store.dispatch('user/logout')
        this.$router.push('/login')
      } else if (command === 'profile') {
        this.$router.push('/profile')
      } else if (command === 'settings') {
        this.$router.push('/admin/settings')
      } else if (command === 'help') {
        window.open('/help', '_blank')
      }
    },
    toggleFullscreen() {
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen()
      } else {
        document.exitFullscreen()
      }
    },
      handleResize() {
      if (window.innerWidth > 992) {
        this.closeMobileMenu()
      }
    },
    isActiveNav(route) {
      return this.$route.path.startsWith(route)
    }
  },
  watch: {
    $route() {
      this.closeMobileMenu()
    }
  },
  mounted() {
    window.addEventListener('resize', this.handleResize)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.handleResize)
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/admin-design-system.scss';

$appbar-height: 72px;

.admin-layout {
  --container-max-width: 1320px;
  --spacing-xxs: 2px;
  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;
  --spacing-xl: 32px;
  --spacing-2xl: 48px;
  --spacing-3xl: 64px;
  --border-radius-sm: 4px;
  --border-radius-md: 8px;
  --border-radius-lg: 12px;
  --border-radius-xl: 16px;
  min-height: 100vh;
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-family: $font-family;
}

// 现代化顶部导航栏样式
.admin-header {
  position: sticky;
  top: 0;
  left: 0;
  right: 0;
  height: $appbar-height;
  background: linear-gradient(to right, #ffffff, #fafbfc);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  z-index: $z-index-sticky;
  font-family: $font-family;
  backdrop-filter: blur(10px);
}

.header-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 0 var(--spacing-xl);
  max-width: 1400px;
  margin: 0 auto;
  gap: var(--spacing-lg);
}

// 左侧区域
.header-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-xl);
  flex: 1;
}

.logo-section {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.mobile-menu-btn {
  display: none;
  flex-direction: column;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: 0;
  gap: 4px;

  .menu-line {
    display: block;
    width: 20px;
    height: 2px;
    background: var(--text-primary);
    border-radius: 2px;
    transition: all $transition-base;
    transform-origin: center;

    &.is-active {
      &:nth-child(1) {
        transform: translateY(6px) rotate(45deg);
      }
      &:nth-child(2) {
        opacity: 0;
      }
      &:nth-child(3) {
        transform: translateY(-6px) rotate(-45deg);
      }
    }
  }

  @include respond-to(lg) {
    display: flex;
  }
}

.branding {
  display: flex !important;
  flex-direction: row !important;
  align-items: center !important;
  font-size: 20px;
  font-weight: 700;
  color: #5b4cfa;
  cursor: pointer;
  transition: all 0.3s;

  i {
    font-size: 28px !important;
    margin-right: 8px !important;
    color: #606266 !important;
  }

  .branding-title {
    color: #5b4cfa !important;
    white-space: nowrap;
    writing-mode: horizontal-tb;
    text-orientation: mixed;
  }

  .env-tag {
    margin-left: var(--spacing-sm);
    border-radius: $border-radius-full;
    font-weight: $font-weight-medium;
    background: rgba(91, 76, 250, 0.1);
    color: var(--primary-color);
    border: 1px solid rgba(91, 76, 250, 0.2);
  }
}

// 主导航
.main-nav {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);

  .nav-item {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
    padding: var(--spacing-md) var(--spacing-lg);
    color: #606266;
    text-decoration: none;
    font-size: 16px;
    font-weight: 500;
    border-radius: var(--border-radius-md);
    transition: all 0.3s;
    position: relative;

    i {
      color: #606266;
      font-size: 20px;
      margin-right: 4px;
    }

    &:hover {
      color: #5b4cfa;

      i {
        color: #5b4cfa;
      }
    }

    &.is-active {
      color: #5b4cfa;
      background: rgba(91, 76, 250, 0.1);

      i {
        color: #5b4cfa;
      }

      &::after {
        content: '';
        position: absolute;
        bottom: -1px;
        left: 50%;
        transform: translateX(-50%);
        width: 30px;
        height: 3px;
        background: #5b4cfa;
        border-radius: 2px;
      }
    }

    @include respond-to(lg) {
      width: 100%;
      padding: var(--spacing-md) var(--spacing-lg);
      margin: 0;

      &::after {
        display: none;
      }
    }

    i {
      font-size: 18px;
    }

    span {
      white-space: nowrap;
    }
  }

  @include respond-to(lg) {
    position: fixed;
    top: $appbar-height;
    left: -100%;
    width: 280px;
    height: calc(100vh - #{$appbar-height});
    background: white;
    flex-direction: column;
    gap: 0;
    box-shadow: 2px 0 12px rgba(0, 0, 0, 0.1);
    z-index: 1040;
    padding: var(--spacing-lg);
    transition: left $transition-base;

    &.is-open {
      left: 0;
    }
  }
}

.appbar-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);

  .appbar-search {
    width: 260px;
  }

  .action-buttons {
    display: inline-flex;
    gap: var(--spacing-sm);

    .el-button {
      border-color: transparent;
      background: rgba(91, 76, 250, 0.08);
      color: var(--primary-color);

      &:hover {
        background: rgba(91, 76, 250, 0.18);
      }
    }
  }

  .user-chip {
    display: inline-flex;
    align-items: center;
    gap: var(--spacing-sm);
    padding: var(--spacing-xs) var(--spacing-sm);
    border-radius: $border-radius-full;
    background: var(--bg-primary);
    box-shadow: var(--shadow-light);
    cursor: pointer;

    .avatar {
      width: 32px;
      height: 32px;
      border-radius: $border-radius-full;
      background: var(--primary-color);
      color: #fff;
      @include flex-center;
      font-weight: $font-weight-semibold;
    }

    .user-meta {
      display: flex;
      flex-direction: column;
      line-height: 1.1;

      .name {
        font-weight: $font-weight-medium;
      }

      small {
        color: var(--text-secondary);
      }
    }
  }

  @include respond-to(lg) {
    flex-wrap: wrap;
    justify-content: flex-start;

    .appbar-search {
      width: 100%;
    }
  }
}

.mobile-nav-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: $z-index-modal-backdrop;
}

// 现代化工作区
.admin-workspace {
  max-width: 1400px;
  margin: 0 auto;
  padding: var(--spacing-xl) var(--spacing-xl) var(--spacing-3xl);
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: var(--spacing-2xl);
  align-items: flex-start;

  @include respond-to(xl) {
    grid-template-columns: 1fr 280px;
  }

  @include respond-to(lg) {
    grid-template-columns: 1fr;
    gap: var(--spacing-xl);
  }

  @include respond-to(sm) {
    padding: var(--spacing-lg) var(--spacing-md);
  }
}

.workspace-main {
  .workspace-content {
    background: var(--bg-primary);
    border-radius: 20px;
    box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
    border: 1px solid rgba(226, 232, 240, 0.6);
    overflow: hidden;
  }
}

// 现代化侧边栏
.workspace-side {
  position: sticky;
  top: calc(#{$appbar-height} + var(--spacing-xl));
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);

  @include respond-to(lg) {
    position: static;
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
    gap: var(--spacing-lg);
  }
}

.sidebar-card {
  background: var(--bg-primary);
  border-radius: 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(226, 232, 240, 0.6);
  overflow: hidden;
  transition: all $transition-base;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
  }

  .sidebar-title {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
    margin: 0 0 var(--spacing-lg);
    padding: var(--spacing-xl) var(--spacing-xl) 0;
    font-size: $font-size-lg;
    font-weight: $font-weight-semibold;
    color: var(--text-primary);

    i {
      color: var(--primary-color);
      font-size: 20px;
    }
  }

  .card-header {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
    padding: var(--spacing-xl) var(--spacing-xl) var(--spacing-md);
    border-bottom: 1px solid var(--border-light);

    h3 {
      margin: 0;
      font-size: $font-size-base;
      font-weight: $font-weight-semibold;
    }

    i {
      color: var(--primary-color);
      font-size: 18px;
    }
  }

  .card-content {
    padding: var(--spacing-md) var(--spacing-xl) var(--spacing-xl);

    p {
      margin: 0 0 var(--spacing-lg);
      color: var(--text-secondary);
      line-height: 1.6;
    }

    .el-button {
      width: 100%;
      border-radius: 12px;
      padding: var(--spacing-sm) var(--spacing-lg);
    }
  }

  &.gradient-card {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    border: none;

    .card-header {
      border-bottom: 1px solid rgba(255, 255, 255, 0.2);

      i {
        color: white;
      }
    }

    .card-content {
      .stat-item {
        @include flex-between;
        padding: var(--spacing-sm) 0;

        &:not(:last-child) {
          border-bottom: 1px solid rgba(255, 255, 255, 0.1);
        }

        .stat-label {
          color: rgba(255, 255, 255, 0.8);
          font-size: $font-size-sm;
        }

        .stat-value {
          font-weight: $font-weight-semibold;
          font-size: $font-size-base;

          &.success {
            color: #4ade80;
          }
        }
      }
    }
  }

  .sidebar-links {
    padding: 0 var(--spacing-xl) var(--spacing-xl);
    display: flex;
    flex-direction: column;
    gap: var(--spacing-sm);
  }

  .sidebar-link {
    display: grid;
    grid-template-columns: auto 1fr auto;
    align-items: center;
    gap: var(--spacing-md);
    padding: var(--spacing-md) var(--spacing-lg);
    border-radius: 16px;
    text-decoration: none;
    color: var(--text-primary);
    transition: all $transition-base;
    border: 2px solid transparent;

    &:hover {
      background: var(--bg-secondary);
      transform: translateX(4px);
      border-color: rgba(91, 76, 250, 0.2);
    }

    .link-icon {
      width: 40px;
      height: 40px;
      border-radius: 12px;
      @include flex-center;
      font-size: 18px;
      transition: all $transition-base;

      &.medal {
        background: linear-gradient(135deg, #fbbf24, #f59e0b);
        color: white;
      }

      &.content {
        background: linear-gradient(135deg, #60a5fa, #3b82f6);
        color: white;
      }

      &.appeal {
        background: linear-gradient(135deg, #f87171, #ef4444);
        color: white;
      }

      &.create {
        background: linear-gradient(135deg, #34d399, #10b981);
        color: white;
      }
    }

    .link-content {
      flex: 1;

      h4 {
        margin: 0 0 var(--spacing-xs);
        font-size: $font-size-base;
        font-weight: $font-weight-medium;
        color: var(--text-primary);
      }

      p {
        margin: 0;
        font-size: $font-size-xs;
        color: var(--text-secondary);
        line-height: 1.4;
      }
    }

    .link-arrow {
      color: var(--text-placeholder);
      font-size: 14px;
      transition: all $transition-base;
    }

    &:hover {
      .link-arrow {
        color: var(--primary-color);
        transform: translateX(2px);
      }
    }
  }
}

.mobile-nav-open {
  overflow: hidden;

  .mobile-nav-overlay {
    display: block;
  }
}

// 现代化补充样式
// 中间搜索区
.header-center {
  flex: 0 1 500px;
  max-width: 500px;

  @include respond-to(md) {
    display: none;
  }
}

.search-container {
  width: 100%;

  .modern-search {
    :deep(.el-input__inner) {
      border-radius: 24px;
      border: 2px solid var(--border-light);
      padding: var(--spacing-md) var(--spacing-lg);
      padding-left: var(--spacing-2xl);
      font-size: $font-size-sm;
      transition: all $transition-base;
      background: rgba(255, 255, 255, 0.8);
      backdrop-filter: blur(10px);

      &:focus {
        border-color: var(--primary-color);
        box-shadow: 0 0 0 4px rgba(91, 76, 250, 0.1);
      }
    }

    :deep(.el-input__prefix) {
      left: var(--spacing-md);
      color: var(--text-placeholder);
    }
  }
}

// 右侧操作区
.header-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);

  @include respond-to(md) {
    gap: var(--spacing-xs);
  }
}

.action-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--text-regular);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  transition: color $transition-base;
  border-radius: var(--border-radius-sm);

  &:hover {
    color: var(--primary-color);
    background: rgba(91, 76, 250, 0.1);
  }

  &.notification-btn {
    .notification-dot {
      position: absolute;
      top: 6px;
      right: 6px;
      width: 6px;
      height: 6px;
      background: var(--danger-color);
      border-radius: 50%;
    }
  }

  @include respond-to(md) {
    width: 32px;
    height: 32px;
    font-size: 14px;
  }
}

// 用户信息区域
// 用户信息区域 - 简化样式
.user-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  cursor: pointer;
  color: var(--text-primary);
  font-size: $font-size-sm;
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--border-radius-sm);
  transition: all $transition-base;

  &:hover {
    color: var(--primary-color);
    background: rgba(91, 76, 250, 0.1);
  }

  i {
    font-size: 16px;
  }

  .el-icon--right {
    font-size: 12px;
  }
}

// 现代化下拉菜单样式
.modern-dropdown {
  border-radius: 16px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
  border: 1px solid var(--border-light);
  overflow: hidden;

  :deep(.el-dropdown-menu) {
    padding: 0;
    border: none;
    border-radius: 16px;
    box-shadow: none;
  }

  .dropdown-header {
    padding: var(--spacing-lg);
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    border-bottom: 1px solid var(--border-light);

    .dropdown-user {
      display: flex;
      align-items: center;
      gap: var(--spacing-md);

      .dropdown-avatar {
        width: 48px;
        height: 48px;
        border-radius: 50%;
        background: rgba(255, 255, 255, 0.2);
        color: white;
        @include flex-center;
        font-weight: $font-weight-bold;
        font-size: $font-size-base;
        border: 2px solid rgba(255, 255, 255, 0.3);
      }

      .dropdown-info {
        .dropdown-name {
          font-size: $font-size-base;
          font-weight: $font-weight-semibold;
          display: block;
          margin-bottom: 2px;
        }

        .dropdown-role {
          font-size: $font-size-xs;
          opacity: 0.8;
          display: block;
        }
      }
    }
  }

  .dropdown-divider {
    height: 1px;
    background: var(--border-light);
    margin: 0;
  }

  .dropdown-item {
    display: flex;
    align-items: center;
    gap: var(--spacing-sm);
    padding: var(--spacing-md) var(--spacing-lg);
    transition: all $transition-base;
    font-size: $font-size-sm;
    color: var(--text-regular);

    i {
      font-size: 16px;
      color: var(--text-placeholder);
      width: 20px;
      text-align: center;
    }

    &:hover {
      background: var(--bg-secondary);
      color: var(--text-primary);

      i {
        color: var(--primary-color);
      }
    }

    &.logout-item {
      color: var(--danger-color);

      &:hover {
        background: rgba(245, 108, 108, 0.1);
      }

      i {
        color: var(--danger-color);
      }
    }
  }
}

// 响应式导航菜单适配
@include respond-to(lg) {
  .header-left {
    gap: var(--spacing-md);
  }

  .header-center {
    display: none;
  }

  .header-right {
    gap: var(--spacing-md);
  }

  .action-buttons {
    .action-btn {
      width: 36px;
      height: 36px;
      font-size: 16px;
    }
  }
}

// 适配移动端
@include respond-to(sm) {
  .header-container {
    padding: 0 var(--spacing-md);
    gap: var(--spacing-md);
  }

  .logo-section {
    .branding {
      .branding-content {
        .branding-subtitle {
          display: none;
        }
      }
    }
  }

  .env-tag {
    display: none;
  }

  .user-details {
    display: none !important;
  }
}
</style>
