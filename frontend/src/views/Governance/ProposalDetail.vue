<template>
  <div class="proposal-detail-page" v-loading="loading">
    <div class="proposal-detail-container">
      <el-button icon="el-icon-back" @click="$router.push('/governance')" style="margin-bottom: 20px;">
        返回提案列表
      </el-button>

    <el-card v-if="proposal" class="proposal-card">
      <!-- 提案标题 -->
      <div class="proposal-header">
        <h1>{{ proposal.title }}</h1>
        <el-tag :type="getStatusType(proposal.status)" size="large">
          {{ getStatusText(proposal.status) }}
        </el-tag>
      </div>

      <!-- 提案元信息 -->
      <div class="proposal-meta">
        <div class="meta-item">
          <span class="label">发起人:</span>
          <span class="value">{{ proposal.proposer }}</span>
        </div>
        <div class="meta-item">
          <span class="label">创建时间:</span>
          <span class="value">{{ formatTime(proposal.createdAt) }}</span>
        </div>
        <div class="meta-item">
          <span class="label">提案ID:</span>
          <span class="value">{{ shortenId(proposal.proposalId) }}</span>
        </div>
      </div>

      <!-- 提案描述 -->
      <el-divider></el-divider>
      <div class="proposal-description">
        <h3>📝 提案说明</h3>
        <div class="description-content" v-html="formattedDescription"></div>
      </div>

      <!-- 投票统计 -->
      <el-divider></el-divider>
      <div class="vote-statistics">
        <h3>📊 投票统计</h3>
        <div class="vote-bars">
          <div class="vote-item for">
            <div class="vote-label">
              <span>✅ 赞成</span>
              <span class="vote-count">{{ getVoteCount('for') }} 票</span>
            </div>
            <el-progress
              :percentage="parseFloat(getVotePercentage('for'))"
              :stroke-width="20"
              color="#67c23a"
            ></el-progress>
          </div>

          <div class="vote-item against">
            <div class="vote-label">
              <span>❌ 反对</span>
              <span class="vote-count">{{ getVoteCount('against') }} 票</span>
            </div>
            <el-progress
              :percentage="parseFloat(getVotePercentage('against'))"
              :stroke-width="20"
              color="#f56c6c"
            ></el-progress>
          </div>

          <div class="vote-item abstain">
            <div class="vote-label">
              <span>🤷 弃权</span>
              <span class="vote-count">{{ getVoteCount('abstain') }} 票</span>
            </div>
            <el-progress
              :percentage="parseFloat(getVotePercentage('abstain'))"
              :stroke-width="20"
              color="#909399"
            ></el-progress>
          </div>
        </div>
      </div>

      <!-- 我的治理资格 -->
      <el-divider></el-divider>
      <div class="eligibility-card" v-if="eligibility">
        <h3>👤 我的治理资格</h3>
        <el-tag :type="eligibility.eligible ? 'success' : 'warning'">
          {{ eligibility.eligible ? '本提案可以投票' : '本提案当前不可投票' }}
        </el-tag>
        <p class="eligibility-item">
          <span class="label">地址:</span>
          <span class="value">{{ eligibility.address }}</span>
        </p>
        <p class="eligibility-item">
          <span class="label">持币:</span>
          <span class="value">{{ eligibility.userBalance }} / {{ eligibility.minBalance }} WEE</span>
        </p>
        <p class="eligibility-item">
          <span class="label">白名单:</span>
          <span class="value">{{ eligibility.isWhitelisted ? '是' : '否' }}</span>
          <span class="label" style="margin-left: 16px;">黑名单:</span>
          <span class="value">{{ eligibility.isBlacklisted ? '是' : '否' }}</span>
        </p>
        <p class="eligibility-item" v-if="eligibility.accountAgeCheckEnabled">
          <span class="label">账户年龄:</span>
          <span class="value">
            首次活跃区块 {{ eligibility.accountFirstActiveBlock }}，
            快照区块 {{ eligibility.snapshotBlock }}，
            需间隔 {{ eligibility.accountAgeThresholdBlocks }} 区块
            （{{ eligibility.accountAgeEnough ? '已满足' : '未满足' }}）
          </span>
        </p>
        <p class="eligibility-item" v-if="!eligibility.eligible && (eligibility.reasonText || eligibility.reason)">
          <span class="label">状态说明:</span>
          <span class="value">{{ eligibility.reasonText || eligibility.reason }}</span>
        </p>
      </div>

      <!-- 投票操作 -->
      <el-divider></el-divider>
      <div class="vote-actions" v-if="proposal.status === 'Active'">
        <h3>🗳️ 参与投票</h3>
        <div v-if="userVote">
          <el-alert
            title="你已投票"
            type="success"
            :closable="false"
          >
            <p>投票类型: {{ getVoteTypeText(userVote.support) }}</p>
            <p>投票权重: {{ formatVoteWeight(userVote.weight) }} 票</p>
          </el-alert>
        </div>
        <div v-else-if="hasVotingPower">
          <el-radio-group v-model="voteSupport" class="vote-options">
            <el-radio :label="1" border>✅ 赞成</el-radio>
            <el-radio :label="0" border>❌ 反对</el-radio>
            <el-radio :label="2" border>🤷 弃权</el-radio>
          </el-radio-group>
          <el-input
            v-model="voteReason"
            type="textarea"
            :rows="3"
            placeholder="投票理由（可选）"
            style="margin-top: 10px;"
          />
          <el-button
            type="primary"
            @click="handleVote"
            :loading="voting"
            style="margin-top: 10px;"
          >
            提交投票
          </el-button>
        </div>
        <el-alert
          v-else
          title="你还没有投票权"
          type="warning"
          :closable="false"
        >
          <p>请先激活投票权才能参与投票</p>
        </el-alert>
      </div>

      <!-- 执行提案 -->
      <div class="execute-actions" v-if="proposal.status === 'Succeeded'">
        <el-divider></el-divider>
        <h3>⚡ 执行提案</h3>
        <el-alert
          title="提案已通过"
          type="success"
          :closable="false"
          style="margin-bottom: 15px;"
        >
          <p>任何人都可以执行这个提案</p>
        </el-alert>
        <el-button
          type="success"
          @click="handleExecute"
          :loading="executing"
        >
          立即执行
        </el-button>
      </div>
    </el-card>
    </div>
  </div>
</template>

<script>
import { mapState, mapGetters, mapActions } from 'vuex'

export default {
  name: 'ProposalDetail',
  data() {
    return {
      loading: false,
      voting: false,
      executing: false,
      proposal: null,
      userVote: null,
      voteSupport: 1,
      voteReason: '',
      eligibility: null
    }
  },
  computed: {
    ...mapState('governance', ['userVotingPower']),
    ...mapGetters('governance', ['hasVotingPower']),
    formattedDescription() {
      if (!this.proposal) return ''
      return this.proposal.description.replace(/\n/g, '<br>')
    }
  },
  mounted() {
    this.loadProposal()
  },
  methods: {
    ...mapActions('governance', ['fetchProposal', 'castVote', 'executeProposal', 'fetchEligibility']),
    
    async loadProposal() {
      this.loading = true
      try {
        const proposalId = this.$route.params.id
        this.proposal = await this.fetchProposal(proposalId)
        await this.loadUserVote()
        await this.loadEligibility()
      } catch (error) {
        this.$message.error('加载提案失败')
        console.error(error)
      } finally {
        this.loading = false
      }
    },
    
    async loadUserVote() {
      // TODO: 从后端加载用户投票
    },

    async loadEligibility() {
      try {
        if (!this.proposal) {
          console.warn('[ProposalDetail] loadEligibility: proposal 为空，跳过')
          return
        }
        console.log('[ProposalDetail] 开始加载投票资格，proposalId:', this.proposal.proposalId)
        const eligibility = await this.fetchEligibility({ proposalId: this.proposal.proposalId })
        console.log('[ProposalDetail] 投票资格已加载:', eligibility)
        this.eligibility = eligibility
      } catch (error) {
        console.error('[ProposalDetail] 加载投票资格失败:', error)
        // 查询失败时也渲染一张卡片，给出失败原因，避免用户误以为前端没有更新
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
        // 友好提示
        this.$message.warning('无法加载投票资格信息，请确保已连接钱包并切换到正确网络')
      }
    },
    
    async handleVote() {
      this.voting = true
      try {
        await this.castVote({
          proposalId: this.proposal.proposalId,
          support: this.voteSupport,
          reason: this.voteReason
        })
        
        this.$message.success('投票成功！')
        await this.loadProposal()
      } catch (error) {
        this.$handleTxError(error)
      } finally {
        this.voting = false
      }
    },
    
    async handleExecute() {
      this.executing = true
      try {
        const targets = JSON.parse(this.proposal.targets)
        const values = JSON.parse(this.proposal.values)
        const calldatas = JSON.parse(this.proposal.calldatas)
        
        // 计算 descriptionHash
        const { ethers } = await import('ethers')
        const descriptionHash = ethers.utils.id(this.proposal.description)
        
        await this.executeProposal({
          targets,
          values,
          calldatas,
          descriptionHash
        })
        
        this.$message.success('提案已执行！')
        await this.loadProposal()
      } catch (error) {
        this.$handleTxError(error)
      } finally {
        this.executing = false
      }
    },
    
    getVotePercentage(type) {
      const total = parseFloat(this.proposal.forVotes || 0) + 
                    parseFloat(this.proposal.againstVotes || 0) + 
                    parseFloat(this.proposal.abstainVotes || 0)
      if (total === 0) return '0'
      
      const value = parseFloat(this.proposal[type + 'Votes'] || 0)
      return ((value / total) * 100).toFixed(1)
    },

    // 一人一票模式下，后端仍以 18 位小数存储票数（1 票 = 0.000000000000000001），这里做一次还原
    getVoteCount(type) {
      if (!this.proposal) return 0
      const raw = parseFloat(this.proposal[type + 'Votes'] || 0)
      if (!raw) return 0
      // 乘以 1e18，还原为整数票数，并取整
      return Math.floor(raw * 1e18 + 1e-6)
    },

    formatVoteWeight(weight) {
      if (!weight) return 0
      const raw = parseFloat(weight)
      if (!raw) return 0
      return Math.floor(raw * 1e18 + 1e-6)
    },
    
    getStatusType(status) {
      const typeMap = {
        'Pending': 'info',
        'Active': 'warning',
        'Succeeded': 'success',
        'Executed': 'success',
        'Defeated': 'danger'
      }
      return typeMap[status] || 'info'
    },
    
    getStatusText(status) {
      const textMap = {
        'Pending': '待激活',
        'Active': '投票中',
        'Succeeded': '已通过',
        'Executed': '已执行',
        'Defeated': '未通过'
      }
      return textMap[status] || status
    },
    
    getVoteTypeText(support) {
      return support === 1 ? '赞成' : support === 0 ? '反对' : '弃权'
    },
    
    formatNumber(num) {
      return parseFloat(num || 0).toLocaleString()
    },
    
    formatTime(time) {
      return time ? new Date(time).toLocaleString('zh-CN') : ''
    },
    
    shortenId(id) {
      return id ? `${id.slice(0, 10)}...${id.slice(-8)}` : ''
    }
  }
}
</script>

<style scoped>
.proposal-detail-page {
  min-height: calc(100vh - 60px);
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  padding: 30px 0;
}

.proposal-detail-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
}

.proposal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.proposal-header h1 {
  margin: 0;
  font-size: 28px;
}

.proposal-meta {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 15px;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
}

.meta-item .label {
  font-weight: bold;
  margin-right: 10px;
}

.proposal-description {
  margin: 20px 0;
}

.description-content {
  line-height: 1.8;
  padding: 15px;
  background: #f5f7fa;
  border-radius: 4px;
}

.vote-statistics h3,
.vote-actions h3,
.execute-actions h3 {
  margin-bottom: 20px;
}

.vote-bars {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.vote-item .vote-label {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: bold;
}

.vote-options {
  display: flex;
  gap: 15px;
  flex-wrap: wrap;
}

.vote-options .el-radio.is-bordered {
  padding: 12px 20px;
}
</style>

