<template>
  <div class="leaderboard-detail-page">
    <div class="container">
      <!-- 页面头部 -->
      <div class="page-header">
        <el-button icon="el-icon-arrow-left" @click="$router.back()">返回</el-button>
        <div class="header-info">
          <h2>{{ leaderboard.leaderboardType === 'PUBLIC' ? '公榜' : '私榜' }} - {{ formatDate(leaderboard.snapshotTime) }}</h2>
          <div class="status-badges">
            <el-tag :type="leaderboard.frozen ? 'danger' : 'success'">
              {{ leaderboard.frozen ? '已冻结' : '未冻结' }}
            </el-tag>
            <el-tag :type="getPublicityStatusType(leaderboard.publicityStatus)" style="margin-left: 8px">
              {{ getPublicityStatusText(leaderboard.publicityStatus) }}
            </el-tag>
          </div>
        </div>
        <el-button type="primary" icon="el-icon-edit" @click="showAppealDialog = true">
          提交异议
        </el-button>
      </div>

      <!-- 榜单信息卡片 -->
      <el-card class="info-card" shadow="never">
        <div class="info-grid">
          <div class="info-item">
            <span class="label">竞赛ID</span>
            <span class="value">{{ leaderboard.competitionId }}</span>
          </div>
          <div class="info-item">
            <span class="label">快照时间</span>
            <span class="value">{{ formatDate(leaderboard.snapshotTime) }}</span>
          </div>
          <div class="info-item">
            <span class="label">公示开始</span>
            <span class="value">{{ formatDate(leaderboard.publicityStartTime) }}</span>
          </div>
          <div class="info-item">
            <span class="label">公示结束</span>
            <span class="value">{{ formatDate(leaderboard.publicityEndTime) }}</span>
          </div>
          <div class="info-item">
            <span class="label">公示天数</span>
            <span class="value">{{ leaderboard.publicityDays }} 天</span>
          </div>
          <div class="info-item">
            <span class="label">确认时间</span>
            <span class="value">{{ formatDate(leaderboard.confirmedAt) }}</span>
          </div>
        </div>
      </el-card>

      <!-- 排名列表 -->
      <div class="rankings-list">
        <h3>排名详情</h3>
        <el-alert
          v-if="leaderboard.leaderboardType === 'PUBLIC'"
          title="公榜仅展示公开测试集得分和排名"
          type="info"
          :closable="false"
          style="margin-bottom: 16px"
        />
        <el-alert
          v-if="leaderboard.leaderboardType === 'PRIVATE'"
          title="私榜展示隐藏测试集得分和排名（竞赛结束后公布）"
          type="warning"
          :closable="false"
          style="margin-bottom: 16px"
        />

        <el-table
          v-loading="loading"
          :data="rankings"
          border
          stripe
        >
          <el-table-column
            prop="rank"
            label="排名"
            width="80"
            align="center"
          >
            <template slot-scope="scope">
              <div class="rank-badge" :class="getRankClass(scope.row.rank)">
                {{ scope.row.rank }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="userId" label="用户ID" width="100" />
          <el-table-column prop="username" label="用户名" min-width="150" />
          <el-table-column prop="score" label="得分" width="120" align="center">
            <template slot-scope="scope">
              <span class="score-text">{{ formatScore(scope.row.score) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="submissionCount" label="提交次数" width="100" align="center" />
          <el-table-column prop="lastSubmitTime" label="最后提交时间" width="180">
            <template slot-scope="scope">
              {{ formatDate(scope.row.lastSubmitTime) }}
            </template>
          </el-table-column>
        </el-table>

        <div v-if="rankings.length === 0 && !loading" class="empty-state">
          <p>暂无排名数据</p>
        </div>
      </div>

      <!-- 提交异议对话框 -->
      <el-dialog
        title="提交榜单异议"
        :visible.sync="showAppealDialog"
        width="600px"
      >
        <el-form :model="appealForm" :rules="appealRules" ref="appealForm" label-width="120px">
          <el-form-item label="异议类型" prop="appealType">
            <el-select v-model="appealForm.appealType" placeholder="请选择异议类型" style="width: 100%">
              <el-option label="分数错误" value="SCORE_ERROR" />
              <el-option label="排名错误" value="RANK_ERROR" />
              <el-option label="数据错误" value="DATA_ERROR" />
              <el-option label="其他" value="OTHER" />
            </el-select>
          </el-form-item>
          <el-form-item label="异议理由" prop="appealReason">
            <el-input
              v-model="appealForm.appealReason"
              type="textarea"
              :rows="4"
              placeholder="请详细描述异议理由"
            />
          </el-form-item>
          <el-form-item label="证据文件">
            <el-upload
              action="#"
              :auto-upload="false"
              :on-change="handleFileChange"
              :file-list="appealForm.evidenceFiles"
              multiple
            >
              <el-button size="small" type="primary">点击上传</el-button>
              <div slot="tip" class="el-upload__tip">支持上传多个文件作为证据</div>
            </el-upload>
          </el-form-item>
        </el-form>
        <span slot="footer">
          <el-button @click="showAppealDialog = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmitAppeal">提交</el-button>
        </span>
      </el-dialog>
    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
// import { getLeaderboardById, submitAppeal } from '@/api/leaderboard'
import dayjs from 'dayjs'

// 模拟排行榜数据
const MOCK_LEADERBOARD_DETAILS = {
  1: {
    id: 1,
    competitionId: 1,
    leaderboardType: 'PUBLIC',
    snapshotTime: '2025-10-20 14:30:00',
    frozen: false,
    publicityStatus: 'IN_PUBLICITY',
    publicityStartTime: '2025-10-20 14:30:00',
    publicityEndTime: '2025-10-27 14:30:00',
    publicityDays: 7,
    confirmedAt: null
  },
  2: {
    id: 2,
    competitionId: 1,
    leaderboardType: 'PRIVATE',
    snapshotTime: '2025-10-22 10:00:00',
    frozen: true,
    publicityStatus: 'CONFIRMED',
    publicityStartTime: '2025-10-22 10:00:00',
    publicityEndTime: '2025-10-29 10:00:00',
    publicityDays: 7,
    confirmedAt: '2025-10-28 15:20:00'
  },
  3: {
    id: 3,
    competitionId: 1,
    leaderboardType: 'PUBLIC',
    snapshotTime: '2025-10-15 16:00:00',
    frozen: true,
    publicityStatus: 'CONFIRMED',
    publicityStartTime: '2025-10-15 16:00:00',
    publicityEndTime: '2025-10-22 16:00:00',
    publicityDays: 7,
    confirmedAt: '2025-10-21 09:30:00'
  },
  4: {
    id: 4,
    competitionId: 2,
    leaderboardType: 'PUBLIC',
    snapshotTime: '2025-10-23 18:00:00',
    frozen: false,
    publicityStatus: 'IN_PUBLICITY',
    publicityStartTime: '2025-10-23 18:00:00',
    publicityEndTime: '2025-10-30 18:00:00',
    publicityDays: 7,
    confirmedAt: null
  },
  5: {
    id: 5,
    competitionId: 2,
    leaderboardType: 'PRIVATE',
    snapshotTime: '2025-10-23 18:00:00',
    frozen: false,
    publicityStatus: 'IN_PUBLICITY',
    publicityStartTime: '2025-10-23 18:00:00',
    publicityEndTime: '2025-10-30 18:00:00',
    publicityDays: 7,
    confirmedAt: null
  },
  6: {
    id: 6,
    competitionId: 3,
    leaderboardType: 'PUBLIC',
    snapshotTime: '2025-10-10 12:00:00',
    frozen: true,
    publicityStatus: 'CONFIRMED',
    publicityStartTime: '2025-10-10 12:00:00',
    publicityEndTime: '2025-10-17 12:00:00',
    publicityDays: 7,
    confirmedAt: '2025-10-17 14:00:00'
  }
}

export default {
  name: 'LeaderboardDetail',
  data() {
    return {
      leaderboard: {},
      rankings: [],
      loading: false,
      showAppealDialog: false,
      submitting: false,
      appealForm: {
        appealType: '',
        appealReason: '',
        evidenceFiles: []
      },
      appealRules: {
        appealType: [
          { required: true, message: '请选择异议类型', trigger: 'change' }
        ],
        appealReason: [
          { required: true, message: '请输入异议理由', trigger: 'blur' },
          { min: 10, message: '异议理由至少10个字符', trigger: 'blur' }
        ]
      }
    }
  },
  computed: {
    ...mapGetters('user', ['userId'])
  },
  created() {
    this.fetchLeaderboardDetail()
  },
  methods: {
    async fetchLeaderboardDetail() {
      this.loading = true
      const leaderboardId = this.$route.params.id

      // 使用模拟数据
      setTimeout(() => {
        const mockData = MOCK_LEADERBOARD_DETAILS[leaderboardId]
        if (mockData) {
          this.leaderboard = mockData
          this.generateMockRankings()
        } else {
          this.$message.error('排行榜不存在')
          this.$router.back()
        }
        this.loading = false
      }, 500)
    },

    // 生成模拟排名数据
    generateMockRankings() {
      const usernames = [
        'AlphaAce', 'BetaMaster', 'GammaCoder', 'DeltaGuru', 'EpsilonPro',
        'ZetaHacker', 'EtaWizard', 'ThetaNinja', 'IotaGenius', 'KappaDev',
        'LambdaKing', 'MuQueen', 'NuChampion', 'XiExpert', 'OmicronStar',
        'PiLegend', 'RhoHero', 'SigmaElite', 'TauMaster', 'UpsilonPro',
        'PhiWinner', 'ChiVictory', 'PsiChampion', 'OmegaBoss', 'AlphaPrime'
      ]

      this.rankings = Array.from({ length: 25 }, (_, i) => {
        const rank = i + 1
        // 第一名得分最高，后面逐渐降低
        const baseScore = 98.5
        const scoreDecrease = i * 1.2
        const randomVariation = (Math.random() - 0.5) * 0.5
        const score = Math.max(0, baseScore - scoreDecrease + randomVariation)

        return {
          rank,
          userId: 1001 + i,
          username: usernames[i] || `Player_${1001 + i}`,
          score: score.toFixed(4),
          submissionCount: Math.floor(Math.random() * 80) + 5,
          lastSubmitTime: dayjs().subtract(i * 30 + Math.random() * 60, 'minute').format('YYYY-MM-DD HH:mm:ss')
        }
      })
    },

    handleFileChange(file, fileList) {
      this.appealForm.evidenceFiles = fileList
    },

    async handleSubmitAppeal() {
      this.$refs.appealForm.validate(async (valid) => {
        if (!valid) return

        this.submitting = true
        // 模拟提交
        setTimeout(() => {
          this.$message.success('异议提交成功，等待审核')
          this.showAppealDialog = false
          this.$refs.appealForm.resetFields()
          this.appealForm.evidenceFiles = []
          this.submitting = false
        }, 800)
      })
    },

    formatDate(date) {
      return date ? dayjs(date).format('YYYY-MM-DD HH:mm:ss') : '-'
    },

    formatScore(score) {
      return parseFloat(score).toFixed(4)
    },

    getPublicityStatusType(status) {
      const typeMap = {
        'IN_PUBLICITY': 'warning',
        'CONFIRMED': 'success',
        'CANCELLED': 'danger'
      }
      return typeMap[status] || 'info'
    },

    getPublicityStatusText(status) {
      const textMap = {
        'IN_PUBLICITY': '公示中',
        'CONFIRMED': '已确认',
        'CANCELLED': '已取消'
      }
      return textMap[status] || status
    },

    getRankClass(rank) {
      if (rank === 1) return 'rank-1'
      if (rank === 2) return 'rank-2'
      if (rank === 3) return 'rank-3'
      return ''
    }
  }
}
</script>

<style lang="scss" scoped>
.leaderboard-detail-page {
  min-height: calc(100vh - 64px);
  padding: 40px 0;
}

.page-header {
  display: flex;
  align-items: center;
  margin-bottom: 24px;
  gap: 16px;

  .header-info {
    flex: 1;

    h2 {
      font-size: 24px;
      font-weight: 700;
      color: #303133;
      margin: 0 0 8px 0;
    }

    .status-badges {
      display: flex;
      gap: 8px;
    }
  }
}

.info-card {
  margin-bottom: 24px;

  .info-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 24px;

    .info-item {
      display: flex;
      flex-direction: column;

      .label {
        font-size: 12px;
        color: #909399;
        margin-bottom: 8px;
      }

      .value {
        font-size: 14px;
        color: #303133;
        font-weight: 500;
      }
    }
  }
}

.rankings-list {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);

  h3 {
    margin: 0 0 16px 0;
    font-size: 18px;
    font-weight: 600;
  }

  .rank-badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 50%;
    font-weight: 700;
    background: #f5f7fa;
    color: #606266;

    &.rank-1 {
      background: linear-gradient(135deg, #ffd700, #ffed4e);
      color: #fff;
    }

    &.rank-2 {
      background: linear-gradient(135deg, #c0c0c0, #e8e8e8);
      color: #fff;
    }

    &.rank-3 {
      background: linear-gradient(135deg, #cd7f32, #e9a76f);
      color: #fff;
    }
  }

  .score-text {
    font-size: 16px;
    font-weight: 600;
    color: #409eff;
  }
}

.empty-state {
  text-align: center;
  padding: 60px 0;
  color: #909399;
  font-size: 14px;

  p {
    margin: 0;
  }
}
</style>
