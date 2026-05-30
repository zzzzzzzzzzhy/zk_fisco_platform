<template>
  <div class="competition-form-page">
    <div class="container">
      <div class="page-header">
        <h2>{{ isEdit ? '编辑竞赛' : '创建竞赛' }}</h2>
        <el-button @click="$router.back()">返回</el-button>
      </div>

      <div class="form-container">
        <el-form
          ref="competitionForm"
          :model="form"
          :rules="rules"
          label-width="140px"
          label-position="right"
        >
          <!-- 基本信息 -->
          <el-card class="form-section" shadow="never">
            <div slot="header">基本信息</div>

            <el-form-item label="竞赛标题" prop="title">
              <el-input v-model="form.title" placeholder="请输入竞赛标题" />
            </el-form-item>

            <el-form-item label="竞赛简介" prop="description">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="3"
                placeholder="请输入竞赛简介"
              />
            </el-form-item>

            <el-form-item label="封面图片">
              <div class="image-upload-container">
                <el-upload
                  class="image-uploader"
                  action="#"
                  :show-file-list="false"
                  :before-upload="file => beforeImageUpload(file, 'cover')"
                  :auto-upload="false"
                  accept="image/*"
                >
                  <img v-if="coverImagePreview" :src="coverImagePreview" class="image-preview" />
                  <i v-else class="el-icon-plus image-uploader-icon"></i>
                </el-upload>
                <div class="upload-tip">推荐尺寸：1200x400px，用于列表页横幅展示</div>
                <el-button v-if="coverImagePreview" size="small" type="text" @click="resetCoverImage">使用默认图片</el-button>
              </div>
            </el-form-item>

            <el-form-item label="详情图片">
              <div class="image-upload-container">
                <el-upload
                  class="image-uploader"
                  action="#"
                  :show-file-list="false"
                  :before-upload="file => beforeImageUpload(file, 'detail')"
                  :auto-upload="false"
                  accept="image/*"
                >
                  <img v-if="detailImagePreview" :src="detailImagePreview" class="image-preview" />
                  <i v-else class="el-icon-plus image-uploader-icon"></i>
                </el-upload>
                <div class="upload-tip">推荐尺寸：1200x600px，用于详情页头部展示</div>
                <el-button v-if="detailImagePreview" size="small" type="text" @click="resetDetailImage">使用默认图片</el-button>
              </div>
            </el-form-item>

            <el-form-item label="竞赛状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态">
                <el-option label="草稿" :value="0"></el-option>
                <el-option label="报名中" :value="1"></el-option>
                <el-option label="进行中" :value="2"></el-option>
                <el-option label="已结束" :value="3"></el-option>
                <el-option label="已取消" :value="4"></el-option>
              </el-select>
            </el-form-item>
          </el-card>

          <!-- 时间设置 -->
          <el-card class="form-section" shadow="never">
            <div slot="header">时间设置</div>

            <el-form-item label="报名开始时间" prop="registrationStartTime">
              <el-date-picker
                v-model="form.registrationStartTime"
                type="datetime"
                placeholder="选择报名开始时间"
                value-format="yyyy-MM-dd HH:mm:ss"
              />
            </el-form-item>

            <el-form-item label="报名结束时间" prop="registrationEndTime">
              <el-date-picker
                v-model="form.registrationEndTime"
                type="datetime"
                placeholder="选择报名结束时间"
                value-format="yyyy-MM-dd HH:mm:ss"
              />
            </el-form-item>

            <el-form-item label="提交开始时间" prop="submissionStartTime">
              <el-date-picker
                v-model="form.submissionStartTime"
                type="datetime"
                placeholder="选择提交开始时间"
                value-format="yyyy-MM-dd HH:mm:ss"
              />
            </el-form-item>

            <el-form-item label="提交结束时间" prop="submissionEndTime">
              <el-date-picker
                v-model="form.submissionEndTime"
                type="datetime"
                placeholder="选择提交结束时间"
                value-format="yyyy-MM-dd HH:mm:ss"
              />
            </el-form-item>
          </el-card>

          <!-- 赛题详情 -->
          <el-card class="form-section" shadow="never">
            <div slot="header">赛题详情</div>

            <el-form-item label="赛题详情">
              <el-input
                v-model="form.detail"
                type="textarea"
                :rows="6"
                placeholder="请输入赛题详情（支持HTML）"
              />
            </el-form-item>

            <el-form-item label="数据说明">
              <el-input
                v-model="form.dataDescription"
                type="textarea"
                :rows="4"
                placeholder="请输入数据说明（支持HTML）"
              />
            </el-form-item>

            <el-form-item label="评测标准">
              <el-input
                v-model="form.evaluationStandard"
                type="textarea"
                :rows="4"
                placeholder="请输入评测标准（支持HTML）"
              />
            </el-form-item>

            <el-form-item label="提交要求">
              <el-input
                v-model="form.submissionRequirement"
                type="textarea"
                :rows="4"
                placeholder="请输入提交要求（支持HTML）"
              />
            </el-form-item>
          </el-card>

          <!-- 奖金设置 -->
          <el-card class="form-section" shadow="never">
            <div slot="header">奖金设置</div>

            <el-form-item label="总奖金池（元）" prop="totalPrize">
              <el-input-number
                v-model="totalPrizeYuan"
                :min="0"
                :precision="2"
                placeholder="请输入总奖金池"
              />
            </el-form-item>

            <el-form-item label="奖金配置">
              <div class="prize-config">
                <div
                  v-for="(prize, index) in prizeList"
                  :key="index"
                  class="prize-item"
                >
                  <span class="prize-label">第 {{ prize.rank }} 名：</span>
                  <el-input-number
                    v-model="prize.amountYuan"
                    :min="0"
                    :precision="2"
                    placeholder="奖金金额（元）"
                  />
                  <el-button
                    type="danger"
                    icon="el-icon-delete"
                    size="small"
                    @click="removePrize(index)"
                  >
                    删除
                  </el-button>
                </div>
                <el-button
                  type="primary"
                  icon="el-icon-plus"
                  size="small"
                  @click="addPrize"
                >
                  添加奖项
                </el-button>
              </div>
            </el-form-item>
          </el-card>

          <!-- 技术配置 -->
          <el-card class="form-section" shadow="never">
            <div slot="header">技术配置</div>

            <el-form-item label="评测镜像">
              <el-input
                v-model="form.evaluationImage"
                placeholder="例如：registry.cn-hangzhou.aliyuncs.com/repo/eval:v1.0"
              />
            </el-form-item>

            <el-form-item label="数据集路径">
              <el-input
                v-model="form.datasetPath"
                placeholder="例如：datasets/competition-1/train.zip"
              />
            </el-form-item>
          </el-card>

          <!-- 提交按钮 -->
          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="handleSubmit">
              {{ isEdit ? '保存修改' : '创建竞赛' }}
            </el-button>
            <el-button @click="$router.back()">取消</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { getCompetitionById, createCompetition, updateCompetition } from '@/api/competition'

export default {
  name: 'CompetitionForm',
  data() {
    return {
      form: {
        title: '',
        description: '',
        detail: '',
        dataDescription: '',
        evaluationStandard: '',
        submissionRequirement: '',
        prizeConfig: '',
        totalPrize: 0,
        registrationStartTime: '',
        registrationEndTime: '',
        submissionStartTime: '',
        submissionEndTime: '',
        evaluationImage: '',
        datasetPath: '',
        status: 0,
        creatorId: null,
        coverImage: '',
        detailImage: ''
      },
      totalPrizeYuan: 0,
      prizeList: [
        { rank: 1, amountYuan: 0 },
        { rank: 2, amountYuan: 0 },
        { rank: 3, amountYuan: 0 }
      ],
      coverImagePreview: '',
      detailImagePreview: '',
      rules: {
        title: [
          { required: true, message: '请输入竞赛标题', trigger: 'blur' }
        ],
        description: [
          { required: true, message: '请输入竞赛简介', trigger: 'blur' }
        ],
        registrationStartTime: [
          { required: true, message: '请选择报名开始时间', trigger: 'change' }
        ],
        registrationEndTime: [
          { required: true, message: '请选择报名结束时间', trigger: 'change' }
        ],
        submissionStartTime: [
          { required: true, message: '请选择提交开始时间', trigger: 'change' }
        ],
        submissionEndTime: [
          { required: true, message: '请选择提交结束时间', trigger: 'change' }
        ],
        status: [
          { required: true, message: '请选择竞赛状态', trigger: 'change' }
        ]
      },
      submitting: false
    }
  },
  computed: {
    ...mapGetters('user', ['userId']),
    isEdit() {
      return !!this.$route.params.id
    }
  },
  created() {
    if (this.isEdit) {
      this.loadCompetition()
    }
  },
  methods: {
    async loadCompetition() {
      try {
        const res = await getCompetitionById(this.$route.params.id)
        if (res.code === 200) {
          const data = res.data
          this.form = {
            ...data,
            registrationStartTime: this.formatDateTime(data.registrationStartTime),
            registrationEndTime: this.formatDateTime(data.registrationEndTime),
            submissionStartTime: this.formatDateTime(data.submissionStartTime),
            submissionEndTime: this.formatDateTime(data.submissionEndTime)
          }

          // 转换奖金为元
          this.totalPrizeYuan = (data.totalPrize || 0) / 100

          // 解析奖金配置
          if (data.prizeConfig) {
            try {
              const config = JSON.parse(data.prizeConfig)
              this.prizeList = config.map(item => ({
                rank: item.rank,
                amountYuan: (item.amount || 0) / 100
              }))
            } catch (e) {
              console.error('解析奖金配置失败:', e)
            }
          }

          // 加载图片预览
          this.loadDefaultImages()
        }
      } catch (error) {
        this.$message.error('加载竞赛信息失败')
      }
    },

    addPrize() {
      const nextRank = this.prizeList.length > 0
        ? Math.max(...this.prizeList.map(p => p.rank)) + 1
        : 1
      this.prizeList.push({ rank: nextRank, amountYuan: 0 })
    },

    removePrize(index) {
      this.prizeList.splice(index, 1)
    },

    handleSubmit() {
      this.$refs.competitionForm.validate(async (valid) => {
        if (!valid) return

        this.submitting = true
        try {
          // 转换奖金为分
          const prizeConfig = this.prizeList.map(item => ({
            rank: item.rank,
            amount: Math.round(item.amountYuan * 100)
          }))

          const data = {
            ...this.form,
            totalPrize: Math.round(this.totalPrizeYuan * 100),
            prizeConfig: JSON.stringify(prizeConfig),
            creatorId: this.userId
          }

          if (this.isEdit) {
            await updateCompetition(this.$route.params.id, data)
            this.$message.success('更新成功！')
          } else {
            await createCompetition(data)
            this.$message.success('创建成功！')
          }

          this.$router.push('/admin/competitions')
        } catch (error) {
          this.$message.error(error.message || '操作失败')
        } finally {
          this.submitting = false
        }
      })
    },

    formatDateTime(dateStr) {
      if (!dateStr) return ''
      // 确保格式为 yyyy-MM-dd HH:mm:ss
      const date = new Date(dateStr)
      const year = date.getFullYear()
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      const hours = String(date.getHours()).padStart(2, '0')
      const minutes = String(date.getMinutes()).padStart(2, '0')
      const seconds = String(date.getSeconds()).padStart(2, '0')
      return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
    },

    beforeImageUpload(file, type) {
      // 验证文件类型
      const isImage = file.type.startsWith('image/')
      if (!isImage) {
        this.$message.error('只能上传图片文件！')
        return false
      }

      // 验证文件大小（5MB）
      const isLt5M = file.size / 1024 / 1024 < 5
      if (!isLt5M) {
        this.$message.error('图片大小不能超过 5MB！')
        return false
      }

      // 读取图片并转换为base64
      const reader = new FileReader()
      reader.onload = (e) => {
        const base64 = e.target.result
        if (type === 'cover') {
          this.coverImagePreview = base64
          this.form.coverImage = base64
        } else if (type === 'detail') {
          this.detailImagePreview = base64
          this.form.detailImage = base64
        }
      }
      reader.readAsDataURL(file)

      return false // 阻止自动上传
    },

    resetCoverImage() {
      this.coverImagePreview = ''
      this.form.coverImage = ''
    },

    resetDetailImage() {
      this.detailImagePreview = ''
      this.form.detailImage = ''
    },

    loadDefaultImages() {
      // 如果没有图片，加载默认图片
      if (!this.form.coverImage && !this.isEdit) {
        this.coverImagePreview = require('@/images/11.jpeg')
      } else if (this.form.coverImage) {
        this.coverImagePreview = this.form.coverImage
      }

      if (!this.form.detailImage && !this.isEdit) {
        this.detailImagePreview = require('@/images/22.jpg')
      } else if (this.form.detailImage) {
        this.detailImagePreview = this.form.detailImage
      }
    }
  },
  mounted() {
    this.loadDefaultImages()
  }
}
</script>

<style lang="scss" scoped>
.competition-form-page {
  min-height: calc(100vh - 64px);
  padding: 40px 0;
  background: #f5f7fa;
}

.page-header {
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

.form-container {
  max-width: 900px;
}

.form-section {
  margin-bottom: 24px;

  ::v-deep .el-card__header {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }
}

.prize-config {
  .prize-item {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 12px;

    .prize-label {
      min-width: 80px;
      font-weight: 600;
      color: #606266;
    }
  }
}

.image-upload-container {
  .image-uploader {
    display: inline-block;
    border: 2px dashed #d9d9d9;
    border-radius: 8px;
    cursor: pointer;
    overflow: hidden;
    transition: all 0.3s;

    &:hover {
      border-color: #5b4cfa;
    }

    ::v-deep .el-upload {
      width: 360px;
      height: 180px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }

  .image-preview {
    width: 360px;
    height: 180px;
    object-fit: cover;
    display: block;
  }

  .image-uploader-icon {
    font-size: 48px;
    color: #8c939d;
  }

  .upload-tip {
    margin-top: 8px;
    font-size: 13px;
    color: #909399;
  }
}
</style>
