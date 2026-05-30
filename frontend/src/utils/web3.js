import { ethers } from 'ethers'
import detectEthereumProvider from '@metamask/detect-provider'
import { getCurrentConfig, validateContractAddress } from '@/config/contracts'
import Web3ErrorHandler from './web3ErrorHandler'
import request from '@/api/request'

// ForumTokenExtension ABI (更新版本)
const FORUM_TOKEN_ABI = [
  // View functions
  {
    "inputs": [{"internalType": "address", "name": "user", "type": "address"}],
    "name": "getUserTokenBalance",
    "outputs": [{"internalType": "uint256", "name": "", "type": "uint256"}],
    "stateMutability": "view",
    "type": "function"
  },
  {
    "inputs": [],
    "name": "rewardConfig",
    "outputs": [
      {"internalType": "uint256", "name": "postReward", "type": "uint256"},
      {"internalType": "uint256", "name": "commentReward", "type": "uint256"},
      {"internalType": "uint256", "name": "dailyCheckinReward", "type": "uint256"},
      {"internalType": "uint256", "name": "featuredPostReward", "type": "uint256"},
      {"internalType": "uint256", "name": "consecutiveBonus", "type": "uint256"},
      {"internalType": "uint256", "name": "contentImageReward", "type": "uint256"},
      {"internalType": "uint256", "name": "contentVideoReward", "type": "uint256"}
    ],
    "stateMutability": "view",
    "type": "function"
  },
  {
    "inputs": [{"internalType": "address", "name": "user", "type": "address"}],
    "name": "canCheckinToday",
    "outputs": [{"internalType": "bool", "name": "", "type": "bool"}],
    "stateMutability": "view",
    "type": "function"
  },
  {
    "inputs": [{"internalType": "address", "name": "user", "type": "address"}],
    "name": "getUserRewardInfo",
    "outputs": [
      {"internalType": "uint256", "name": "lastCheckinTime", "type": "uint256"},
      {"internalType": "uint256", "name": "consecutiveDays", "type": "uint256"},
      {"internalType": "uint256", "name": "dailyRewardAmount", "type": "uint256"},
      {"internalType": "uint256", "name": "totalRewarded", "type": "uint256"}
    ],
    "stateMutability": "view",
    "type": "function"
  },
  // Write functions
  {
    "inputs": [],
    "name": "checkin",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  },
  {
    "inputs": [
      {"internalType": "address", "name": "user", "type": "address"},
      {"internalType": "uint256", "name": "day", "type": "uint256"},
      {"internalType": "uint256", "name": "deadline", "type": "uint256"},
      {"internalType": "bytes", "name": "signature", "type": "bytes"}
    ],
    "name": "claimDailyCheckinReward",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  },
  {
    "inputs": [
      {"internalType": "address", "name": "user", "type": "address"},
      {"internalType": "string", "name": "commentId", "type": "string"},
      {"internalType": "uint256", "name": "deadline", "type": "uint256"},
      {"internalType": "bytes", "name": "signature", "type": "bytes"}
    ],
    "name": "claimCommentReward",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  },
  {
    "inputs": [
      {"internalType": "address", "name": "user", "type": "address"},
      {"internalType": "string", "name": "contentId", "type": "string"},
      {"internalType": "bool", "name": "isVideo", "type": "bool"},
      {"internalType": "uint256", "name": "deadline", "type": "uint256"},
      {"internalType": "bytes", "name": "signature", "type": "bytes"}
    ],
    "name": "claimContentShareReward",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  },
  {
    "inputs": [{"internalType": "address", "name": "user", "type": "address"}],
    "name": "dailyCheckin",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  },
  {
    "inputs": [
      {"internalType": "address", "name": "user", "type": "address"},
      {"internalType": "string", "name": "postId", "type": "string"}
    ],
    "name": "rewardPost",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  },
  {
    "inputs": [
      {"internalType": "address", "name": "user", "type": "address"},
      {"internalType": "string", "name": "commentId", "type": "string"}
    ],
    "name": "rewardComment",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  },
  {
    "inputs": [
      {"internalType": "address", "name": "creator", "type": "address"},
      {"internalType": "uint256", "name": "amount", "type": "uint256"},
      {"internalType": "string", "name": "contentType", "type": "string"},
      {"internalType": "string", "name": "contentId", "type": "string"}
    ],
    "name": "tipContent",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  },
  // 新增：用户自行付费置顶帖子
  {
    "inputs": [
      {"internalType": "address", "name": "user", "type": "address"},
      {"internalType": "uint256", "name": "amount", "type": "uint256"},
      {"internalType": "string", "name": "postId", "type": "string"}
    ],
    "name": "purchasePinPost",
    "outputs": [],
    "stateMutability": "nonpayable",
    "type": "function"
  }
]

// MTK Token ABI (ERC20)
const MTK_TOKEN_ABI = [
  {
    "inputs": [{"name": "account", "type": "address"}],
    "name": "balanceOf",
    "outputs": [{"name": "", "type": "uint256"}],
    "stateMutability": "view",
    "type": "function"
  },
  {
    "inputs": [],
    "name": "decimals",
    "outputs": [{"name": "", "type": "uint8"}],
    "stateMutability": "view",
    "type": "function"
  },
  {
    "inputs": [],
    "name": "symbol",
    "outputs": [{"name": "", "type": "string"}],
    "stateMutability": "view",
    "type": "function"
  },
  {
    "inputs": [
      {"name": "spender", "type": "address"},
      {"name": "value", "type": "uint256"}
    ],
    "name": "approve",
    "outputs": [{"name": "", "type": "bool"}],
    "stateMutability": "nonpayable",
    "type": "function"
  },
  {
    "inputs": [
      {"name": "owner", "type": "address"},
      {"name": "spender", "type": "address"}
    ],
    "name": "allowance",
    "outputs": [{"name": "", "type": "uint256"}],
    "stateMutability": "view",
    "type": "function"
  }
]

class Web3Service {
  constructor() {
    this.provider = null
    this.signer = null
    this.mtkTokenContract = null
    this.forumTokenContract = null
    this.isConnected = false
    this.currentAddress = null
    this.balanceUpdateInterval = null

    // 使用配置文件
    this.config = getCurrentConfig()

    // 验证合约地址
    this.validateConfig()
  }

  // 验证配置
  validateConfig() {
    if (!validateContractAddress(this.config.mtkTokenAddress)) {
      console.error('MTK Token 地址无效:', this.config.mtkTokenAddress)
    }
    if (!validateContractAddress(this.config.forumTokenAddress)) {
      console.error('Forum Token 地址无效:', this.config.forumTokenAddress)
    }
    if (!this.config.chainId || !this.config.chainName) {
      console.error('区块链配置无效')
    }
  }

  // 检测并连接MetaMask
  async connectMetaMask() {
    console.log('🔗 开始连接 MetaMask...')

    try {
      const provider = await detectEthereumProvider({
        mustBeMetaMask: true,
        timeout: 30000 // 30秒超时
      })

      if (!provider) {
        throw new Error('请安装MetaMask钱包')
      }

      // 检查是否已解锁
      let accounts
      try {
        accounts = await provider.request({ method: 'eth_accounts' })
        console.log('📋 当前账户:', accounts)

        if (accounts.length === 0) {
          console.log('🔓 请求账户连接...')
          // 请求连接账户
          accounts = await provider.request({
            method: 'eth_requestAccounts',
            params: []
          })
          console.log('✅ 账户连接成功:', accounts)
        }
      } catch (requestError) {
        if (requestError.code === 4001) {
          throw new Error('用户拒绝了连接请求')
        }
        throw new Error('账户连接失败: ' + requestError.message)
      }

      if (accounts.length === 0) {
        throw new Error('没有可用的账户')
      }

      // 初始化 provider 和 signer
      this.provider = new ethers.providers.Web3Provider(provider, 'any')
      this.signer = this.provider.getSigner()

      // 获取当前地址
      this.currentAddress = await this.signer.getAddress()
      console.log('📍 当前地址:', this.currentAddress)

      // 切换到正确的网络
      try {
        console.log('🌐 检查网络配置...')
        await this.switchToCorrectNetwork()
      } catch (networkError) {
        console.warn('⚠️ 网络切换失败，但连接继续:', networkError)
        // 不抛出错误，允许用户手动切换网络
      }

      // 初始化合约实例
      try {
        console.log('📋 初始化合约实例...')
        await this.initializeContracts()
      } catch (contractError) {
        console.warn('⚠️ 合约初始化失败:', contractError)
        // 不抛出错误，允许后续手动重试
      }

      // 最后设置连接状态
      this.isConnected = true
      console.log('✅ MetaMask 连接成功')

      return {
        success: true,
        address: this.currentAddress,
        message: 'MetaMask连接成功'
      }
    } catch (error) {
      console.error('❌ MetaMask连接失败:', error)
      this.isConnected = false
      this.currentAddress = null

      const errorInfo = Web3ErrorHandler.handleConnectionError(error)
      return {
        success: false,
        error: errorInfo.error,
        type: errorInfo.type,
        message: `连接失败: ${errorInfo.error}`
      }
    }
  }

  // 切换到正确的网络
  async switchToCorrectNetwork() {
    try {
      if (!this.provider) {
        throw new Error('Provider未初始化')
      }

      const network = await this.provider.getNetwork()
      if (network.chainId !== this.config.chainId) {
        // 使用统一的 provider 接口
        const ethereum = this.provider.provider || window.ethereum
        if (!ethereum) {
          throw new Error('无法访问以太坊接口')
        }

        try {
          await ethereum.request({
            method: 'wallet_switchEthereumChain',
            params: [{ chainId: `0x${this.config.chainId.toString(16)}` }]
          })
        } catch (switchError) {
          // 如果网络不存在，添加网络
          if (switchError.code === 4902) {
            await ethereum.request({
              method: 'wallet_addEthereumChain',
              params: [
                {
                  chainId: `0x${this.config.chainId.toString(16)}`,
                  chainName: this.config.chainName,
                  rpcUrls: this.config.rpcUrls.slice(0, 1), // 只使用第一个RPC地址
                  nativeCurrency: {
                    name: 'MATIC',
                    symbol: 'MATIC',
                    decimals: 18
                  },
                  blockExplorerUrls: ['https://polygonscan.com/']
                }
              ]
            })
          } else {
            throw switchError
          }
        }
      }
    } catch (error) {
      console.error('切换网络失败:', error)
      throw new Error(`网络切换失败: ${error.message}`)
    }
  }

  // 初始化合约实例
  async initializeContracts() {
    if (!this.signer) {
      throw new Error('Signer未初始化，请先连接钱包')
    }

    this.mtkTokenContract = new ethers.Contract(
      this.config.mtkTokenAddress,
      MTK_TOKEN_ABI,
      this.signer
    )

    this.forumTokenContract = new ethers.Contract(
      this.config.forumTokenAddress,
      FORUM_TOKEN_ABI,
      this.signer
    )
  }

  // 断开连接
  disconnect() {
    this.provider = null
    this.signer = null
    this.mtkTokenContract = null
    this.forumTokenContract = null
    this.isConnected = false
    this.currentAddress = null

    // 清理定时器
    if (this.balanceUpdateInterval) {
      clearInterval(this.balanceUpdateInterval)
      this.balanceUpdateInterval = null
    }
  }

  isWalletConnected() {
    const connected = this.isConnected && !!this.currentAddress && !!this.provider && !!this.signer
    console.log('🔍 检查钱包连接状态:', {
      isConnected: this.isConnected,
      hasAddress: !!this.currentAddress,
      hasProvider: !!this.provider,
      hasSigner: !!this.signer,
      finalResult: connected
    })
    return connected
  }

  async ensureConnected() {
    if (this.isWalletConnected()) {
      return { success: true, address: this.currentAddress }
    }
    return this.connectMetaMask()
  }

  async ensureTokenAllowance(amountWei) {
    if (!this.mtkTokenContract) {
      throw new Error('合约未初始化')
    }
    const allowance = await this.mtkTokenContract.allowance(this.currentAddress, this.config.forumTokenAddress)
    if (allowance.lt(amountWei)) {
      // ethers v5中使用ethers.constants.MaxUint256
      const tx = await this.mtkTokenContract.approve(this.config.forumTokenAddress, ethers.constants.MaxUint256)
      await tx.wait()
    }
  }

  async tipContent({ creatorAddress, amount, contentType = 'CONTENT_SHARE', contentId = '' }) {
    await this.ensureConnected()
    if (!ethers.utils.isAddress(creatorAddress)) {
      throw new Error('创作者钱包地址无效')
    }
    if (!this.forumTokenContract) {
      await this.initializeContracts()
    }
    const amountWei = ethers.utils.parseUnits(String(amount), 18)
    await this.ensureTokenAllowance(amountWei)
    const tx = await this.forumTokenContract.tipContent(
      creatorAddress,
      amountWei,
      contentType,
      contentId.toString()
    )
    const receipt = await tx.wait()
    return {
      txHash: receipt.transactionHash || tx.hash,
      blockNumber: receipt.blockNumber
    }
  }

  /**
   * 内容置顶（由用户钱包直接支付 Gas 和置顶费用）
   * 对应合约 ForumTokenExtension.purchasePinPost(user, amount, postId)
   */
  async purchasePinPost(postId, pinPriceWEE = '50') {
    // 1. 确保钱包已连接
    await this.ensureConnected()

    // 2. 确保合约已初始化
    if (!this.forumTokenContract) {
      await this.initializeContracts()
    }

    // 3. 计算置顶价格（默认 50 WEE）
    const amountWei = ethers.utils.parseUnits(String(pinPriceWEE), 18)

    // 4. 确保对 ForumTokenExtension 授权足够额度
    await this.ensureTokenAllowance(amountWei)

    // 5. 由用户地址直接调用 purchasePinPost，自付 Gas 和置顶费用
    const tx = await this.forumTokenContract.purchasePinPost(
      this.currentAddress,
      amountWei,
      postId.toString()
    )
    const receipt = await tx.wait()

    if (receipt.status === 0) {
      throw new Error('置顶交易失败')
    }

    return {
      success: true,
      txHash: receipt.transactionHash || tx.hash,
      blockNumber: receipt.blockNumber
    }
  }

  // 获取MTK代币余额
  async getMTKBalance() {
    if (!this.mtkTokenContract) {
      throw new Error('合约未初始化')
    }

    try {
      const balance = await this.mtkTokenContract.balanceOf(this.currentAddress)
      const decimals = await this.mtkTokenContract.decimals()
      return ethers.utils.formatUnits(balance, decimals)
    } catch (error) {
      console.error('获取余额失败:', error)
      throw error
    }
  }

  // 兼容命名：获取WEE余额（与MTK相同代币）
  async getWEEBalance() {
    return this.getMTKBalance()
  }

  // 用户签到 (新版本 - 使用后端签名)
  async dailyCheckin(captchaToken = null) {
    try {
      await this.connectMetaMask()
      if (!this.currentAddress) {
        throw new Error('钱包未连接')
      }

      const day = new Date().toISOString().slice(0, 10)
      const message = `WEE_CHECKIN:${day}:${this.currentAddress.toLowerCase()}`
      const signature = await this.signMessage(message)

      const response = await request.post('/checkin/consent', {
        userAddress: this.currentAddress,
        day,
        signature,
        captchaToken: captchaToken || undefined
      })

      return {
        success: true,
        pending: true,
        day,
        response: response.data
      }
    } catch (error) {
      console.error('每日签到失败:', error)
      
      // 解析错误信息
      let errorMessage = error.message
      if (error.message.includes('Already checked in today') || error.message.includes('Already claimed')) {
        errorMessage = '今日已签到，请明天再来'
      } else if (error.message.includes('Signature expired')) {
        errorMessage = '签名已过期，请重试'
      } else if (error.message.includes('Invalid signature')) {
        errorMessage = '签名验证失败，请重试'
      } else if (error.message.includes('Daily reward limit exceeded')) {
        errorMessage = '今日奖励已达上限'
      }
      
      return {
        success: false,
        error: errorMessage
      }
    }
  }

  // 评论奖励领取（通过后端签名 + 合约 claimCommentReward）
  async claimCommentReward(commentId) {
    if (!this.forumTokenContract) {
      await this.initializeContracts()
    }
    if (!this.currentAddress) {
      throw new Error('钱包未连接，无法领取评论奖励')
    }

    try {
      // 向后端请求签名
      const payload = {
        commentId: String(commentId),
        userAddress: this.currentAddress
      }
      const response = await request.post('/forum/token/comment/reward-sign', payload)

      if (response.code !== 200 || !response.data) {
        throw new Error(response.message || '获取评论奖励签名失败')
      }

      const { user, commentId: cid, deadline, signature } = response.data

      const tx = await this.forumTokenContract.claimCommentReward(
        user,
        String(cid),
        deadline,
        signature
      )
      const receipt = await tx.wait()

      if (receipt.status === 0) {
        throw new Error('评论奖励交易失败')
      }

      return {
        success: true,
        transactionHash: tx.hash,
        blockNumber: receipt.blockNumber,
        gasUsed: receipt.gasUsed.toString()
      }
    } catch (error) {
      const msg = error && error.message ? String(error.message) : ''
      const isUserRejected =
        error?.code === 'ACTION_REJECTED' ||
        error?.code === 4001 ||
        /user rejected/i.test(msg)

      if (isUserRejected) {
        // 用户主动取消，不算真正错误，只做调试级别输出
        console.info('用户取消了评论奖励领取')
        return {
          success: false,
          error: '用户取消了评论奖励领取'
        }
      }

      console.error('评论奖励领取失败:', error)
      return {
        success: false,
        error: msg || '评论奖励领取失败'
      }
    }
  }

  // 检查今日是否可以签到
  async hasDailyCheckinToday() {
    try {
      const response = await request.get('/checkin/status')
      return !!response.data?.submitted
    } catch (error) {
      console.error('检查签到状态失败:', error)
      return false
    }
  }

  // 获取用户奖励信息（包含连续签到天数）
  async getUserRewardInfo() {
    if (!this.forumTokenContract) {
      throw new Error('合约未初始化')
    }

    try {
      const info = await this.forumTokenContract.getUserRewardInfo(this.currentAddress)
      let totalRewarded = ethers.utils.formatUnits(info.totalRewarded, 18)
      try {
        const response = await request.get('/forum/token/reward-info')
        const backendTotal = response?.data?.totalRewarded
        if (backendTotal !== undefined && backendTotal !== null) {
          totalRewarded = String(backendTotal)
        }
      } catch (error) {
        console.warn('获取后端累计奖励失败，使用链上数据:', error)
      }
      return {
        lastCheckinTime: info.lastCheckinTime.toNumber(),
        consecutiveDays: info.consecutiveDays.toNumber(),
        dailyRewardAmount: ethers.utils.formatUnits(info.dailyRewardAmount, 18),
        totalRewarded
      }
    } catch (error) {
      console.error('获取用户奖励信息失败:', error)
      return {
        lastCheckinTime: 0,
        consecutiveDays: 0,
        dailyRewardAmount: '0',
        totalRewarded: '0'
      }
    }
  }

  // 保持向后兼容的函数
  async getConsecutiveDays() {
    const info = await this.getUserRewardInfo()
    return info.consecutiveDays
  }

  // 奖励发帖 (管理员功能)
  async rewardPostCreation() {
    if (!this.forumTokenContract) {
      throw new Error('合约未初始化')
    }

    try {
      // 注意：这需要管理员权限，普通用户无法调用
      const tx = await this.forumTokenContract.rewardPost(this.currentAddress, `post_${Date.now()}`)
      await tx.wait()
      return {
        success: true,
        transactionHash: tx.hash
      }
    } catch (error) {
      console.error('发帖奖励失败:', error)
      return {
        success: false,
        error: error.message
      }
    }
  }

  // 奖励评论 (管理员功能)
  async rewardCommentCreation() {
    if (!this.forumTokenContract) {
      throw new Error('合约未初始化')
    }

    try {
      // 注意：这需要管理员权限，普通用户无法调用
      const tx = await this.forumTokenContract.rewardComment(this.currentAddress, `comment_${Date.now()}`)
      await tx.wait()
      return {
        success: true,
        transactionHash: tx.hash
      }
    } catch (error) {
      console.error('评论奖励失败:', error)
      return {
        success: false,
        error: error.message
      }
    }
  }

  // 获取奖励配置
  async getRewardConfig() {
    if (!this.forumTokenContract) {
      throw new Error('合约未初始化')
    }

    try {
      const config = await this.forumTokenContract.rewardConfig()
      return {
        postReward: ethers.utils.formatUnits(config.postReward, 18),
        commentReward: ethers.utils.formatUnits(config.commentReward, 18),
        dailyCheckinReward: ethers.utils.formatUnits(config.dailyCheckinReward, 18),
        featuredPostReward: ethers.utils.formatUnits(config.featuredPostReward, 18),
        consecutiveBonus: ethers.utils.formatUnits(config.consecutiveBonus, 18),
        contentImageReward: ethers.utils.formatUnits(config.contentImageReward, 18),
        contentVideoReward: ethers.utils.formatUnits(config.contentVideoReward, 18)
      }
    } catch (error) {
      console.error('获取奖励配置失败:', error)
      return null
    }
  }

  // 监听账户变化
  onAccountChanged(callback) {
    if (window.ethereum) {
      window.ethereum.on('accountsChanged', (accounts) => {
        if (accounts.length === 0) {
          this.disconnect()
          callback(null)
        } else {
          this.currentAddress = accounts[0]
          callback(accounts[0])
        }
      })
    }
  }

  // 监听网络变化
  onNetworkChanged(callback) {
    if (window.ethereum) {
      window.ethereum.on('chainChanged', (chainId) => {
        callback(chainId)
      })
    }
  }

  // 获取原生代币余额
  async getBalance() {
    if (!window.ethereum || !this.currentAddress) {
      return '0'
    }

    try {
      const balance = await window.ethereum.request({
        method: 'eth_getBalance',
        params: [this.currentAddress, 'latest']
      })
      return parseInt(balance, 16) / Math.pow(10, 18) + ''
    } catch (error) {
      console.error('获取余额失败:', error)
      return '0'
    }
  }

  // 检查今日是否可以签到
  async canCheckinToday() {
    try {
      const response = await request.get('/checkin/status')
      return !response.data?.submitted
    } catch (error) {
      console.error('检查签到状态失败:', error)
      // 状态查询失败时不阻塞用户操作
      return true
    }
  }

  async signMessage(message) {
    if (!this.signer) {
      await this.connectMetaMask()
    }
    if (!this.signer) {
      throw new Error('钱包未连接')
    }
    return this.signer.signMessage(message)
  }

  // 监听余额变化
  onBalanceUpdated(callback) {
    // 避免重复创建定时器
    if (this.balanceUpdateInterval) {
      return
    }

    this.balanceUpdateInterval = setInterval(async () => {
      if (this.currentAddress && this.isConnected) {
        try {
          const balance = await this.getBalance()
          const rewardInfo = await this.getUserRewardInfo()
          callback(balance, rewardInfo.consecutiveDays)
        } catch (error) {
          console.error('余额更新检查失败:', error)
        }
      }
    }, 30000) // 每30秒检查一次
  }

  // 获取当前状态
  getState() {
    return {
      isConnected: this.isConnected,
      address: this.currentAddress,
      network: this.config.chainName, // 使用配置的默认网络名称
      balance: '0' // 可以在这里缓存余额
    }
  }

  // 异步获取完整状态（包括实际网络）
  async getFullState() {
    return {
      isConnected: this.isConnected,
      address: this.currentAddress,
      network: await this.getCurrentNetwork(),
      balance: '0'
    }
  }

  // 获取当前网络名称
  async getCurrentNetwork() {
    if (!window.ethereum) {
      return this.config.chainName
    }

    try {
      const chainId = await window.ethereum.request({ method: 'eth_chainId' })
      const chainIdNum = parseInt(chainId, 16)

      switch (chainIdNum) {
        case 80002:
          return 'Polygon Amoy Testnet'
        case 137:
          return 'Polygon Mainnet'
        case 1:
          return 'Ethereum Mainnet'
        default:
          return `Chain ID: ${chainIdNum}`
      }
    } catch (error) {
      console.error('获取网络信息失败:', error)
      return '未知网络'
    }
  }
}

const web3Service = new Web3Service()

// 挂到全局，方便在任意组件中调用奖励领取等方法
if (typeof window !== 'undefined') {
  window.web3Service = web3Service
}

export default web3Service
