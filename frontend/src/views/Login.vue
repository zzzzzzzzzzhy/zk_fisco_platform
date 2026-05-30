<template>
  <div class="auth-page">
    <div class="auth-container">
      <div class="auth-card">
        <div class="auth-header">
          <h2>登录</h2>
          <p>登录您的账户，开始参加竞赛</p>
        </div>

        <el-form ref="loginForm" :model="form" :rules="rules" @submit.native.prevent="handleSubmit">
          <el-form-item prop="username">
            <el-input
              v-model="form.username"
              placeholder="用户名"
              prefix-icon="el-icon-user"
              size="large"
            />
          </el-form-item>

          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="密码"
              prefix-icon="el-icon-lock"
              size="large"
              show-password
              @keyup.enter.native="handleSubmit"
            />
          </el-form-item>

          <div class="wallet-section">
            <p class="wallet-section__label">连接钱包</p>
            <meta-mask-connect
              :show-balance-card="false"
              @connected="handleWalletConnected"
              @disconnected="handleWalletDisconnected"
            />
            <el-alert
              v-if="walletAddress"
              type="success"
              :closable="false"
              class="wallet-tip"
              show-icon
            >
              <span>已连接钱包：{{ formatAddress(walletAddress) }}</span>
            </el-alert>
            <el-alert
              v-else
              type="warning"
              :closable="false"
              class="wallet-tip"
              show-icon
            >
              <span>请先连接 MetaMask 钱包，登录时会自动绑定。</span>
            </el-alert>
          </div>

          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="submit-btn"
            @click="handleSubmit"
          >
            登录
          </el-button>
        </el-form>

        <div class="auth-footer">
          <span>还没有账户？</span>
          <router-link to="/register">立即注册</router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapActions } from 'vuex'
import MetaMaskConnect from '@/components/Web3/MetaMaskConnect.vue'

export default {
  name: 'UserLogin',
  components: {
    MetaMaskConnect
  },
  data() {
    return {
      form: {
        username: '',
        password: ''
      },
      rules: {
        username: [
          { required: true, message: '请输入用户名', trigger: 'blur' }
        ],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
        ]
      },
      walletAddress: '',
      loading: false
    }
  },
  methods: {
    ...mapActions('user', ['login']),
    formatAddress(address) {
      if (!address) return ''
      return `${address.slice(0, 6)}...${address.slice(-4)}`
    },
    handleWalletConnected(payload) {
      this.walletAddress = payload.address
    },
    handleWalletDisconnected() {
      this.walletAddress = ''
    },
    handleSubmit() {
      this.$refs.loginForm.validate(async (valid) => {
        if (!valid) return
        if (!this.walletAddress) {
          this.$message.warning('请先连接钱包后再登录')
          return
        }

        this.loading = true
        try {
          await this.login({
            ...this.form,
            walletAddress: this.walletAddress
          })
          this.$message.success({
            message: '登录成功！',
            duration: 1500
          })

          const redirect = this.$route.query.redirect || '/'
          this.$router.push(redirect)
        } catch (error) {
          this.$message.error({
            message: error.message || '登录失败',
            duration: 1500
          })
        } finally {
          this.loading = false
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.auth-container {
  width: 100%;
  max-width: 420px;
}

.auth-card {
  background: white;
  border-radius: 20px;
  padding: 48px 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.auth-header {
  text-align: center;
  margin-bottom: 40px;

  h2 {
    font-size: 32px;
    font-weight: 700;
    color: #303133;
    margin-bottom: 12px;
  }

  p {
    font-size: 15px;
    color: #909399;
  }
}

.wallet-section {
  margin: 20px 0 10px;

  &__label {
    font-size: 14px;
    color: #606266;
    margin-bottom: 10px;
    font-weight: 500;
  }

  .wallet-tip {
    margin-top: 12px;
  }
}

.submit-btn {
  width: 100%;
  margin-top: 12px;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
}

.auth-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 14px;
  color: #606266;

  a {
    color: #5b4cfa;
    text-decoration: none;
    font-weight: 600;
    margin-left: 8px;

    &:hover {
      color: lighten(#5b4cfa, 10%);
    }
  }
}
</style>
