<template>
  <div class="coffee-mapping-page">
    <div class="coffee-mapping-container">
      <section class="coffee-hero surface-card">
        <div class="hero-grid">
          <div class="hero-left">
            <p class="hero-eyebrow">RWA 现实权益映射</p>
            <h1>咖啡映射</h1>
            <p class="hero-lead">
              扫码领取算力，累计真实消费贡献。每个 Slot 一枚不可转让身份 Token。
            </p>
            <div class="hero-actions">
              <el-button type="primary" @click="connectWallet" :loading="connecting">
                {{ walletAddress ? '已连接钱包' : '连接钱包' }}
              </el-button>
              <span v-if="walletAddress" class="hero-chip">
                {{ shortAddress }}
              </span>
              <span v-if="networkName" class="hero-chip hero-chip--ghost">
                {{ networkName }}
              </span>
            </div>
          </div>
          <div class="hero-right">
            <div class="hero-metrics">
              <div class="metric-card">
                <p class="metric-label">我的总算力</p>
                <p class="metric-value">{{ totalPowerDisplay }}</p>
                <span class="metric-hint">累计算力（跨所有 Slot）</span>
              </div>
              <div class="metric-card">
                <p class="metric-label">当前 Slot</p>
                <p class="metric-value">{{ slotPowerDisplay }}</p>
                <span class="metric-hint">Slot 算力</span>
              </div>
              <div class="metric-card">
                <p class="metric-label">Token ID</p>
                <p class="metric-value">{{ tokenIdDisplay }}</p>
                <span class="metric-hint">身份锚定</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <el-row :gutter="24" class="main-grid">
        <el-col :xs="24" :lg="14">
          <el-card class="mapping-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>映射领取</span>
              <span class="hint">需要后端签名 + 钱包确认交易</span>
            </div>

            <el-form ref="form" :model="form" label-width="90px">
              <el-form-item label="订单 ID">
                <el-input v-model="form.orderId" placeholder="输入或扫码获得订单 ID" />
              </el-form-item>

            <el-form-item label="门店">
              <el-select v-model="form.storeId" placeholder="选择门店">
                <el-option
                  v-for="option in storeOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="咖啡类型">
              <el-select v-model="form.slot" placeholder="选择 Slot">
                <el-option
                  v-for="option in slotOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>

            <el-form-item label="算力档位">
              <el-select v-model="form.powerPreset" placeholder="选择档位" @change="applyPowerPreset">
                <el-option
                  v-for="option in powerOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>

              <el-form-item label="算力">
                <el-input-number v-model="form.amount" :min="0.1" :step="0.1" :precision="2" />
              </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="claimPower" :loading="claiming">
                领取算力
              </el-button>
              <el-button type="success" plain @click="simulateScan" :loading="claiming">
                模拟扫码
              </el-button>
              <el-button @click="loadStats" :disabled="!walletAddress || !contract">
                刷新统计
              </el-button>
            </el-form-item>
          </el-form>

            <div v-if="txHash" class="tx-info">
              <span>交易哈希:</span>
              <a :href="txExplorerUrl" target="_blank" rel="noopener noreferrer">
                {{ txHashShort }}
              </a>
            </div>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="10">
          <el-card class="info-card badge-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>身份徽章</span>
            </div>
            <div class="badge-preview">
              <div class="badge-image">
                <img :src="currentBadge.image" :alt="currentBadge.name" />
              </div>
              <div class="badge-info">
                <div class="badge-name">{{ currentBadge.name }}</div>
                <div class="badge-subtitle">{{ currentBadge.subtitle }}</div>
                <div class="badge-meta">Slot {{ form.slot }} · SBT 身份锚定</div>
              </div>
            </div>
          </el-card>
          <el-card class="info-card reward-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>每周奖励</span>
              <span class="hint">每周奖池 {{ weeklyPoolDisplay }} WEE</span>
            </div>
            <div class="reward-row">
              <span>预计可领</span>
              <strong>{{ rewardEstimateDisplay }} WEE</strong>
            </div>
            <div class="reward-row">
              <span>我的算力</span>
              <strong>{{ rewardUserPowerDisplay }}</strong>
            </div>
            <div class="reward-row">
              <span>全网算力</span>
              <strong>{{ rewardTotalPowerDisplay }}</strong>
            </div>
            <div class="reward-row">
              <span>下次结算</span>
              <strong>{{ nextSnapshotDisplay }}</strong>
            </div>
            <div class="reward-visual">
              <div class="reward-metric">
                <div class="reward-metric-header">
                  <span>我的算力占比</span>
                  <strong>{{ powerPercentDisplay }}%</strong>
                </div>
                <div class="reward-bar">
                  <span class="reward-bar-fill" :style="{ width: powerPercentDisplay + '%' }"></span>
                </div>
              </div>
              <div class="reward-metric">
                <div class="reward-metric-header">
                  <span>预计奖励占比</span>
                  <strong>{{ rewardPercentDisplay }}%</strong>
                </div>
                <div class="reward-bar reward-bar--accent">
                  <span class="reward-bar-fill" :style="{ width: rewardPercentDisplay + '%' }"></span>
                </div>
              </div>
            </div>
            <div class="reward-actions">
              <el-button size="mini" @click="fetchRewardEstimate" :loading="rewardLoading">
                刷新估算
              </el-button>
              <el-button
                size="mini"
                type="success"
                :loading="claimingReward"
                :disabled="!canClaimReward || !rewardContract"
                @click="claimReward"
              >
                领取奖励
              </el-button>
            </div>
            <div class="reward-note">领取流程基于 Merkle 证明快照</div>
          </el-card>
          <el-card class="info-card policy-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>策略参数</span>
              <span class="hint">演示配置，可热更新</span>
            </div>
            <el-form :model="policyForm" label-width="120px" size="mini" class="policy-form">
              <el-form-item label="聚合桶">
                <el-select v-model="policyForm.aggregation_bucket" placeholder="选择粒度">
                  <el-option label="day" value="day" />
                  <el-option label="hour" value="hour" />
                </el-select>
              </el-form-item>
              <el-form-item label="阈值 T">
                <el-input-number v-model="policyForm.threshold" :min="0" :step="1" />
              </el-form-item>
              <el-form-item label="alpha">
                <el-input-number v-model="policyForm.alpha" :min="0" :step="0.05" :precision="2" />
              </el-form-item>
              <el-form-item label="min_balance">
                <el-input-number v-model="policyForm.min_balance" :min="0" :step="0.1" :precision="2" />
              </el-form-item>
              <el-form-item label="loyalty_step">
                <el-input-number v-model="policyForm.loyalty_step" :min="0" :step="0.01" :precision="2" />
              </el-form-item>
              <el-form-item label="loyalty_cap">
                <el-input-number v-model="policyForm.loyalty_cap" :min="0" :step="0.05" :precision="2" />
              </el-form-item>
            </el-form>
            <div class="policy-actions">
              <el-button size="mini" @click="fetchPolicyConfig" :loading="policyLoading">
                刷新配置
              </el-button>
              <el-button size="mini" type="primary" @click="savePolicyConfig" :loading="policySaving">
                保存配置
              </el-button>
            </div>
            <div class="policy-note">
              最新更新时间：{{ policyUpdatedAtDisplay }}
            </div>
            <div class="policy-formula">
              <div class="policy-formula-header">
                <span>计算摘要</span>
                <el-button type="text" size="mini" @click="togglePolicyFormula">
                  {{ policyFormulaOpen ? '收起' : '查看详情' }}
                </el-button>
              </div>
              <ul class="policy-formula-list">
                <li>聚合桶：{{ policyForm.aggregation_bucket }}</li>
                <li>平滑 DS：S = 1 / (1 + α × max(0, N - T))</li>
                <li>算力：Power = Σ sqrt(A') × Loyalty</li>
              </ul>
              <div v-if="policyFormulaOpen" class="policy-formula-detail">
                <p>当前参数：T={{ policyForm.threshold }}，α={{ policyForm.alpha }}，min_balance={{ policyForm.min_balance }}。</p>
                <p>当 N 超过阈值时，S 会降低；A' = A × S。</p>
                <p>每周结算按地址聚合后取 sqrt(A')，再乘连续活跃加成。</p>
              </div>
            </div>
          </el-card>
          <el-card class="info-card verify-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>快照验证中心</span>
              <span class="hint">本地校验，不上传隐私数据</span>
            </div>
            <div class="verify-hero">
              <div class="verify-pill" :class="snapshotVerifyClass">
                <i v-if="snapshotVerifyStatus === 'valid'" class="el-icon-circle-check"></i>
                <i v-else-if="snapshotVerifyStatus === 'invalid' || snapshotVerifyStatus === 'error'" class="el-icon-warning-outline"></i>
                <i v-else-if="verifyingSnapshot" class="el-icon-loading"></i>
                <i v-else class="el-icon-info"></i>
                <span>{{ snapshotVerifyLabel }}</span>
              </div>
              <div class="verify-meta">
                <div class="verify-meta-row">
                  <span>Epoch</span>
                  <strong>{{ snapshotEpoch || reward.epoch || '--' }}</strong>
                </div>
                <div class="verify-meta-row">
                  <span>校验时间</span>
                  <strong>{{ snapshotCheckedAtDisplay }}</strong>
                </div>
              </div>
            </div>
            <div class="verify-progress">
              <el-progress
                :percentage="snapshotProgress"
                :status="snapshotProgressStatus"
                :text-inside="true"
                :stroke-width="16"
              />
            </div>
            <el-steps :active="snapshotStepActive" direction="vertical" class="verify-steps">
              <el-step title="获取快照与证明" :status="snapshotStepStatusOne" :description="snapshotProofDesc" />
              <el-step title="生成 Leaf 哈希" :status="snapshotStepStatusTwo" :description="snapshotLeafShort || '等待校验'" />
              <el-step title="校验 Merkle Root" :status="snapshotStepStatusThree" :description="snapshotRootShort || '等待校验'" />
            </el-steps>
            <div class="verify-tree" :class="snapshotVerifyStateClass">
              <svg viewBox="0 0 320 160" aria-hidden="true">
                <line class="tree-line" x1="80" y1="120" x2="140" y2="80" />
                <line class="tree-line" x1="120" y1="120" x2="140" y2="80" />
                <line class="tree-line" x1="200" y1="120" x2="180" y2="80" />
                <line class="tree-line" x1="240" y1="120" x2="180" y2="80" />
                <line class="tree-line" x1="140" y1="80" x2="160" y2="40" />
                <line class="tree-line" x1="180" y1="80" x2="160" y2="40" />

                <circle class="tree-node" cx="80" cy="120" r="10" />
                <circle class="tree-node" cx="120" cy="120" r="10" />
                <circle class="tree-node" cx="200" cy="120" r="10" />
                <circle class="tree-node" cx="240" cy="120" r="10" />
                <circle class="tree-node" cx="140" cy="80" r="10" />
                <circle class="tree-node" cx="180" cy="80" r="10" />
                <circle class="tree-node tree-node--root" cx="160" cy="40" r="12" />

                <text class="tree-label" x="160" y="18">Merkle Root</text>
                <text class="tree-label" x="80" y="148">Leaves</text>
                <text class="tree-label" x="240" y="148">Proof</text>
              </svg>
            </div>
            <div class="verify-grid">
              <div class="verify-item">
                <span>我的算力</span>
                <strong>{{ snapshotAmountDisplay }}</strong>
              </div>
              <div class="verify-item">
                <span>Proof 节点</span>
                <strong>{{ snapshotProofLengthDisplay }}</strong>
              </div>
              <div class="verify-item">
                <span>快照人数</span>
                <strong>{{ snapshotTotalRecipientsDisplay }}</strong>
              </div>
              <div class="verify-item">
                <span>快照总算力</span>
                <strong>{{ snapshotTotalPowerDisplay }}</strong>
              </div>
              <div class="verify-item">
                <span>周池总额</span>
                <strong>{{ snapshotWeeklyPoolDisplay }}</strong>
              </div>
              <div class="verify-item">
                <span>生成时间</span>
                <strong>{{ snapshotCreatedAtDisplay }}</strong>
              </div>
              <div v-if="snapshotRoot" class="verify-item verify-item--mono">
                <span>Root</span>
                <strong>{{ snapshotRootShort }}</strong>
              </div>
              <div v-if="snapshotLeaf" class="verify-item verify-item--mono">
                <span>Leaf</span>
                <strong>{{ snapshotLeafShort }}</strong>
              </div>
            </div>
            <div class="verify-actions">
              <el-button type="info" :loading="verifyingSnapshot" @click="verifyRewardSnapshot">
                校验快照
              </el-button>
              <el-button type="warning" :loading="snapshotRebuilding" @click="forceRewardSnapshot">
                重算快照
              </el-button>
              <el-button type="primary" :loading="downloadingSnapshot" @click="downloadSnapshotEntries">
                下载快照
              </el-button>
            </div>
            <div class="verify-note">使用 Merkle Tree 校验地址、算力与 epoch 的哈希一致性。</div>
          </el-card>
          <el-card class="info-card tee-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>TEE 证明</span>
            </div>
            <div class="tee-status" :class="teeStatusClass">
              <i v-if="teeStatus === 'valid'" class="el-icon-circle-check"></i>
              <i v-else-if="teeStatus === 'invalid' || teeStatus === 'error'" class="el-icon-warning-outline"></i>
              <i v-else-if="teeLoading" class="el-icon-loading"></i>
              <i v-else class="el-icon-info"></i>
              <span>{{ teeStatusLabel }}</span>
            </div>
            <div class="tee-grid">
              <div class="tee-item">
                <span>Attestor</span>
                <strong>{{ teeAttestorShort || '--' }}</strong>
              </div>
              <div class="tee-item">
                <span>Epoch</span>
                <strong>{{ tee.epoch || '--' }}</strong>
              </div>
              <div class="tee-item">
                <span>生成时间</span>
                <strong>{{ teeIssuedAtDisplay }}</strong>
              </div>
              <div class="tee-item tee-item--mono">
                <span>Report Data</span>
                <strong>{{ teeReportShort }}</strong>
              </div>
              <div class="tee-item tee-item--mono">
                <span>Attestation Hash</span>
                <strong>{{ teeAttestationShort }}</strong>
              </div>
              <div class="tee-item tee-item--mono">
                <span>Signature</span>
                <strong>{{ teeSignatureShort }}</strong>
              </div>
              <div class="tee-item">
                <span>校验时间</span>
                <strong>{{ teeCheckedAtDisplay }}</strong>
              </div>
            </div>
            <div class="tee-actions">
              <el-button size="mini" type="primary" :loading="teeLoading" @click="fetchTeeAttestation">
                拉取证明
              </el-button>
            </div>
            <div class="tee-note">签名覆盖快照元数据与时间戳，用于演示“不可篡改”证明。</div>
          </el-card>
          <el-card class="info-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>映射说明</span>
            </div>
            <ul class="info-list">
              <li>每个 Slot 对应一种咖啡资产类别。</li>
              <li>首次映射自动铸造身份 Token，后续累加算力。</li>
              <li>算力累计用于后续权益分配（按总量统计）。</li>
              <li>演示模式可使用“模拟扫码”快速生成订单。</li>
              <li>需要连接钱包并切换至 {{ expectedNetworkName }}。</li>
            </ul>
          </el-card>

          <el-card class="info-card" shadow="hover">
            <div slot="header" class="card-header">
              <span>合约信息</span>
            </div>
          <div class="contract-row">
            <span>合约地址</span>
            <strong>{{ coffeeSftAddress || '未部署' }}</strong>
          </div>
          <div class="contract-row">
            <span>奖励合约</span>
            <strong>{{ rewardDistributorAddress || '未部署' }}</strong>
          </div>
            <div class="contract-row">
              <span>网络</span>
              <strong>{{ expectedNetworkName }}</strong>
            </div>
            <div class="contract-row">
              <span>API</span>
              <strong>{{ apiBaseUrl }}</strong>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script>
import { ethers } from 'ethers'
import axios from 'axios'
import { getCurrentConfig } from '@/config/contracts'
import espressoImage from '@/assets/coffee/espresso.png'
import americanoImage from '@/assets/coffee/americano.png'

const CONTRACT_ABI = [
  'function claimPower(uint256 slot, uint256 amount, uint256 nonce, bytes signature) external',
  'function tokenIdOf(address owner, uint256 slot) external view returns (uint256)',
  'function balanceOf(uint256 tokenId) external view returns (uint256)',
  'function totalPower(address owner) external view returns (uint256)',
  'function nonces(address owner) external view returns (uint256)'
]

const REWARD_ABI = [
  'function claim(uint256 epoch, uint256 amount, bytes32[] proof) external',
  'function claimed(uint256 epoch, address account) external view returns (bool)'
]

const SLOT_BADGES = {
  101: {
    name: '意式咖啡',
    subtitle: 'Espresso SBT',
    image: espressoImage
  },
  102: {
    name: '美式咖啡',
    subtitle: 'Americano SBT',
    image: americanoImage
  }
}

export default {
  name: 'CoffeeMapping',
  data() {
    return {
      connecting: false,
      claiming: false,
      claimingReward: false,
      walletAddress: '',
      networkName: '',
      contract: null,
      rewardContract: null,
      readProvider: null,
      readProviderUrl: '',
      txHash: '',
      rewardLoading: false,
      rewardStream: null,
      verifyingSnapshot: false,
      snapshotVerifyStatus: 'unknown',
      snapshotRoot: '',
      snapshotEpoch: '',
      snapshotLeaf: '',
      snapshotAmount: '',
      snapshotProofLength: 0,
      snapshotTotalRecipients: 0,
      snapshotWeeklyPool: '',
      snapshotTotalPower: '',
      snapshotCreatedAt: '',
      snapshotCheckedAt: '',
      snapshotRebuilding: false,
      downloadingSnapshot: false,
      teeLoading: false,
      teeStatus: 'unknown',
      teeCheckedAt: '',
      tee: {
        epoch: '',
        merkleRoot: '',
        weeklyPool: '',
        totalPower: '',
        createdAt: '',
        reportData: '',
        attestationHash: '',
        attestor: '',
        signature: '',
        issuedAt: '',
        nonce: '',
        mode: ''
      },
      form: {
        orderId: '',
        storeId: 'store-01',
        slot: 101,
        amount: 1.0,
        powerPreset: 'standard'
      },
      storeOptions: [
        { label: '门店 01', value: 'store-01' },
        { label: '门店 02', value: 'store-02' },
        { label: '门店 03', value: 'store-03' }
      ],
      powerOptions: [
        { label: '标准杯 · 1.0', value: 'standard', amount: 1.0 },
        { label: '升级杯 · 1.5', value: 'upgrade', amount: 1.5 },
        { label: '加倍杯 · 2.0', value: 'double', amount: 2.0 }
      ],
      stats: {
        totalPower: '0',
        slotPower: '0',
        tokenId: ''
      },
      reward: {
        weeklyPool: '0',
        userPower: '0',
        totalPower: '0',
        estimate: '0',
        epoch: '',
        nextSnapshotAt: ''
      },
      policyLoading: false,
      policySaving: false,
      policyFormulaOpen: false,
      policyMeta: {},
      policyForm: {
        aggregation_bucket: 'day',
        threshold: 2,
        alpha: 0.3,
        min_balance: 0.2,
        loyalty_step: 0.05,
        loyalty_cap: 0.3
      }
    }
  },
  computed: {
    contractConfig() {
      return getCurrentConfig()
    },
    coffeeSftAddress() {
      return this.contractConfig.coffeeSftAddress || ''
    },
    rewardDistributorAddress() {
      return this.contractConfig.coffeeRewardDistributorAddress || ''
    },
    expectedNetworkName() {
      return this.contractConfig.chainName || 'Polygon'
    },
    apiBaseUrl() {
      const envBase = process.env.VUE_APP_COFFEE_API_BASE_URL
      const base = envBase && envBase.trim() ? envBase : '/coffee-api'
      return base.replace(/\/+$/, '')
    },
    shortAddress() {
      if (!this.walletAddress) return ''
      return `${this.walletAddress.slice(0, 6)}...${this.walletAddress.slice(-4)}`
    },
    totalPowerDisplay() {
      return this.stats.totalPower || '0'
    },
    slotPowerDisplay() {
      return this.stats.slotPower || '0'
    },
    tokenIdDisplay() {
      return this.stats.tokenId || '--'
    },
    txHashShort() {
      if (!this.txHash) return ''
      return `${this.txHash.slice(0, 10)}...${this.txHash.slice(-6)}`
    },
    txExplorerUrl() {
      const explorer = (this.contractConfig.blockExplorerUrls || [])[0] || 'https://amoy.polygonscan.com'
      return `${explorer.replace(/\/$/, '')}/tx/${this.txHash}`
    },
    currentBadge() {
      return SLOT_BADGES[this.form.slot] || SLOT_BADGES[101]
    },
    slotOptions() {
      return [
        { label: '意式咖啡 (101)', value: 101 },
        { label: '美式咖啡 (102)', value: 102 }
      ]
    },
    weeklyPoolDisplay() {
      return this.formatWei(this.reward.weeklyPool, 2)
    },
    rewardEstimateDisplay() {
      return this.formatWei(this.reward.estimate, 4)
    },
    rewardUserPowerDisplay() {
      return this.formatPower(this.reward.userPower, 2)
    },
    rewardTotalPowerDisplay() {
      return this.formatPower(this.reward.totalPower, 2)
    },
    nextSnapshotDisplay() {
      if (!this.reward.nextSnapshotAt) return '--'
      const ts = Number(this.reward.nextSnapshotAt)
      if (!ts) return '--'
      return new Date(ts * 1000).toLocaleString()
    },
    powerPercentDisplay() {
      return this.formatPercent(this.reward.userPower, this.reward.totalPower)
    },
    rewardPercentDisplay() {
      return this.formatPercent(this.reward.estimate, this.reward.weeklyPool)
    },
    canClaimReward() {
      if (!this.rewardDistributorAddress || !this.walletAddress) return false
      try {
        return ethers.BigNumber.from(this.reward.estimate || '0').gt(0)
      } catch (error) {
        return false
      }
    },
    snapshotVerifyLabel() {
      if (this.verifyingSnapshot) return '校验中'
      if (this.snapshotVerifyStatus === 'valid') return '已验证'
      if (this.snapshotVerifyStatus === 'invalid') return '未通过'
      if (this.snapshotVerifyStatus === 'error') return '校验失败'
      return '未校验'
    },
    snapshotVerifyClass() {
      if (this.snapshotVerifyStatus === 'valid') return 'reward-verify reward-verify--ok'
      if (this.snapshotVerifyStatus === 'invalid' || this.snapshotVerifyStatus === 'error') {
        return 'reward-verify reward-verify--fail'
      }
      if (this.verifyingSnapshot) return 'reward-verify reward-verify--pending'
      return 'reward-verify'
    },
    snapshotRootShort() {
      if (!this.snapshotRoot) return ''
      return `${this.snapshotRoot.slice(0, 10)}...${this.snapshotRoot.slice(-8)}`
    },
    snapshotLeafShort() {
      if (!this.snapshotLeaf) return ''
      return `${this.snapshotLeaf.slice(0, 10)}...${this.snapshotLeaf.slice(-8)}`
    },
    snapshotAmountDisplay() {
      if (!this.snapshotAmount) return '--'
      return this.formatWei(this.snapshotAmount, 4)
    },
    snapshotTotalRecipientsDisplay() {
      if (!this.snapshotTotalRecipients) return '--'
      return String(this.snapshotTotalRecipients)
    },
    snapshotWeeklyPoolDisplay() {
      if (!this.snapshotWeeklyPool) return '--'
      return this.formatWei(this.snapshotWeeklyPool, 2)
    },
    snapshotTotalPowerDisplay() {
      if (!this.snapshotTotalPower) return '--'
      return this.formatPower(this.snapshotTotalPower, 2)
    },
    snapshotProofLengthDisplay() {
      if (!this.snapshotProofLength) return '--'
      return String(this.snapshotProofLength)
    },
    snapshotCheckedAtDisplay() {
      if (!this.snapshotCheckedAt) return '--'
      return new Date(this.snapshotCheckedAt).toLocaleString()
    },
    snapshotCreatedAtDisplay() {
      if (!this.snapshotCreatedAt) return '--'
      const ts = Number(this.snapshotCreatedAt)
      if (!Number.isFinite(ts) || ts <= 0) return '--'
      return new Date(ts * 1000).toLocaleString()
    },
    snapshotStepActive() {
      if (this.snapshotVerifyStatus === 'valid' || this.snapshotVerifyStatus === 'invalid' || this.snapshotVerifyStatus === 'error') {
        return 3
      }
      if (this.snapshotLeaf) return 2
      if (this.snapshotProofLength) return 1
      return 0
    },
    snapshotStepStatusOne() {
      if (this.snapshotProofLength) return 'success'
      if (this.verifyingSnapshot) return 'process'
      return 'wait'
    },
    snapshotStepStatusTwo() {
      if (this.snapshotLeaf) return 'success'
      if (this.snapshotProofLength) return 'process'
      return 'wait'
    },
    snapshotStepStatusThree() {
      if (this.snapshotVerifyStatus === 'valid') return 'success'
      if (this.snapshotVerifyStatus === 'invalid' || this.snapshotVerifyStatus === 'error') return 'error'
      if (this.verifyingSnapshot) return 'process'
      return 'wait'
    },
    snapshotProofDesc() {
      if (this.snapshotProofLength) return `Proof 节点 ${this.snapshotProofLength}`
      if (this.verifyingSnapshot) return '拉取中'
      return '等待校验'
    },
    snapshotProgress() {
      if (this.snapshotVerifyStatus === 'valid' || this.snapshotVerifyStatus === 'invalid' || this.snapshotVerifyStatus === 'error') {
        return 100
      }
      if (this.snapshotLeaf) return 70
      if (this.snapshotProofLength) return 40
      if (this.verifyingSnapshot) return 20
      return 0
    },
    snapshotProgressStatus() {
      if (this.snapshotVerifyStatus === 'valid') return 'success'
      if (this.snapshotVerifyStatus === 'invalid' || this.snapshotVerifyStatus === 'error') return 'exception'
      if (this.verifyingSnapshot) return 'warning'
      return ''
    },
    snapshotVerifyStateClass() {
      if (this.snapshotVerifyStatus === 'valid') return 'verify-state--ok'
      if (this.snapshotVerifyStatus === 'invalid' || this.snapshotVerifyStatus === 'error') {
        return 'verify-state--fail'
      }
      if (this.verifyingSnapshot) return 'verify-state--pending'
      return 'verify-state--idle'
    },
    teeStatusLabel() {
      if (this.teeLoading) return '生成中'
      if (this.teeStatus === 'valid') return '已认证'
      if (this.teeStatus === 'invalid') return '未通过'
      if (this.teeStatus === 'error') return '校验失败'
      return '未生成'
    },
    teeStatusClass() {
      if (this.teeStatus === 'valid') return 'tee-status tee-status--ok'
      if (this.teeStatus === 'invalid' || this.teeStatus === 'error') return 'tee-status tee-status--fail'
      if (this.teeLoading) return 'tee-status tee-status--pending'
      return 'tee-status'
    },
    teeAttestorShort() {
      if (!this.tee.attestor) return ''
      return `${this.tee.attestor.slice(0, 10)}...${this.tee.attestor.slice(-6)}`
    },
    teeReportShort() {
      if (!this.tee.reportData) return '--'
      return `${this.tee.reportData.slice(0, 12)}...${this.tee.reportData.slice(-8)}`
    },
    teeAttestationShort() {
      if (!this.tee.attestationHash) return '--'
      return `${this.tee.attestationHash.slice(0, 12)}...${this.tee.attestationHash.slice(-8)}`
    },
    teeSignatureShort() {
      if (!this.tee.signature) return '--'
      return `${this.tee.signature.slice(0, 12)}...${this.tee.signature.slice(-8)}`
    },
    teeIssuedAtDisplay() {
      if (!this.tee.issuedAt) return '--'
      const ts = Number(this.tee.issuedAt)
      if (!Number.isFinite(ts) || ts <= 0) return '--'
      return new Date(ts * 1000).toLocaleString()
    },
    teeCheckedAtDisplay() {
      if (!this.teeCheckedAt) return '--'
      return new Date(this.teeCheckedAt).toLocaleString()
    },
    policyUpdatedAtDisplay() {
      const items = Object.values(this.policyMeta || {})
      const latest = items.reduce((max, item) => Math.max(max, item.updated_at || 0), 0)
      if (!latest) return '--'
      return new Date(latest * 1000).toLocaleString()
    }
  },
  mounted() {
    this.fetchPolicyConfig()
  },
  watch: {
    walletAddress(newVal, oldVal) {
      if (newVal && newVal !== oldVal) {
        this.startRewardStream()
      } else if (!newVal) {
        this.stopRewardStream()
      }
    }
  },
  beforeDestroy() {
    this.stopRewardStream()
  },
  methods: {
    formatWei(value, digits) {
      if (!value) return '0'
      try {
        const formatted = ethers.utils.formatEther(value.toString())
        const [whole, frac] = formatted.split('.')
        if (!frac || digits === 0) return whole
        return `${whole}.${frac.slice(0, digits)}`
      } catch (error) {
        return '0'
      }
    },
    formatPower(value, digits) {
      if (!value) return '0'
      try {
        // sqrt(wei) -> 1e9 量级，按 9 位小数换算为可读算力
        const formatted = ethers.utils.formatUnits(value.toString(), 9)
        const [whole, frac] = formatted.split('.')
        if (!frac || digits === 0) return whole
        return `${whole}.${frac.slice(0, digits)}`
      } catch (error) {
        return '0'
      }
    },
    formatPercent(numerator, denominator) {
      try {
        const num = ethers.BigNumber.from(numerator || '0')
        const den = ethers.BigNumber.from(denominator || '0')
        if (den.isZero()) return 0
        const basisPoints = num.mul(10000).div(den).toNumber()
        return Math.min(100, Math.max(0, Math.round(basisPoints / 100)))
      } catch (error) {
        return 0
      }
    },
    parseNumber(value, fallback) {
      const parsed = Number(value)
      return Number.isFinite(parsed) ? parsed : fallback
    },
    applyPolicyConfig(items) {
      const form = { ...this.policyForm }
      const meta = {}
      for (const item of items || []) {
        const key = item.key
        meta[key] = {
          version: Number(item.version || 0),
          updated_at: Number(item.updated_at || 0)
        }
        if (key === 'aggregation_bucket') {
          form.aggregation_bucket = item.value || form.aggregation_bucket
        } else if (key === 'threshold') {
          form.threshold = this.parseNumber(item.value, form.threshold)
        } else if (key === 'alpha') {
          form.alpha = this.parseNumber(item.value, form.alpha)
        } else if (key === 'min_balance') {
          form.min_balance = this.parseNumber(item.value, form.min_balance)
        } else if (key === 'loyalty_step') {
          form.loyalty_step = this.parseNumber(item.value, form.loyalty_step)
        } else if (key === 'loyalty_cap') {
          form.loyalty_cap = this.parseNumber(item.value, form.loyalty_cap)
        }
      }
      this.policyForm = form
      this.policyMeta = meta
    },
    async fetchPolicyConfig() {
      try {
        this.policyLoading = true
        const response = await axios.get(`${this.apiBaseUrl}/v1/policy-config`)
        const items = response?.data?.items || []
        this.applyPolicyConfig(items)
      } catch (error) {
        const apiError = error?.response?.data?.error
        this.$message.error(`读取配置失败: ${apiError || error.message || error}`)
      } finally {
        this.policyLoading = false
      }
    },
    async savePolicyConfig() {
      try {
        this.policySaving = true
        const items = [
          { key: 'aggregation_bucket', value: String(this.policyForm.aggregation_bucket || 'day') },
          { key: 'threshold', value: String(this.policyForm.threshold) },
          { key: 'alpha', value: String(this.policyForm.alpha) },
          { key: 'min_balance', value: String(this.policyForm.min_balance) },
          { key: 'loyalty_step', value: String(this.policyForm.loyalty_step) },
          { key: 'loyalty_cap', value: String(this.policyForm.loyalty_cap) }
        ]
        const response = await axios.post(`${this.apiBaseUrl}/v1/policy-config`, { items })
        const data = response?.data?.items || []
        this.applyPolicyConfig(data)
        this.$message.success('配置已更新')
      } catch (error) {
        const apiError = error?.response?.data?.error
        this.$message.error(`保存失败: ${apiError || error.message || error}`)
      } finally {
        this.policySaving = false
      }
    },

    togglePolicyFormula() {
      this.policyFormulaOpen = !this.policyFormulaOpen
    },

    buildDemoOrderId(storeId) {
      const random = Math.floor(Math.random() * 1000)
      const normalized = storeId || 'store-01'
      return `demo-${normalized}-${Date.now()}-${random}`
    },

    applyPowerPreset(value) {
      const preset = this.powerOptions.find(option => option.value === value)
      if (preset) {
        this.form.amount = preset.amount
      }
    },

    async connectWallet() {
      if (!window.ethereum) {
        this.$message.error('未检测到钱包，请安装 MetaMask')
        return
      }
      try {
        this.connecting = true
        const accounts = await window.ethereum.request({ method: 'eth_requestAccounts' })
        this.walletAddress = accounts[0] || ''
        await this.ensureNetwork()
        await this.initContract()
        await this.initRewardContract()
        await Promise.all([
          this.loadStats(),
          this.fetchRewardEstimate()
        ])
        this.$message.success('钱包连接成功')
      } catch (error) {
        this.$message.error(`连接失败: ${error.message || error}`)
      } finally {
        this.connecting = false
      }
    },

    async ensureNetwork() {
      const { chainId, chainName, rpcUrls, blockExplorerUrls, nativeCurrency } = this.contractConfig
      const targetChainId = `0x${chainId.toString(16)}`
      const currentChainId = await window.ethereum.request({ method: 'eth_chainId' })
      if (currentChainId === targetChainId) {
        this.networkName = chainName
        return
      }
      try {
        await window.ethereum.request({
          method: 'wallet_switchEthereumChain',
          params: [{ chainId: targetChainId }]
        })
        this.networkName = chainName
      } catch (error) {
        if (error.code === 4902) {
          await window.ethereum.request({
            method: 'wallet_addEthereumChain',
            params: [{
              chainId: targetChainId,
              chainName,
              rpcUrls,
              blockExplorerUrls,
              nativeCurrency
            }]
          })
          this.networkName = chainName
        } else {
          throw error
        }
      }
    },

    async initContract() {
      if (!this.coffeeSftAddress) {
        throw new Error('CoffeeSFT 合约地址未配置')
      }
      const provider = new ethers.providers.Web3Provider(window.ethereum)
      const signer = provider.getSigner()
      this.contract = new ethers.Contract(this.coffeeSftAddress, CONTRACT_ABI, signer)
    },

    async initRewardContract() {
      if (!this.rewardDistributorAddress) {
        this.rewardContract = null
        return
      }
      const provider = new ethers.providers.Web3Provider(window.ethereum)
      const signer = provider.getSigner()
      this.rewardContract = new ethers.Contract(this.rewardDistributorAddress, REWARD_ABI, signer)
    },

    getReadProvider() {
      const urls = (this.contractConfig && this.contractConfig.rpcUrls) || []
      const rpcUrl = urls[0]
      if (!rpcUrl) return null
      if (this.readProvider && this.readProviderUrl === rpcUrl) {
        return this.readProvider
      }
      this.readProviderUrl = rpcUrl
      this.readProvider = new ethers.providers.JsonRpcProvider(rpcUrl)
      return this.readProvider
    },

    async loadStats() {
      if (!this.walletAddress) {
        return
      }
      try {
        const provider = this.getReadProvider()
        if (!provider) {
          this.$message.error('读取算力失败: 未配置 RPC')
          return
        }
        if (!this.coffeeSftAddress) {
          this.$message.error('读取算力失败: 合约地址未配置')
          return
        }
        const readContract = new ethers.Contract(this.coffeeSftAddress, CONTRACT_ABI, provider)
        const total = await readContract.totalPower(this.walletAddress)
        this.stats.totalPower = ethers.utils.formatEther(total)

        const tokenId = await readContract.tokenIdOf(this.walletAddress, this.form.slot)
        if (tokenId && !tokenId.isZero()) {
          const slotValue = await readContract.balanceOf(tokenId)
          this.stats.slotPower = ethers.utils.formatEther(slotValue)
          this.stats.tokenId = tokenId.toString()
        } else {
          this.stats.slotPower = '0'
          this.stats.tokenId = ''
        }
      } catch (error) {
        this.$message.error(`读取算力失败: ${error.response?.data?.error || error.message || error}`)
      }
    },

    async claimPower() {
      if (!this.walletAddress || !this.contract) {
        this.$message.warning('请先连接钱包')
        return
      }
      if (!this.form.orderId) {
        this.$message.error('请填写订单 ID')
        return
      }
      const orderId = this.form.orderId
      let reserved = false
      try {
        this.claiming = true
        let chainNonce = null
        try {
          chainNonce = await this.contract.nonces(this.walletAddress)
        } catch (error) {
          console.warn('读取链上 nonce 失败', error)
        }
        const payload = {
          user_address: this.walletAddress,
          order_id: this.form.orderId,
          slot: this.form.slot,
          amount: this.form.amount.toString()
        }
        if (chainNonce !== null) {
          payload.nonce = chainNonce.toString()
        }
        const response = await axios.post(`${this.apiBaseUrl}/v1/claim`, payload)
        const data = response.data
        if (!data || !data.signature) {
          throw new Error(data?.error || '签名获取失败')
        }
        reserved = true

        const tx = await this.contract.claimPower(
          data.slot,
          data.amount,
          data.nonce,
          data.signature
        )
        this.$message.info('交易已提交，请等待确认')
        const receipt = await tx.wait()
        this.txHash = receipt.transactionHash
        let confirmOk = true
        try {
          await axios.post(`${this.apiBaseUrl}/v1/claim-confirm`, {
            user_address: this.walletAddress,
            order_id: orderId,
            tx_hash: receipt.transactionHash
          })
        } catch (confirmError) {
          confirmOk = false
          const apiError = confirmError?.response?.data?.error
          this.$message.warning(`链上成功，但后端确认失败: ${apiError || confirmError.message || confirmError}`)
        }
        await this.loadStats()
        await this.fetchRewardEstimate()
        this.$message.success('映射成功')
        if (confirmOk) {
          this.form.orderId = ''
        }
      } catch (error) {
        const apiError = error?.response?.data?.error
        const message = apiError || error?.message || error
        const lowerMessage = String(message || '').toLowerCase()
        if (reserved && this.walletAddress && orderId) {
          try {
            await axios.post(`${this.apiBaseUrl}/v1/claim-cancel`, {
              user_address: this.walletAddress,
              order_id: orderId
            })
          } catch (cancelError) {
            console.warn('取消预留失败', cancelError)
          }
        }
        if (error?.code === 4001 || error?.code === 'ACTION_REJECTED' || lowerMessage.includes('user rejected')) {
          this.$message.info('已取消交易')
          return
        }
        this.$message.error(`映射失败: ${message}`)
      } finally {
        this.claiming = false
      }
    },

    async simulateScan() {
      if (!SLOT_BADGES[this.form.slot]) {
        this.form.slot = 101
      }
      if (!this.form.storeId) {
        this.form.storeId = 'store-01'
      }
      if (!this.form.powerPreset) {
        this.form.powerPreset = 'standard'
      }
      this.applyPowerPreset(this.form.powerPreset)
      if (!this.form.amount || this.form.amount <= 0) {
        this.form.amount = 1.0
      }
      this.form.orderId = this.buildDemoOrderId(this.form.storeId)
      this.$message.info('已生成模拟订单，请点击“领取算力”完成上链')
    },

    startRewardStream() {
      if (!this.walletAddress) return
      this.stopRewardStream()
      const url = `${this.apiBaseUrl}/v1/reward-stream?user_address=${this.walletAddress}`
      const stream = new EventSource(url)
      stream.onmessage = event => {
        try {
          const data = JSON.parse(event.data || '{}')
          const nextEpoch = data.epoch || ''
          const nextEpochKey = String(nextEpoch || '')
          this.reward = {
            weeklyPool: data.weekly_pool || '0',
            userPower: data.user_power || '0',
            totalPower: data.total_power || '0',
            estimate: data.estimate || '0',
            epoch: nextEpoch,
            nextSnapshotAt: data.next_snapshot_at || ''
          }
          if (nextEpochKey && !this.snapshotEpoch) {
            this.snapshotEpoch = nextEpochKey
          }
          this.rewardLoading = false
        } catch (error) {
          console.warn('奖励推送解析失败', error)
        }
      }
      stream.onerror = () => {
        this.rewardLoading = false
      }
      this.rewardStream = stream
    },

    stopRewardStream() {
      if (this.rewardStream) {
        this.rewardStream.close()
        this.rewardStream = null
      }
    },

    resetSnapshotState(nextEpochKey = '') {
      if (nextEpochKey) {
        this.snapshotEpoch = nextEpochKey
      }
      this.snapshotVerifyStatus = 'unknown'
      this.snapshotRoot = ''
      this.snapshotLeaf = ''
      this.snapshotAmount = ''
      this.snapshotProofLength = 0
      this.snapshotTotalRecipients = 0
      this.snapshotWeeklyPool = ''
      this.snapshotTotalPower = ''
      this.snapshotCreatedAt = ''
      this.snapshotCheckedAt = ''
      this.resetTeeState()
    },

    normalizeHex(value) {
      return String(value || '')
        .toLowerCase()
        .replace(/^0x/, '')
        .padStart(64, '0')
    },

    leafHash(address, amount, epoch) {
      const addrBytes = ethers.utils.arrayify(ethers.utils.getAddress(address))
      const amountHex = ethers.utils.hexZeroPad(ethers.BigNumber.from(amount).toHexString(), 32)
      const epochHex = ethers.utils.hexZeroPad(ethers.BigNumber.from(epoch).toHexString(), 32)
      const data = ethers.utils.concat([
        addrBytes,
        ethers.utils.arrayify(amountHex),
        ethers.utils.arrayify(epochHex)
      ])
      return ethers.utils.keccak256(data)
    },

    hashPair(left, right) {
      const leftHex = this.normalizeHex(left)
      const rightHex = this.normalizeHex(right)
      const ordered = leftHex <= rightHex ? [leftHex, rightHex] : [rightHex, leftHex]
      const data = ethers.utils.concat([
        ethers.utils.arrayify(`0x${ordered[0]}`),
        ethers.utils.arrayify(`0x${ordered[1]}`)
      ])
      return ethers.utils.keccak256(data)
    },

    verifyMerkleProof(address, amount, epoch, proof, root) {
      let hash = this.leafHash(address, amount, epoch)
      for (const sibling of proof || []) {
        hash = this.hashPair(hash, sibling)
      }
      return this.normalizeHex(hash) === this.normalizeHex(root)
    },

    resetTeeState() {
      this.teeStatus = 'unknown'
      this.teeCheckedAt = ''
      this.tee = {
        epoch: '',
        merkleRoot: '',
        weeklyPool: '',
        totalPower: '',
        createdAt: '',
        reportData: '',
        attestationHash: '',
        attestor: '',
        signature: '',
        issuedAt: '',
        nonce: '',
        mode: ''
      }
    },

    verifyTeeAttestation() {
      const data = this.tee
      if (!data.attestor || !data.signature || !data.reportData || !data.attestationHash) {
        this.teeStatus = 'invalid'
        this.teeCheckedAt = new Date().toISOString()
        return false
      }
      const snapshotPayload = `epoch:${data.epoch}|root:${data.merkleRoot}|weekly_pool:${data.weeklyPool}|total_power:${data.totalPower}|created_at:${data.createdAt}`
      const reportData = ethers.utils.keccak256(ethers.utils.toUtf8Bytes(snapshotPayload))
      const reportMatch = this.normalizeHex(reportData) === this.normalizeHex(data.reportData)
      const attestationPayload = `COFFEE_TEE_ATTEST|${data.reportData}|${data.issuedAt}|${data.nonce}`
      const attestationHash = ethers.utils.keccak256(ethers.utils.toUtf8Bytes(attestationPayload))
      const attestationMatch = this.normalizeHex(attestationHash) === this.normalizeHex(data.attestationHash)
      let signerMatch = false
      try {
        const recovered = ethers.utils.recoverAddress(attestationHash, data.signature)
        signerMatch = this.normalizeHex(recovered) === this.normalizeHex(data.attestor)
      } catch (error) {
        signerMatch = false
      }
      const valid = reportMatch && attestationMatch && signerMatch
      this.teeStatus = valid ? 'valid' : 'invalid'
      this.teeCheckedAt = new Date().toISOString()
      return valid
    },

    async fetchTeeAttestation() {
      try {
        this.teeLoading = true
        const epoch = this.snapshotEpoch || this.reward.epoch || ''
        const params = epoch ? { epoch } : {}
        const response = await axios.get(`${this.apiBaseUrl}/v1/tee-attestation`, { params })
        const data = response.data || {}
        this.tee = {
          epoch: String(data.epoch || ''),
          merkleRoot: data.merkle_root || '',
          weeklyPool: data.weekly_pool || '',
          totalPower: data.total_power || '',
          createdAt: String(data.created_at || ''),
          reportData: data.report_data || '',
          attestationHash: data.attestation_hash || '',
          attestor: data.attestor || '',
          signature: data.signature || '',
          issuedAt: String(data.issued_at || ''),
          nonce: String(data.nonce || ''),
          mode: data.mode || ''
        }
        const valid = this.verifyTeeAttestation()
        if (valid) {
          this.$message.success('TEE 证明校验通过')
        } else {
          this.$message.warning('TEE 证明未通过')
        }
      } catch (error) {
        const apiError = error?.response?.data?.error
        this.teeStatus = 'error'
        this.teeCheckedAt = new Date().toISOString()
        this.$message.error(`TEE 证明获取失败: ${apiError || error.message || error}`)
      } finally {
        this.teeLoading = false
      }
    },

    async verifyRewardSnapshot() {
      if (!this.walletAddress) {
        this.$message.warning('请先连接钱包')
        return
      }
      try {
        this.verifyingSnapshot = true
        this.snapshotVerifyStatus = 'pending'
        const proofResponse = await axios.post(`${this.apiBaseUrl}/v1/reward-proof`, {
          user_address: this.walletAddress
        })
        const proofData = proofResponse.data || {}
        const epoch = proofData.epoch
        const proofRecipient = proofData.recipient || this.walletAddress
        const amount = proofData.amount || '0'
        const proof = proofData.proof || []
        const snapshotResponse = await axios.post(`${this.apiBaseUrl}/v1/reward-snapshot`, {
          epoch
        })
        const snapshotData = snapshotResponse.data || {}
        const root = snapshotData.merkle_root || proofData.merkle_root || ''
        const leaf = this.leafHash(proofRecipient, amount, epoch)
        const valid = root && this.verifyMerkleProof(proofRecipient, amount, epoch, proof, root)
        this.snapshotEpoch = String(epoch || snapshotData.epoch || '')
        this.snapshotRoot = root
        this.snapshotLeaf = leaf
        this.snapshotAmount = amount
        this.snapshotProofLength = proof.length
        this.snapshotTotalRecipients = snapshotData.total_recipients || 0
        this.snapshotWeeklyPool = snapshotData.weekly_pool || ''
        this.snapshotTotalPower = snapshotData.total_power || ''
        this.snapshotCreatedAt = snapshotData.created_at || ''
        this.snapshotCheckedAt = new Date().toISOString()
        this.snapshotVerifyStatus = valid ? 'valid' : 'invalid'
        if (valid) {
          this.$message.success('快照校验通过')
        } else {
          this.$message.error('快照校验失败')
        }
      } catch (error) {
        const apiError = error?.response?.data?.error
        this.snapshotVerifyStatus = 'error'
        this.snapshotCheckedAt = new Date().toISOString()
        this.$message.error(`快照校验失败: ${apiError || error.message || error}`)
      } finally {
        this.verifyingSnapshot = false
      }
    },

    async forceRewardSnapshot() {
      try {
        this.snapshotRebuilding = true
        const epoch = this.snapshotEpoch || this.reward.epoch || ''
        const payload = { force: true }
        if (epoch) {
          payload.epoch = Number(epoch)
        }
        await axios.post(`${this.apiBaseUrl}/v1/reward-snapshot`, payload)
        this.$message.success('快照已重算')
        await this.verifyRewardSnapshot()
      } catch (error) {
        const apiError = error?.response?.data?.error
        this.$message.error(`快照重算失败: ${apiError || error.message || error}`)
      } finally {
        this.snapshotRebuilding = false
      }
    },

    async downloadSnapshotEntries() {
      try {
        this.downloadingSnapshot = true
        const epoch = this.snapshotEpoch || this.reward.epoch || ''
        const params = epoch ? { epoch } : {}
        const response = await axios.get(`${this.apiBaseUrl}/v1/reward-entries`, { params })
        const data = response.data || {}
        const entries = data.entries || []
        if (!entries.length) {
          this.$message.warning('暂无快照明细')
          return
        }
        const header = ['address', 'amount_wei'].join(',')
        const rows = entries.map(item => `${item.address},${item.amount}`)
        const csv = [header, ...rows].join('\n')
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' })
        const url = URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = `reward-snapshot-${data.epoch || epoch || 'latest'}.csv`
        link.click()
        URL.revokeObjectURL(url)
      } catch (error) {
        const apiError = error?.response?.data?.error
        this.$message.error(`下载失败: ${apiError || error.message || error}`)
      } finally {
        this.downloadingSnapshot = false
      }
    },

    async fetchRewardEstimate() {
      if (!this.walletAddress) {
        return
      }
      try {
        this.rewardLoading = true
        const response = await axios.post(`${this.apiBaseUrl}/v1/reward-estimate`, {
          user_address: this.walletAddress
        })
        const data = response.data || {}
        const nextEpoch = data.epoch || ''
        const nextEpochKey = String(nextEpoch || '')
        this.reward = {
          weeklyPool: data.weekly_pool || '0',
          userPower: data.user_power || '0',
          totalPower: data.total_power || '0',
          estimate: data.estimate || '0',
          epoch: nextEpoch,
          nextSnapshotAt: data.next_snapshot_at || ''
        }
        if (nextEpochKey && !this.snapshotEpoch) {
          this.snapshotEpoch = nextEpochKey
        }
      } catch (error) {
        const apiError = error?.response?.data?.error
        this.$message.error(`奖励估算失败: ${apiError || error.message || error}`)
      } finally {
        this.rewardLoading = false
      }
    },

    async claimReward() {
      if (!this.rewardContract || !this.walletAddress) {
        this.$message.warning('请先连接钱包')
        return
      }
      if (!this.canClaimReward) {
        this.$message.warning('当前暂无可领取奖励')
        return
      }
      try {
        this.claimingReward = true
        const response = await axios.post(`${this.apiBaseUrl}/v1/reward-proof`, {
          user_address: this.walletAddress
        })
        const data = response.data || {}
        if (!data.amount || !data.proof || !data.epoch) {
          throw new Error('领取数据不完整')
        }
        if (ethers.BigNumber.from(data.amount).isZero()) {
          this.$message.warning('当前奖励为 0')
          return
        }
        const tx = await this.rewardContract.claim(data.epoch, data.amount, data.proof)
        this.$message.info('奖励领取已提交，请等待确认')
        await tx.wait()
        this.$message.success('奖励领取成功')
        await this.fetchRewardEstimate()
      } catch (error) {
        const apiError = error?.response?.data?.error
        this.$message.error(`领取失败: ${apiError || error.message || error}`)
      } finally {
        this.claimingReward = false
      }
    }
  }
}
</script>

<style scoped>
.coffee-mapping-page {
  background: #edf1f7;
  min-height: 100vh;
  padding: 32px 0 60px;
}

.coffee-mapping-container {
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

.coffee-hero {
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
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.hero-chip {
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.35);
  font-size: 12px;
  color: #fff;
  letter-spacing: 0.02em;
}

.hero-chip--ghost {
  background: rgba(255, 255, 255, 0.22);
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
  word-break: break-all;
}

.metric-hint {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.75);
}

.main-grid {
  margin-bottom: 24px;
}

.mapping-card .hint {
  font-size: 12px;
  color: #999;
}

.tx-info {
  margin-top: 12px;
  font-size: 12px;
  color: #666;
  display: flex;
  gap: 8px;
}

.info-card {
  margin-bottom: 16px;
}

.badge-card {
  overflow: hidden;
}

.badge-preview {
  display: flex;
  align-items: center;
  gap: 16px;
}

.badge-image {
  width: 140px;
  height: 140px;
  border-radius: 18px;
  overflow: hidden;
  background: #f3f4f6;
  flex-shrink: 0;
  box-shadow: 0 16px 28px rgba(15, 23, 42, 0.12);
}

.badge-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.badge-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.badge-name {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.badge-subtitle {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #7a7a7a;
}

.badge-meta {
  font-size: 13px;
  color: #4b5563;
}

.reward-card .hint {
  font-size: 12px;
  color: #9aa0a6;
}

.reward-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #4b5563;
  margin-bottom: 8px;
}

.reward-row strong {
  color: #1f2937;
}

.reward-row--mono strong {
  font-family: "Courier New", Courier, monospace;
}

.reward-verify {
  font-weight: 600;
}

.reward-verify--ok {
  color: #15803d;
}

.reward-verify--fail {
  color: #dc2626;
}

.reward-verify--pending {
  color: #d97706;
}

.verify-card .hint {
  font-size: 12px;
  color: #9aa0a6;
}

.verify-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.verify-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: #f5f7fb;
  font-size: 12px;
}

.verify-meta {
  display: grid;
  gap: 4px;
}

.verify-meta-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #4b5563;
  gap: 16px;
}

.verify-steps {
  margin-top: 12px;
}

.verify-progress {
  margin-top: 12px;
}

.verify-grid {
  margin-top: 12px;
  display: grid;
  gap: 8px;
}

.verify-item {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #4b5563;
}

.verify-item--mono strong {
  font-family: "Courier New", Courier, monospace;
}

.verify-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.verify-tree {
  margin-top: 12px;
  background: linear-gradient(180deg, #f8fafc, #fff);
  border-radius: 12px;
  padding: 12px;
}

.verify-tree svg {
  width: 100%;
  height: 140px;
  display: block;
}

.verify-tree .tree-line {
  stroke: #d1d5db;
  stroke-width: 2;
}

.verify-tree .tree-node {
  fill: #e5e7eb;
  stroke: #9ca3af;
  stroke-width: 2;
}

.verify-tree .tree-node--root {
  fill: #cbd5f5;
}

.verify-tree .tree-label {
  fill: #6b7280;
  font-size: 10px;
  text-anchor: middle;
}

.verify-tree.verify-state--ok .tree-line,
.verify-tree.verify-state--ok .tree-node {
  stroke: #16a34a;
}

.verify-tree.verify-state--ok .tree-node {
  fill: #bbf7d0;
}

.verify-tree.verify-state--fail .tree-line,
.verify-tree.verify-state--fail .tree-node {
  stroke: #dc2626;
}

.verify-tree.verify-state--fail .tree-node {
  fill: #fecaca;
}

.verify-tree.verify-state--pending .tree-line,
.verify-tree.verify-state--pending .tree-node {
  stroke: #f59e0b;
}

.verify-tree.verify-state--pending .tree-node {
  fill: #fde68a;
}

.verify-note {
  font-size: 12px;
  color: #9aa0a6;
  margin-top: 8px;
}

.tee-card .hint {
  font-size: 12px;
  color: #9aa0a6;
}

.tee-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: #f5f7fb;
  font-size: 12px;
}

.tee-status--ok {
  color: #15803d;
  background: rgba(22, 163, 74, 0.12);
}

.tee-status--fail {
  color: #dc2626;
  background: rgba(220, 38, 38, 0.12);
}

.tee-status--pending {
  color: #d97706;
  background: rgba(217, 119, 6, 0.12);
}

.tee-grid {
  margin-top: 12px;
  display: grid;
  gap: 8px;
}

.tee-item {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #4b5563;
}

.tee-item--mono strong {
  font-family: "Courier New", Courier, monospace;
}

.tee-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.tee-note {
  font-size: 12px;
  color: #9aa0a6;
  margin-top: 8px;
}

.reward-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.reward-note {
  font-size: 12px;
  color: #9aa0a6;
  margin-top: 8px;
}

.reward-visual {
  margin-top: 12px;
  display: grid;
  gap: 12px;
}

.reward-metric {
  display: grid;
  gap: 6px;
}

.reward-metric-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #4b5563;
}

.reward-bar {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: #eef2f7;
  overflow: hidden;
}

.reward-bar--accent {
  background: #fef3c7;
}

.reward-bar-fill {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #2563eb, #38bdf8);
}

.info-list {
  padding-left: 18px;
  color: #666;
  line-height: 1.8;
}

.contract-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  margin-bottom: 8px;
  color: #666;
  word-break: break-all;
}

.policy-card .el-form-item {
  margin-bottom: 0;
}

.policy-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px 16px;
}

.policy-form .el-input-number,
.policy-form .el-select {
  width: 100%;
}

.policy-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.policy-note {
  margin-top: 8px;
  font-size: 12px;
  color: #9aa0a6;
}

.policy-formula {
  margin-top: 12px;
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
}

.policy-formula-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #111827;
}

.policy-formula-list {
  margin: 8px 0 0;
  padding-left: 18px;
  font-size: 12px;
  color: #4b5563;
  line-height: 1.6;
}

.policy-formula-detail {
  margin-top: 8px;
  font-size: 12px;
  color: #6b7280;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .surface-card {
    padding: 24px;
  }

  .hero-left h1 {
    font-size: 28px;
  }

  .badge-preview {
    flex-direction: column;
    align-items: flex-start;
  }

  .badge-image {
    width: 100%;
    height: auto;
    aspect-ratio: 1 / 1;
  }
}
</style>
