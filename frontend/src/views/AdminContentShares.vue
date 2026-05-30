<template>
  <div class="admin-content-shares">
    <!-- 现代化页面头部 -->
    <div class="page-hero">
      <div class="hero-background">
        <div class="hero-pattern"></div>
      </div>
      <div class="hero-content">
        <div class="hero-text">
          <h1 class="hero-title">
            <i class="el-icon-picture"></i>
            {{ userRole === 'ADMIN' ? '内容分享管理' : '内容分享浏览' }}
          </h1>
          <p class="hero-subtitle">
            {{ userRole === 'ADMIN' ? '管理用户分享的图片和视频内容，支持审核、删除和链上状态查询' : '浏览用户分享的精彩图片和视频内容' }}
          </p>
        </div>
        <div class="hero-actions">
          <el-button type="primary" icon="el-icon-refresh" @click="refreshData" class="hero-btn">
            刷新数据
          </el-button>
          <el-button icon="el-icon-download" @click="exportData" class="hero-btn outline">
            导出数据
          </el-button>
        </div>
      </div>
    </div>

    <!-- 现代化统计卡片 -->
    <div class="stats-section">
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon-wrapper primary">
            <i class="el-icon-collection"></i>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.total }}</div>
            <div class="stat-label">总分享数</div>
            <div class="stat-trend positive">
              <i class="el-icon-top"></i>
              本周 +12%
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon-wrapper image">
            <i class="el-icon-picture-outline"></i>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.imageCount }}</div>
            <div class="stat-label">图片内容</div>
            <div class="stat-trend">
              占比 {{ stats.total > 0 ? Math.round(stats.imageCount / stats.total * 100) : 0 }}%
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon-wrapper video">
            <i class="el-icon-video-camera"></i>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.videoCount }}</div>
            <div class="stat-label">视频内容</div>
            <div class="stat-trend">
              占比 {{ stats.total > 0 ? Math.round(stats.videoCount / stats.total * 100) : 0 }}%
            </div>
          </div>
        </div>

        <div class="stat-card">
          <div class="stat-icon-wrapper blockchain">
            <i class="el-icon-link"></i>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.onChainCount }}</div>
            <div class="stat-label">双链确权</div>
            <div class="stat-trend positive">
              <i class="el-icon-success"></i>
              成功率 {{ stats.total > 0 ? Math.round(stats.onChainCount / stats.total * 100) : 0 }}%
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 现代化筛选栏 -->
    <div class="filter-section">
      <div class="filter-card">
        <div class="filter-header">
          <div class="filter-title">
            <i class="el-icon-search"></i>
            搜索与筛选
          </div>
          <el-button size="small" @click="resetFilters" class="reset-btn">
            <i class="el-icon-refresh"></i>
            重置
          </el-button>
        </div>
        <div class="filter-content">
          <!-- 快速分栏筛选：媒体类型 + 审核状态 -->
          <div class="quick-tabs">
            <div class="tab-group">
              <span class="tab-label">媒体类型</span>
              <el-button
                size="mini"
                :type="!filters.mediaType ? 'primary' : 'default'"
                @click="setMediaTypeFilter('')"
              >
                全部
              </el-button>
              <el-button
                size="mini"
                :type="filters.mediaType === 'IMAGE' ? 'primary' : 'default'"
                @click="setMediaTypeFilter('IMAGE')"
              >
                图片
              </el-button>
              <el-button
                size="mini"
                :type="filters.mediaType === 'VIDEO' ? 'primary' : 'default'"
                @click="setMediaTypeFilter('VIDEO')"
              >
                视频
              </el-button>
              <el-button
                size="mini"
                :type="filters.mediaType === 'WORLD_MODEL' ? 'primary' : 'default'"
                @click="setMediaTypeFilter('WORLD_MODEL')"
              >
                世界模型
              </el-button>
            </div>

            <div class="tab-group">
              <span class="tab-label">审核状态</span>
              <el-button
                size="mini"
                :type="filters.reviewStatus === '' ? 'primary' : 'default'"
                @click="setReviewStatusFilter('')"
              >
                全部
              </el-button>
              <el-button
                size="mini"
                :type="filters.reviewStatus === 0 ? 'primary' : 'default'"
                @click="setReviewStatusFilter(0)"
              >
                待审核
              </el-button>
              <el-button
                size="mini"
                :type="filters.reviewStatus === 1 ? 'primary' : 'default'"
                @click="setReviewStatusFilter(1)"
              >
                审核通过
              </el-button>
              <el-button
                size="mini"
                :type="filters.reviewStatus === 2 ? 'primary' : 'default'"
                @click="setReviewStatusFilter(2)"
              >
                审核不通过
              </el-button>
            </div>
          </div>

          <!-- 详细筛选表单 -->
          <el-form :model="filters" class="filter-form">
            <div class="filter-row">
              <div class="form-group search-group">
                <label class="form-label">搜索内容</label>
                <el-input
                  v-model="filters.keyword"
                  placeholder="搜索标题、描述或作者"
                  prefix-icon="el-icon-search"
                  clearable
                  class="modern-input"
                  @keyup.enter.native="handleSearch"
                />
              </div>

              <div class="form-group">
                <label class="form-label">媒体类型</label>
                <el-select v-model="filters.mediaType" placeholder="全部类型" clearable class="modern-select">
                  <el-option label="图片" value="IMAGE">
                    <i class="el-icon-picture"></i> 图片
                  </el-option>
                  <el-option label="视频" value="VIDEO">
                    <i class="el-icon-video-camera"></i> 视频
                  </el-option>
                  <el-option label="世界模型" value="WORLD_MODEL">
                    <i class="el-icon-box"></i> 世界模型
                  </el-option>
                </el-select>
              </div>

              <div class="form-group">
                <label class="form-label">状态</label>
                <el-select v-model="filters.visibility" placeholder="全部状态" clearable class="modern-select">
                  <el-option label="公开" :value="1">
                    <span class="status-indicator online"></span> 公开
                  </el-option>
                  <el-option label="已下线" :value="0">
                    <span class="status-indicator offline"></span> 已下线
                  </el-option>
                </el-select>
              </div>

              <div class="form-group">
                <label class="form-label">链上状态</label>
                <el-select v-model="filters.chainStatus" placeholder="全部状态" clearable class="modern-select">
                  <el-option label="已上链" :value="2">
                    <span class="status-indicator success"></span> 已上链
                  </el-option>
                  <el-option label="处理中" :value="1">
                    <span class="status-indicator warning"></span> 处理中
                  </el-option>
                  <el-option label="失败" :value="3">
                    <span class="status-indicator error"></span> 失败
                  </el-option>
                  <el-option label="待处理" :value="0">
                    <span class="status-indicator pending"></span> 待处理
                  </el-option>
                </el-select>
              </div>

              <div class="form-group">
                <label class="form-label">审核状态</label>
                <el-select v-model="filters.reviewStatus" placeholder="全部状态" clearable class="modern-select">
                  <el-option label="待审核" :value="0">
                    <span class="status-indicator pending"></span> 待审核
                  </el-option>
                  <el-option label="已通过" :value="1">
                    <span class="status-indicator success"></span> 已通过
                  </el-option>
                  <el-option label="已拒绝" :value="2">
                    <span class="status-indicator error"></span> 已拒绝
                  </el-option>
                </el-select>
              </div>

              <div class="form-group actions">
                <el-button type="primary" @click="handleSearch" class="search-btn">
                  <i class="el-icon-search"></i>
                  搜索
                </el-button>
              </div>
            </div>
          </el-form>
        </div>
      </div>
    </div>

    <!-- 现代化内容列表 -->
    <div class="content-section">
      <div class="content-card">
        <div class="content-header">
          <div class="content-title">
            <h3>
              <i class="el-icon-document"></i>
              内容列表
            </h3>
            <span class="content-count">共 {{ pagination.total }} 条记录</span>
          </div>
          <div class="content-actions">
            <el-button size="small" @click="refreshData">
              <i class="el-icon-refresh"></i>
              刷新
            </el-button>
          </div>
        </div>

        <div class="content-body">
          <el-table
            v-loading="loading"
            :data="shares"
            class="modern-table"
            :default-sort="{ prop: 'createdAt', order: 'descending' }"
            @sort-change="handleSortChange"
            empty-text="暂无数据"
          >
            <el-table-column label="预览" width="120" align="center">
              <template slot-scope="scope">
                <div class="media-preview">
                  <img
                    v-if="scope.row.mediaType === 'IMAGE'"
                    :src="scope.row.mediaUrl"
                    :alt="scope.row.title"
                    @error="handleImageError"
                  />
                  <video
                    v-else
                    :src="scope.row.mediaUrl"
                    preload="metadata"
                    @error="handleVideoError"
                  >
                    <source :type="getVideoType(scope.row)" />
                  </video>
                  <div class="media-type-badge" :class="scope.row.mediaType.toLowerCase()">
                    {{ scope.row.mediaType === 'IMAGE' ? '图' : '视频' }}
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="内容信息" min-width="300">
              <template slot-scope="scope">
                <div class="content-info">
                  <h4 class="content-title">{{ scope.row.title }}</h4>
                  <p class="content-desc">{{ scope.row.description || '暂无描述' }}</p>
                  <div class="content-meta">
                    <span class="author">作者: {{ scope.row.authorName || '用户' + scope.row.userId }}</span>
                    <span class="time">{{ formatTime(scope.row.createdAt) }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="媒体类型" width="100" align="center">
              <template slot-scope="scope">
                <el-tag :type="scope.row.mediaType === 'IMAGE' ? 'primary' : 'warning'" size="mini">
                  {{ scope.row.mediaType === 'IMAGE' ? '图片' : '视频' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="状态" width="120" align="center">
              <template slot-scope="scope">
                <el-tag :type="scope.row.visibility === 1 ? 'success' : 'info'" size="mini">
                  {{ scope.row.visibility === 1 ? '公开' : '已下线' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="审核" width="120" align="center">
              <template slot-scope="scope">
                <el-tag
                  :type="scope.row.reviewStatus === 1 ? 'success' : (scope.row.reviewStatus === 2 ? 'danger' : 'warning')"
                  size="mini"
                >
                  {{ reviewStatusText(scope.row.reviewStatus) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="链上状态" width="180" align="center">
              <template slot-scope="scope">
                <div class="chain-status">
                  <div class="chain-item">
                    <span>FISCO:</span>
                    <el-tag :type="statusTag(scope.row.fiscoStatus)" size="mini">
                      {{ statusText(scope.row.fiscoStatus) }}
                    </el-tag>
                  </div>
                  <div class="chain-item">
                    <span>Polygon:</span>
                    <el-tag :type="statusTag(scope.row.polygonStatus)" size="mini">
                      {{ statusText(scope.row.polygonStatus) }}
                    </el-tag>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="统计信息" width="140" align="center">
              <template slot-scope="scope">
                <div class="content-stats">
                  <div v-if="scope.row.totalTips" class="stat-item">
                    <i class="el-icon-coin"></i>
                    {{ scope.row.totalTips }} WEE
                  </div>
                  <div v-if="scope.row.likeCount" class="stat-item">
                    <i class="el-icon-thumb"></i>
                    {{ scope.row.likeCount }}
                  </div>
                  <div v-if="scope.row.pinned" class="stat-item pinned">
                    <i class="el-icon-top"></i>
                    置顶
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="260" fixed="right">
              <template slot-scope="scope">
                <el-button-group size="mini">
                  <el-button
                    type="primary"
                    icon="el-icon-view"
                    @click="viewDetail(scope.row)"
                    title="查看详情"
                  />
                  <el-button
                    type="success"
                    icon="el-icon-link"
                    @click="viewChainInfo(scope.row)"
                    title="链上信息"
                  />
                  <el-button
                    v-show="scope.row.visibility === 1"
                    type="warning"
                    icon="el-icon-remove-outline"
                    @click="toggleVisibility(scope.row, 0)"
                    title="下线内容"
                  />
                  <el-button
                    v-show="scope.row.visibility !== 1"
                    type="success"
                    icon="el-icon-check"
                    @click="toggleVisibility(scope.row, 1)"
                    title="恢复显示"
                  />
                  <el-button
                    type="danger"
                    icon="el-icon-delete"
                    @click="deleteContent(scope.row)"
                    title="删除内容"
                  />
                  <el-button
                    v-if="scope.row.reviewStatus !== 1"
                    type="success"
                    icon="el-icon-circle-check"
                    @click="approveContent(scope.row)"
                    title="审核通过"
                  />
                  <el-button
                    v-if="scope.row.reviewStatus !== 2"
                    type="warning"
                    icon="el-icon-circle-close"
                    @click="rejectContent(scope.row)"
                    title="审核拒绝"
                  />
                </el-button-group>
              </template>
            </el-table-column>
          </el-table>

          <!-- 现代化分页 -->
          <div class="pagination-wrapper">
            <el-pagination
              background
              :current-page="pagination.current"
              :page-size="pagination.size"
              :total="pagination.total"
              layout="total, sizes, prev, pager, next, jumper"
              :page-sizes="[12, 24, 48, 96]"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 现代化详情弹窗 -->
    <el-dialog
      title="内容详情"
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
              <span class="meta-value">{{ formatTime(currentShare.createdAt) }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-label">文件哈希:</span>
              <div class="hash-container">
                <code>{{ currentShare.fileHash || '-' }}</code>
                <el-button
                  v-if="currentShare.fileHash"
                  type="text"
                  size="mini"
                  @click="copyHash(currentShare.fileHash)"
                >
                  <i class="el-icon-copy-document"></i>
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 现代化链上信息弹窗 -->
    <el-dialog
      title="区块链信息"
      :visible.sync="chainInfoVisible"
      width="600px"
      class="modern-dialog"
      :modal-append-to-body="false"
    >
      <div v-if="currentShare" class="chain-info">
        <div class="chain-networks">
          <div class="network-card">
            <div class="network-header">
              <div class="network-icon fisco">
                <i class="el-icon-connection"></i>
              </div>
              <h4>FISCO BCOS</h4>
            </div>
            <div class="network-status">
              <el-tag :type="statusTag(currentShare.fiscoStatus)" size="small">
                {{ statusText(currentShare.fiscoStatus) }}
              </el-tag>
            </div>
            <div v-if="currentShare.fiscoTxHash" class="network-tx">
              <div class="tx-label">交易哈希:</div>
              <code class="tx-hash">{{ currentShare.fiscoTxHash }}</code>
            </div>
          </div>

          <div class="network-card">
            <div class="network-header">
              <div class="network-icon polygon">
                <i class="el-icon-magic-stick"></i>
              </div>
              <h4>Polygon</h4>
            </div>
            <div class="network-status">
              <el-tag :type="statusTag(currentShare.polygonStatus)" size="small">
                {{ statusText(currentShare.polygonStatus) }}
              </el-tag>
            </div>
            <div v-if="currentShare.polygonTxHash" class="network-tx">
              <div class="tx-label">交易哈希:</div>
              <a
                :href="polygonExplorer(currentShare.polygonTxHash)"
                target="_blank"
                rel="noopener"
                class="tx-hash link"
              >
                {{ currentShare.polygonTxHash }}
              </a>
            </div>
            <div v-if="ipfsCid(currentShare)" class="network-tx">
              <div class="tx-label">IPFS CID:</div>
              <a
                :href="ipfsCidUrl(ipfsCid(currentShare))"
                target="_blank"
                rel="noopener"
                class="tx-hash link"
              >
                {{ ipfsCid(currentShare) }}
              </a>
            </div>
          </div>
        </div>

        <div class="file-hash-section">
          <div class="hash-header">
            <i class="el-icon-lock"></i>
            <h4>文件指纹</h4>
          </div>
          <div class="hash-content">
            <code>{{ currentShare.fileHash || '-' }}</code>
            <el-button
              v-if="currentShare.fileHash"
              type="text"
              size="small"
              @click="copyHash(currentShare.fileHash)"
            >
              <i class="el-icon-copy-document"></i>
              复制
            </el-button>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { adminListContentShares, fetchContentShares, deleteContentShare, toggleContentVisibility, reviewContentShare } from '@/api/contentShare'
import VideoPlayer from '@/components/VideoPlayer.vue'

export default {
  name: 'AdminContentShares',
  components: {
    VideoPlayer
  },
  data() {
    return {
      loading: false,
      shares: [],
      stats: {
        total: 0,
        imageCount: 0,
        videoCount: 0,
        onChainCount: 0
      },
      filters: {
        keyword: '',
        mediaType: '',
        visibility: '',
        chainStatus: '',
        reviewStatus: ''
      },
      pagination: {
        current: 1,
        size: 24,
        total: 0
      },
      sortProp: 'createdAt',
      sortOrder: 'descending',
      detailVisible: false,
      chainInfoVisible: false,
      currentShare: null
    }
  },
  computed: {
    ...mapGetters('user', ['token', 'userRole'])
  },
  mounted() {
    // Check if user has admin role
    if (this.userRole !== 'ADMIN') {
      this.$message.error('您没有管理员权限，无法访问此页面')
      this.$router.push({ name: 'Home' })
      return
    }
    this.fetchShares()
  },
  methods: {
    async fetchShares() {
      this.loading = true
      try {
        const params = {
          current: this.pagination.current,
          size: this.pagination.size,
          title: this.filters.keyword || undefined,
          mediaType: this.filters.mediaType || undefined,
          visibility: this.filters.visibility !== '' ? this.filters.visibility : undefined,
          reviewStatus: this.filters.reviewStatus !== '' ? this.filters.reviewStatus : undefined
        }

        let response
        if (this.userRole === 'ADMIN') {
          response = await adminListContentShares(params)
        } else {
          // For non-admin users, use the regular content shares endpoint
          // Only show public content
          const publicParams = {
            ...params,
            visibility: 1 // Only show public content for non-admins
          }
          response = await fetchContentShares(publicParams)
        }

        this.shares = response.data.records || []
        this.pagination.total = response.data.total || 0
        this.calculateStats()
      } catch (error) {
        this.$message.error(error.message || '加载数据失败')
      } finally {
        this.loading = false
      }
    },

    calculateStats() {
      const total = this.shares.length
      const imageCount = this.shares.filter(item => item.mediaType === 'IMAGE').length
      const videoCount = this.shares.filter(item => item.mediaType === 'VIDEO').length
      const onChainCount = this.shares.filter(
        item => item.fiscoStatus === 2 && item.polygonStatus === 2
      ).length

      this.stats = { total, imageCount, videoCount, onChainCount }
    },

    handleSearch() {
      this.pagination.current = 1
      this.fetchShares()
    },

    resetFilters() {
      this.filters = {
        keyword: '',
        mediaType: '',
        visibility: '',
        chainStatus: '',
        reviewStatus: ''
      }
      this.pagination.current = 1
      this.fetchShares()
    },

    handleSortChange({ prop, order }) {
      this.sortProp = prop
      this.sortOrder = order
      this.fetchShares()
    },

    handleSizeChange(size) {
      this.pagination.size = size
      this.pagination.current = 1
      this.fetchShares()
    },

    handleCurrentChange(current) {
      this.pagination.current = current
      this.fetchShares()
    },

    refreshData() {
      this.fetchShares()
      this.$message.success('数据已刷新')
    },

    setMediaTypeFilter(type) {
      this.filters.mediaType = type || ''
      this.pagination.current = 1
      this.fetchShares()
    },

    setReviewStatusFilter(status) {
      this.filters.reviewStatus = status
      this.pagination.current = 1
      this.fetchShares()
    },

    exportData() {
      this.$message.info('导出功能开发中...')
    },

    viewDetail(share) {
      this.currentShare = share
      this.detailVisible = true
    },

    viewChainInfo(share) {
      this.currentShare = share
      this.chainInfoVisible = true
    },

    async toggleVisibility(share, visibility) {
      const action = visibility === 1 ? '恢复显示' : '下线'
      try {
        await this.$confirm(
          `确定要${action}内容"${share.title}"吗？`,
          `确认${action}`,
          {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
          }
        )

        await toggleContentVisibility(share.id, visibility)
        this.$message.success(`${action}成功`)
        this.fetchShares()
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error(error.message || `${action}失败`)
        }
      }
    },

    async deleteContent(share) {
      try {
        await this.$confirm(
          `确定要删除内容"${share.title}"吗？此操作不可恢复，但链上记录将保留。`,
          '确认删除',
          {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
          }
        )

        await deleteContentShare(share.id)
        this.$message.success('删除成功')
        this.fetchShares()
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error(error.message || '删除失败')
        }
      }
    },

    reviewStatusText(status) {
      switch (status) {
        case 1:
          return '已通过'
        case 2:
          return '已拒绝'
        case 0:
        default:
          return '待审核'
      }
    },

    async approveContent(row) {
      try {
        await reviewContentShare(row.id, 1, '')
        this.$message.success('审核通过')
        this.fetchShares()
      } catch (error) {
        this.$message.error(error.message || '审核失败')
      }
    },

    async rejectContent(row) {
      try {
        const result = await this.$prompt('请输入拒绝原因（可选）', '拒绝内容', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          inputPlaceholder: '例如：与平台内容规范不符'
        }).catch(e => e)

        if (!result || result.action === 'cancel') {
          return
        }

        await reviewContentShare(row.id, 2, result.value || '')
        this.$message.success('已标记为拒绝')
        this.fetchShares()
      } catch (error) {
        this.$message.error(error.message || '操作失败')
      }
    },

    statusText(status) {
      switch (status) {
        case 1: return '处理中'
        case 2: return '已上链'
        case 3: return '失败'
        default: return '待处理'
      }
    },

    statusTag(status) {
      switch (status) {
        case 2: return 'success'
        case 3: return 'danger'
        case 1: return 'warning'
        default: return 'info'
      }
    },

    polygonExplorer(txHash) {
      if (!txHash) return '#'
      // 统一跳转 Polygon 主网浏览器
      return `https://polygonscan.com/tx/${txHash}`
    },
    ipfsCidUrl(cid) {
      if (!cid) return '#'
      const gateway = (process.env.VUE_APP_IPFS_PUBLIC_GATEWAY || 'https://ipfs.4everland.io').replace(/\/+$/, '')
      return `${gateway}/ipfs/${cid}`
    },
    ipfsCid(share) {
      if (!share) return ''
      const url = share.mediaUrl || ''
      const marker = '/ipfs/'
      if (typeof url === 'string' && url.includes(marker)) {
        const after = url.substring(url.lastIndexOf(marker) + marker.length)
        const q = after.indexOf('?')
        const cid = (q >= 0 ? after.substring(0, q) : after).split('/')[0]
        return cid || ''
      }
      if (share.metadata) {
        try {
          const meta = JSON.parse(share.metadata)
          return meta && meta.cid ? String(meta.cid) : ''
        } catch (e) {
          return ''
        }
      }
      return ''
    },

    copyHash(hash) {
      navigator.clipboard.writeText(hash).then(() => {
        this.$message.success('哈希已复制')
      }).catch(() => {
        this.$message.error('复制失败')
      })
    },

    formatTime(time) {
      if (!time) return ''
      return new Date(time).toLocaleString('zh-CN')
    },

    getVideoType(share) {
      const fileName = share.mediaUrl || ''
      if (fileName.includes('.mp4')) return 'video/mp4'
      if (fileName.includes('.webm')) return 'video/webm'
      if (fileName.includes('.ogg')) return 'video/ogg'
      return 'video/mp4'
    },

    handleImageError(event) {
      event.target.src = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgZmlsbD0iI2Y1ZjVmYSIvPjx0ZXh0IHg9IjUwIiB5PSI1NSIgZm9udC1mYW1pbHk9IkFyaWFsIiBmb250LXNpemU9IjE0IiBmaWxsPSIjOTk5IiB0ZXh0LWFuY2hvcj0ibWlkZGxlIj7lm77niYfliqDovb3lpLHotKU8L3RleHQ+PC9zdmc+'
    },

    handleVideoError(event) {
      event.target.style.display = 'none'
      const placeholder = document.createElement('div')
      placeholder.className = 'video-error-placeholder'
      placeholder.innerHTML = '<i class="el-icon-video-camera-solid"></i><span>视频加载失败</span>'
      event.target.parentNode.appendChild(placeholder)
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/admin-design-system.scss';

// 现代化页面布局
.admin-content-shares {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xl);
  padding: 0;
  background: var(--bg-secondary);
  min-height: 100vh;
}

// 现代化英雄区域
.page-hero {
  position: relative;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 20px;
  margin: 0;
  overflow: hidden;
  box-shadow: 0 20px 40px rgba(102, 126, 234, 0.3);

  .hero-background {
    position: absolute;
    inset: 0;
    opacity: 0.1;

    .hero-pattern {
      width: 100%;
      height: 100%;
      background-image:
        radial-gradient(circle at 20% 50%, rgba(255, 255, 255, 0.3) 0%, transparent 50%),
        radial-gradient(circle at 80% 20%, rgba(255, 255, 255, 0.2) 0%, transparent 50%),
        radial-gradient(circle at 40% 80%, rgba(255, 255, 255, 0.2) 0%, transparent 50%);
    }
  }

  .hero-content {
    position: relative;
    z-index: 2;
    padding: var(--spacing-3xl);
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: var(--spacing-xl);

    @include respond-to(md) {
      flex-direction: column;
      align-items: flex-start;
      padding: var(--spacing-2xl);
    }
  }

  .hero-text {
    flex: 1;

    .hero-title {
      display: flex;
      align-items: center;
      gap: var(--spacing-md);
      margin: 0 0 var(--spacing-md);
      font-size: $font-size-3xl;
      font-weight: $font-weight-bold;
      line-height: 1.2;

      i {
        font-size: $font-size-2xl;
      }
    }

    .hero-subtitle {
      margin: 0;
      font-size: $font-size-base;
      opacity: 0.9;
      line-height: 1.6;
      max-width: 600px;
    }
  }

  .hero-actions {
    display: flex;
    gap: var(--spacing-md);
    align-items: center;

    @include respond-to(md) {
      margin-top: var(--spacing-lg);
    }

    .hero-btn {
      border-radius: 12px;
      padding: var(--spacing-md) var(--spacing-lg);
      font-weight: $font-weight-medium;
      border: none;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);

      &.outline {
        background: rgba(255, 255, 255, 0.2);
        backdrop-filter: blur(10px);
        border: 1px solid rgba(255, 255, 255, 0.3);
        color: white;

        &:hover {
          background: rgba(255, 255, 255, 0.3);
        }
      }
    }
  }
}

// 现代化统计区域
.stats-section {
  margin: var(--spacing-xl) 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--spacing-xl);
}

.stat-card {
  background: var(--bg-primary);
  border-radius: 20px;
  padding: var(--spacing-xl);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(226, 232, 240, 0.6);
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  transition: all $transition-base;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
  }

  .stat-icon-wrapper {
    width: 64px;
    height: 64px;
    border-radius: 16px;
    @include flex-center;
    font-size: 28px;
    color: white;
    flex-shrink: 0;

    &.primary {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    }

    &.image {
      background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
    }

    &.video {
      background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%);
    }

    &.blockchain {
      background: linear-gradient(135deg, #34d399 0%, #10b981 100%);
    }
  }

  .stat-content {
    flex: 1;

    .stat-value {
      font-size: $font-size-3xl;
      font-weight: $font-weight-bold;
      color: var(--text-primary);
      margin-bottom: var(--spacing-xs);
      line-height: 1;
    }

    .stat-label {
      font-size: $font-size-base;
      color: var(--text-secondary);
      margin-bottom: var(--spacing-sm);
      font-weight: $font-weight-medium;
    }

    .stat-trend {
      display: flex;
      align-items: center;
      gap: var(--spacing-xs);
      font-size: $font-size-sm;
      color: var(--text-regular);

      &.positive {
        color: var(--success-color);
        font-weight: $font-weight-medium;
      }

      i {
        font-size: 14px;
      }
    }
  }
}

// 现代化筛选区域
.filter-section {
  margin: var(--spacing-xl) 0;
}

.filter-card {
  background: var(--bg-primary);
  border-radius: 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(226, 232, 240, 0.6);
  overflow: hidden;

  .filter-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: var(--spacing-xl) var(--spacing-xl) var(--spacing-md);
    border-bottom: 1px solid var(--border-light);

    .filter-title {
      display: flex;
      align-items: center;
      gap: var(--spacing-sm);
      font-size: $font-size-lg;
      font-weight: $font-weight-semibold;
      color: var(--text-primary);

      i {
        color: var(--primary-color);
      }
    }

    .reset-btn {
      border: 1px solid var(--border-base);
      color: var(--text-regular);
      border-radius: 8px;

      &:hover {
        border-color: var(--primary-color);
        color: var(--primary-color);
      }
    }
  }

  .filter-content {
    padding: var(--spacing-lg) var(--spacing-xl) var(--spacing-xl);
  }
}

.filter-form {
  .filter-row {
    display: grid;
    grid-template-columns: 2fr 1fr 1fr 1fr auto;
    gap: var(--spacing-lg);
    align-items: end;

    @include respond-to(xl) {
      grid-template-columns: 1fr;
      gap: var(--spacing-md);
    }
  }

  .form-group {
    display: flex;
    flex-direction: column;
    gap: var(--spacing-xs);

    &.search-group {
      grid-column: span 1;

      @include respond-to(xl) {
        grid-column: span 1;
      }
    }

    &.actions {
      align-self: flex-end;
    }

    .form-label {
      font-size: $font-size-sm;
      font-weight: $font-weight-medium;
      color: var(--text-primary);
      margin-bottom: var(--spacing-xs);
    }

    .modern-input,
    .modern-select {
      :deep(.el-input__inner),
      :deep(.el-select .el-input__inner) {
        border-radius: 10px;
        border: 2px solid var(--border-light);
        padding: var(--spacing-md) var(--spacing-lg);
        font-size: $font-size-sm;
        transition: all $transition-base;

        &:focus {
          border-color: var(--primary-color);
          box-shadow: 0 0 0 3px rgba(91, 76, 250, 0.1);
        }
      }
    }

    .search-btn {
      border-radius: 10px;
      padding: var(--spacing-md) var(--spacing-xl);
      background: linear-gradient(135deg, var(--primary-color), #7269ff);
      border: none;
      box-shadow: 0 4px 12px rgba(91, 76, 250, 0.3);

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 6px 16px rgba(91, 76, 250, 0.4);
      }
    }
  }
}

// 状态指示器
.status-indicator {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: var(--spacing-xs);

  &.online { background: var(--success-color); }
  &.offline { background: var(--text-placeholder); }
  &.success { background: var(--success-color); }
  &.warning { background: var(--warning-color); }
  &.error { background: var(--danger-color); }
  &.pending { background: var(--info-color); }
}

// 内容区域
.content-section {
  margin: var(--spacing-xl) 0;
}

.content-card {
  background: var(--bg-primary);
  border-radius: 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(226, 232, 240, 0.6);
  overflow: hidden;

  .content-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: var(--spacing-xl) var(--spacing-xl) var(--spacing-md);
    border-bottom: 1px solid var(--border-light);

    .content-title {
      display: flex;
      align-items: center;
      gap: var(--spacing-lg);

      h3 {
        margin: 0;
        font-size: $font-size-xl;
        font-weight: $font-weight-semibold;
        color: var(--text-primary);
        display: flex;
        align-items: center;
        gap: var(--spacing-sm);

        i {
          color: var(--primary-color);
          font-size: 20px;
        }
      }

      .content-count {
        font-size: $font-size-sm;
        color: var(--text-secondary);
        background: var(--bg-secondary);
        padding: var(--spacing-xs) var(--spacing-md);
        border-radius: 12px;
      }
    }
  }

  .content-body {
    padding: var(--spacing-xl);
  }

  .modern-table {
    :deep(.el-table) {
      border-radius: 12px;
      overflow: hidden;
      border: none;

      &::before {
        display: none;
      }

      .el-table__header {
        background: var(--bg-secondary);

        th {
          background: var(--bg-secondary) !important;
          color: var(--text-primary);
          font-weight: $font-weight-semibold;
          border-bottom: 2px solid var(--border-light);
          padding: var(--spacing-md) var(--spacing-lg);
        }
      }

      .el-table__body {
        tr {
          transition: all $transition-base;

          &:hover {
            background: var(--bg-secondary);
          }

          td {
            border-bottom: 1px solid var(--border-light);
            padding: var(--spacing-md) var(--spacing-lg);
          }
        }
      }
    }

    .media-preview {
      position: relative;
      width: 80px;
      height: 80px;
      border-radius: 12px;
      overflow: hidden;
      background: var(--bg-secondary);

      img,
      video {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .media-type-badge {
        position: absolute;
        top: 4px;
        right: 4px;
        padding: 2px 6px;
        border-radius: var(--border-radius-full);
        font-size: 10px;
        font-weight: bold;
        color: white;

        &.image { background: rgba(96, 165, 250, 0.9); }
        &.video { background: rgba(245, 158, 11, 0.9); }
      }
    }

    .content-info {
      .content-title {
        margin: 0 0 var(--spacing-xs);
        font-size: $font-size-base;
        font-weight: $font-weight-medium;
        color: var(--text-primary);
        line-height: 1.4;
      }

      .content-desc {
        margin: 0 0 var(--spacing-sm);
        font-size: $font-size-sm;
        color: var(--text-secondary);
        line-height: 1.4;
        max-height: 2.8em;
        overflow: hidden;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
      }

      .content-meta {
        font-size: $font-size-xs;
        color: var(--text-placeholder);

        .author {
          font-weight: $font-weight-medium;
        }
      }
    }

    .chain-status {
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: $font-size-xs;

      .chain-item {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--spacing-xs);
      }
    }

    .content-stats {
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: $font-size-xs;

      .stat-item {
        display: flex;
        align-items: center;
        gap: 4px;
        color: var(--text-regular);

        &.pinned {
          color: var(--warning-color);
        }

        i {
          font-size: 12px;
        }
      }
    }

    .pagination-wrapper {
      margin-top: var(--spacing-xl);
      text-align: center;
      padding: var(--spacing-lg) 0;

      :deep(.el-pagination) {
        .el-pager li {
          border-radius: 8px;
          margin: 0 2px;

          &.active {
            background: linear-gradient(135deg, var(--primary-color), #7269ff);
            color: white;
          }
        }
      }
    }
  }
}

// 现代化弹窗
.modern-dialog {
  :deep(.el-dialog) {
    border-radius: 20px;
    overflow: hidden;
    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.1);

    .el-dialog__header {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: var(--spacing-xl) var(--spacing-2xl);
      margin: 0;

      .el-dialog__title {
        color: white;
        font-weight: $font-weight-semibold;
      }

      .el-dialog__headerbtn {
        top: var(--spacing-xl);
        right: var(--spacing-xl);

        .el-dialog__close {
          color: white;
          font-size: 20px;
        }
      }
    }

    .el-dialog__body {
      padding: var(--spacing-2xl);
    }
  }
}

// 详情内容样式
.detail-content {
  .detail-media {
    text-align: center;
    margin-bottom: var(--spacing-xl);

    img,
    video,
    .detail-image {
      max-width: 100%;
      border-radius: 16px;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
    }
  }

  .detail-info {
    .detail-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: var(--spacing-lg);

      h2 {
        margin: 0;
        font-size: $font-size-2xl;
        font-weight: $font-weight-bold;
        color: var(--text-primary);
      }

      .detail-badges {
        display: flex;
        gap: var(--spacing-sm);
      }
    }

    .detail-description {
      margin: 0 0 var(--spacing-xl);
      color: var(--text-secondary);
      line-height: 1.6;
    }

    .detail-meta {
      .meta-row {
        display: flex;
        align-items: center;
        padding: var(--spacing-md) 0;
        border-bottom: 1px solid var(--border-light);

        &:last-child {
          border-bottom: none;
        }

        .meta-label {
          font-weight: $font-weight-semibold;
          color: var(--text-primary);
          margin-right: var(--spacing-md);
          min-width: 100px;
        }

        .meta-value {
          color: var(--text-secondary);
          flex: 1;
        }

        .hash-container {
          display: flex;
          align-items: center;
          gap: var(--spacing-sm);
          flex: 1;

          code {
            background: var(--bg-secondary);
            padding: var(--spacing-xs) var(--spacing-sm);
            border-radius: 8px;
            font-family: $font-family-mono;
            font-size: $font-size-xs;
            word-break: break-all;
            flex: 1;
          }
        }
      }
    }
  }
}

// 链上信息样式
.chain-info {
  .chain-networks {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: var(--spacing-lg);
    margin-bottom: var(--spacing-xl);

    @include respond-to(sm) {
      grid-template-columns: 1fr;
    }
  }

  .network-card {
    background: var(--bg-secondary);
    border-radius: 16px;
    padding: var(--spacing-lg);
    border: 1px solid var(--border-light);

    .network-header {
      display: flex;
      align-items: center;
      gap: var(--spacing-md);
      margin-bottom: var(--spacing-md);

      .network-icon {
        width: 40px;
        height: 40px;
        border-radius: 12px;
        @include flex-center;
        font-size: 20px;
        color: white;

        &.fisco {
          background: linear-gradient(135deg, #667eea, #764ba2);
        }

        &.polygon {
          background: linear-gradient(135deg, #8247e5, #6b21a8);
        }
      }

      h4 {
        margin: 0;
        font-size: $font-size-lg;
        font-weight: $font-weight-semibold;
        color: var(--text-primary);
      }
    }

    .network-status {
      margin-bottom: var(--spacing-md);
    }

    .network-tx {
      .tx-label {
        font-size: $font-size-sm;
        color: var(--text-secondary);
        margin-bottom: var(--spacing-xs);
      }

      .tx-hash {
        font-family: $font-family-mono;
        font-size: $font-size-xs;
        background: var(--bg-primary);
        padding: var(--spacing-xs) var(--spacing-sm);
        border-radius: 8px;
        word-break: break-all;
        display: block;

        &.link {
          color: var(--primary-color);
          text-decoration: none;

          &:hover {
            text-decoration: underline;
          }
        }
      }
    }
  }

  .file-hash-section {
    background: var(--bg-secondary);
    border-radius: 16px;
    padding: var(--spacing-lg);
    border: 1px solid var(--border-light);

    .hash-header {
      display: flex;
      align-items: center;
      gap: var(--spacing-md);
      margin-bottom: var(--spacing-md);

      i {
        color: var(--primary-color);
        font-size: 20px;
      }

      h4 {
        margin: 0;
        font-size: $font-size-lg;
        font-weight: $font-weight-semibold;
        color: var(--text-primary);
      }
    }

    .hash-content {
      display: flex;
      align-items: center;
      gap: var(--spacing-sm);

      code {
        background: var(--bg-primary);
        padding: var(--spacing-sm) var(--spacing-md);
        border-radius: 8px;
        font-family: $font-family-mono;
        font-size: $font-size-sm;
        word-break: break-all;
        flex: 1;
      }
    }
  }
}

.video-error-placeholder {
  @include flex-center;
  flex-direction: column;
  gap: var(--spacing-xs);
  width: 80px;
  height: 80px;
  background: var(--bg-secondary);
  border-radius: var(--border-radius-md);
  color: var(--text-placeholder);
  font-size: $font-size-xs;

  i {
    font-size: 20px;
  }
}
</style>
