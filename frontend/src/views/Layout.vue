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

  </div>
</template>

<script>
import { mapGetters, mapActions } from 'vuex'
export default {
  name: 'MainLayout',
  components: {},
  data() {
    return {
      mobileMenuOpen: false
    }
  },
  computed: {
    ...mapGetters('user', ['isLoggedIn', 'username', 'userRole']),
  },
  watch: {
    '$route'() {
      this.mobileMenuOpen = false
    }
  },
  mounted() {},
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
