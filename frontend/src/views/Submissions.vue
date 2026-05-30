<template>
  <div class="submissions-page">
    <div class="container">
      <div class="page-title">
        <h2>我的提交</h2>
        <el-button type="primary" icon="el-icon-upload" @click="showSubmitDialog = true">
          提交作品
        </el-button>
      </div>

      <!-- 提交列表 -->
      <div class="submissions-list">
        <el-table
          v-loading="loading"
          :data="submissions"
          border
          stripe
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="competitionId" label="竞赛ID" width="100" />
          <el-table-column prop="filePath" label="文件路径" min-width="200" />
          <el-table-column prop="fileHash" label="文件哈希" min-width="150">
            <template slot-scope="scope">
              <el-tooltip v-if="scope.row.fileHash" :content="scope.row.fileHash" placement="top">
                <span>{{ scope.row.fileHash.substring(0, 16) }}...</span>
              </el-tooltip>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="precheckStatus" label="预检状态" width="100">
            <template slot-scope="scope">
              <el-tag :type="getPrecheckStatusType(scope.row.precheckStatus)">
                {{ getPrecheckStatusText(scope.row.precheckStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="chainStatus" label="上链状态" width="100">
            <template slot-scope="scope">
              <el-tag :type="getChainStatusType(scope.row.chainStatus)">
                {{ getChainStatusText(scope.row.chainStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="提交时间" width="180" />
          <el-table-column label="操作" width="120" fixed="right">
            <template slot-scope="scope">
              <el-button size="mini" @click="viewDetail(scope.row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination">
          <el-pagination
            :current-page.sync="pagination.current"
            :page-size="pagination.size"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="fetchData"
          />
        </div>
      </div>

      <!-- 提交作品对话框 -->
      <el-dialog
        title="提交作品"
        :visible.sync="showSubmitDialog"
        width="600px"
      >
        <el-form :model="submitForm" :rules="submitRules" ref="submitForm" label-width="100px">
          <el-form-item label="选择竞赛" prop="competitionId">
            <el-select v-model="submitForm.competitionId" placeholder="请选择竞赛" style="width: 100%">
              <el-option
                v-for="comp in availableCompetitions"
                :key="comp.id"
                :label="comp.title"
                :value="comp.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="上传文件" prop="file">
            <el-upload
              ref="upload"
              :auto-upload="false"
              :on-change="handleFileChange"
              :limit="1"
              action="#"
            >
              <el-button slot="trigger" size="small" type="primary">选择文件</el-button>
              <div slot="tip" class="el-upload__tip">
                只能上传zip/rar文件，且不超过500MB
              </div>
            </el-upload>
          </el-form-item>
        </el-form>

        <span slot="footer">
          <el-button @click="showSubmitDialog = false">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="handleSubmit">提交</el-button>
        </span>
      </el-dialog>

      <!-- 提交详情对话框 -->
      <el-dialog
        title="提交详情"
        :visible.sync="showDetailDialog"
        width="720px"
        @close="handleDetailClose"
      >
        <div v-loading="detailLoading">
          <template v-if="currentSubmission">
            <el-descriptions :column="2" border>
              <el-descriptions-item label="提交ID">{{ currentSubmission.id }}</el-descriptions-item>
              <el-descriptions-item label="竞赛ID">{{ currentSubmission.competitionId }}</el-descriptions-item>
              <el-descriptions-item label="用户ID">{{ currentSubmission.userId }}</el-descriptions-item>
              <el-descriptions-item label="提交时间">{{ currentSubmission.createdAt }}</el-descriptions-item>
              <el-descriptions-item label="文件路径" :span="2">{{ currentSubmission.filePath || '-' }}</el-descriptions-item>
              <el-descriptions-item label="文件哈希" :span="2">
                <span v-if="currentSubmission.fileHash">{{ currentSubmission.fileHash }}</span>
                <span v-else class="text-muted">预检完成后生成</span>
              </el-descriptions-item>
              <el-descriptions-item label="预检状态">
                <el-tag :type="getPrecheckStatusType(currentSubmission.precheckStatus)">
                  {{ getPrecheckStatusText(currentSubmission.precheckStatus) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="上链状态">
                <el-tag :type="getChainStatusType(currentSubmission.chainStatus)">
                  {{ getChainStatusText(currentSubmission.chainStatus) }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="链上交易哈希" :span="2">
                {{ currentSubmission.chainTxHash || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="区块高度">{{ currentSubmission.blockHeight || '-' }}</el-descriptions-item>
              <el-descriptions-item label="区块时间">{{ currentSubmission.blockTime || '-' }}</el-descriptions-item>
              <el-descriptions-item label="最近更新时间">{{ currentSubmission.updatedAt || '-' }}</el-descriptions-item>
            </el-descriptions>

            <el-alert
              v-if="currentSubmission.precheckStatus === 3"
              type="error"
              :closable="false"
              title="预检失败原因"
              class="detail-alert"
              :description="currentSubmission.precheckReason || '请联系管理员排查具体原因'"
            />
          </template>
          <el-empty v-else description="暂无提交信息" />
        </div>
      </el-dialog>
    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { getMySubmissions, getPresignedUploadUrl, createSubmission, getSubmissionById } from '@/api/submission'
import { getMyRegisteredCompetitions } from '@/api/competition'
import { calculateFileHash } from '@/utils/hash'
import axios from 'axios'

export default {
  name: 'UserSubmissions',
  data() {
    return {
      submissions: [],
      total: 0,
      loading: false,
      pagination: {
        current: 1,
        size: 10
      },
      showSubmitDialog: false,
      submitForm: {
        competitionId: null,
        file: null
      },
      submitRules: {
        competitionId: [
          { required: true, message: '请选择竞赛', trigger: 'change' }
        ],
        file: [
          { required: true, message: '请选择文件', trigger: 'change' }
        ]
      },
      availableCompetitions: [],
      uploading: false,
      showDetailDialog: false,
      detailLoading: false,
      currentSubmission: null
    }
  },
  computed: {
    ...mapGetters('user', ['userId'])
  },
  created() {
    this.fetchData()
    this.loadAvailableCompetitions()
  },
  methods: {
    async fetchData() {
      this.loading = true
      try {
        const res = await getMySubmissions({
          userId: this.userId,
          current: this.pagination.current,
          size: this.pagination.size
        })
        if (res.code === 200) {
          this.submissions = res.data.records
          this.total = res.data.total
        }
      } finally {
        this.loading = false
      }
    },

    async loadAvailableCompetitions() {
      try {
        const res = await getMyRegisteredCompetitions(this.userId)
        if (res.code === 200) {
          this.availableCompetitions = res.data
        }
      } catch (error) {
        console.error('加载已报名竞赛列表失败', error)
      }
    },

    handleFileChange(file) {
      this.submitForm.file = file.raw
    },

    async handleSubmit() {
      this.$refs.submitForm.validate(async (valid) => {
        if (!valid) return

        this.uploading = true
        try {
          const file = this.submitForm.file
          const fileName = file.name

          // 1. 计算文件哈希值
          this.$message.info('正在计算文件哈希值...')
          const fileHash = await calculateFileHash(file)
          console.log('文件哈希值:', fileHash)

          // 2. 获取预签名上传URL
          const urlRes = await getPresignedUploadUrl({
            competitionId: this.submitForm.competitionId,
            fileName
          })

          if (urlRes.code !== 200) {
            throw new Error('获取上传URL失败')
          }

          const { uploadUrl, objectName } = urlRes.data

          // 3. 直传文件到MinIO
          this.$message.info('正在上传文件...')
          await axios.put(uploadUrl, file, {
            headers: { 'Content-Type': file.type }
          })

          // 4. 创建提交记录
          this.$message.info('正在创建提交记录...')
          await createSubmission({
            userId: this.userId,
            competitionId: this.submitForm.competitionId,
            filePath: objectName,
            hashAlgorithm: 'SHA256',
            fileHash: fileHash
          })

          this.$message.success('提交成功！')
          this.showSubmitDialog = false
          this.fetchData()
        } catch (error) {
          this.$message.error(error.message || '提交失败')
        } finally {
          this.uploading = false
        }
      })
    },

    async viewDetail(row) {
      this.showDetailDialog = true
      this.detailLoading = true
      try {
        const res = await getSubmissionById(row.id)
        if (res.code === 200) {
          this.currentSubmission = res.data
        } else {
          this.$message.error(res.message || '加载详情失败')
          this.currentSubmission = null
        }
      } catch (error) {
        this.$message.error(error.message || '加载详情失败')
        this.currentSubmission = null
      } finally {
        this.detailLoading = false
      }
    },

    handleDetailClose() {
      this.currentSubmission = null
      this.detailLoading = false
    },

    getPrecheckStatusType(status) {
      const typeMap = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
      return typeMap[status] || 'info'
    },

    getPrecheckStatusText(status) {
      const textMap = { 0: '待检查', 1: '检查中', 2: '通过', 3: '不通过' }
      return textMap[status] || '未知'
    },

    getChainStatusType(status) {
      const typeMap = { 0: 'info', 1: 'warning', 2: 'success', 3: 'danger' }
      return typeMap[status] || 'info'
    },

    getChainStatusText(status) {
      const textMap = { 0: '未上链', 1: '上链中', 2: '已上链', 3: '上链失败' }
      return textMap[status] || '未知'
    }
  }
}
</script>

<style lang="scss" scoped>
.submissions-page {
  min-height: calc(100vh - 64px);
  padding: 40px 0;
}

.page-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;

  h2 {
    font-size: 32px;
    font-weight: 700;
    color: #303133;
  }
}

.submissions-list {
  background: white;
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

.detail-alert {
  margin-top: 16px;
}

.text-muted {
  color: #909399;
}
</style>
