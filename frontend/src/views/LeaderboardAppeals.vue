<template>
  <div class="admin-appeals admin-page">
    <div class="container">
      <div class="page-header hero-card">
        <div class="page-header__content">
          <h2>榜单异议管理</h2>
          <p>快速洞察当前所有排行榜异议，并在一个界面完成筛查、审核与沟通。</p>
        </div>
        <div class="page-header__actions">
          <el-button icon="el-icon-refresh" @click="refreshData">
            刷新数据
          </el-button>
        </div>
      </div>

      <!-- 统计卡片 -->
      <div class="stats-cards">
        <div
          v-for="card in statusStats"
          :key="card.key"
          class="stat-card"
        >
          <div :class="['stat-icon', card.variant]">
            <i :class="card.icon"></i>
          </div>
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-helper">{{ card.helper }}</div>
        </div>
      </div>

      <!-- 筛选条件 -->
      <div class="filter-section">
        <el-form
          ref="filterForm"
          :inline="true"
          :model="filters"
          label-width="80px"
          @submit.native.prevent
        >
          <el-form-item label="竞赛ID">
            <el-input
              v-model="filters.competitionId"
              placeholder="输入竞赛ID"
              clearable
            />
          </el-form-item>
          <el-form-item label="状态">
            <el-select
              v-model="filters.status"
              placeholder="全部状态"
              clearable
            >
              <el-option label="待处理" value="PENDING" />
              <el-option label="审核中" value="REVIEWING" />
              <el-option label="已接受" value="ACCEPTED" />
              <el-option label="已拒绝" value="REJECTED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <div class="filter-actions">
              <el-button type="primary" icon="el-icon-search" @click="handleFilterSubmit">
                查询
              </el-button>
              <el-button icon="el-icon-refresh-left" @click="resetFilters">
                重置
              </el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>

      <!-- 异议列表 -->
      <div class="data-table-container">
        <div class="table-header">
          <div class="table-title">
            异议列表
            <span class="table-subtitle">实时同步榜单异议处理情况</span>
          </div>
          <div class="table-actions">
            <el-button type="primary" plain icon="el-icon-share" @click="exportCurrent">
              导出当前结果
            </el-button>
          </div>
        </div>

        <el-table
          v-loading="loading"
          :data="appeals"
          :header-cell-style="{ backgroundColor: 'var(--bg-secondary)' }"
        >
          <el-table-column prop="id" label="ID" width="90" />
          <el-table-column prop="competitionId" label="竞赛" width="120">
            <template slot-scope="scope">
              <div class="table-competition">
                <span class="id">#{{ scope.row.competitionId }}</span>
                <small class="meta">{{ scope.row.competitionName || '未命名竞赛' }}</small>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="userId" label="申诉用户" min-width="140">
            <template slot-scope="scope">
              <div class="user-cell">
                <i class="el-icon-user"></i>
                <span>{{ scope.row.userId }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="appealType" label="异议类型" width="140">
            <template slot-scope="scope">
              <el-tag size="small" class="tag-elevated">
                {{ getAppealTypeText(scope.row.appealType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="appealReason"
            label="异议内容"
            min-width="240"
            show-overflow-tooltip
          />
          <el-table-column prop="status" label="状态" width="120">
            <template slot-scope="scope">
              <el-tag
                size="small"
                :type="getStatusType(scope.row.status)"
                class="status-badge"
              >
                {{ getStatusText(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="提交时间" width="180">
            <template slot-scope="scope">
              {{ formatDate(scope.row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template slot-scope="scope">
              <div class="table-actions-group">
                <el-button size="mini" type="text" @click="viewDetail(scope.row)">
                  <i class="el-icon-view"></i>
                  <span>详情</span>
                </el-button>
                <el-button
                  v-if="scope.row.status === 'PENDING'"
                  size="mini"
                  type="text"
                  class="success"
                  @click="showReviewDialog(scope.row)"
                >
                  <i class="el-icon-edit-outline"></i>
                  <span>审核</span>
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          :current-page.sync="pagination.current"
          :page-size="pagination.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>

      <!-- 审核对话框 -->
      <el-dialog
        title="审核异议"
        :visible.sync="showReview"
        width="560px"
        custom-class="admin-dialog"
      >
        <div class="dialog-summary">
          <div class="summary-title">{{ getAppealTypeText(currentAppeal.appealType) }}</div>
          <p class="summary-reason">{{ currentAppeal.appealReason }}</p>
        </div>
        <el-form :model="reviewForm" :rules="reviewRules" ref="reviewForm" label-width="90px">
          <el-form-item label="审核结果" prop="result">
            <el-radio-group v-model="reviewForm.result">
              <el-radio label="ACCEPTED">接受</el-radio>
              <el-radio label="REJECTED">拒绝</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="审核说明" prop="reviewNotes">
            <el-input
              v-model="reviewForm.reviewNotes"
              type="textarea"
              :rows="4"
              placeholder="补充审核原因，便于追溯"
            />
          </el-form-item>
        </el-form>
        <span slot="footer">
          <el-button @click="showReview = false">取消</el-button>
          <el-button type="primary" :loading="reviewing" @click="handleReview">提交审核</el-button>
        </span>
      </el-dialog>

      <!-- 详情对话框 -->
      <el-dialog
        title="异议详情"
        :visible.sync="showDetail"
        width="640px"
        custom-class="admin-dialog"
      >
        <div class="detail-grid">
          <div class="detail-item">
            <span class="label">异议ID</span>
            <span class="value">#{{ currentAppeal.id }}</span>
          </div>
          <div class="detail-item">
            <span class="label">竞赛</span>
            <span class="value">#{{ currentAppeal.competitionId }}</span>
          </div>
          <div class="detail-item">
            <span class="label">申诉用户</span>
            <span class="value">{{ currentAppeal.userId }}</span>
          </div>
          <div class="detail-item">
            <span class="label">异议类型</span>
            <span class="value">{{ getAppealTypeText(currentAppeal.appealType) }}</span>
          </div>
          <div class="detail-item detail-item--span">
            <span class="label">异议内容</span>
            <p class="value multiline">{{ currentAppeal.appealReason }}</p>
          </div>
          <div class="detail-item">
            <span class="label">状态</span>
            <el-tag size="small" :type="getStatusType(currentAppeal.status)">
              {{ getStatusText(currentAppeal.status) }}
            </el-tag>
          </div>
          <div class="detail-item" v-if="currentAppeal.reviewResult">
            <span class="label">审核结果</span>
            <span class="value">{{ getStatusText(currentAppeal.reviewResult) }}</span>
          </div>
          <div class="detail-item detail-item--span" v-if="currentAppeal.reviewNotes">
            <span class="label">审核说明</span>
            <p class="value multiline">{{ currentAppeal.reviewNotes }}</p>
          </div>
          <div class="detail-item">
            <span class="label">提交时间</span>
            <span class="value">{{ formatDate(currentAppeal.createdAt) }}</span>
          </div>
          <div class="detail-item" v-if="currentAppeal.reviewedAt">
            <span class="label">审核时间</span>
            <span class="value">{{ formatDate(currentAppeal.reviewedAt) }}</span>
          </div>
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script>
import { getAppeals, reviewAppeal } from '@/api/leaderboard'

export default {
  name: 'LeaderboardAppeals',
  data() {
    return {
      filters: {
        competitionId: '',
        status: ''
      },
      appeals: [],
      total: 0,
      loading: false,
      pagination: {
        current: 1,
        size: 10
      },
      showReview: false,
      showDetail: false,
      reviewing: false,
      currentAppeal: {},
      reviewForm: {
        result: '',
        reviewNotes: ''
      },
      reviewRules: {
        result: [
          { required: true, message: '请选择审核结果', trigger: 'change' }
        ],
        reviewNotes: [
          { required: true, message: '请输入审核说明', trigger: 'blur' }
        ]
      }
    }
  },
  computed: {
    statusStats() {
      const pending = this.appeals.filter(item => item.status === 'PENDING').length
      const reviewing = this.appeals.filter(item => item.status === 'REVIEWING').length
      const accepted = this.appeals.filter(item => item.status === 'ACCEPTED').length
      const rejected = this.appeals.filter(item => item.status === 'REJECTED').length

      return [
        {
          key: 'total',
          label: '全部异议',
          value: this.total,
          icon: 'el-icon-collection',
          helper: '总记录量',
          variant: 'primary'
        },
        {
          key: 'pending',
          label: '待处理',
          value: pending,
          icon: 'el-icon-time',
          helper: '等待分派',
          variant: 'warning'
        },
        {
          key: 'reviewing',
          label: '审核中',
          value: reviewing,
          icon: 'el-icon-loading',
          helper: '审核进行中',
          variant: 'info'
        },
        {
          key: 'accepted',
          label: '已接受',
          value: accepted,
          icon: 'el-icon-circle-check',
          helper: '确认存在问题',
          variant: 'success'
        },
        {
          key: 'rejected',
          label: '已拒绝',
          value: rejected,
          icon: 'el-icon-circle-close',
          helper: '无效异议',
          variant: 'danger'
        }
      ]
    }
  },
  created() {
    this.fetchAppeals()
  },
  methods: {
    async fetchAppeals() {
      this.loading = true
      try {
        const params = {
          ...this.filters,
          current: this.pagination.current,
          size: this.pagination.size
        }
        const res = await getAppeals(params)
        if (res.code === 200) {
          this.appeals = res.data.records || []
          this.total = res.data.total || 0
        }
      } catch (error) {
        this.$message.error('加载异议列表失败')
      } finally {
        this.loading = false
      }
    },

    refreshData() {
      this.fetchAppeals()
    },

    handleFilterSubmit() {
      this.pagination.current = 1
      this.fetchAppeals()
    },

    handlePageChange(page) {
      this.pagination.current = page
      this.fetchAppeals()
    },

    resetFilters() {
      this.filters = {
        competitionId: '',
        status: ''
      }
      this.pagination.current = 1
      this.fetchAppeals()
    },

    showReviewDialog(row) {
      this.currentAppeal = row
      this.reviewForm = {
        result: '',
        reviewNotes: ''
      }
      this.showReview = true
    },

    async handleReview() {
      this.$refs.reviewForm.validate(async (valid) => {
        if (!valid) return

        this.reviewing = true
        try {
          const data = {
            status: this.reviewForm.result,
            reviewResult: this.reviewForm.result,
            reviewNotes: this.reviewForm.reviewNotes,
            reviewerId: this.$store.state.user.userId
          }

          const res = await reviewAppeal(this.currentAppeal.id, data)
          if (res.code === 200) {
            this.$message.success('审核成功')
            this.showReview = false
            this.fetchAppeals()
          }
        } catch (error) {
          this.$message.error(error.message || '审核失败')
        } finally {
          this.reviewing = false
        }
      })
    },

    viewDetail(row) {
      this.currentAppeal = row
      this.showDetail = true
    },

    exportCurrent() {
      if (!this.appeals.length) {
        this.$message.info('当前没有可导出的记录')
        return
      }
      const content = JSON.stringify(this.appeals, null, 2)
      const blob = new Blob([content], { type: 'application/json' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `appeals-${Date.now()}.json`
      link.click()
      window.URL.revokeObjectURL(url)
      this.$message.success('已导出当前页数据')
    },

    formatDate(dateStr) {
      if (!dateStr) return '-'
      const date = new Date(dateStr)
      const y = date.getFullYear()
      const m = String(date.getMonth() + 1).padStart(2, '0')
      const d = String(date.getDate()).padStart(2, '0')
      const hh = String(date.getHours()).padStart(2, '0')
      const mm = String(date.getMinutes()).padStart(2, '0')
      return `${y}-${m}-${d} ${hh}:${mm}`
    },

    getAppealTypeText(type) {
      const textMap = {
        'SCORE_ERROR': '分数错误',
        'RANK_ERROR': '排名错误',
        'DATA_ERROR': '数据错误',
        'OTHER': '其他'
      }
      return textMap[type] || type
    },

    getStatusType(status) {
      const typeMap = {
        'PENDING': 'warning',
        'REVIEWING': 'info',
        'ACCEPTED': 'success',
        'REJECTED': 'danger'
      }
      return typeMap[status] || 'info'
    },

    getStatusText(status) {
      const textMap = {
        'PENDING': '待处理',
        'REVIEWING': '审核中',
        'ACCEPTED': '已接受',
        'REJECTED': '已拒绝'
      }
      return textMap[status] || status
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/admin-design-system.scss';
@import '@/styles/admin-common.scss';

.admin-appeals {
  padding-bottom: var(--spacing-3xl);

  .page-header {
    align-items: flex-start;

    &__content {
      max-width: 560px;

      p {
        margin: var(--spacing-sm) 0 0 0;
        color: var(--text-secondary);
        font-size: $font-size-base;
      }
    }

    &__actions {
      .el-button {
        border-radius: $border-radius-lg;
      }
    }

    &.hero-card {
      .page-header__content {
        p {
          color: rgba(255, 255, 255, 0.85);
        }
      }
    }
  }

  .stats-cards {
    .stat-card {
      position: relative;

      .stat-helper {
        font-size: $font-size-xs;
        color: var(--text-secondary);
        margin-top: var(--spacing-xs);
      }
    }
  }

  .filter-section {
    .el-form-item {
      margin-right: var(--spacing-lg);
      margin-bottom: 0;
    }

    .filter-actions {
      display: flex;
      gap: var(--spacing-sm);
    }

    @include respond-to(sm) {
      .el-form-item {
        width: 100%;
      }

      .filter-actions {
        width: 100%;
        flex-direction: column;

        .el-button {
          width: 100%;
        }
      }
    }
  }

  .table-title {
    display: flex;
    flex-direction: column;
    font-size: $font-size-lg;

    .table-subtitle {
      font-size: $font-size-xs;
      font-weight: $font-weight-normal;
      color: var(--text-secondary);
      margin-top: var(--spacing-xxs);
    }
  }

  .table-competition {
    display: flex;
    flex-direction: column;
    line-height: 1.2;

    .id {
      font-weight: $font-weight-semibold;
      color: var(--text-primary);
    }

    .meta {
      color: var(--text-secondary);
      font-size: $font-size-xs;
    }
  }

  .user-cell {
    display: inline-flex;
    align-items: center;
    gap: var(--spacing-xs);
    color: var(--text-primary);

    i {
      color: var(--primary-color);
    }
  }

  .status-badge {
    border-radius: $border-radius-lg;
    padding: 0 var(--spacing-sm);
  }

  .tag-elevated {
    border-radius: $border-radius-lg;
    background: var(--bg-secondary);
    color: var(--text-primary);
    border: none;
  }

  .admin-dialog {
    border-radius: var(--border-radius-xl);

    .el-dialog__body {
      padding-top: var(--spacing-lg);
    }
  }

  .dialog-summary {
    background: var(--bg-secondary);
    border-radius: var(--border-radius-lg);
    padding: var(--spacing-md);
    margin-bottom: var(--spacing-lg);

    .summary-title {
      font-weight: $font-weight-semibold;
      margin-bottom: var(--spacing-xs);
      color: var(--text-primary);
    }

    .summary-reason {
      margin: 0;
      color: var(--text-secondary);
      line-height: $line-height-relaxed;
    }
  }

  .detail-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--spacing-lg);

    .detail-item {
      display: flex;
      flex-direction: column;
      gap: var(--spacing-xxs);

      .label {
        font-size: $font-size-xs;
        color: var(--text-secondary);
        letter-spacing: 0.2px;
        text-transform: uppercase;
      }

      .value {
        font-weight: $font-weight-medium;
        color: var(--text-primary);
      }

      .multiline {
        margin: 0;
        line-height: $line-height-relaxed;
        white-space: pre-wrap;
      }

      &--span {
        grid-column: span 2;
      }
    }

    @include respond-to(sm) {
      grid-template-columns: 1fr;

      .detail-item {
        &--span {
          grid-column: span 1;
        }
      }
    }
  }
}
</style>
