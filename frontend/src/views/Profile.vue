<template>
  <div class="profile-page">
    <div class="container">
      <div class="profile-header">
        <div class="user-avatar">
          <i class="el-icon-user-solid"></i>
        </div>
        <div class="user-info">
          <h2>{{ userInfo.username }}</h2>
          <p>{{ userInfo.email }}</p>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="profile-tabs">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="info">
          <div class="info-section">
            <el-form :model="userInfo" label-width="100px">
              <el-form-item label="用户名">
                <el-input v-model="userInfo.username" disabled />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="userInfo.email" disabled />
              </el-form-item>
              <el-form-item label="真实姓名">
                <el-input v-model="userForm.realname" placeholder="请输入真实姓名" />
              </el-form-item>
              <el-form-item label="KYC状态">
                <el-tag :type="kycStatusType">{{ kycStatusText }}</el-tag>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="updateUserInfo">保存</el-button>
              </el-form-item>
            </el-form>
          </div>
        </el-tab-pane>

        <!-- 我的钱包 -->
        <el-tab-pane label="我的钱包" name="wallet">
          <div class="wallet-section">
            <div class="wee-profile-section">
              <h3>WEE 积分</h3>
              <p class="section-desc">参与社区互动即可获得 WEE 积分奖励</p>
              <WeeBalance style="margin: 16px 0;" />
              <el-divider />
              <div class="reward-rules">
                <div class="rule-item"><span class="rule-badge">+10</span> 发布帖子</div>
                <div class="rule-item"><span class="rule-badge">+2</span> 发表评论</div>
                <div class="rule-item"><span class="rule-badge">+5</span> 每日签到</div>
                <div class="rule-item"><span class="rule-badge">+5</span> 分享图片</div>
                <div class="rule-item"><span class="rule-badge">+10</span> 分享视频</div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 我的竞赛 -->
        <el-tab-pane label="我的竞赛" name="competitions">
          <div class="competitions-section">
            <el-empty description="暂无参加的竞赛" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex'
import WeeBalance from '@/components/WeeBalance.vue'

export default {
  name: 'UserProfile',
  components: { WeeBalance },
  data() {
    return {
      activeTab: 'info',
      userForm: { realname: '' }
    }
  },
  computed: {
    ...mapState('user', ['userInfo']),
    kycStatusType() {
      const typeMap = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
      return typeMap[this.userInfo.kycStatus] || 'info'
    },
    kycStatusText() {
      const textMap = { 0: '未认证', 1: '审核中', 2: '已认证', 3: '未通过' }
      return textMap[this.userInfo.kycStatus] || '未知'
    }
  },
  watch: {
    activeTab(val) {
      if (val === 'wallet') {
        // 法币钱包模块暂未启用，这里不再自动请求余额和交易记录
      }
    }
  },
  created() {
    const tab = this.$route.query.tab
    if (tab) {
      this.activeTab = tab
    }
  },
  methods: {
    updateUserInfo() {
      this.$message.success('保存成功')
    },

    getTransactionTypeTag(type) {
      const tagMap = {
        PRIZE_IN: 'success',
        WITHDRAW_APPLY: 'warning',
        WITHDRAW_SUCCESS: 'info',
        WITHDRAW_FAIL: 'danger',
        REFUND: 'success'
      }
      return tagMap[type] || 'info'
    },

    getTransactionTypeText(type) {
      const textMap = {
        PRIZE_IN: '奖金入账',
        WITHDRAW_APPLY: '提现申请',
        WITHDRAW_FREEZE: '余额冻结',
        WITHDRAW_SUCCESS: '提现成功',
        WITHDRAW_FAIL: '提现失败',
        REFUND: '退款',
        ADJUST: '调整'
      }
      return textMap[type] || type
    },

  }
}
</script>

<style lang="scss" scoped>
.profile-page {
  min-height: calc(100vh - 64px);
  padding: 40px 0;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 24px;
  background: white;
  border-radius: 16px;
  padding: 32px;
  margin-bottom: 32px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);

  .user-avatar {
    width: 80px;
    height: 80px;
    border-radius: 50%;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    display: flex;
    align-items: center;
    justify-content: center;

    i {
      font-size: 48px;
      color: white;
    }
  }

  .user-info {
    h2 {
      font-size: 28px;
      font-weight: 700;
      color: #303133;
      margin-bottom: 8px;
    }

    p {
      font-size: 15px;
      color: #909399;
    }
  }
}

.profile-tabs {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.balance-card {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
  margin-bottom: 32px;

  .balance-info,
  .frozen-info {
    padding: 24px;
    border-radius: 12px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;

    .label {
      display: block;
      font-size: 14px;
      opacity: 0.9;
      margin-bottom: 8px;
    }

    .amount {
      display: block;
      font-size: 32px;
      font-weight: 700;
    }
  }

  .frozen-info {
    background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  }
}

.transactions-section {
  h3 {
    font-size: 20px;
    font-weight: 700;
    margin-bottom: 16px;
  }
}

.amount-in {
  color: #67c23a;
  font-weight: 600;
}

.amount-out {
  color: #f56c6c;
  font-weight: 600;
}

.wee-profile-section {
  padding: 24px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0,0,0,0.06);

  h3 { margin: 0 0 4px; font-size: 18px; }
  .section-desc { color: #909399; font-size: 13px; margin: 0 0 16px; }
}

.reward-rules {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-top: 12px;
}

.rule-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}

.rule-badge {
  background: #f0f4ff;
  color: #5b4cfa;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.section-header h3 {
  margin: 0;
}

.section-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #909399;
}

.network-pill {
  padding: 4px 12px;
  border-radius: 999px;
  background: #ebeef5;
  color: #606266;
  font-size: 12px;
}

.network-pill.connected {
  background: #ecfdf5;
  color: #0bb07b;
}

.mtk-balance-card {
  margin-top: 16px;
  padding: 16px;
  border-radius: 12px;
  background: linear-gradient(120deg, rgba(103, 118, 255, 0.08), rgba(32, 201, 151, 0.08));
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.mtk-info,
.mtk-balance {
  min-width: 180px;
}

.muted {
  margin: 0;
  font-size: 12px;
  color: #909399;
}

.mtk-balance .balance-value {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

</style>
