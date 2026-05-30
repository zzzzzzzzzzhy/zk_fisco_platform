<template>
  <div class="gas-monitoring">
    <div class="header">
      <h1>🔥 Gas费实时监控</h1>
      <p>监控所有合约交易的Gas费用</p>
    </div>

    <!-- 当前Gas费状态 -->
    <el-row :gutter="20" class="gas-stats">
      <el-col :span="6">
        <el-card class="gas-card">
          <div class="stat-content">
            <div class="stat-icon primary">
              <i class="el-icon-price-tag"></i>
            </div>
            <div class="stat-info">
              <div class="stat-label">当前Gas费</div>
              <div class="stat-value">{{ currentGas.gasPrice }} Gwei</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="gas-card">
          <div class="stat-content">
            <div class="stat-icon success">
              <i class="el-icon-coin"></i>
            </div>
            <div class="stat-info">
              <div class="stat-label">网络</div>
              <div class="stat-value">{{ currentGas.network }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="gas-card">
          <div class="stat-content">
            <div class="stat-icon warning">
              <i class="el-icon-document-copy"></i>
            </div>
            <div class="stat-info">
              <div class="stat-label">今日交易</div>
              <div class="stat-value">{{ todayTransactions }}</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="gas-card">
          <div class="stat-content">
            <div class="stat-icon info">
              <i class="el-icon-money"></i>
            </div>
            <div class="stat-info">
              <div class="stat-label">今日Gas费</div>
              <div class="stat-value">{{ todayGasFee }} MATIC</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 交易历史 -->
    <el-card class="transaction-history">
      <template #header>
        <div class="card-header">
          <span>📊 最近交易Gas费记录</span>
          <el-button @click="refreshData" type="primary" size="small">
            <i class="el-icon-refresh"></i>
            刷新数据
          </el-button>
        </div>
      </template>

      <el-table :data="transactions" stripe v-loading="loading">
        <el-table-column prop="txHash" label="交易哈希" width="120">
          <template #default="{ row }">
            <el-link
              :href="`https://polygonscan.com/tx/${row.txHash}`"
              target="_blank"
              type="primary"
            >
              {{ row.txHash.slice(0, 10) }}...
            </el-link>
          </template>
        </el-table-column>

        <el-table-column prop="fromAddress" label="发送方" width="120">
          <template #default="{ row }">
            {{ row.fromAddress.slice(0, 8) }}...
          </template>
        </el-table-column>

        <el-table-column prop="toAddress" label="接收方" width="120">
          <template #default="{ row }">
            {{ row.toAddress.slice(0, 8) }}...
          </template>
        </el-table-column>

        <el-table-column prop="gasPrice" label="Gas价格" width="100">
          <template #default="{ row }">
            <el-tag size="mini" type="warning">{{ row.gasPrice }} Gwei</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="gasUsed" label="Gas消耗" width="100">
          <template #default="{ row }">
            {{ formatNumber(row.gasUsed) }}
          </template>
        </el-table-column>

        <el-table-column prop="gasFeeEth" label="Gas费用" width="120">
          <template #default="{ row }">
            <el-tag size="mini" type="danger">{{ row.gasFeeEth }} MATIC</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="success" label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="mini" :type="row.success ? 'success' : 'danger'">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="timestamp" label="时间" width="150">
          <template #default="{ row }">
            {{ formatDate(row.timestamp) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Gas费统计图表 -->
    <el-row :gutter="20" class="charts">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>📈 24小时Gas费趋势</span>
          </template>
          <div class="chart-placeholder">
            <div v-if="trendData.length" class="chart-trend">
              <div
                v-for="(item, index) in trendData"
                :key="index"
                class="trend-bar"
              >
                <div
                  class="trend-bar-inner"
                  :style="{ height: calcBarHeight(item.value) }"
                ></div>
                <div class="trend-bar-label">{{ item.hour }}</div>
              </div>
            </div>
            <div v-else class="chart-empty">最近24小时暂无Gas记录</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>
            <span>💰 Gas费用分布</span>
          </template>
          <div class="chart-placeholder">
            <div v-if="distributionData.length" class="chart-pie">
              <div
                v-for="(item, index) in distributionData"
                :key="item.label"
                class="pie-segment"
                :style="{
                  width: (item.percent || 0) + '%',
                  background: pieColors[index % pieColors.length]
                }"
              >
                {{ item.label }} {{ item.percent }}%
              </div>
            </div>
            <div v-else class="chart-empty">最近24小时暂无Gas分布数据</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import request from '@/api/request'

export default {
  name: 'GasMonitoring',
  data() {
    return {
      currentGas: {
        gasPrice: '0',
        network: 'Loading...',
        timestamp: null
      },
      transactions: [],
      loading: false,
      todayTransactions: 0,
      todayGasFee: '0',
      refreshInterval: null,
      // 图表数据
      trendData: [],
      trendMax: 0,
      distributionData: [],
      pieColors: ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399']
    }
  },
  mounted() {
    this.initGasMonitoring()
    this.startAutoRefresh()
  },
  beforeDestroy() {
    if (this.refreshInterval) {
      clearInterval(this.refreshInterval)
    }
  },
  methods: {
    async initGasMonitoring() {
      await this.loadCurrentGas()
      await this.loadTransactions()
      await this.loadTrend()
      await this.loadDistribution()
    },

    async loadCurrentGas() {
      try {
        const response = await request({
          url: '/gas/current',
          method: 'get'
        })

        if (response.code === 200) {
          this.currentGas = response.data
        }
      } catch (error) {
        console.error('获取Gas费信息失败:', error)
      }
    },

    async loadTransactions() {
      this.loading = true
      try {
        const response = await request({
          url: '/gas/transactions',
          method: 'get'
        })

        if (response.code === 200) {
          this.transactions = response.data || []
        } else {
          this.transactions = []
        }
        this.calculateStats()
      } catch (error) {
        console.error('获取交易历史失败:', error)
        this.$message.error('获取交易历史失败')
      } finally {
        this.loading = false
      }
    },

    calculateStats() {
      const today = new Date()
      const todayStr = today.toISOString().slice(0, 10) // yyyy-MM-dd

      const todayList = this.transactions.filter(tx => {
        if (!tx.timestamp) return false
        const d = new Date(tx.timestamp)
        return d.toISOString().slice(0, 10) === todayStr
      })

      this.todayTransactions = todayList.length
      this.todayGasFee = todayList
        .reduce((sum, tx) => sum + parseFloat(tx.gasFeeEth || 0), 0)
        .toFixed(4)
    },

    async loadTrend() {
      try {
        const res = await request({
          url: '/gas/stats/24h-trend',
          method: 'get'
        })
        if (res.code === 200 && res.data) {
          const labels = res.data.labels || []
          const values = res.data.values || []
          this.trendData = labels.map((label, index) => ({
            hour: label,
            value: Number(values[index] || 0)
          }))
          this.trendMax = this.trendData.reduce(
            (max, item) => Math.max(max, item.value),
            0
          )
        }
      } catch (e) {
        console.error('获取24小时Gas趋势失败', e)
      }
    },

    async loadDistribution() {
      try {
        const res = await request({
          url: '/gas/stats/distribution',
          method: 'get'
        })
        if (res.code === 200 && Array.isArray(res.data)) {
          const total = res.data.reduce(
            (sum, item) => sum + Number(item.value || 0),
            0
          )
          this.distributionData = res.data.map(item => {
            const value = Number(item.value || 0)
            const percent = total > 0 ? ((value * 100) / total).toFixed(1) : 0
            return {
              label: item.label || item.bizType || '其他',
              value,
              percent: Number(percent)
            }
          })
        } else {
          this.distributionData = []
        }
      } catch (e) {
        console.error('获取Gas分布失败', e)
      }
    },

    startAutoRefresh() {
      this.refreshInterval = setInterval(() => {
        this.loadCurrentGas()
      }, 10000) // 每10秒刷新一次
    },

    async refreshData() {
      await this.initGasMonitoring()
      this.$message.success('数据已刷新')
    },

    formatNumber(num) {
      return num ? num.toLocaleString() : '0'
    },

    formatDate(date) {
      return new Date(date).toLocaleString('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      })
    },

    calcBarHeight(value) {
      if (!this.trendMax || !value) {
        return '20%';
      }
      const ratio = value / this.trendMax
      const height = 20 + ratio * 80 // 20% ~ 100%
      return `${height}%`
    }
  }
}
</script>

<style scoped>
.gas-monitoring {
  padding: 20px;
}

.header {
  text-align: center;
  margin-bottom: 30px;
}

.header h1 {
  margin: 0;
  color: #409eff;
}

.header p {
  color: #909399;
  margin: 10px 0 0 0;
}

.gas-stats {
  margin-bottom: 30px;
}

.gas-card {
  text-align: center;
}

.stat-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 15px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: white;
}

.stat-icon.primary { background: #409eff; }
.stat-icon.success { background: #67c23a; }
.stat-icon.warning { background: #e6a23c; }
.stat-icon.info { background: #909399; }

.stat-info {
  text-align: left;
}

.stat-label {
  color: #909399;
  font-size: 14px;
  margin-bottom: 5px;
}

.stat-value {
  color: #303133;
  font-size: 18px;
  font-weight: 600;
}

.transaction-history {
  margin-bottom: 30px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.charts {
  margin-top: 30px;
}

.chart-placeholder {
  text-align: center;
  padding: 20px;
  color: #909399;
}

.chart-trend {
  display: flex;
  align-items: end;
  height: 150px;
  gap: 2px;
  margin-top: 10px;
}

.trend-bar {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.trend-bar-inner {
  width: 100%;
  background: #409eff;
  border-radius: 2px 2px 0 0;
  min-height: 20px;
}

.trend-bar-label {
  margin-top: 4px;
  font-size: 10px;
  color: #909399;
}

.chart-pie {
  margin-top: 10px;
  text-align: left;
}

.pie-segment {
  padding: 5px 10px;
  color: white;
  text-align: center;
  margin: 2px 0;
  border-radius: 4px;
}
</style>
