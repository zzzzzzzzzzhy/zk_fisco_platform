// Vuex Store for DAO Governance
import request from '@/api/request'
import { ethers } from 'ethers'
import { getCurrentConfig } from '@/config/contracts'
import governorABI from '@/contracts/abis/RewardGovernor.json'
import weeTokenABI from '@/contracts/abis/WEEToken.json'

const contractConfig = getCurrentConfig()

const state = {
  proposals: [],
  currentProposal: null,
  userVotingPower: '0',
  userDelegated: false,
  // 一人一票治理合约地址（法定人数 = 2）
  governorAddress: contractConfig.rewardGovernorAddress || '',
  weeTokenAddress: contractConfig.mtkTokenAddress || '',
  // 最新部署的 ForumTokenExtension 地址（与后端 application.yml / blockchain 部署记录保持一致）
  forumExtensionAddress: contractConfig.forumTokenAddress || '',
  loading: false
}

// 将合约返回的英文 reason 映射为更友好的中文文案
function mapEligibilityReason (reason, eligible) {
  if (!reason) {
    return eligible ? '满足所有条件，可以参与投票' : '暂时不满足投票条件'
  }

  switch (reason) {
    case 'Eligible':
      return '满足所有条件，可以参与投票'
    case 'Whitelisted':
      return '已加入白名单，可以直接参与投票'
    case 'Account is blacklisted':
      return '地址处于黑名单中，暂时无法参与投票'
    case 'Insufficient token balance':
      return '在提案快照区块时，持有的有效 WEE 数量低于最低投票门槛'
    case 'Account not registered':
      return '账户尚未在系统中记录首次活跃区块，请先通过正常使用平台产生链上记录'
    case 'Account too young':
      return '账户创建时间距离提案快照区块不足要求的区块间隔'
    default:
      // 已经是中文或未知情况时，直接原样展示
      return reason
  }
}

const mutations = {
  SET_PROPOSALS(state, proposals) {
    state.proposals = proposals
  },
  SET_CURRENT_PROPOSAL(state, proposal) {
    state.currentProposal = proposal
  },
  SET_VOTING_POWER(state, power) {
    state.userVotingPower = power
  },
  SET_DELEGATED(state, delegated) {
    state.userDelegated = delegated
  },
  SET_LOADING(state, loading) {
    state.loading = loading
  }
}

const actions = {
  /**
   * 获取提案列表
   */
  async fetchProposals({ commit }, { page = 1, size = 10, status = '' }) {
    commit('SET_LOADING', true)
    try {
      const response = await request.get('/governance/proposals', {
        params: { page, size, status }
      })
      // 后端返回的是分页对象 Page<GovernanceProposal>
      const pageData = response.data || {}
      commit('SET_PROPOSALS', pageData.records || [])
      return pageData
    } catch (error) {
      console.error('获取提案列表失败:', error)
      throw error
    } finally {
      commit('SET_LOADING', false)
    }
  },

  /**
   * 获取提案详情
   */
  async fetchProposal({ commit }, proposalId) {
    commit('SET_LOADING', true)
    try {
      const response = await request.get(`/governance/proposals/${proposalId}`)
      commit('SET_CURRENT_PROPOSAL', response.data)
      return response.data
    } catch (error) {
      console.error('获取提案详情失败:', error)
      throw error
    } finally {
      commit('SET_LOADING', false)
    }
  },

  /**
   * 获取用户投票权
   */
  async fetchVotingPower({ commit, state }, userAddress) {
    try {
      const provider = new ethers.providers.Web3Provider(window.ethereum)
      const weeToken = new ethers.Contract(state.weeTokenAddress, weeTokenABI, provider)
      
      const votes = await weeToken.getVotes(userAddress)
      const votesFormatted = ethers.utils.formatEther(votes)
      
      commit('SET_VOTING_POWER', votesFormatted)
      commit('SET_DELEGATED', parseFloat(votesFormatted) > 0)
      
      return votesFormatted
    } catch (error) {
      console.error('获取投票权失败:', error)
      throw error
    }
  },

  /**
   * 委托投票权给自己
   */
  async delegateToSelf({ dispatch }, userAddress) {
    try {
      const provider = new ethers.providers.Web3Provider(window.ethereum)
      const signer = provider.getSigner()
      const weeToken = new ethers.Contract(state.weeTokenAddress, weeTokenABI, signer)
      
      const tx = await weeToken.delegate(userAddress)
      await tx.wait()
      
      // 刷新投票权
      await dispatch('fetchVotingPower', userAddress)
      
      return tx
    } catch (error) {
      console.error('委托失败:', error)
      throw error
    }
  },

  /**
   * 创建提案
   */
  async createProposal({ state }, { targets, values, calldatas, description, metadata }) {
    try {
      const provider = new ethers.providers.Web3Provider(window.ethereum)
      const signer = provider.getSigner()
      const governor = new ethers.Contract(state.governorAddress, governorABI, signer)
      
      // 发送链上提案
      const tx = await governor.propose(targets, values, calldatas, description)
      const receipt = await tx.wait()
      
      // 从事件中获取 proposalId
      const event = receipt.events.find(e => e.event === 'ProposalCreated')
      const proposalId = event.args.proposalId.toString()
      
      // 保存到后端
      await request.post('/governance/proposals', {
        proposalId,
        proposer: await signer.getAddress(),
        title: metadata.title,
        description: metadata.description,
        targets: JSON.stringify(targets),
        values: JSON.stringify(values.map(v => v.toString())),
        calldatas: JSON.stringify(calldatas),
        status: 'Pending',
        createTxHash: tx.hash
      })
      
      return { proposalId, txHash: tx.hash }
    } catch (error) {
      console.error('创建提案失败:', error)
      throw error
    }
  },

  /**
   * 投票
   */
  async castVote({ state }, { proposalId, support, reason = '' }) {
    try {
      const provider = new ethers.providers.Web3Provider(window.ethereum)
      const signer = provider.getSigner()
      const governor = new ethers.Contract(state.governorAddress, governorABI, signer)
      
      let tx
      if (reason) {
        tx = await governor.castVoteWithReason(proposalId, support, reason)
      } else {
        tx = await governor.castVote(proposalId, support)
      }
      
      const receipt = await tx.wait()
      
      // 获取投票权重
      const event = receipt.events.find(e => e.event === 'VoteCast')
      const weight = event ? ethers.utils.formatEther(event.args.weight) : '0'
      
      // 保存到后端
      await request.post('/governance/votes', {
        proposalId: proposalId.toString(),
        voter: await signer.getAddress(),
        support,
        weight,
        reason,
        txHash: tx.hash,
        blockNumber: receipt.blockNumber
      })
      
      return tx
    } catch (error) {
      console.error('投票失败:', error)
      throw error
    }
  },

  /**
   * 执行提案
   */
  async executeProposal({ state }, { targets, values, calldatas, descriptionHash }) {
    try {
      const provider = new ethers.providers.Web3Provider(window.ethereum)
      const signer = provider.getSigner()
      const governor = new ethers.Contract(state.governorAddress, governorABI, signer)
      
      const tx = await governor.execute(targets, values, calldatas, descriptionHash)
      await tx.wait()
      
      return tx
    } catch (error) {
      console.error('执行提案失败:', error)
      throw error
    }
  },

  /**
   * 查询当前用户在某个提案上的投票资格（直接读链上，一人一票专用）
   * 返回一个对象，不修改全局 state，供前端页面展示。
   */
  async fetchEligibility({ state }, { proposalId }) {
    try {
      if (!window.ethereum) {
        throw new Error('未检测到钱包环境')
      }

      const provider = new ethers.providers.Web3Provider(window.ethereum)
      const signer = provider.getSigner()
      const userAddress = await signer.getAddress()

      // 只包含一人一票治理相关的必要函数，避免依赖完整 ABI
      const eligibilityABI = [
        'function proposalSnapshot(uint256) view returns (uint256)',
        'function checkVotingEligibility(address,uint256) view returns (bool,string)',
        'function whitelist(address) view returns (bool)',
        'function blacklist(address) view returns (bool)',
        'function accountFirstActiveBlock(address) view returns (uint256)',
        'function accountAgeThreshold() view returns (uint256)',
        'function minVotingBalance() view returns (uint256)',
        'function accountAgeCheckEnabled() view returns (bool)'
      ]

      const governor = new ethers.Contract(state.governorAddress, eligibilityABI, provider)

      // 提案快照区块（如果查询失败，则退化为当前区块）
      let snapshotBlock
      try {
        snapshotBlock = await governor.proposalSnapshot(proposalId)
      } catch (e) {
        console.warn('读取 proposalSnapshot 失败，退化为当前区块:', e)
        const currentBlock = await provider.getBlockNumber()
        snapshotBlock = ethers.BigNumber.from(currentBlock)
      }

      // 并行读取资格与账户信息
      const [
        eligibility,
        isWhitelisted,
        isBlacklisted,
        firstActiveBlock,
        ageThreshold,
        minBalance,
        ageCheckEnabled,
        currentBlock,
        userBalanceRaw
      ] = await Promise.all([
        governor.checkVotingEligibility(userAddress, snapshotBlock).catch(() => [false, 'Eligibility check failed']),
        governor.whitelist(userAddress).catch(() => false),
        governor.blacklist(userAddress).catch(() => false),
        governor.accountFirstActiveBlock(userAddress).catch(() => ethers.BigNumber.from(0)),
        governor.accountAgeThreshold().catch(() => ethers.BigNumber.from(0)),
        governor.minVotingBalance().catch(() => ethers.BigNumber.from(0)),
        governor.accountAgeCheckEnabled().catch(() => false),
        provider.getBlockNumber().catch(() => 0),
        // 使用 WEE 余额而不是 votes，方便直观看条件是否满足
        new ethers.Contract(state.weeTokenAddress, weeTokenABI, provider)
          .balanceOf(userAddress)
          .catch(() => ethers.BigNumber.from(0))
      ])

      const [eligible, reason] = eligibility

      const minBalanceWEE = ethers.utils.formatEther(minBalance || 0)
      const userBalanceWEE = ethers.utils.formatEther(userBalanceRaw || 0)
      const reasonText = mapEligibilityReason(reason, eligible)

      // 账户年龄是否满足（仅在启用年龄检查且记录了首次区块时计算）
      let accountAgeEnough = false
      if (ageCheckEnabled && firstActiveBlock && firstActiveBlock.gt(0)) {
        const diff = snapshotBlock.sub(firstActiveBlock)
        accountAgeEnough = diff.gte(ageThreshold)
      }

      return {
        address: userAddress,
        eligible,
        reason,
        reasonText,
        isWhitelisted,
        isBlacklisted,
        minBalance: minBalanceWEE,
        userBalance: userBalanceWEE,
        accountFirstActiveBlock: firstActiveBlock ? firstActiveBlock.toString() : '0',
        accountAgeThresholdBlocks: ageThreshold ? ageThreshold.toString() : '0',
        accountAgeCheckEnabled: ageCheckEnabled,
        accountAgeEnough,
        snapshotBlock: snapshotBlock ? snapshotBlock.toString() : '0',
        currentBlock: currentBlock.toString()
      }
    } catch (error) {
      console.error('查询投票资格失败:', error)
      throw error
    }
  },

  /**
   * 同步提案状态
   */
  // eslint-disable-next-line no-unused-vars
  async syncProposalStatus({ commit }, proposalId) {
    try {
      await request.post(`/governance/proposals/${proposalId}/sync`)
    } catch (error) {
      console.error('同步状态失败:', error)
      throw error
    }
  }
}

const getters = {
  hasVotingPower: state => parseFloat(state.userVotingPower) > 0,
  canPropose: state => parseFloat(state.userVotingPower) >= 10000
}

export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters
}
