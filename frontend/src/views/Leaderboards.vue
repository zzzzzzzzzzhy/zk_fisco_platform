<template>
  <div class="leaderboards-page">
    <div class="container">
      <!-- 竞赛选择器 -->
      <div class="page-header hero-card">
        <div class="page-header__content">
          <h2>排行榜管理</h2>
          <p>统一管理公榜、私榜和公示节奏，保证榜单透明可信。</p>
        </div>
        <div class="page-header__actions">
          <el-select
            v-model="selectedCompetitionId"
            placeholder="选择竞赛"
            filterable
            @change="handleCompetitionChange"
            style="width: 300px; margin-right: 16px"
          >
            <el-option
              v-for="comp in competitions"
              :key="comp.id"
              :label="comp.title"
              :value="comp.id"
            />
          </el-select>
          <el-button
            v-if="selectedCompetitionId"
            type="primary"
            icon="el-icon-plus"
            @click="showCreateDialog = true"
          >
            创建榜单快照
          </el-button>
        </div>
      </div>

      <!-- 排行榜列表 -->
      <div v-if="selectedCompetitionId" class="leaderboards-list">
        <el-tabs v-model="activeTab" @tab-click="fetchLeaderboards">
          <el-tab-pane label="公榜" name="PUBLIC" />
          <el-tab-pane label="私榜" name="PRIVATE" />
        </el-tabs>

        <el-table
          v-loading="loading"
          :data="leaderboards"
          border
          stripe
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="snapshotTime" label="快照时间" width="180">
            <template slot-scope="scope">
              {{ formatDate(scope.row.snapshotTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="leaderboardType" label="榜单类型" width="100">
            <template slot-scope="scope">
              <el-tag :type="scope.row.leaderboardType === 'PUBLIC' ? 'success' : 'warning'">
                {{ scope.row.leaderboardType === 'PUBLIC' ? '公榜' : '私榜' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="frozen" label="冻结状态" width="100">
            <template slot-scope="scope">
              <el-tag :type="scope.row.frozen ? 'danger' : 'success'">
                {{ scope.row.frozen ? '已冻结' : '未冻结' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="publicityStatus" label="公示状态" width="120">
            <template slot-scope="scope">
              <el-tag :type="getPublicityStatusType(scope.row.publicityStatus)">
                {{ getPublicityStatusText(scope.row.publicityStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="publicityEndTime" label="公示结束时间" width="180">
            <template slot-scope="scope">
              {{ formatDate(scope.row.publicityEndTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template slot-scope="scope">
              <el-button size="mini" @click="viewDetail(scope.row)">查看</el-button>
              <el-button
                v-if="!scope.row.frozen"
                size="mini"
                type="warning"
                @click="handleFreeze(scope.row)"
              >
                冻结
              </el-button>
              <el-button
                v-if="scope.row.frozen"
                size="mini"
                type="success"
                @click="handleUnfreeze(scope.row)"
              >
                解冻
              </el-button>
              <el-button
                v-if="scope.row.publicityStatus === 'IN_PUBLICITY'"
                size="mini"
                type="primary"
                @click="handleConfirm(scope.row)"
              >
                确认
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="leaderboards.length === 0 && !loading" class="empty-state">
          <p>暂无榜单数据</p>
        </div>
      </div>

      <div v-else class="empty-state">
        <p>请选择竞赛查看排行榜</p>
      </div>

      <!-- 创建榜单对话框 -->
      <el-dialog
        title="创建榜单快照"
        :visible.sync="showCreateDialog"
        width="500px"
      >
        <el-form :model="createForm" :rules="createRules" ref="createForm" label-width="120px">
          <el-form-item label="榜单类型" prop="leaderboardType">
            <el-radio-group v-model="createForm.leaderboardType">
              <el-radio label="PUBLIC">公榜</el-radio>
              <el-radio label="PRIVATE">私榜</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="快照时间" prop="snapshotTime">
            <el-date-picker
              v-model="createForm.snapshotTime"
              type="datetime"
              placeholder="选择快照时间"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="公示天数" prop="publicityDays">
            <el-input-number v-model="createForm.publicityDays" :min="1" :max="30" />
          </el-form-item>
        </el-form>
        <span slot="footer">
          <el-button @click="showCreateDialog = false">取消</el-button>
          <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
        </span>
      </el-dialog>
    </div>
  </div>
</template>

<script>
// import { getCompetitions } from '@/api/competition'
// import { getLeaderboards, createLeaderboard, freezeLeaderboard, unfreezeLeaderboard, confirmLeaderboard } from '@/api/leaderboard'
import dayjs from 'dayjs'

// 模拟数据
const MOCK_COMPETITIONS = [
  { id: 1, title: '全国大学生数学建模竞赛', status: 'ONGOING' },
  { id: 2, title: 'ACM程序设计竞赛', status: 'ONGOING' },
  { id: 3, title: '数据分析挑战赛', status: 'FINISHED' },
  { id: 4, title: '人工智能创新大赛', status: 'UPCOMING' }
]

const MOCK_LEADERBOARDS = {
  1: [ // 竞赛1的排行榜
    {
      id: 1,
      competitionId: 1,
      leaderboardType: 'PUBLIC',
      snapshotTime: '2025-10-20 14:30:00',
      frozen: false,
      publicityStatus: 'IN_PUBLICITY',
      publicityEndTime: '2025-10-27 14:30:00'
    },
    {
      id: 2,
      competitionId: 1,
      leaderboardType: 'PRIVATE',
      snapshotTime: '2025-10-22 10:00:00',
      frozen: true,
      publicityStatus: 'CONFIRMED',
      publicityEndTime: '2025-10-29 10:00:00',
      confirmedAt: '2025-10-28 15:20:00'
    },
    {
      id: 3,
      competitionId: 1,
      leaderboardType: 'PUBLIC',
      snapshotTime: '2025-10-15 16:00:00',
      frozen: true,
      publicityStatus: 'CONFIRMED',
      publicityEndTime: '2025-10-22 16:00:00',
      confirmedAt: '2025-10-21 09:30:00'
    }
  ],
  2: [ // 竞赛2的排行榜
    {
      id: 4,
      competitionId: 2,
      leaderboardType: 'PUBLIC',
      snapshotTime: '2025-10-23 18:00:00',
      frozen: false,
      publicityStatus: 'IN_PUBLICITY',
      publicityEndTime: '2025-10-30 18:00:00'
    },
    {
      id: 5,
      competitionId: 2,
      leaderboardType: 'PRIVATE',
      snapshotTime: '2025-10-23 18:00:00',
      frozen: false,
      publicityStatus: 'IN_PUBLICITY',
      publicityEndTime: '2025-10-30 18:00:00'
    }
  ],
  3: [ // 竞赛3的排行榜
    {
      id: 6,
      competitionId: 3,
      leaderboardType: 'PUBLIC',
      snapshotTime: '2025-10-10 12:00:00',
      frozen: true,
      publicityStatus: 'CONFIRMED',
      publicityEndTime: '2025-10-17 12:00:00',
      confirmedAt: '2025-10-17 14:00:00'
    }
  ]
}

export default {
  // eslint-disable-next-line vue/multi-word-component-names
  name: 'Leaderboards',
  data() {
    return {
      competitions: [],
      selectedCompetitionId: null,
      activeTab: 'PUBLIC',
      leaderboards: [],
      loading: false,
      showCreateDialog: false,
      creating: false,
      createForm: {
        leaderboardType: 'PUBLIC',
        snapshotTime: new Date(),
        publicityDays: 7
      },
      createRules: {
        leaderboardType: [
          { required: true, message: '请选择榜单类型', trigger: 'change' }
        ],
        snapshotTime: [
          { required: true, message: '请选择快照时间', trigger: 'change' }
        ],
        publicityDays: [
          { required: true, message: '请输入公示天数', trigger: 'blur' }
        ]
      }
    }
  },
  created() {
    this.loadCompetitions()
  },
  methods: {
    async loadCompetitions() {
      // 使用模拟数据
      setTimeout(() => {
        this.competitions = MOCK_COMPETITIONS
      }, 300)
    },

    handleCompetitionChange() {
      this.fetchLeaderboards()
    },

    async fetchLeaderboards() {
      if (!this.selectedCompetitionId) return

      this.loading = true
      // 使用模拟数据
      setTimeout(() => {
        const allLeaderboards = MOCK_LEADERBOARDS[this.selectedCompetitionId] || []
        // 根据当前选中的tab过滤
        this.leaderboards = allLeaderboards.filter(item => item.leaderboardType === this.activeTab)
        this.loading = false
      }, 500)
    },

    async handleCreate() {
      this.$refs.createForm.validate(async (valid) => {
        if (!valid) return

        this.creating = true
        // 模拟创建
        setTimeout(() => {
          const newId = Math.max(...Object.values(MOCK_LEADERBOARDS).flat().map(lb => lb.id), 0) + 1
          const newLeaderboard = {
            id: newId,
            competitionId: this.selectedCompetitionId,
            leaderboardType: this.createForm.leaderboardType,
            snapshotTime: dayjs(this.createForm.snapshotTime).format('YYYY-MM-DD HH:mm:ss'),
            frozen: false,
            publicityStatus: 'IN_PUBLICITY',
            publicityEndTime: dayjs(this.createForm.snapshotTime).add(this.createForm.publicityDays, 'day').format('YYYY-MM-DD HH:mm:ss')
          }

          if (!MOCK_LEADERBOARDS[this.selectedCompetitionId]) {
            MOCK_LEADERBOARDS[this.selectedCompetitionId] = []
          }
          MOCK_LEADERBOARDS[this.selectedCompetitionId].push(newLeaderboard)

          this.$message.success('创建成功')
          this.showCreateDialog = false
          this.creating = false
          this.fetchLeaderboards()
        }, 800)
      })
    },

    async handleFreeze(row) {
      try {
        await this.$confirm('确认冻结此榜单吗？', '提示', { type: 'warning' })
        // 模拟冻结
        setTimeout(() => {
          row.frozen = true
          this.$message.success('冻结成功')
        }, 500)
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error('冻结失败')
        }
      }
    },

    async handleUnfreeze(row) {
      try {
        await this.$confirm('确认解冻此榜单吗？', '提示', { type: 'warning' })
        // 模拟解冻
        setTimeout(() => {
          row.frozen = false
          this.$message.success('解冻成功')
        }, 500)
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error('解冻失败')
        }
      }
    },

    async handleConfirm(row) {
      try {
        await this.$confirm('确认此榜单吗？确认后将结束公示期。', '提示', { type: 'warning' })
        // 模拟确认
        setTimeout(() => {
          row.publicityStatus = 'CONFIRMED'
          row.confirmedAt = dayjs().format('YYYY-MM-DD HH:mm:ss')
          this.$message.success('确认成功')
        }, 500)
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error('确认失败')
        }
      }
    },

    viewDetail(row) {
      this.$router.push({ name: 'LeaderboardDetail', params: { id: row.id } })
    },

    formatDate(date) {
      return date ? dayjs(date).format('YYYY-MM-DD HH:mm:ss') : '-'
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
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/admin-common.scss';

.leaderboards-page {
  min-height: calc(100vh - 64px);
  padding: 40px 0;
}

.page-header {
  margin-bottom: var(--spacing-xl);

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--spacing-md);
  }

  @include respond-to(sm) {
    flex-direction: column;
    align-items: flex-start;

    &__actions {
      width: 100%;
      flex-direction: column;

      .el-select,
      .el-button {
        width: 100%;
      }
    }
  }
}

.leaderboards-list {
  background: var(--bg-primary);
  border-radius: var(--border-radius-xl);
  padding: var(--spacing-xl);
  box-shadow: var(--shadow-light);
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
