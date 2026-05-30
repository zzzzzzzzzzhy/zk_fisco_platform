<template>
  <div class="competitions-page">
    <div class="competitions-container">
      <section class="competitions-hero surface-card">
        <div class="hero-grid">
          <div class="hero-left">
            <p class="hero-eyebrow">Global Challenges · On-chain Track</p>
            <h1>探索竞赛宇宙</h1>
            <p class="hero-lead">
              参与顶尖 Web3 / AI / 金融风控赛事，展示实力、赢取奖金，并在链上留下不可篡改的成绩。
            </p>
            <div class="hero-actions">
              <el-button type="primary" icon="el-icon-refresh" size="medium" @click="fetchData">
                刷新竞赛列表
              </el-button>
              <el-button plain size="medium" icon="el-icon-document" @click="handleSearch">
                快速检索
              </el-button>
            </div>
          </div>
          <div class="hero-right">
            <div class="hero-metrics">
              <div class="metric-card">
                <p class="metric-label">在办赛事</p>
                <p class="metric-value">{{ competitionStats.active }}</p>
                <span class="metric-hint">报名 + 进行中</span>
              </div>
              <div class="metric-card">
                <p class="metric-label">累计奖金池</p>
                <p class="metric-value">¥{{ competitionStats.totalPrize }}</p>
                <span class="metric-hint">含历史赛事</span>
              </div>
              <div class="metric-card">
                <p class="metric-label">全部赛事</p>
                <p class="metric-value">{{ total }}</p>
                <span class="metric-hint">覆盖多个赛道</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="filter-panel surface-card">
        <div class="filter-section">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索竞赛名称..."
            prefix-icon="el-icon-search"
            clearable
            class="search-input"
            @input="handleSearch"
          />

          <el-select v-model="filterStatus" placeholder="竞赛状态" class="filter-select" @change="fetchData">
            <el-option label="全部" :value="null"></el-option>
            <el-option label="报名中" :value="1"></el-option>
            <el-option label="进行中" :value="2"></el-option>
            <el-option label="已结束" :value="3"></el-option>
          </el-select>
        </div>
      </section>

      <section class="competitions-shell surface-card">
        <div v-loading="loading" class="competitions-grid">
          <div
            v-for="competition in competitions"
            :key="competition.id"
            class="competition-card"
            @click="goToDetail(competition.id)"
          >
            <div class="card-cover" :style="getCoverStyle(competition)">
              <div class="cover-gradient">
                <span :class="['status-badge', getStatusClass(competition.status)]">
                  {{ getStatusText(competition.status) }}
                </span>
              </div>
            </div>
            <div class="card-logo">
              <img :src="getLogoImage(competition)" alt="竞赛Logo" class="logo-image" />
            </div>
            <div class="card-content">
              <h3 class="competition-title">{{ competition.title }}</h3>
              <p class="competition-desc">{{ competition.description || '暂无描述' }}</p>
              <div class="competition-meta">
                <div class="meta-item">
                  <i class="el-icon-time"></i>
                  <span>{{ formatDate(competition.submissionEndTime) }}</span>
                </div>
                <div class="meta-item prize">
                  <i class="el-icon-trophy"></i>
                  <span>¥{{ formatPrize(competition.totalPrize) }}</span>
                </div>
              </div>
              <div class="card-footer">
                <el-button type="primary" size="small" plain>
                  查看详情
                </el-button>
              </div>
            </div>
          </div>

          <div v-if="!loading && competitions.length === 0" class="empty-state">
            <i class="el-icon-document"></i>
            <p>暂无竞赛</p>
          </div>
        </div>

        <div v-if="total > 0" class="pagination">
          <el-pagination
            :current-page.sync="pagination.current"
            :page-size="pagination.size"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="handlePageChange"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<script>
import { mapState, mapActions } from 'vuex'

export default {
  name: 'CompetitionList',
  data() {
    return {
      searchKeyword: '',
      filterStatus: null,
      pagination: {
        current: 1,
        size: 12
      }
    }
  },
  computed: {
    ...mapState('competition', ['competitions', 'total', 'loading']),
    competitionStats() {
      const active = this.competitions.filter(item => [1, 2].includes(item.status)).length
      const totalPrizeAmount = this.competitions.reduce(
        (sum, item) => sum + (item.totalPrize || 0),
        0
      )
      return {
        active,
        totalPrize: this.formatPrize(totalPrizeAmount)
      }
    }
  },
  created() {
    this.fetchData()
  },
  methods: {
    ...mapActions('competition', ['fetchCompetitions']),

    async fetchData() {
      await this.fetchCompetitions({
        current: this.pagination.current,
        size: this.pagination.size,
        status: this.filterStatus
      })
    },

    handleSearch() {
      this.pagination.current = 1
      this.fetchData()
    },

    handlePageChange() {
      this.fetchData()
      window.scrollTo({ top: 0, behavior: 'smooth' })
    },

    goToDetail(id) {
      this.$router.push(`/competitions/${id}`)
    },

    getStatusClass(status) {
      const statusMap = {
        0: 'draft',
        1: 'ongoing',
        2: 'ongoing',
        3: 'ended'
      }
      return statusMap[status] || 'draft'
    },

    getStatusText(status) {
      const statusMap = {
        0: '草稿',
        1: '报名中',
        2: '进行中',
        3: '已结束',
        4: '已取消'
      }
      return statusMap[status] || '未知'
    },

    formatDate(dateStr) {
      if (!dateStr) return '-'
      const date = new Date(dateStr)
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
    },

    formatPrize(prize) {
      if (!prize) return '0'
      return (prize / 100).toLocaleString()
    },

    getCoverStyle(competition) {
      // 如果有自定义封面图片，使用自定义图片
      if (competition.coverImage) {
        return {
          backgroundImage: `url(${competition.coverImage})`,
          backgroundSize: 'cover',
          backgroundPosition: 'center'
        }
      }
      // 否则使用默认图片
      try {
        const defaultCover = require('@/images/11.jpeg')
        return {
          backgroundImage: `url(${defaultCover})`,
          backgroundSize: 'cover',
          backgroundPosition: 'center'
        }
      } catch (e) {
        // 如果默认图片不存在，使用渐变背景
        return {
          background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
        }
      }
    },

    getLogoImage(competition) {
      // 如果有自定义详情图片，使用自定义图片作为Logo
      if (competition.detailImage) {
        return competition.detailImage
      }
      // 否则使用默认Logo图片
      try {
        return require('@/images/22.jpg')
      } catch (e) {
        // 如果默认图片不存在，返回占位图
        return 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMTAwIiBoZWlnaHQ9IjEwMCIgZmlsbD0iIzVCNENGQSIvPjwvc3ZnPg=='
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.competitions-page {
  background: #edf1f7;
  min-height: 100vh;
  padding: 32px 0 60px;
}

.competitions-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.surface-card {
  background: #fff;
  border-radius: 28px;
  padding: 32px;
  box-shadow: 0 25px 60px rgba(15, 23, 42, 0.08);
  border: 1px solid rgba(226, 232, 240, 0.7);
}

.competitions-hero {
  background: linear-gradient(135deg, #5b4cfa 0%, #7269ff 45%, #0f172a 100%);
  color: #fff;
  overflow: hidden;
}

.hero-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 32px;
}

.hero-eyebrow {
  font-size: 14px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.75);
  margin: 0 0 12px;
}

.hero-left h1 {
  font-size: 36px;
  margin: 0 0 12px;
}

.hero-lead {
  margin: 0 0 20px;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.6;
}

.hero-actions {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 16px;
}

.metric-card {
  background: rgba(15, 23, 42, 0.4);
  border-radius: 20px;
  padding: 20px;
  backdrop-filter: blur(10px);
}

.metric-card .metric-label {
  text-transform: uppercase;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: rgba(255, 255, 255, 0.8);
  margin-bottom: 8px;
}

.metric-card .metric-value {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 4px;
}

.metric-card .metric-hint {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
}

.filter-panel .filter-section {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.filter-panel .filter-section .search-input {
  flex: 1;
  min-width: 240px;
}

.filter-panel .filter-section .filter-select {
  width: 180px;
}

.competitions-shell {
  padding: 32px 24px 40px;
}

.competitions-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 24px;
  min-height: 300px;
}

.competition-card {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

.competition-card:hover {
  transform: translateY(-8px);
  box-shadow: 0 12px 28px rgba(91, 76, 250, 0.15);
}

.card-cover {
  height: 180px;
  position: relative;
  overflow: hidden;
  border-radius: 16px 16px 0 0;
}

.card-cover::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: inherit;
  filter: blur(30px);
  transform: scale(1.2);
  opacity: 0.4;
}

.cover-gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, 0.05), rgba(0, 0, 0, 0.5));
  padding: 20px;
  display: flex;
  justify-content: flex-end;
}

.card-logo {
  position: absolute;
  top: 140px;
  left: 24px;
  z-index: 10;
}

.card-logo .logo-image {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  border: 4px solid white;
  object-fit: cover;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  background: white;
}

.status-badge {
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 13px;
  font-weight: 600;
  backdrop-filter: blur(10px);
}

.status-badge.draft {
  background: rgba(255, 255, 255, 0.9);
  color: #909399;
}

.status-badge.ongoing {
  background: rgba(103, 194, 58, 0.9);
  color: white;
}

.status-badge.ended {
  background: rgba(245, 108, 108, 0.9);
  color: white;
}

.card-content {
  padding: 60px 24px 24px 24px;
}

.competition-title {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 12px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.competition-desc {
  font-size: 14px;
  color: #909399;
  line-height: 1.6;
  margin-bottom: 16px;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  min-height: 44px;
}

.competition-meta {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.competition-meta .meta-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #606266;
}

.competition-meta .meta-item i {
  font-size: 16px;
}

.competition-meta .meta-item.prize {
  color: #f56c6c;
  font-weight: 600;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
}

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 20px;
  color: #909399;
}

.empty-state i {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

@media (max-width: 768px) {
  .surface-card {
    padding: 24px;
  }

  .hero-left h1 {
    font-size: 28px;
  }

  .filter-panel .filter-section {
    flex-direction: column;
  }

  .filter-panel .filter-section .search-input {
    max-width: 100%;
  }
}
</style>

