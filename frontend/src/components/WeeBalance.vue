<template>
  <div v-if="isLoggedIn" class="wee-balance">
    <span class="wee-icon">🪙</span>
    <span class="wee-amount">{{ balance }} WEE</span>
    <el-button
      v-if="!checkedIn"
      type="text"
      size="mini"
      class="checkin-btn"
      :loading="checkingIn"
      @click="doCheckin"
    >签到+{{ CHECKIN_REWARD }}</el-button>
    <span v-else class="checked-in">✓ 已签到</span>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import request from '@/api/request'

export default {
  name: 'WeeBalance',
  data() {
    return {
      balance: 0,
      checkedIn: false,
      checkingIn: false,
      CHECKIN_REWARD: 5
    }
  },
  computed: {
    ...mapGetters('user', ['isLoggedIn'])
  },
  watch: {
    isLoggedIn(val) {
      if (val) this.load()
    }
  },
  mounted() {
    if (this.isLoggedIn) this.load()
  },
  methods: {
    async load() {
      try {
        const [balRes, statusRes] = await Promise.all([
          request.get('/wallet/wee'),
          request.get('/checkin/status')
        ])
        this.balance = balRes.data ?? 0
        this.checkedIn = statusRes.data?.submitted ?? false
      } catch {
        // 静默处理
      }
    },
    async doCheckin() {
      this.checkingIn = true
      try {
        const res = await request.post('/checkin/daily')
        if (res.data?.success) {
          this.balance = res.data.balance ?? this.balance + this.CHECKIN_REWARD
          this.checkedIn = true
          this.$message.success(res.data.message)
        } else {
          this.$message.info(res.data?.message || '今日已签到')
          this.checkedIn = true
        }
      } catch {
        this.$message.error('签到失败，请稍后重试')
      } finally {
        this.checkingIn = false
      }
    }
  }
}
</script>

<style scoped>
.wee-balance {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #f0f4ff;
  border-radius: 16px;
  padding: 4px 12px;
  font-size: 13px;
  color: #5b4cfa;
  font-weight: 600;
}
.wee-icon { font-size: 14px; }
.checkin-btn { color: #ff6b35 !important; font-weight: 700; padding: 0 4px !important; }
.checked-in { color: #67c23a; font-size: 12px; }
</style>
