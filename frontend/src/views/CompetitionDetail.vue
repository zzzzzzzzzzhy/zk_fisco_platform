<template>
  <div v-loading="loading" class="competition-detail">
    <div v-if="competition" class="detail-wrapper">
      <!-- 详情页头部大图 (Position 2) -->
      <div class="detail-banner">
        <img :src="getDetailImage()" alt="竞赛详情头图" class="banner-image" />
      </div>

      <div class="container">
        <!-- 头部信息 -->
        <div class="detail-header">
        <div class="header-content">
          <h1 class="title">{{ competition.title }}</h1>
          <p class="description">{{ competition.description }}</p>

          <div class="meta-info">
            <span :class="['status-badge', getStatusClass(competition.status)]">
              {{ getStatusText(competition.status) }}
            </span>
            <div class="prize-info">
              <i class="el-icon-trophy"></i>
              <span class="prize-amount">¥{{ formatPrize(competition.totalPrize) }}</span>
              <span class="prize-label">总奖金</span>
            </div>
          </div>

          <template v-if="isLoggedIn">
            <el-button
              v-if="!isRegistered"
              type="primary"
              size="large"
              icon="el-icon-check"
              :disabled="competition.status === 3"
              @click="handleRegister"
            >
              {{ competition.status === 3 ? '竞赛已结束' : '立即报名' }}
            </el-button>
            <el-button
              v-else
              type="success"
              size="large"
              icon="el-icon-success"
              plain
              disabled
            >
              已报名
            </el-button>
          </template>
          <el-button
            v-else
            type="primary"
            size="large"
            icon="el-icon-check"
            @click="handleRegister"
          >
            立即报名
          </el-button>
        </div>

        <div class="header-side">
          <div class="time-card">
            <div class="time-item">
              <span class="label">报名开始</span>
              <span class="time">{{ formatDateTime(competition.registrationStartTime) }}</span>
            </div>
            <div class="time-item">
              <span class="label">报名截止</span>
              <span class="time">{{ formatDateTime(competition.registrationEndTime) }}</span>
            </div>
            <div class="time-item">
              <span class="label">提交截止</span>
              <span class="time">{{ formatDateTime(competition.submissionEndTime) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 详情内容 -->
      <div class="detail-content">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="赛题详情" name="detail">
            <div class="content-section">
              <h3>赛题说明</h3>
              <div class="content-text" v-html="competition.detail || '暂无详情'"></div>
            </div>

            <div class="content-section">
              <h3>数据说明</h3>
              <div class="content-text" v-html="competition.dataDescription || '暂无数据说明'"></div>
            </div>

            <div class="content-section">
              <h3>评测标准</h3>
              <div class="content-text" v-html="competition.evaluationStandard || '暂无评测标准'"></div>
            </div>

            <div class="content-section">
              <h3>提交要求</h3>
              <div class="content-text" v-html="competition.submissionRequirement || '暂无提交要求'"></div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="奖金分配" name="prize">
            <div class="prize-section">
              <div v-if="competition.prizeConfig" class="prize-list">
                <div v-for="(prize, index) in parsePrizeConfig(competition.prizeConfig)" :key="index" class="prize-item">
                  <div class="rank">第 {{ prize.rank }} 名</div>
                  <div class="amount">¥{{ (prize.amount / 100).toLocaleString() }}</div>
                </div>
              </div>
              <el-empty v-else description="暂无奖金配置"></el-empty>
            </div>
          </el-tab-pane>

          <el-tab-pane label="榜单" name="leaderboard">
            <div class="leaderboard-section">
              <div class="leaderboard-header" v-if="userRole === 'ADMIN'">
                <el-button
                  type="primary"
                  icon="el-icon-lock"
                  :loading="freezing"
                  @click="handleFreezeLeaderboard"
                >
                  冻结榜单
                </el-button>
              </div>

              <el-alert
                v-if="frozenLeaderboard"
                title="榜单已冻结"
                type="success"
                :closable="false"
                style="margin-bottom: 20px"
              >
                <template slot>
                  <p>快照ID: {{ frozenLeaderboard.snapshotId }}</p>
                  <p>冻结时间: {{ formatDateTime(frozenLeaderboard.frozenAt) }}</p>
                  <p>区块高度: {{ frozenLeaderboard.blockHeight }}</p>
                  <p>交易哈希: {{ frozenLeaderboard.chainTxHash }}</p>
                  <p>Merkle Root: {{ frozenLeaderboard.merkleRoot }}</p>
                </template>
              </el-alert>

              <el-table :data="leaderboardData" v-loading="loadingLeaderboard" border stripe>
                <el-table-column prop="rank" label="排名" width="80" align="center">
                  <template slot-scope="scope">
                    <el-tag v-if="scope.row.rank === 1" type="danger">🥇</el-tag>
                    <el-tag v-else-if="scope.row.rank === 2" type="warning">🥈</el-tag>
                    <el-tag v-else-if="scope.row.rank === 3" type="success">🥉</el-tag>
                    <span v-else>{{ scope.row.rank }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="username" label="用户名" min-width="150" />
                <el-table-column prop="score" label="得分" width="120" align="center">
                  <template slot-scope="scope">
                    <strong>{{ scope.row.score }}</strong>
                  </template>
                </el-table-column>
                <el-table-column prop="submitTime" label="提交时间" width="180">
                  <template slot-scope="scope">
                    {{ formatDateTime(scope.row.submitTime) }}
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <el-tab-pane label="FAQ" name="faq">
            <div class="faq-section">
              <el-empty description="常见问题开发中..."></el-empty>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
      </div>
    </div>

    <!-- 报名对话框 -->
    <el-dialog
      title="确认报名"
      :visible.sync="registerDialogVisible"
      width="500px"
    >
      <p>您确定要报名参加这个竞赛吗？</p>
      <p class="hint">报名后，您将可以下载数据集并提交作品。</p>
      <span slot="footer">
        <el-button @click="registerDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRegister">确认报名</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { mapActions, mapGetters } from 'vuex'
import { registerCompetition, checkUserRegistration } from '@/api/competition'

export default {
  name: 'CompetitionDetail',
  data() {
    return {
      competition: null,
      loading: false,
      activeTab: 'detail',
      isRegistered: false,
      registerDialogVisible: false,
      leaderboardData: [],
      frozenLeaderboard: null,
      loadingLeaderboard: false,
      freezing: false,
      userRole: null
    }
  },
  computed: {
    ...mapGetters('user', ['isLoggedIn', 'userId'])
  },
  watch: {
    activeTab(newTab) {
      if (newTab === 'leaderboard') {
        this.loadLeaderboardData()
      }
    }
  },
  created() {
    this.fetchData()
    // 从localStorage获取角色
    const userInfo = localStorage.getItem('userInfo')
    if (userInfo) {
      this.userRole = JSON.parse(userInfo).role
    }
  },
  methods: {
    ...mapActions('competition', ['fetchCompetitionById']),

    async fetchData() {
      this.loading = true
      try {
        await this.fetchCompetitionById(this.$route.params.id)
        this.competition = this.$store.state.competition.currentCompetition

        // 检查用户是否已报名
        if (this.isLoggedIn) {
          await this.checkRegistrationStatus()
        }
      } finally {
        this.loading = false
      }
    },

    async loadLeaderboardData() {
      this.loadingLeaderboard = true
      try {
        // 加载榜单数据
        const res = await this.$http.get(`/leaderboards/${this.$route.params.id}`)
        if (res.data.code === 200) {
          this.leaderboardData = res.data.data
        }

        // 加载冻结榜单信息
        const frozenRes = await this.$http.get(`/leaderboards/${this.$route.params.id}/frozen`)
        if (frozenRes.data.code === 200 && frozenRes.data.data) {
          this.frozenLeaderboard = frozenRes.data.data
          if (this.frozenLeaderboard.leaderboardData) {
            this.leaderboardData = this.frozenLeaderboard.leaderboardData
          }
        }
      } catch (error) {
        console.error('加载榜单失败', error)
      } finally {
        this.loadingLeaderboard = false
      }
    },

    async handleFreezeLeaderboard() {
      this.$confirm('确定要冻结榜单吗？冻结后将无法修改。', '确认', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        this.freezing = true
        try {
          const res = await this.$http.post(`/leaderboards/${this.$route.params.id}/freeze`)
          if (res.data.code === 200) {
            this.$message.success('榜单冻结成功！')
            await this.loadLeaderboardData()
          }
        } catch (error) {
          this.$message.error(error.response?.data?.message || '冻结失败')
        } finally {
          this.freezing = false
        }
      }).catch(() => {})
    },

    async checkRegistrationStatus() {
      try {
        const res = await checkUserRegistration(this.userId, this.$route.params.id)
        // 如果返回的数据不为null，说明已报名
        this.isRegistered = res.code === 200 && res.data !== null
      } catch (error) {
        console.error('检查报名状态失败', error)
        this.isRegistered = false
      }
    },

    handleRegister() {
      if (!this.isLoggedIn) {
        this.$message.warning('请先登录')
        this.$router.push({ name: 'Login', query: { redirect: this.$route.fullPath } })
        return
      }
      this.registerDialogVisible = true
    },

    async confirmRegister() {
      try {
        const res = await registerCompetition({
          userId: this.userId,
          competitionId: this.competition.id,
          agreementVersion: 'v1.0'
        })
        if (res.code === 200) {
          this.$message.success('报名成功！')
          this.isRegistered = true
          this.registerDialogVisible = false
        }
      } catch (error) {
        this.$message.error(error.message || '报名失败')
      }
    },

    handleSubmit() {
      this.$router.push({ name: 'Submissions', query: { competitionId: this.competition.id } })
    },

    getStatusClass(status) {
      const statusMap = { 0: 'draft', 1: 'ongoing', 2: 'ongoing', 3: 'ended' }
      return statusMap[status] || 'draft'
    },

    getStatusText(status) {
      const statusMap = { 0: '草稿', 1: '报名中', 2: '进行中', 3: '已结束', 4: '已取消' }
      return statusMap[status] || '未知'
    },

    formatDateTime(dateStr) {
      if (!dateStr) return '-'
      const date = new Date(dateStr)
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    },

    formatPrize(prize) {
      if (!prize) return '0'
      return (prize / 100).toLocaleString()
    },

    parsePrizeConfig(configStr) {
      try {
        return JSON.parse(configStr)
      } catch {
        return []
      }
    },

    getDetailImage() {
      // 如果有自定义封面图片，使用自定义图片，否则使用默认图片
      if (this.competition && this.competition.coverImage) {
        return this.competition.coverImage
      }
      // 返回默认图片 11.jpeg
      return require('@/images/11.jpeg')
    }
  }
}
</script>

<style lang="scss" scoped>
.competition-detail {
  min-height: calc(100vh - 64px);
}

.detail-wrapper {
  width: 100%;
}

.detail-banner {
  width: 100vw; // 使用视口宽度，突破容器限制
  height: 400px;
  overflow: hidden;
  margin-bottom: 40px;
  margin-left: calc(-50vw + 50%); // 居中并突破容器
  background: #f5f7fa; // 添加背景色

  .banner-image {
    width: 100%;
    height: 100%;
    object-fit: cover; // 使用cover填充整个区域
    object-position: center; // 图片居中显示
  }
}

.detail-header {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 40px;
  margin-bottom: 48px;

  .header-content {
    .title {
      font-size: 42px;
      font-weight: 800;
      color: #303133;
      margin-bottom: 16px;
      line-height: 1.3;
    }

    .description {
      font-size: 18px;
      color: #606266;
      line-height: 1.6;
      margin-bottom: 24px;
    }

    .meta-info {
      display: flex;
      align-items: center;
      gap: 24px;
      margin-bottom: 32px;

      .prize-info {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 12px 20px;
        background: linear-gradient(135deg, #ff6b6b 0%, #ee5a6f 100%);
        border-radius: 12px;
        color: white;

        i {
          font-size: 24px;
        }

        .prize-amount {
          font-size: 24px;
          font-weight: 700;
        }

        .prize-label {
          font-size: 14px;
          opacity: 0.9;
        }
      }
    }
  }

  .header-side {
    .time-card {
      background: white;
      border-radius: 16px;
      padding: 24px;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);

      .time-item {
        display: flex;
        flex-direction: column;
        gap: 8px;
        padding: 16px 0;

        &:not(:last-child) {
          border-bottom: 1px solid #f0f0f0;
        }

        .label {
          font-size: 14px;
          color: #909399;
        }

        .time {
          font-size: 16px;
          font-weight: 600;
          color: #303133;
        }
      }
    }
  }
}

.detail-content {
  background: white;
  border-radius: 16px;
  padding: 32px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.content-section {
  margin-bottom: 40px;

  &:last-child {
    margin-bottom: 0;
  }

  h3 {
    font-size: 24px;
    font-weight: 700;
    color: #303133;
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 2px solid #5b4cfa;
  }

  .content-text {
    font-size: 15px;
    line-height: 1.8;
    color: #606266;
  }
}

.prize-section {
  .prize-list {
    display: grid;
    gap: 16px;

    .prize-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 20px 24px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      border-radius: 12px;
      color: white;

      .rank {
        font-size: 18px;
        font-weight: 600;
      }

      .amount {
        font-size: 24px;
        font-weight: 700;
      }
    }
  }
}

.hint {
  color: #909399;
  font-size: 14px;
  margin-top: 12px;
}
</style>
