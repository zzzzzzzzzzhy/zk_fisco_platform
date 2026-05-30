<template>
  <el-dialog
    title="创建提案"
    :visible.sync="dialogVisible"
    width="600px"
    @close="handleClose"
  >
    <el-form :model="form" :rules="rules" ref="proposalForm" label-width="120px">
      <el-form-item label="提案标题" prop="title">
        <el-input
          v-model="form.title"
          placeholder="例如：提高视频分享奖励至 10 WEE"
          maxlength="100"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="提案类型" prop="type">
        <el-select v-model="form.type" placeholder="选择提案类型" style="width: 100%;">
          <el-option label="修改奖励参数" value="updateReward"></el-option>
          <el-option label="自定义合约调用" value="custom" disabled></el-option>
        </el-select>
      </el-form-item>

      <!-- 修改奖励参数表单 -->
      <div v-if="form.type === 'updateReward'">
        <el-form-item label="发帖奖励">
          <el-input-number
            v-model="form.postReward"
            :min="0"
            :max="1000"
            :step="1"
            placeholder="WEE"
          />
          <span class="unit">WEE</span>
        </el-form-item>

        <el-form-item label="评论奖励">
          <el-input-number
            v-model="form.commentReward"
            :min="0"
            :max="1000"
            :step="1"
          />
          <span class="unit">WEE</span>
        </el-form-item>

        <el-form-item label="签到奖励">
          <el-input-number
            v-model="form.checkinReward"
            :min="0"
            :max="1000"
            :step="1"
          />
          <span class="unit">WEE</span>
        </el-form-item>

        <el-form-item label="精华帖奖励">
          <el-input-number
            v-model="form.featuredPostReward"
            :min="0"
            :max="1000"
            :step="1"
          />
          <span class="unit">WEE</span>
        </el-form-item>

        <el-form-item label="连续签到奖励">
          <el-input-number
            v-model="form.consecutiveBonus"
            :min="0"
            :max="1000"
            :step="1"
          />
          <span class="unit">WEE (连续7天)</span>
        </el-form-item>

        <el-form-item label="图片分享奖励">
          <el-input-number
            v-model="form.contentImageReward"
            :min="0"
            :max="1000"
            :step="1"
          />
          <span class="unit">WEE</span>
        </el-form-item>

        <el-form-item label="视频分享奖励">
          <el-input-number
            v-model="form.contentVideoReward"
            :min="0"
            :max="1000"
            :step="1"
          />
          <span class="unit">WEE</span>
        </el-form-item>
      </div>

      <el-form-item label="提案说明" prop="description">
        <el-input
          type="textarea"
          v-model="form.description"
          :rows="6"
          placeholder="详细说明提案的理由和预期效果..."
          maxlength="1000"
          show-word-limit
        />
      </el-form-item>

      <el-alert
        title="提示"
        type="info"
        :closable="false"
        style="margin-bottom: 20px;"
      >
        <p>• 创建提案需要至少 10,000 WEE 投票权</p>
        <p>• 提案将在 1 小时后自动开始投票</p>
        <p>• 需要 4% 的代币参与投票才能通过</p>
      </el-alert>
    </el-form>

    <span slot="footer" class="dialog-footer">
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        @click="handleSubmit"
        :loading="submitting"
      >
        创建提案
      </el-button>
    </span>
  </el-dialog>
</template>

<script>
import { mapActions, mapState } from 'vuex'
import { ethers } from 'ethers'
import { getCurrentConfig } from '@/config/contracts'

const contractConfig = getCurrentConfig()

export default {
  name: 'CreateProposalDialog',
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      dialogVisible: false,
      submitting: false,
      form: {
        title: '',
        type: 'updateReward',
        postReward: 10,
        commentReward: 5,
        checkinReward: 5,
        featuredPostReward: 20,
        consecutiveBonus: 10,
        contentImageReward: 3,
        contentVideoReward: 5,
        description: ''
      },
      rules: {
        title: [
          { required: true, message: '请输入提案标题', trigger: 'blur' }
        ],
        type: [
          { required: true, message: '请选择提案类型', trigger: 'change' }
        ],
        description: [
          { required: true, message: '请输入提案说明', trigger: 'blur' },
          { min: 20, message: '说明至少20个字符', trigger: 'blur' }
        ]
      }
    }
  },
  computed: {
    ...mapState('governance', ['forumExtensionAddress'])
  },
  watch: {
    visible(val) {
      this.dialogVisible = val
    },
    dialogVisible(val) {
      this.$emit('update:visible', val)
    }
  },
  methods: {
    ...mapActions('governance', ['createProposal']),
    
    handleClose() {
      this.dialogVisible = false
      this.$refs.proposalForm.resetFields()
    },
    
    async handleSubmit() {
      this.$refs.proposalForm.validate(async (valid) => {
        if (!valid) return
        
        this.submitting = true
        try {
          // 生成 calldata（包含真正提交到链上的 description）
          const { targets, values, calldatas, description } = this.generateProposalData()
          
          // 创建提案
          const result = await this.createProposal({
            targets,
            values,
            calldatas,
            description,
            // metadata.description 必须和链上使用的 description 完全一致
            // 否则后续用它计算的 descriptionHash 会和 proposalId 对不上
            metadata: {
              title: this.form.title,
              description
            }
          })
          
          this.$message.success('提案已创建！')
          this.$emit('created', result)
          this.handleClose()
        } catch (error) {
          console.error('创建提案失败:', error)
          this.$message.error('创建失败: ' + (error.message || '未知错误'))
        } finally {
          this.submitting = false
        }
      })
    },
    
    generateProposalData() {
      // 使用最新的 ForumTokenExtension 地址（优先取 Vuex 中的配置）
      const forumExtension = this.$store.state.governance.forumExtensionAddress || contractConfig.forumTokenAddress || ''
      
      // 目标合约
      const targets = [forumExtension]
      
      // 发送的 ETH 数量（0）
      const values = [0]
      
      // 生成 calldata（结构体参数需要用元组形式）
      const iface = new ethers.utils.Interface([
        'function updateRewardConfig(tuple(uint256 postReward, uint256 commentReward, uint256 dailyCheckinReward, uint256 featuredPostReward, uint256 consecutiveBonus, uint256 contentImageReward, uint256 contentVideoReward) newConfig)'
      ])
      
      // 构建 RewardConfig 结构体（按照合约定义的顺序）
      const rewardConfig = {
        postReward: ethers.utils.parseEther(this.form.postReward.toString()),
        commentReward: ethers.utils.parseEther(this.form.commentReward.toString()),
        dailyCheckinReward: ethers.utils.parseEther(this.form.checkinReward.toString()),
        featuredPostReward: ethers.utils.parseEther(this.form.featuredPostReward.toString()),
        consecutiveBonus: ethers.utils.parseEther(this.form.consecutiveBonus.toString()),
        contentImageReward: ethers.utils.parseEther(this.form.contentImageReward.toString()),
        contentVideoReward: ethers.utils.parseEther(this.form.contentVideoReward.toString())
      }
      
      const calldata = iface.encodeFunctionData('updateRewardConfig', [rewardConfig])
      
      const calldatas = [calldata]
      
      // 完整描述（用于生成 proposalId）
      const description = `# ${this.form.title}\n\n${this.form.description}\n\n## 参数修改\n- 发帖奖励: ${this.form.postReward} WEE\n- 评论奖励: ${this.form.commentReward} WEE\n- 签到奖励: ${this.form.checkinReward} WEE\n- 精华帖奖励: ${this.form.featuredPostReward} WEE\n- 连续签到奖励: ${this.form.consecutiveBonus} WEE (7天)\n- 图片分享奖励: ${this.form.contentImageReward} WEE\n- 视频分享奖励: ${this.form.contentVideoReward} WEE`
      
      return { targets, values, calldatas, description }
    }
  }
}
</script>

<style scoped>
.unit {
  margin-left: 10px;
  color: #666;
}

.dialog-footer {
  text-align: right;
}

.el-alert p {
  margin: 5px 0;
  font-size: 14px;
}
</style>
