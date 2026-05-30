<template>
  <div class="prize-management-page">
    <div class="container">
      <div class="page-header hero-card">
        <div class="page-header__content">
          <h2>奖金管理</h2>
          <p>维护奖金池、分配流和发放批次，保障激励闭环。</p>
        </div>
      </div>

      <el-tabs v-model="activeTab" type="card">
        <!-- 奖金池管理 -->
        <el-tab-pane label="奖金池管理" name="pools">
          <div class="tab-content">
            <div class="actions-bar">
              <el-button type="primary" icon="el-icon-plus" @click="showCreatePoolDialog = true">
                创建奖金池
              </el-button>
            </div>

            <el-table :data="prizePools" border stripe v-loading="loading">
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column prop="poolNo" label="奖金池编号" width="180" />
              <el-table-column prop="poolName" label="名称" min-width="150" />
              <el-table-column prop="competitionId" label="竞赛ID" width="100" />
              <el-table-column prop="totalAmount" label="总金额（元）" width="120">
                <template slot-scope="scope">
                  ¥{{ (scope.row.totalAmount / 100).toFixed(2) }}
                </template>
              </el-table-column>
              <el-table-column prop="allocatedAmount" label="已分配（元）" width="120">
                <template slot-scope="scope">
                  ¥{{ (scope.row.allocatedAmount / 100).toFixed(2) }}
                </template>
              </el-table-column>
              <el-table-column prop="status" label="状态" width="100">
                <template slot-scope="scope">
                  <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'">
                    {{ scope.row.status === 'ACTIVE' ? '活跃' : '关闭' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="locked" label="锁定" width="80">
                <template slot-scope="scope">
                  <el-tag :type="scope.row.locked ? 'danger' : 'success'" size="small">
                    {{ scope.row.locked ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template slot-scope="scope">
                  <el-button size="mini" @click="viewPoolDetail(scope.row)">详情</el-button>
                  <el-button
                    v-if="!scope.row.locked"
                    size="mini"
                    type="warning"
                    @click="lockPool(scope.row.id)"
                  >
                    锁定
                  </el-button>
                  <el-button
                    v-else
                    size="mini"
                    type="success"
                    @click="unlockPool(scope.row.id)"
                  >
                    解锁
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 奖金分配 -->
        <el-tab-pane label="奖金分配" name="allocations">
          <div class="tab-content">
            <div class="actions-bar">
              <el-select v-model="selectedPoolId" placeholder="选择奖金池" style="width: 300px; margin-right: 16px">
                <el-option
                  v-for="pool in prizePools"
                  :key="pool.id"
                  :label="`${pool.poolName} (${pool.poolNo})`"
                  :value="pool.id"
                />
              </el-select>
              <el-button type="primary" icon="el-icon-plus" @click="showCreateAllocationDialog = true">
                创建分配
              </el-button>
              <el-button type="success" icon="el-icon-upload" @click="showBatchAllocationDialog = true">
                批量分配
              </el-button>
            </div>

            <el-table :data="allocations" border stripe v-loading="loading">
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column prop="userId" label="用户ID" width="100" />
              <el-table-column prop="amount" label="金额（元）" width="120">
                <template slot-scope="scope">
                  ¥{{ (scope.row.amount / 100).toFixed(2) }}
                </template>
              </el-table-column>
              <el-table-column prop="rank" label="排名" width="80" />
              <el-table-column prop="status" label="状态" width="100">
                <template slot-scope="scope">
                  <el-tag :type="getAllocationStatusType(scope.row.status)">
                    {{ getAllocationStatusText(scope.row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="kycStatus" label="KYC状态" width="100">
                <template slot-scope="scope">
                  <el-tag :type="getKycStatusType(scope.row.kycStatus)" size="small">
                    {{ getKycStatusText(scope.row.kycStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="riskStatus" label="风控状态" width="100">
                <template slot-scope="scope">
                  <el-tag :type="getRiskStatusType(scope.row.riskStatus)" size="small">
                    {{ getRiskStatusText(scope.row.riskStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right">
                <template slot-scope="scope">
                  <el-button size="mini" @click="viewAllocationDetail(scope.row)">详情</el-button>
                  <el-button
                    v-if="scope.row.riskStatus === 'PENDING'"
                    size="mini"
                    type="warning"
                    @click="checkRisk(scope.row)"
                  >
                    风控
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 风控审核 -->
        <el-tab-pane label="风控审核" name="risk">
          <div class="tab-content">
            <el-table :data="riskRecords" border stripe v-loading="loading">
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column prop="allocationId" label="分配ID" width="100" />
              <el-table-column prop="userId" label="用户ID" width="100" />
              <el-table-column prop="prizeAmount" label="金额（元）" width="120">
                <template slot-scope="scope">
                  ¥{{ (scope.row.prizeAmount / 100).toFixed(2) }}
                </template>
              </el-table-column>
              <el-table-column prop="riskScore" label="风险评分" width="100">
                <template slot-scope="scope">
                  <span :style="{ color: getRiskScoreColor(scope.row.riskScore) }">
                    {{ scope.row.riskScore }}
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="riskLevel" label="风险等级" width="100">
                <template slot-scope="scope">
                  <el-tag :type="getRiskLevelType(scope.row.riskLevel)" size="small">
                    {{ scope.row.riskLevel }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="decision" label="决策" width="100">
                <template slot-scope="scope">
                  <el-tag :type="getDecisionType(scope.row.decision)">
                    {{ getDecisionText(scope.row.decision) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template slot-scope="scope">
                  <el-button size="mini" @click="viewRiskDetail(scope.row)">详情</el-button>
                  <el-button
                    v-if="scope.row.decision === 'REVIEW'"
                    size="mini"
                    type="primary"
                    @click="showReviewRiskDialog(scope.row)"
                  >
                    审核
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 批次发放 -->
        <el-tab-pane label="批次发放" name="disbursement">
          <div class="tab-content">
            <div class="actions-bar">
              <el-button type="primary" icon="el-icon-plus" @click="showCreateBatchDialog = true">
                创建发放批次
              </el-button>
            </div>

            <el-table :data="batches" border stripe v-loading="loading">
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column prop="batchNo" label="批次号" width="200" />
              <el-table-column prop="totalCount" label="总人数" width="100" />
              <el-table-column prop="totalAmount" label="总金额（元）" width="120">
                <template slot-scope="scope">
                  ¥{{ (scope.row.totalAmount / 100).toFixed(2) }}
                </template>
              </el-table-column>
              <el-table-column prop="successCount" label="成功" width="80" />
              <el-table-column prop="failedCount" label="失败" width="80" />
              <el-table-column prop="status" label="状态" width="100">
                <template slot-scope="scope">
                  <el-tag :type="getBatchStatusType(scope.row.status)">
                    {{ getBatchStatusText(scope.row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="200" fixed="right">
                <template slot-scope="scope">
                  <el-button size="mini" @click="viewBatchDetail(scope.row)">详情</el-button>
                  <el-button
                    v-if="scope.row.status === 'CREATED'"
                    size="mini"
                    type="success"
                    @click="executeBatch(scope.row.id)"
                  >
                    执行发放
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 创建奖金池对话框 -->
      <el-dialog
        title="创建奖金池"
        :visible.sync="showCreatePoolDialog"
        width="500px"
      >
        <el-form :model="poolForm" label-width="120px">
          <el-form-item label="竞赛ID">
            <el-input v-model.number="poolForm.competitionId" type="number" />
          </el-form-item>
          <el-form-item label="奖金池名称">
            <el-input v-model="poolForm.poolName" />
          </el-form-item>
          <el-form-item label="总金额（元）">
            <el-input-number v-model="poolForm.totalAmount" :min="0" :precision="2" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="poolForm.description" type="textarea" :rows="3" />
          </el-form-item>
        </el-form>
        <span slot="footer">
          <el-button @click="showCreatePoolDialog = false">取消</el-button>
          <el-button type="primary" @click="handleCreatePool">确定</el-button>
        </span>
      </el-dialog>
    </div>
  </div>
</template>

<script>
import {
  createPrizePool,
  lockPrizePool,
  unlockPrizePool
} from '@/api/prize'

export default {
  name: 'PrizeManagement',
  data() {
    return {
      activeTab: 'pools',
      loading: false,
      prizePools: [],
      allocations: [],
      riskRecords: [],
      batches: [],
      selectedPoolId: null,
      showCreatePoolDialog: false,
      showCreateAllocationDialog: false,
      showBatchAllocationDialog: false,
      showCreateBatchDialog: false,
      poolForm: {
        competitionId: null,
        poolName: '',
        totalAmount: 0,
        description: ''
      }
    }
  },
  watch: {
    activeTab(newTab) {
      this.loadTabData(newTab)
    },
    selectedPoolId(newVal) {
      if (newVal && this.activeTab === 'allocations') {
        this.loadAllocations()
      }
    }
  },
  created() {
    this.loadTabData('pools')
  },
  methods: {
    loadTabData(tab) {
      switch (tab) {
        case 'pools':
          this.loadPrizePools()
          break
        case 'allocations':
          if (this.selectedPoolId) {
            this.loadAllocations()
          }
          break
        case 'risk':
          this.loadRiskRecords()
          break
        case 'disbursement':
          this.loadBatches()
          break
      }
    },

    async loadPrizePools() {
      // 模拟数据
      this.prizePools = [
        {
          id: 1,
          poolNo: 'POOL_20251024_001',
          poolName: '零界演化奖金池',
          competitionId: 1,
          totalAmount: 10000000,
          allocatedAmount: 5000000,
          status: 'ACTIVE',
          locked: false
        }
      ]
    },

    async loadAllocations() {
      // 模拟数据
      this.allocations = []
    },

    async loadRiskRecords() {
      // 模拟数据
      this.riskRecords = []
    },

    async loadBatches() {
      // 模拟数据
      this.batches = []
    },

    async handleCreatePool() {
      try {
        const data = {
          ...this.poolForm,
          totalAmount: Math.floor(this.poolForm.totalAmount * 100)
        }
        await createPrizePool(data)
        this.$message.success('创建成功')
        this.showCreatePoolDialog = false
        this.loadPrizePools()
      } catch (error) {
        this.$message.error('创建失败')
      }
    },

    async lockPool(id) {
      try {
        await lockPrizePool(id)
        this.$message.success('锁定成功')
        this.loadPrizePools()
      } catch (error) {
        this.$message.error('锁定失败')
      }
    },

    async unlockPool(id) {
      try {
        await unlockPrizePool(id)
        this.$message.success('解锁成功')
        this.loadPrizePools()
      } catch (error) {
        this.$message.error('解锁失败')
      }
    },

    viewPoolDetail() {
      this.$message.info('查看奖金池详情')
    },

    viewAllocationDetail() {
      this.$message.info('查看分配详情')
    },

    checkRisk() {
      this.$message.info('执行风控检查')
    },

    viewRiskDetail() {
      this.$message.info('查看风控详情')
    },

    showReviewRiskDialog() {
      this.$message.info('人工审核')
    },

    viewBatchDetail() {
      this.$message.info('查看批次详情')
    },

    async executeBatch() {
      try {
        await this.$confirm('确认执行批量发放吗？', '提示', { type: 'warning' })
        this.$message.info('执行批量发放')
      } catch (error) {
        // Cancelled
      }
    },

    // 状态类型映射
    getAllocationStatusType(status) {
      const map = { CREATED: 'info', KYC_PENDING: 'warning', RISK_PASSED: 'success', DISBURSED: 'success' }
      return map[status] || 'info'
    },

    getAllocationStatusText(status) {
      const map = { CREATED: '已创建', KYC_PENDING: 'KYC待审', RISK_PASSED: '风控通过', DISBURSED: '已发放' }
      return map[status] || status
    },

    getKycStatusType(status) {
      const map = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
      return map[status] || 'info'
    },

    getKycStatusText(status) {
      const map = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }
      return map[status] || status
    },

    getRiskStatusType(status) {
      const map = { PENDING: 'warning', PASS: 'success', REVIEW: 'warning', REJECT: 'danger' }
      return map[status] || 'info'
    },

    getRiskStatusText(status) {
      const map = { PENDING: '待检查', PASS: '通过', REVIEW: '人工审核', REJECT: '拒绝' }
      return map[status] || status
    },

    getRiskScoreColor(score) {
      if (score < 30) return '#67c23a'
      if (score < 60) return '#e6a23c'
      return '#f56c6c'
    },

    getRiskLevelType(level) {
      const map = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }
      return map[level] || 'info'
    },

    getDecisionType(decision) {
      const map = { PASS: 'success', REVIEW: 'warning', REJECT: 'danger' }
      return map[decision] || 'info'
    },

    getDecisionText(decision) {
      const map = { PASS: '通过', REVIEW: '人工审核', REJECT: '拒绝' }
      return map[decision] || decision
    },

    getBatchStatusType(status) {
      const map = { CREATED: 'info', PROCESSING: 'warning', COMPLETED: 'success', FAILED: 'danger' }
      return map[status] || 'info'
    },

    getBatchStatusText(status) {
      const map = { CREATED: '已创建', PROCESSING: '处理中', COMPLETED: '已完成', FAILED: '失败' }
      return map[status] || status
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/admin-common.scss';

.prize-management-page {
  min-height: calc(100vh - 64px);
  padding: 40px 0;
}

.page-header {
  margin-bottom: var(--spacing-xl);

  h2 {
    margin: 0;
    font-size: 32px;
    font-weight: 700;
  }

  p {
    margin: var(--spacing-xs) 0 0;
    color: rgba(255, 255, 255, 0.9);
  }
}

.tab-content {
  padding: 24px 0;
}

.actions-bar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
}
</style>
