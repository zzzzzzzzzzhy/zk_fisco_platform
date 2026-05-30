<template>
  <el-alert
    v-if="!userDelegated && walletAddress"
    title="激活投票权"
    type="warning"
    :closable="false"
    class="delegate-banner"
  >
    <div class="banner-content">
      <div class="banner-text">
        <p>💡 你需要先委托投票权给自己才能参与 DAO 治理</p>
        <p class="help-text">
          当前余额: <strong>{{ weeBalance }} WEE</strong> | 
          投票权: <strong>{{ userVotingPower }} WEE</strong>
        </p>
      </div>
      <el-button
        type="primary"
        size="small"
        @click="handleDelegate"
        :loading="delegating"
      >
        激活投票权
      </el-button>
    </div>
  </el-alert>

  <el-alert
    v-else-if="userDelegated"
    title="投票权已激活"
    type="success"
    :closable="true"
    class="delegate-banner"
  >
    <div class="banner-content">
      <p>
        ✅ 你的投票权: <strong>{{ userVotingPower }} WEE</strong>
        <span v-if="canPropose" class="badge-can-propose">(可发起提案)</span>
      </p>
    </div>
  </el-alert>
</template>

<script>
import { mapState, mapGetters, mapActions } from 'vuex'
import { walletState } from '@/store/wallet'

export default {
  name: 'DelegateBanner',
  data() {
    return {
      delegating: false,
      weeBalance: '0'
    }
  },
  computed: {
    ...mapState('governance', ['userVotingPower', 'userDelegated']),
    ...mapGetters('governance', ['canPropose']),
    walletAddress() {
      // 优先从全局 walletState 读取（MetaMask 连接的地址）
      // 如果没有，则从 user store 读取（后端绑定的地址）
      return walletState.address || this.$store.getters['user/walletAddress'] || ''
    }
  },
  mounted() {
    if (this.walletAddress) {
      this.loadVotingPower()
      this.loadBalance()
    }
  },
  watch: {
    walletAddress(newVal) {
      if (newVal) {
        this.loadVotingPower()
        this.loadBalance()
      }
    }
  },
  methods: {
    ...mapActions('governance', ['fetchVotingPower', 'delegateToSelf']),
    
    async loadVotingPower() {
      try {
        await this.fetchVotingPower(this.walletAddress)
      } catch (error) {
        console.error('加载投票权失败:', error)
      }
    },
    
    async loadBalance() {
      try {
        const { ethers } = await import('ethers')
        const provider = new ethers.providers.Web3Provider(window.ethereum)
        const weeToken = new ethers.Contract(
          this.$store.state.governance.weeTokenAddress,
          ['function balanceOf(address) view returns (uint256)'],
          provider
        )
        const balance = await weeToken.balanceOf(this.walletAddress)
        this.weeBalance = ethers.utils.formatEther(balance)
      } catch (error) {
        console.error('加载余额失败:', error)
      }
    },
    
    async handleDelegate() {
      if (!this.walletAddress) {
        this.$message.warning('请先连接钱包')
        return
      }
      
      this.delegating = true
      try {
        await this.delegateToSelf(this.walletAddress)
        this.$message.success('投票权已激活！')
        await this.loadVotingPower()
      } catch (error) {
        console.error('委托失败:', error)
        this.$message.error('激活失败: ' + (error.message || '未知错误'))
      } finally {
        this.delegating = false
      }
    }
  }
}
</script>

<style scoped>
.delegate-banner {
  margin-bottom: 20px;
}

.banner-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.banner-text p {
  margin: 5px 0;
}

.help-text {
  font-size: 14px;
  color: #666;
}

.badge-can-propose {
  color: #67c23a;
  font-weight: bold;
  margin-left: 10px;
}
</style>

