<template>
  <div class="admin-competitions admin-page">
    <div class="container">
      <div class="page-header hero-card">
        <div class="page-header__content">
          <div class="header-title">
            <h2>竞赛管理</h2>
            <p>锁定竞赛报名、进度及预算情况，保持运营节奏一致。</p>
          </div>
          <div class="metrics-row">
            <div
              v-for="item in summaryCards"
              :key="item.label"
              class="metric-item"
            >
              <span class="metric-label">{{ item.label }}</span>
              <span class="metric-number">{{ item.value }}</span>
              <small>{{ item.subLabel }}</small>
            </div>
          </div>
        </div>
        <div class="page-header__actions">
          <el-button type="primary" icon="el-icon-plus" @click="handleCreate">
            创建竞赛
          </el-button>
          <el-button plain icon="el-icon-download" @click="handleExport">
            导出报表
          </el-button>
        </div>
      </div>

      <div class="filter-toolbar">
        <div class="filter-fields">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索竞赛标题"
            prefix-icon="el-icon-search"
            clearable
          />
          <el-select v-model="filterStatus" placeholder="竞赛状态" clearable @change="fetchData">
            <el-option label="全部" :value="null"></el-option>
            <el-option label="草稿" :value="0"></el-option>
            <el-option label="报名中" :value="1"></el-option>
            <el-option label="进行中" :value="2"></el-option>
            <el-option label="已结束" :value="3"></el-option>
            <el-option label="已取消" :value="4"></el-option>
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="yyyy-MM-dd"
            start-placeholder="报名开始"
            end-placeholder="报名结束"
            range-separator="至"
          />
          <div class="toggle-inline">
            <span>仅显示报名/进行中</span>
            <el-switch v-model="showOnlyActive" />
          </div>
        </div>
        <div class="filter-actions">
          <el-button icon="el-icon-refresh" @click="refreshList">刷新</el-button>
          <el-button icon="el-icon-close" @click="resetFilters">重置筛选</el-button>
        </div>
      </div>

      <div class="data-table-container">
        <div class="table-header">
          <span class="table-title">竞赛列表</span>
          <div class="table-actions">
            <el-tag size="small" type="info">共 {{ total }} 条</el-tag>
          </div>
        </div>

        <el-table
          v-loading="loading"
          :data="displayedCompetitions"
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="title" label="竞赛标题" min-width="220" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="120">
            <template slot-scope="scope">
              <el-tag :class="getStatusClass(scope.row.status)">
                {{ getStatusText(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="totalPrize" label="总奖金" width="140">
            <template slot-scope="scope">
              ¥{{ formatPrize(scope.row.totalPrize) }}
            </template>
          </el-table-column>
          <el-table-column prop="registrationStartTime" label="报名开始" width="180">
            <template slot-scope="scope">
              {{ formatDate(scope.row.registrationStartTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="registrationEndTime" label="报名结束" width="180">
            <template slot-scope="scope">
              {{ formatDate(scope.row.registrationEndTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template slot-scope="scope">
              <div class="table-actions-group">
                <el-button size="mini" type="text" @click="handleView(scope.row.id)">
                  <i class="el-icon-view"></i>
                  <span>查看</span>
                </el-button>
                <el-button size="mini" type="text" @click="handleEdit(scope.row.id)">
                  <i class="el-icon-edit"></i>
                  <span>编辑</span>
                </el-button>
                <el-button
                  v-if="scope.row.status === 0"
                  size="mini"
                  type="text"
                  class="success"
                  @click="handlePublish(scope.row.id)"
                >
                  <i class="el-icon-upload2"></i>
                  <span>发布</span>
                </el-button>
                <el-button
                  size="mini"
                  type="text"
                  class="danger"
                  @click="handleDelete(scope.row.id)"
                >
                  <i class="el-icon-delete"></i>
                  <span>删除</span>
                </el-button>
              </div>
            </template>
          </el-table-column>
          <div slot="empty" class="table-empty">
            <i class="el-icon-collection-tag"></i>
            <p>没有符合条件的竞赛，换个筛选试试？</p>
            <el-button type="primary" size="mini" @click="handleCreate">创建竞赛</el-button>
          </div>
        </el-table>
      </div>

      <div class="pagination-container">
        <el-pagination
          :current-page.sync="pagination.current"
          :page-size="pagination.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script>
import { getCompetitions, publishCompetition, deleteCompetition } from '@/api/competition'

export default {
  name: 'AdminCompetitions',
  data() {
    return {
      competitions: [],
      total: 0,
      loading: false,
      filterStatus: null,
      searchKeyword: '',
      dateRange: [],
      showOnlyActive: false,
      pagination: {
        current: 1,
        size: 20
      }
    }
  },
  created() {
    this.fetchData()
  },
  computed: {
    filteredCompetitions() {
      const keyword = this.searchKeyword.trim().toLowerCase()
      const hasKeyword = keyword.length > 0
      const start = this.dateRange && this.dateRange.length ? new Date(this.dateRange[0]).getTime() : null
      const end =
        this.dateRange && this.dateRange.length === 2
          ? new Date(this.dateRange[1]).getTime() + 86399999
          : null

      return this.competitions.filter(item => {
        const title = (item.title || '').toLowerCase()
        const titleMatch = hasKeyword ? title.includes(keyword) : true
        if (!titleMatch) return false

        if (start || end) {
          const registrationStart = item.registrationStartTime
            ? new Date(item.registrationStartTime).getTime()
            : null
          if (!registrationStart) return false
          if (start && registrationStart < start) return false
          if (end && registrationStart > end) return false
        }

        return true
      })
    },
    displayedCompetitions() {
      if (!this.showOnlyActive) return this.filteredCompetitions
      return this.filteredCompetitions.filter(item => item.status === 1 || item.status === 2)
    },
    metrics() {
      const summary = {
        totalPrize: 0,
        active: 0,
        draft: 0,
        ended: 0
      }

      this.competitions.forEach(item => {
        const prize = Number(item.totalPrize) || 0
        summary.totalPrize += prize
        if (item.status === 0) summary.draft += 1
        if (item.status === 1 || item.status === 2) summary.active += 1
        if (item.status === 3) summary.ended += 1
      })

      const total = this.competitions.length || 1
      return {
        totalPrize: summary.totalPrize,
        active: summary.active,
        draft: summary.draft,
        ended: summary.ended,
        avgPrize: summary.totalPrize / total
      }
    },
    summaryCards() {
      const { active, draft, totalPrize, avgPrize } = this.metrics
      return [
        {
          label: '活跃竞赛',
          value: active,
          subLabel: '报名中 / 进行中'
        },
        {
          label: '待发布',
          value: draft,
          subLabel: '草稿待确认'
        },
        {
          label: '总奖金池',
          value: `¥${(totalPrize / 100).toLocaleString()}`,
          subLabel: '预算已配置'
        },
        {
          label: '平均奖金',
          value: totalPrize ? `¥${(avgPrize / 100).toFixed(0)}` : '¥0',
          subLabel: '单赛参考'
        }
      ]
    }
  },
  methods: {
    async fetchData() {
      this.loading = true
      try {
        const res = await getCompetitions({
          current: this.pagination.current,
          size: this.pagination.size,
          status: this.filterStatus
        })
        if (res && (res.code === 200 || res.success)) {
          const payload = res.data || res.result || {}
          this.competitions = payload.records || []
          this.total = payload.total || 0
        }
      } finally {
        this.loading = false
      }
    },

    handlePageChange(page) {
      this.pagination.current = page
      this.fetchData()
    },

    handleCreate() {
      this.$router.push('/admin/competitions/create')
    },

    handleView(id) {
      this.$router.push(`/competitions/${id}`)
    },

    handleEdit(id) {
      this.$router.push(`/admin/competitions/edit/${id}`)
    },

    async handlePublish(id) {
      try {
        await this.$confirm('确定要发布这个竞赛吗？发布后用户将可以看到并报名。', '确认发布', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })

        await publishCompetition(id)
        this.$message.success('发布成功！')
        this.fetchData()
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error((error && error.message) || '发布失败')
        }
      }
    },

    async handleDelete(id) {
      try {
        await this.$confirm('确定要删除这个竞赛吗？此操作不可恢复。', '确认删除', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'danger'
        })

        await deleteCompetition(id)
        this.$message.success('删除成功！')
        this.fetchData()
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error((error && error.message) || '删除失败')
        }
      }
    },

    refreshList() {
      this.fetchData()
    },

    resetFilters() {
      this.searchKeyword = ''
      this.dateRange = []
      this.showOnlyActive = false
      this.filterStatus = null
      this.pagination.current = 1
      this.fetchData()
    },

    handleExport() {
      this.$message.info('导出功能建设中，请稍后再试。')
    },

    getStatusClass(status) {
      const classMap = {
        0: 'status-tag status-draft',
        1: 'status-tag status-registration',
        2: 'status-tag status-ongoing',
        3: 'status-tag status-ended',
        4: 'status-tag status-cancelled'
      }
      return classMap[status] || 'status-tag status-draft'
    },

    getStatusText(status) {
      const textMap = {
        0: '草稿',
        1: '报名中',
        2: '进行中',
        3: '已结束',
        4: '已取消'
      }
      return textMap[status] || '未知'
    },

    formatDate(dateInput) {
      if (!dateInput) return '-'
      const date = new Date(dateInput)
      if (Number.isNaN(date.getTime())) return '-'
      const y = date.getFullYear()
      const m = String(date.getMonth() + 1).padStart(2, '0')
      const d = String(date.getDate()).padStart(2, '0')
      const h = String(date.getHours()).padStart(2, '0')
      const min = String(date.getMinutes()).padStart(2, '0')
      return `${y}-${m}-${d} ${h}:${min}`
    },

    formatPrize(prize) {
      if (!prize) return '0'
      return (prize / 100).toLocaleString()
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/admin-common.scss';

.admin-competitions {
  .container {
    padding: var(--spacing-xl);
    display: flex;
    flex-direction: column;
    gap: var(--spacing-xl);

    @include respond-to(md) {
      padding: var(--spacing-lg);
    }
  }
}

.page-header__content {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.header-title h2 {
  margin: 0;
  color: var(--text-primary);
}

.header-title p {
  margin: var(--spacing-xxs) 0 0;
  color: var(--text-secondary);
}

.page-header__actions {
  display: flex;
  gap: var(--spacing-sm);
  align-items: flex-start;

  @include respond-to(sm) {
    width: 100%;
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}

.metrics-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--spacing-lg);
}

.metric-item {
  background: var(--bg-secondary);
  border-radius: var(--border-radius-md);
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xxs);

  .metric-label {
    font-size: $font-size-xs;
    color: var(--text-secondary);
    letter-spacing: 0.5px;
  }

  .metric-number {
    font-size: $font-size-2xl;
    color: var(--text-primary);
    font-weight: $font-weight-semibold;
  }

  small {
    color: var(--text-secondary);
  }
}

.page-header.hero-card {
  .header-title h2 {
    color: #fff;
  }

  .header-title p {
    color: rgba(255, 255, 255, 0.85);
  }

  .metric-item {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(255, 255, 255, 0.25);

    .metric-label,
    small {
      color: rgba(255, 255, 255, 0.75);
    }

    .metric-number {
      color: #fff;
    }
  }
}

.filter-toolbar {
  background: var(--bg-primary);
  border-radius: var(--border-radius-xl);
  padding: var(--spacing-lg);
  box-shadow: var(--shadow-light);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);

  .filter-fields {
    display: flex;
    gap: var(--spacing-md);
    flex-wrap: wrap;
    align-items: center;

    .el-input,
    .el-select,
    .el-date-editor {
      min-width: 220px;
    }

    .toggle-inline {
      display: inline-flex;
      align-items: center;
      gap: var(--spacing-xs);
      color: var(--text-secondary);
      padding-left: var(--spacing-sm);
    }
  }

  .filter-actions {
    display: flex;
    align-items: center;
    gap: var(--spacing-md);
    justify-content: flex-end;
  }
}

.data-table-container {
  .table-header {
    background: var(--bg-primary);
  }
}

.table-empty {
  text-align: center;
  padding: var(--spacing-2xl);
  color: var(--text-secondary);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  align-items: center;

  i {
    font-size: 32px;
    color: var(--primary-color);
  }
}

.pagination-container {
  align-self: flex-end;
  padding-right: var(--spacing-lg);
}

// 状态标签
.status-draft {
  background-color: #909399 !important;
  border-color: #909399 !important;
  color: #ffffff !important;
}

.status-registration {
  background-color: #5b4cfa !important;
  border-color: #5b4cfa !important;
  color: #ffffff !important;
}

.status-ongoing {
  background-color: #67c23a !important;
  border-color: #67c23a !important;
  color: #ffffff !important;
}

.status-ended {
  background-color: #909399 !important;
  border-color: #909399 !important;
  color: #ffffff !important;
}

.status-cancelled {
  background-color: #f56c6c !important;
  border-color: #f56c6c !important;
  color: #ffffff !important;
}

.table-actions-group {
  .el-button--text {
    &.success:hover {
      color: #67c23a !important;
      background-color: rgba(103, 194, 58, 0.1) !important;
    }

    &.danger:hover {
      color: #f56c6c !important;
      background-color: rgba(245, 108, 108, 0.1) !important;
    }

    &:hover {
      color: #5b4cfa !important;
      background-color: rgba(91, 76, 250, 0.1) !important;
    }
  }
}
</style>
