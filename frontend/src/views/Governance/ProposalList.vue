<template>
  <div class="governance-page">
    <div class="governance-container">
      <section class="governance-hero surface-card">
        <div class="hero-grid">
          <div class="hero-left">
            <p class="hero-eyebrow">COMMUNITY GOVERNANCE · WEB3 DAO</p>
            <h1>🗳️ DAO 治理</h1>
            <p class="hero-lead">
              社区驱动的参数治理平台。通过提案投票决定平台奖励机制，每个有投票资格的地址都有发言权。
            </p>
            <div class="hero-actions">
              <el-button
                type="primary"
                icon="el-icon-plus"
                size="medium"
                @click="showCreateDialog = true"
                :disabled="!canPropose"
              >
                {{ canPropose ? '创建提案' : '需要 10K WEE' }}
              </el-button>
              <el-button plain size="medium" icon="el-icon-refresh" @click="loadProposals">
                刷新列表
              </el-button>
            </div>
          </div>
          <div class="hero-right">
            <div class="hero-metrics">
              <div class="metric-card">
                <p class="metric-label">提案总数</p>
                <p class="metric-value">{{ totalProposals }}</p>
                <span class="metric-hint">社区投票决策</span>
              </div>
              <div class="metric-card">
                <p class="metric-label">投票权要求</p>
                <p class="metric-value">1 票</p>
                <span class="metric-hint">一人一票（需满足资格）</span>
              </div>
              <div class="metric-card">
                <p class="metric-label">投票周期</p>
                <p class="metric-value">1 小时</p>
                <span class="metric-hint">测试环境配置</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- 我的治理资格（总览） -->
      <section
        v-if="eligibility"
        class="governance-eligibility surface-card"
      >
        <div class="eligibility-header">
          <h2>👤 我的治理资格总览</h2>
          <el-tag :type="eligibility.eligible ? 'success' : 'warning'">
            {{ eligibility.eligible ? '当前可参与投票' : '当前暂不可投票' }}
          </el-tag>
        </div>
        <div class="eligibility-grid">
          <div class="eligibility-item">
            <span class="label">地址</span>
            <span class="value">{{ eligibility.address || '未连接钱包' }}</span>
          </div>
          <div class="eligibility-item">
            <span class="label">持币</span>
            <span class="value">
              {{ eligibility.userBalance }} / {{ eligibility.minBalance }} WEE
            </span>
          </div>
          <div class="eligibility-item">
            <span class="label">白名单</span>
            <span class="value">{{ eligibility.isWhitelisted ? '是' : '否' }}</span>
            <span class="label" style="margin-left: 16px;">黑名单</span>
            <span class="value">{{ eligibility.isBlacklisted ? '是' : '否' }}</span>
          </div>
          <div
            class="eligibility-item"
            v-if="eligibility.accountAgeCheckEnabled"
          >
            <span class="label">账户年龄</span>
            <span class="value">
              首次活跃区块 {{ eligibility.accountFirstActiveBlock }}，
              当前快照区块 {{ eligibility.snapshotBlock }}，
              需间隔 {{ eligibility.accountAgeThresholdBlocks }} 区块
              （{{ eligibility.accountAgeEnough ? '已满足' : '未满足' }}）
            </span>
          </div>
          <div
            class="eligibility-item"
            v-if="!eligibility.eligible && (eligibility.reasonText || eligibility.reason)"
          >
            <span class="label">状态说明</span>
            <span class="value">{{ eligibility.reasonText || eligibility.reason }}</span>
          </div>
        </div>
      </section>

      <!-- 委托提示 -->
      <delegate-banner />

      <section class="governance-body surface-card">
        <!-- 筛选标签 -->
        <div class="governance-filter-tabs">
          <el-radio-group v-model="statusFilter" size="small" @change="loadProposals">
            <el-radio-button label="">全部</el-radio-button>
            <el-radio-button label="Active">投票中</el-radio-button>
            <el-radio-button label="Succeeded">已通过</el-radio-button>
            <el-radio-button label="Executed">已执行</el-radio-button>
            <el-radio-button label="Defeated">未通过</el-radio-button>
          </el-radio-group>
        </div>

        <!-- 提案列表 -->
        <div class="proposals-list">
          <!-- 骨架屏加载效果 -->
          <el-row :gutter="24" v-if="loading">
            <el-col
              v-for="i in 6"
              :key="'skeleton-' + i"
              :xs="24" :sm="24" :md="12" :lg="8"
            >
              <div class="proposal-card skeleton-card">
                <el-skeleton :rows="4" animated>
                  <template slot="template">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px;">
                      <el-skeleton-item variant="h3" style="width: 60%;" />
                      <el-skeleton-item variant="button" style="width: 70px; height: 28px;" />
                    </div>
                    <div style="margin-bottom: 12px;">
                      <el-skeleton-item variant="text" style="width: 30%; margin-bottom: 8px;" />
                      <el-skeleton-item variant="text" style="width: 25%;" />
                    </div>
                    <el-skeleton-item variant="text" style="width: 100%; margin-bottom: 8px;" />
                    <el-skeleton-item variant="text" style="width: 90%; margin-bottom: 16px;" />
                    <div style="display: flex; gap: 12px;">
                      <div style="flex: 1;">
                        <el-skeleton-item variant="text" style="width: 60%; margin-bottom: 6px;" />
                        <el-skeleton-item variant="h3" style="width: 40%;" />
                      </div>
                      <div style="flex: 1;">
                        <el-skeleton-item variant="text" style="width: 60%; margin-bottom: 6px;" />
                        <el-skeleton-item variant="h3" style="width: 40%;" />
                      </div>
                    </div>
                  </template>
                </el-skeleton>
              </div>
            </el-col>
          </el-row>

          <div v-if="!loading && proposals.length === 0" class="empty-state">
            <i class="el-icon-document"></i>
            <p class="empty-title">暂无提案</p>
            <p class="empty-hint">成为第一个发起提案的人，开启社区治理之旅</p>
            <el-button 
              v-if="canPropose"
              type="primary" 
              size="small"
              icon="el-icon-plus"
              @click="showCreateDialog = true"
              style="margin-top: 16px;"
            >
              创建提案
            </el-button>
          </div>

          <el-row :gutter="24" v-else-if="proposals.length > 0">
            <el-col
              v-for="proposal in proposals"
              :key="proposal.proposalId"
              :xs="24" :sm="24" :md="12" :lg="8"
            >
              <div class="proposal-card" @click="goToDetail(proposal.proposalId)">
                <div class="proposal-status-badge">
                  <el-tag :type="getStatusType(proposal.status)" size="small">
                    {{ getStatusText(proposal.status) }}
                  </el-tag>
                </div>

                <div class="proposal-header">
                  <h3 class="proposal-title">{{ proposal.title }}</h3>
                  <div class="proposal-meta">
                    <span class="meta-item">
                      <i class="el-icon-user"></i>
                      {{ shortenAddress(proposal.proposer) }}
                    </span>
                    <span class="meta-item">
                      <i class="el-icon-time"></i>
                      {{ formatTime(proposal.createdAt) }}
                    </span>
                  </div>
                </div>

                <div class="proposal-description">
                  {{ truncateText(proposal.description, 120) }}
                </div>

                <div class="proposal-votes">
                  <div class="vote-stats">
                    <div class="vote-stat for">
                      <span class="vote-label">✅ 赞成</span>
                      <span class="vote-value">{{ getVoteCount(proposal, 'for') }}</span>
                      <span class="vote-unit">票</span>
                    </div>
                    <div class="vote-stat against">
                      <span class="vote-label">❌ 反对</span>
                      <span class="vote-value">{{ getVoteCount(proposal, 'against') }}</span>
                      <span class="vote-unit">票</span>
                    </div>
                  </div>
                  <div class="vote-bar">
                    <div
                      class="vote-segment for"
                      :style="{ width: getVotePercentage(proposal, 'for') + '%' }"
                    ></div>
                    <div
                      class="vote-segment against"
                      :style="{ width: getVotePercentage(proposal, 'against') + '%' }"
                    ></div>
                  </div>
                </div>

                <div class="proposal-actions">
                  <el-button type="text" icon="el-icon-view" @click.stop="goToDetail(proposal.proposalId)">
                    查看详情
                  </el-button>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>

        <!-- 分页 -->
        <el-pagination
          v-if="totalProposals > 0"
          @current-change="handlePageChange"
          :current-page="currentPage"
          :page-size="pageSize"
          layout="total, prev, pager, next"
          :total="totalProposals"
          class="pagination"
        />
      </section>

      <!-- 创建提案对话框 -->
      <create-proposal-dialog
        :visible.sync="showCreateDialog"
        @created="handleProposalCreated"
      />
    </div>
  </div>
</template>

<script>
import { mapState, mapGetters, mapActions } from 'vuex'
import DelegateBanner from './components/DelegateBanner.vue'
import CreateProposalDialog from './components/CreateProposalDialog.vue'

export default {
  name: 'ProposalList',
  components: {
    DelegateBanner,
    CreateProposalDialog
  },
  data() {
    return {
      currentPage: 1,
      pageSize: 10,
      totalProposals: 0,
      statusFilter: '',
      showCreateDialog: false,
      eligibility: null
    }
  },
  computed: {
    ...mapState('governance', ['proposals', 'loading']),
    ...mapGetters('governance', ['canPropose'])
  },
  mounted() {
    this.loadProposals()
    this.loadEligibility()
  },
  watch: {
    statusFilter() {
      this.currentPage = 1
      this.loadProposals()
    }
  },
  methods: {
    ...mapActions('governance', ['fetchProposals', 'fetchEligibility']),
    
    async loadProposals() {
      try {
        const pageData = await this.fetchProposals({
          page: this.currentPage,
          size: this.pageSize,
          status: this.statusFilter
        })
        this.totalProposals = pageData.total || (pageData.records ? pageData.records.length : 0) || 0
      } catch (error) {
        this.$message.error('加载提案失败')
      }
    },
    
    handlePageChange(page) {
      this.currentPage = page
      this.loadProposals()
    },

    async loadEligibility() {
      try {
        // 直接复用治理模块的链上查询逻辑
        const eligibility = await this.fetchEligibility({ proposalId: 0 })
        this.eligibility = eligibility
      } catch (error) {
        console.error('[ProposalList] 加载治理资格失败:', error)
        // 查询失败时也给出一张状态卡片，避免用户以为前端没更新
        this.eligibility = {
          address: '',
          eligible: false,
          reason: '无法加载投票资格信息，请确保已连接钱包并切换到正确网络',
          userBalance: '0',
          minBalance: '-',
          isWhitelisted: false,
          isBlacklisted: false,
          accountAgeCheckEnabled: false,
          accountFirstActiveBlock: '0',
          snapshotBlock: '0',
          accountAgeThresholdBlocks: '0',
          accountAgeEnough: false
        }
      }
    },
    
    goToDetail(proposalId) {
      this.$router.push(`/governance/proposals/${proposalId}`)
    },
    
    handleProposalCreated() {
      this.showCreateDialog = false
      this.loadProposals()
      this.$message.success('提案已创建')
    },
    
    getStatusType(status) {
      const typeMap = {
        'Pending': 'info',
        'Active': 'warning',
        'Succeeded': 'success',
        'Executed': 'success',
        'Defeated': 'danger',
        'Canceled': 'info'
      }
      return typeMap[status] || 'info'
    },
    
    getStatusText(status) {
      const textMap = {
        'Pending': '待激活',
        'Active': '投票中',
        'Succeeded': '已通过',
        'Executed': '已执行',
        'Defeated': '未通过',
        'Canceled': '已取消',
        'Queued': '排队中',
        'Expired': '已过期'
      }
      return textMap[status] || status
    },
    
    getVotePercentage(proposal, type) {
      const total = parseFloat(proposal.forVotes || 0) + parseFloat(proposal.againstVotes || 0)
      if (total === 0) return 0
      
      const value = type === 'for' ? parseFloat(proposal.forVotes || 0) : parseFloat(proposal.againstVotes || 0)
      return ((value / total) * 100).toFixed(1)
    },
    
    // 与详情页一致：1 票在数据库中以 0.000000000000000001 存储，这里恢复为整数票数
    getVoteCount(proposal, type) {
      const raw = parseFloat(proposal[type + 'Votes'] || 0)
      if (!raw) return 0
      return Math.floor(raw * 1e18 + 1e-6)
    },
    
    formatNumber(num) {
      return parseFloat(num || 0).toLocaleString()
    },
    
    shortenAddress(address) {
      if (!address) return ''
      return `${address.slice(0, 6)}...${address.slice(-4)}`
    },
    
    truncateText(text, maxLength) {
      if (!text) return ''
      return text.length > maxLength ? text.slice(0, maxLength) + '...' : text
    },
    
    formatTime(time) {
      if (!time) return ''
      return new Date(time).toLocaleDateString('zh-CN')
    }
  }
}
</script>

<style lang="scss" scoped>
.governance-page {
  background: #eef1f7;
  min-height: 100vh;
  padding: 32px 0 60px;
}

.governance-container {
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

.governance-hero {
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

  .metric-label {
    text-transform: uppercase;
    font-size: 12px;
    letter-spacing: 0.08em;
    color: rgba(255, 255, 255, 0.8);
    margin-bottom: 8px;
  }

  .metric-value {
    font-size: 32px;
    font-weight: 700;
    margin: 0 0 4px;
  }

  .metric-hint {
    font-size: 13px;
    color: rgba(255, 255, 255, 0.75);
  }
}

.governance-body {
  min-height: 200px;
}

.governance-eligibility {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.eligibility-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.eligibility-header h2 {
  margin: 0;
  font-size: 20px;
}

.eligibility-grid {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
}

.eligibility-item .label {
  font-weight: 600;
  margin-right: 8px;
  color: #606266;
}

.eligibility-item .value {
  color: #303133;
}

.governance-filter-tabs {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 24px;
}

// 确保栅格行高度一致
.el-row {
  display: flex;
  flex-wrap: wrap;

  .el-col {
    display: flex;
    margin-bottom: 24px;
  }
}

.proposals-list {
  min-height: 400px;
}

.empty-state {
  text-align: center;
  padding: 100px 40px;
  max-width: 480px;
  margin: 0 auto;

  i {
    font-size: 96px;
    color: #dcdfe6;
    margin-bottom: 24px;
    display: block;
  }

  .empty-title {
    font-size: 20px;
    font-weight: 600;
    color: #606266;
    margin: 0 0 12px;
  }

  .empty-hint {
    font-size: 15px;
    color: #909399;
    margin: 0;
    line-height: 1.6;
  }
}

.proposal-card {
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
  border-radius: 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04),
              0 1px 3px rgba(0, 0, 0, 0.02);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  border: 1px solid rgba(0, 0, 0, 0.02);
  
  /* 顶部渐变条 */
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 3px;
    background: linear-gradient(90deg,
      #667eea 0%,
      #764ba2 50%,
      #f093fb 100%);
    opacity: 0;
    transition: opacity 0.3s ease;
  }

  &:hover {
    transform: translateY(-8px) scale(1.01);
    box-shadow: 0 20px 48px rgba(0, 0, 0, 0.12),
                0 8px 16px rgba(0, 0, 0, 0.06);
    border-color: rgba(102, 126, 234, 0.2);
    
    &::before {
      opacity: 1;
    }
  }
  
  &:active {
    transform: translateY(-6px) scale(1.005);
  }

  &.skeleton-card {
    cursor: default;
    padding: 24px;
    
    &:hover {
      transform: none;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
    }
  }

  .proposal-status-badge {
    position: absolute;
    top: 16px;
    right: 16px;
    z-index: 10;
  }

  .proposal-header {
    padding: 20px 20px 12px;
    flex: 1;
    display: flex;
    flex-direction: column;

    .proposal-title {
      margin: 0 30px 12px 0;
      font-size: 18px;
      color: #303133;
      line-height: 1.4;
      height: 2.8em;
      overflow: hidden;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
    }

    .proposal-meta {
      display: flex;
      gap: 16px;
      font-size: 13px;

      .meta-item {
        display: flex;
        align-items: center;
        gap: 4px;
        color: #909399;

        i {
          font-size: 14px;
        }
      }
    }
  }

  .proposal-description {
    padding: 0 20px 16px;
    color: #666;
    font-size: 14px;
    line-height: 1.5;
    height: 60px;
    overflow: hidden;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
  }

  .proposal-votes {
    padding: 16px 20px;
    border-top: 1px solid #f0f0f0;

    .vote-stats {
      display: flex;
      justify-content: space-between;
      margin-bottom: 12px;

      .vote-stat {
        display: flex;
        align-items: baseline;
        gap: 6px;

        .vote-label {
          font-size: 13px;
          color: #909399;
        }

        .vote-value {
          font-size: 18px;
          font-weight: 600;
        }

        .vote-unit {
          font-size: 12px;
          color: #c0c4cc;
        }

        &.for .vote-value {
          color: #67c23a;
        }

        &.against .vote-value {
          color: #f56c6c;
        }
      }
    }

    .vote-bar {
      display: flex;
      height: 8px;
      border-radius: 4px;
      overflow: hidden;
      background: #f0f0f0;

      .vote-segment {
        transition: all 0.3s;

        &.for {
          background: #67c23a;
        }

        &.against {
          background: #f56c6c;
        }
      }
    }
  }

  .proposal-actions {
    padding: 12px 20px;
    border-top: 1px solid #f0f0f0;
    text-align: center;

    .el-button {
      color: #409eff;
    }
  }
}

.pagination {
  margin-top: 24px;
  text-align: center;
}

@media (max-width: 768px) {
  .surface-card {
    padding: 24px;
  }

  .hero-left h1 {
    font-size: 28px;
  }

  .hero-grid {
    grid-template-columns: 1fr;
  }

  .governance-filter-tabs {
    overflow-x: auto;
  }
}
</style>

