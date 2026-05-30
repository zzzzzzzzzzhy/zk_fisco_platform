<template>
  <div class="polygon-proof-button">
    <!-- 未存证状态 -->
    <el-button
      v-if="!hasProof && !loading"
      type="primary"
      size="small"
      icon="el-icon-upload2"
      @click="startProof"
    >
      提交到 Polygon 存证
    </el-button>

    <!-- 存证中 -->
    <el-button
      v-else-if="loading"
      type="primary"
      size="small"
      loading
      disabled
    >
      {{ loadingText }}
    </el-button>

    <!-- 已存证 -->
    <div v-else class="proof-success">
      <el-tag type="success" size="medium">
        <i class="el-icon-circle-check"></i>
        已存证
      </el-tag>
      <el-button
        v-if="txHash"
        type="text"
        size="small"
        @click="viewOnPolygonScan"
      >
        查看交易
      </el-button>
    </div>
  </div>
</template>

<script>
import { ethers } from 'ethers'
import axios from 'axios'
import { findShareRecordedTxHash } from '@/utils/polygonProof'
import { getCurrentConfig } from '@/config/contracts'

const contractConfig = getCurrentConfig()

export default {
  name: 'PolygonProofButton',
  props: {
    shareId: {
      type: Number,
      required: true
    },
    polygonStatus: {
      type: Number,
      default: 0
    },
    polygonTxHash: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      loading: false,
      loadingText: '准备中...',
      txHash: this.polygonTxHash,
      contractABI: [
        'function recordShare(bytes32 dataHash, address publisher, uint256 shareId, string metadata, bytes signature) external',
        'function verifyShare(bytes32 dataHash) external view returns (bool)'
      ]
    }
  },
  computed: {
    hasProof() {
      return this.polygonStatus === 2 || !!this.txHash
    }
  },
  methods: {
    async startProof() {
      this.loading = true
      this.loadingText = '检查 MetaMask...'

      try {
        // Step 1: 检查 MetaMask
        if (!window.ethereum) {
          this.$message.error('请先安装 MetaMask 浏览器插件')
          return
        }

        // Step 2: 连接钱包
        this.loadingText = '连接钱包...'
        const accounts = await window.ethereum.request({ method: 'eth_requestAccounts' })
        const userAddress = accounts[0]

        // Step 3: 切换到目标网络
        this.loadingText = '切换到 Polygon 网络...'
        const chainIdHex = `0x${contractConfig.chainId.toString(16)}`
        try {
          await window.ethereum.request({
            method: 'wallet_switchEthereumChain',
            params: [{ chainId: chainIdHex }]
          })
        } catch (switchError) {
          // 网络未添加，引导用户添加
          if (switchError.code === 4902) {
            await window.ethereum.request({
              method: 'wallet_addEthereumChain',
              params: [{
                chainId: chainIdHex,
                chainName: contractConfig.chainName,
                nativeCurrency: contractConfig.nativeCurrency,
                rpcUrls: contractConfig.rpcUrls,
                blockExplorerUrls: contractConfig.blockExplorerUrls
              }]
            })
          } else {
            throw switchError
          }
        }

        // Step 4: 获取签名数据
        this.loadingText = '获取签名数据...'
        const { data: signDataResp } = await axios.get(`/api/content-shares/${this.shareId}/polygon-sign-data`)
        
        if (signDataResp.code !== 200) {
          throw new Error(signDataResp.message || '获取签名数据失败')
        }
        
        const { domain, types, primaryType, message, contractAddress } = signDataResp.data

        // Step 5: 如果链上已经存证过该 hash，则直接同步（不再重复签名/发交易）
        this.loadingText = '检查链上状态...'
        const provider = new ethers.providers.Web3Provider(window.ethereum)
        const signer = provider.getSigner()
        const contract = new ethers.Contract(contractAddress, this.contractABI, signer)
        try {
          const alreadyRecorded = await contract.verifyShare(message.dataHash)
          if (alreadyRecorded) {
            const existingTxHash = await findShareRecordedTxHash({
              provider,
              contractAddress,
              dataHash: message.dataHash,
              shareId: message.shareId
            })
            if (!existingTxHash) {
              throw new Error('内容已存证，但未查询到对应交易哈希，请稍后重试')
            }
            this.loadingText = '同步验证中...'
            const { data: verifyResp } = await axios.post(`/api/content-shares/${this.shareId}/polygon-proof`, {
              txHash: existingTxHash
            })
            if (verifyResp.code === 200) {
              this.txHash = existingTxHash
              this.$message.success('✅ 已检测到链上存证，已同步状态')
              this.$emit('proof-success', existingTxHash)
              return
            }
            throw new Error(verifyResp.message || '同步验证失败')
          }
        } catch (e) {
          console.warn('检查链上存证状态失败，继续走签名+交易:', e)
        }

        // Step 6: 检查 POL 余额
        this.loadingText = '检查余额...'
        const balance = await provider.getBalance(userAddress)
        
        // 预估 Gas 费用
        let estimatedGas, gasPrice
        
        try {
          // 暂时用一个假签名来估算 Gas
          const dummySignature = '0x' + '00'.repeat(65)
          estimatedGas = await contract.estimateGas.recordShare(
            message.dataHash,
            message.publisher,
            message.shareId,
            message.metadata,
            dummySignature
          )
          gasPrice = await provider.getGasPrice()
          
          const estimatedCost = estimatedGas.mul(gasPrice)
          
          if (balance.lt(estimatedCost)) {
            const required = ethers.utils.formatEther(estimatedCost)
            const current = ethers.utils.formatEther(balance)
            this.$confirm(
              `您的 POL 余额不足。当前余额: ${current} POL，预计需要: ${required} POL。是否继续？`,
              '余额不足',
              {
                confirmButtonText: '继续',
                cancelButtonText: '取消',
                type: 'warning'
              }
            ).catch(() => {
              throw new Error('用户取消：余额不足')
            })
          }
        } catch (e) {
          console.warn('Gas 估算失败，继续操作:', e)
        }

        // Step 7: 用户签名
        this.loadingText = '等待签名...'
        const signature = await window.ethereum.request({
          method: 'eth_signTypedData_v4',
          params: [
            userAddress,
            JSON.stringify({
              domain,
              types,
              primaryType,
              message
            })
          ]
        })

        // Step 8: 调用合约
        this.loadingText = '发送交易...'
        let tx
        try {
          tx = await contract.recordShare(
            message.dataHash,
            message.publisher,
            message.shareId,
            message.metadata,
            signature
          )
        } catch (e) {
          const msg = String(e?.reason || e?.error?.message || e?.message || '')
          if (msg.includes('Content hash already recorded')) {
            const existingTxHash = await findShareRecordedTxHash({
              provider,
              contractAddress,
              dataHash: message.dataHash,
              shareId: message.shareId
            })
            if (!existingTxHash) {
              throw new Error('内容已存证，但未查询到对应交易哈希，请稍后重试')
            }
            this.loadingText = '同步验证中...'
            const { data: verifyResp } = await axios.post(`/api/content-shares/${this.shareId}/polygon-proof`, {
              txHash: existingTxHash
            })
            if (verifyResp.code === 200) {
              this.txHash = existingTxHash
              this.$message.success('✅ 已检测到链上存证，已同步状态')
              this.$emit('proof-success', existingTxHash)
              return
            }
            throw new Error(verifyResp.message || '同步验证失败')
          }
          throw e
        }

        this.loadingText = '等待确认...'
        this.$message.info(`交易已发送: ${tx.hash}`)
        
        const receipt = await tx.wait()

        // Step 8: 提交给后端验证
        this.loadingText = '验证中...'
        const { data: verifyResp } = await axios.post(`/api/content-shares/${this.shareId}/polygon-proof`, {
          txHash: receipt.transactionHash
        })

        if (verifyResp.code === 200) {
          this.txHash = receipt.transactionHash
          this.$message.success('✅ Polygon 存证成功！WEE 奖励已发放')
          this.$emit('proof-success', receipt.transactionHash)
        } else {
          throw new Error(verifyResp.message || '验证失败')
        }

      } catch (error) {
        console.error('Polygon 存证失败:', error)

        if (error.code === 'INSUFFICIENT_FUNDS') {
          this.$message.error('POL 余额不足，无法支付 Gas 费用')
        } else if (error.code === 4001) {
          this.$message.warning('您取消了交易')
        } else if (error.message?.includes('用户取消')) {
          this.$message.info(error.message)
        } else {
          this.$message.error('存证失败: ' + (error.message || '未知错误'))
        }
      } finally {
        this.loading = false
        this.loadingText = '准备中...'
      }
    },

    viewOnPolygonScan() {
      if (this.txHash) {
        const explorer = (contractConfig.blockExplorerUrls || [])[0] || 'https://polygonscan.com'
        window.open(`${explorer.replace(/\/$/, '')}/tx/${this.txHash}`, '_blank')
      }
    }
  }
}
</script>

<style scoped>
.polygon-proof-button {
  display: inline-block;
}

.proof-success {
  display: flex;
  align-items: center;
  gap: 8px;
}

.proof-success .el-tag {
  cursor: default;
}
</style>
