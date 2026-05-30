<template>
  <div class="admin-content-reports">
    <div class="page-hero">
      <div class="hero-background">
        <div class="hero-pattern"></div>
      </div>
      <div class="hero-content">
        <div class="hero-text">
          <h1 class="hero-title">
            <i class="el-icon-warning-outline"></i>
            内容举报管理
          </h1>
          <p class="hero-subtitle">
            查看并处理用户对内容的举报，保障社区内容质量
          </p>
        </div>
      </div>
    </div>

    <div class="filter-section">
      <div class="filter-card">
        <div class="filter-header">
          <div class="filter-title">
            <i class="el-icon-search"></i>
            筛选
          </div>
          <el-button size="small" @click="resetFilters" class="reset-btn">
            <i class="el-icon-refresh"></i>
            重置
          </el-button>
        </div>
        <div class="filter-content">
          <el-form :model="filters" inline>
            <el-form-item label="处理状态">
              <el-select v-model="filters.status" placeholder="全部" clearable>
                <el-option label="待处理" :value="0" />
                <el-option label="已处理" :value="1" />
                <el-option label="已忽略" :value="2" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleSearch">
                <i class="el-icon-search"></i>
                搜索
              </el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>
    </div>

    <div class="content-section">
      <div class="content-card">
        <div class="content-header">
          <div class="content-title">
            <h3>
              <i class="el-icon-document"></i>
              举报列表
            </h3>
            <span class="content-count">共 {{ pagination.total }} 条记录</span>
          </div>
        </div>

        <el-table
          v-loading="loading"
          :data="reports"
          class="modern-table"
          empty-text="暂无举报"
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="contentShareId" label="内容ID" width="100" />
          <el-table-column label="举报类型" width="120">
            <template slot-scope="scope">
              <el-tag size="mini" :type="reasonTag(scope.row.reasonCode)">
                {{ reasonText(scope.row.reasonCode) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reasonText" label="说明" min-width="220" show-overflow-tooltip />
          <el-table-column label="状态" width="120">
            <template slot-scope="scope">
              <el-tag size="mini" :type="statusTag(scope.row.status)">
                {{ statusText(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" width="180" />
          <el-table-column label="操作" width="260" fixed="right">
            <template slot-scope="scope">
              <el-button-group size="mini">
                <el-button
                  type="primary"
                  icon="el-icon-view"
                  @click="viewContent(scope.row)"
                >
                  查看内容
                </el-button>
                <el-button
                  v-if="scope.row.status === 0"
                  type="success"
                  icon="el-icon-circle-check"
                  @click="handleMarkProcessed(scope.row)"
                >
                  标记已处理
                </el-button>
                <el-button
                  v-if="scope.row.status === 0"
                  type="warning"
                  icon="el-icon-circle-close"
                  @click="handleIgnore(scope.row)"
                >
                  忽略
                </el-button>
              </el-button-group>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            background
            :current-page="pagination.current"
            :page-size="pagination.size"
            :total="pagination.total"
            layout="total, prev, pager, next, jumper"
            @current-change="handleCurrentChange"
          />
        </div>
      </div>
    </div>

    <!-- 举报对应内容详情 -->
    <el-dialog
      title="举报内容详情"
      :visible.sync="detailVisible"
      width="900px"
      class="modern-dialog"
      :modal-append-to-body="false"
    >
      <div v-if="currentShare" class="detail-content">
        <div class="detail-media">
          <el-image
            v-if="currentShare.mediaType === 'IMAGE'"
            :src="currentShare.mediaUrl"
            :alt="currentShare.title"
            fit="contain"
            :preview-src-list="[currentShare.mediaUrl]"
            class="detail-image"
          />
          <VideoPlayer
            v-else
            :src="currentShare.mediaUrl"
          />
        </div>

        <div class="detail-info">
          <div class="detail-header">
            <h2>{{ currentShare.title }}</h2>
            <div class="detail-badges">
              <el-tag :type="currentShare.visibility === 1 ? 'success' : 'info'" size="small">
                {{ currentShare.visibility === 1 ? '公开' : '已下线' }}
              </el-tag>
              <el-tag :type="currentShare.mediaType === 'IMAGE' ? 'primary' : 'warning'" size="small">
                {{ currentShare.mediaType === 'IMAGE' ? '图片' : '视频' }}
              </el-tag>
            </div>
          </div>

          <div class="detail-description">
            <p>{{ currentShare.description || '暂无描述' }}</p>
          </div>

          <div class="detail-meta">
            <div class="meta-row">
              <span class="meta-label">作者:</span>
              <span class="meta-value">{{ currentShare.authorName || '用户' + currentShare.userId }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-label">创建时间:</span>
              <span class="meta-value">{{ currentShare.createdAt }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-else>
        <el-empty description="未找到该内容或已被删除" />
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { fetchContentReports, handleContentReport } from '@/api/contentReport'
import { getContentShareById } from '@/api/contentShare'
import VideoPlayer from '@/components/VideoPlayer.vue'

export default {
  name: 'AdminContentReports',
  components: {
    VideoPlayer
  },
  data() {
    return {
      loading: false,
      reports: [],
      filters: {
        status: ''
      },
      pagination: {
        current: 1,
        size: 20,
        total: 0
      },
      detailVisible: false,
      currentShare: null
    }
  },
  created() {
    this.fetchReports()
  },
  methods: {
    async fetchReports() {
      this.loading = true
      try {
        const params = {
          current: this.pagination.current,
          size: this.pagination.size,
          status: this.filters.status !== '' ? this.filters.status : undefined
        }
        const { data } = await fetchContentReports(params)
        this.reports = data.records || []
        this.pagination.total = data.total || 0
      } catch (error) {
        this.$message.error(error.message || '加载举报列表失败')
      } finally {
        this.loading = false
      }
    },
    handleSearch() {
      this.pagination.current = 1
      this.fetchReports()
    },
    resetFilters() {
      this.filters.status = ''
      this.pagination.current = 1
      this.fetchReports()
    },
    handleCurrentChange(page) {
      this.pagination.current = page
      this.fetchReports()
    },
    reasonTag(code) {
      switch (code) {
        case 'ILLEGAL':
          return 'danger'
        case 'INFRINGE':
          return 'warning'
        case 'SPAM':
          return 'info'
        default:
          return 'default'
      }
    },
    reasonText(code) {
      switch (code) {
        case 'ILLEGAL':
          return '违法违规'
        case 'INFRINGE':
          return '侵权'
        case 'SPAM':
          return '垃圾信息'
        default:
          return '其他'
      }
    },
    statusTag(status) {
      switch (status) {
        case 1:
          return 'success'
        case 2:
          return 'info'
        default:
          return 'warning'
      }
    },
    statusText(status) {
      switch (status) {
        case 1:
          return '已处理'
        case 2:
          return '已忽略'
        default:
          return '待处理'
      }
    },
    async viewContent(row) {
      if (!row.contentShareId) return
      try {
        const { data } = await getContentShareById(row.contentShareId)
        this.currentShare = data
        this.detailVisible = true
      } catch (error) {
        this.$message.error(error.message || '加载内容详情失败')
      }
    },
    async handleMarkProcessed(row) {
      try {
        const { value } = await this.$prompt('请输入处理结果说明（可选）', '标记已处理', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          inputPlaceholder: '例如：已下线内容 / 已联系作者整改'
        }).catch(e => e)

        if (value === undefined && !row) {
          return
        }

        await handleContentReport(row.id, {
          status: 1,
          resultNote: value || ''
        })
        this.$message.success('已标记为已处理')
        this.fetchReports()
      } catch (error) {
        this.$message.error(error.message || '操作失败')
      }
    },
    async handleIgnore(row) {
      try {
        await handleContentReport(row.id, {
          status: 2,
          resultNote: '已忽略'
        })
        this.$message.success('已忽略该举报')
        this.fetchReports()
      } catch (error) {
        this.$message.error(error.message || '操作失败')
      }
    }
  }
}
</script>

<style scoped>
.admin-content-reports {
  padding: 24px;
}

.page-hero {
  position: relative;
  margin-bottom: 24px;
}

.hero-background {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #5c7cfa, #82c4ff);
  border-radius: 16px;
  opacity: 0.9;
}

.hero-pattern {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle at 20% 20%, rgba(255, 255, 255, 0.2) 0, transparent 50%),
    radial-gradient(circle at 80% 0, rgba(255, 255, 255, 0.18) 0, transparent 50%);
}

.hero-content {
  position: relative;
  padding: 20px 24px;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hero-title {
  margin: 0 0 4px;
  font-size: 22px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.hero-subtitle {
  margin: 0;
  opacity: 0.9;
}

.filter-section {
  margin-bottom: 16px;
}

.filter-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.filter-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.filter-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
}

.reset-btn {
  padding: 4px 10px;
}

.content-section {
  margin-top: 8px;
}

.content-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.content-title h3 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.content-count {
  font-size: 13px;
  color: #909399;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.detail-content {
  display: flex;
  gap: 24px;
}

.detail-media {
  flex: 1.2;
}

.detail-image {
  width: 100%;
  max-height: 400px;
}

.detail-info {
  flex: 1.5;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.detail-badges {
  display: flex;
  gap: 8px;
}

.detail-description {
  margin-bottom: 12px;
  color: #606266;
}

.detail-meta .meta-row {
  display: flex;
  gap: 6px;
  margin-bottom: 4px;
  font-size: 13px;
}

.meta-label {
  color: #909399;
}

.meta-value {
  color: #303133;
}
</style>


