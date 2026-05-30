<template>
  <div class="content-share-page">
    <div class="content-share-container">
      <!-- 全局滑块验证码（用于发布内容 / 置顶前的人机验证） -->
      <SliderCaptcha
        ref="sliderCaptcha"
        @success="onCaptchaSuccess"
        @cancel="onCaptchaCancel"
      />
      <section class="share-hero surface-card">
        <div class="hero-grid">
          <div class="hero-left">
            <p class="hero-eyebrow">Web3 Gallery · 链上确权</p>
            <h1>内容分享画廊</h1>
            <p class="hero-lead">
              上传图片或视频，使用您的钱包签名确认投稿并进入批次上链流程，证明原创权益并获得 WEE 代币奖励。同时系统会自动在 FISCO 企业链备份。
            </p>
            <div class="hero-actions">
              <el-button
                type="primary"
                icon="el-icon-upload"
                size="medium"
                @click="openDialog"
                :disabled="!isLoggedIn"
              >
                {{ isLoggedIn ? '发布内容' : '登录后发布' }}
              </el-button>
              <el-button plain size="medium" icon="el-icon-refresh" @click="fetchShares">
                刷新列表
              </el-button>
            </div>
          </div>
          <div class="hero-right">
            <div class="hero-metrics">
              <div class="metric-card">
                <p class="metric-label">作品总数</p>
                <p class="metric-value">{{ shareStats.total }}</p>
                <span class="metric-hint">其中图片 {{ shareStats.imageCount }} 个</span>
              </div>
              <div class="metric-card">
                <p class="metric-label">双链确权</p>
                <p class="metric-value">{{ shareStats.onChainCount }}</p>
                <span class="metric-hint">FISCO + Rollup</span>
              </div>
              <div class="metric-card">
                <p class="metric-label">待处理</p>
                <p class="metric-value">{{ shareStats.pendingCount }}</p>
                <span class="metric-hint">排队中 / 异常</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="share-body surface-card">
        <!-- 媒体类型筛选导航 -->
        <div class="share-filter-tabs">
          <el-radio-group v-model="filterType" size="small">
            <el-radio-button label="ALL">全部</el-radio-button>
            <el-radio-button label="IMAGE">图片</el-radio-button>
            <el-radio-button label="VIDEO">视频</el-radio-button>
            <el-radio-button label="WORLD_MODEL">世界模型</el-radio-button>
          </el-radio-group>
        </div>

        <div
          v-if="pinnedShares.length"
          class="pinned-wrapper"
        >
          <div class="pinned-header">
            <span class="pinned-title">置顶内容</span>
            <span class="pinned-count">{{ pinnedShares.length }} 条</span>
          </div>
          <el-row :gutter="24">
            <el-col
              v-for="share in pinnedShares"
              :key="'pinned-' + share.id"
              :xs="24" :sm="12" :md="8" :lg="6"
            >
              <div class="share-card pinned" @click="openDetail(share)">
                <div class="pin-badge">
                  <i class="el-icon-top"></i>
                  置顶中
                </div>
                <div class="media-wrapper" :class="share.mediaType === 'VIDEO' ? 'video' : 'image'">
                <img
                  v-if="share.mediaType === 'IMAGE'"
                  :src="share.mediaUrl"
                  :alt="share.title"
                  loading="eager"
                  style="image-rendering: -webkit-optimize-contrast; image-rendering: crisp-edges;"
                  @load="onImageLoad"
                  @error="onImageError"
                />
                <video
                  v-else
                    :src="share.mediaUrl"
                    controls
                    preload="metadata"
                    playsinline
                    webkit-playsinline
                    :type="getVideoType(share)"
                    @error="handleVideoError"
                    @loadeddata="handleVideoLoaded"
                    ref="'video-' + share.id"
                    :_share="share"
                  >
                    您的浏览器不支持此视频格式，请下载观看或使用 MP4 格式
                  </video>
                  <div v-if="ipfsCid(share)" class="ipfs-cid-overlay">
                    <a
                      :href="ipfsCidUrl(ipfsCid(share))"
                      target="_blank"
                      rel="noopener"
                      @click.stop
                    >
                      IPFS CID: {{ ipfsCid(share) }}
                    </a>
                  </div>
                </div>
                <div class="card-body">
                  <h3 class="title">{{ share.title }}</h3>
                  <p class="desc">{{ share.description || '暂无描述' }}</p>

                  <div class="content-stats">
                    <div class="stat-item" v-if="share.likeCount">
                      <i class="el-icon-thumb"></i>
                      {{ share.likeCount }}
                    </div>
                    <div class="stat-item" v-if="share.totalTips">
                      <i class="el-icon-coin"></i>
                      {{ share.totalTips }} WEE
                    </div>
                  </div>

                  <div class="card-actions">
                    <el-button-group size="mini">
                      <el-button
                        type="primary"
                        icon="el-icon-view"
                        @click.stop="openDetail(share)"
                        title="查看详情"
                      >
                        详情
                      </el-button>
                      <el-button
                        v-if="share.userId !== userId"
                        type="warning"
                        icon="el-icon-coin"
                        @click.stop="openTipDialog(share)"
                        title="打赏"
                        :disabled="!isLoggedIn"
                      >
                        打赏
                      </el-button>
                      <el-button
                        type="success"
                        icon="el-icon-top"
                        disabled
                        title="置顶中"
                      >
                        已置顶
                      </el-button>
                      </el-button-group>
                  </div>
                </div>
              </div>
            </el-col>
          </el-row>
          <el-divider />
        </div>

        <!-- 骨架屏加载效果 -->
        <el-row v-if="loading" :gutter="24">
          <el-col
            v-for="i in 8"
            :key="'skeleton-' + i"
            :xs="24" :sm="12" :md="8" :lg="6"
          >
            <div class="share-card skeleton-card">
              <el-skeleton :rows="0" animated>
                <template slot="template">
                  <div class="skeleton-media">
                    <el-skeleton-item variant="image" style="width: 100%; height: 200px; border-radius: 8px;" />
                  </div>
                  <div style="padding: 14px;">
                    <el-skeleton-item variant="h3" style="width: 80%; margin-bottom: 12px;" />
                    <el-skeleton-item variant="text" style="width: 60%; margin-bottom: 8px;" />
                    <el-skeleton-item variant="text" style="width: 40%;" />
                  </div>
                </template>
              </el-skeleton>
            </div>
          </el-col>
        </el-row>

        <el-empty
          v-else-if="!loading && visibleCount === 0"
          description="还没有内容，快来发布首条分享吧"
        >
          <el-button v-if="isLoggedIn" type="primary" @click="openDialog">立即发布</el-button>
        </el-empty>

        <el-row v-else-if="regularShares.length > 0" :gutter="24">
          <el-col
            v-for="share in regularShares"
            :key="share.id"
            :xs="24" :sm="12" :md="8" :lg="6"
          >
            <div class="share-card" @click="openDetail(share)">
              <!-- 置顶标识 -->
              <div v-if="share.pinned" class="pin-badge">
                <i class="el-icon-top"></i>
                置顶中
              </div>

              <div class="media-wrapper" :class="share.mediaType === 'VIDEO' ? 'video' : 'image'">
                <img
                  v-if="share.mediaType === 'IMAGE'"
                  :src="share.mediaUrl"
                  :alt="share.title"
                  loading="eager"
                  style="image-rendering: -webkit-optimize-contrast; image-rendering: crisp-edges;"
                  @load="onImageLoad"
                  @error="onImageError"
                />
                <video
                  v-else
                  :src="share.mediaUrl"
                  controls
                  preload="metadata"
                  playsinline
                  webkit-playsinline
                  :type="getVideoType(share)"
                  @error="handleVideoError"
                  @loadeddata="handleVideoLoaded"
                  ref="'video-' + share.id"
                  :_share="share"
                >
                  您的浏览器不支持此视频格式，请下载观看或使用 MP4 格式
                </video>
                <div v-if="ipfsCid(share)" class="ipfs-cid-overlay">
                  <a
                    :href="ipfsCidUrl(ipfsCid(share))"
                    target="_blank"
                    rel="noopener"
                    @click.stop
                  >
                    IPFS CID: {{ ipfsCid(share) }}
                  </a>
                </div>
              </div>
              <div class="card-body">
                <h3 class="title">{{ share.title }}</h3>
                <p class="desc">{{ share.description || '暂无描述' }}</p>

                <!-- 统计信息 -->
                <div class="content-stats">
                  <div class="stat-item" v-if="share.likeCount">
                    <i class="el-icon-thumb"></i>
                    {{ share.likeCount }}
                  </div>
                  <div class="stat-item" v-if="share.totalTips">
                    <i class="el-icon-coin"></i>
                    {{ share.totalTips }} WEE
                  </div>
                </div>

                <!-- 操作按钮 -->
                <div class="card-actions">
                  <el-button-group size="mini">
                    <el-button
                      type="primary"
                      icon="el-icon-chat-dot-round"
                      @click.stop="openCommentDialog(share)"
                      title="评论"
                    >
                      评论
                    </el-button>
                    <el-button
                      v-if="share.userId !== userId"
                      type="warning"
                      icon="el-icon-coin"
                      @click.stop="openTipDialog(share)"
                      title="打赏"
                      :disabled="!isLoggedIn"
                    >
                      打赏
                    </el-button>
                    <el-button
                      type="success"
                      icon="el-icon-top"
                      @click.stop="handlePurchasePin(share)"
                      title="置顶24小时 (50 WEE)"
                      :loading="pinning"
                      :disabled="!isLoggedIn || share.pinned"
                    >
                      {{ share.pinned ? '已置顶' : '置顶' }}
                    </el-button>
                    </el-button-group>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>

        <el-empty
          v-else-if="!loading && pinnedShares.length > 0"
          description="其他内容暂未发布，可浏览上方置顶内容"
        />

        <div class="pagination" v-if="total > pagination.size">
          <el-pagination
            layout="total, prev, pager, next"
            :total="total"
            :current-page.sync="pagination.current"
            :page-size="pagination.size"
            @current-change="fetchShares"
          />
        </div>
      </section>
    </div>

    <!-- 发布内容 -->
    <el-dialog
      title="发布内容"
      :visible.sync="dialogVisible"
      width="640px"
      @closed="resetForm"
    >
      <el-form
        ref="shareFormRef"
        :model="shareForm"
        :rules="shareRules"
        label-width="100px"
      >
        <el-form-item label="标题" prop="title">
          <el-input v-model="shareForm.title" maxlength="120" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="shareForm.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="媒体类型" prop="mediaType">
          <el-radio-group v-model="shareForm.mediaType">
            <el-radio-button label="IMAGE">图片</el-radio-button>
            <el-radio-button label="VIDEO">视频</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="选择文件" prop="file">
          <el-upload
            class="upload-block"
            action="#"
            :auto-upload="false"
            :limit="1"
            :file-list="uploadFileList"
            :on-change="handleFileChange"
            :accept="shareForm.mediaType === 'IMAGE' ? 'image/*' : 'video/*'"
          >
            <el-button slot="trigger" type="primary">选择{{ shareForm.mediaType === 'IMAGE' ? '图片' : '视频' }}</el-button>
            <div slot="tip" class="el-upload__tip">
              <div v-if="shareForm.mediaType === 'IMAGE'">
                建议 20MB 内的图片格式：PNG, JPG, GIF
              </div>
              <div v-else>
                <p>建议不超过 200MB 的视频，推荐格式：</p>
                <p style="font-size: 12px; color: #67c23a; margin: 4px 0;">
                  ✅ MP4 (最佳兼容性) | WebM | OGG
                </p>
                <p style="font-size: 12px; color: #e6a23c; margin: 4px 0;">
                  ⚠️ WMV/AVI/FLV (部分浏览器不支持)
                </p>
              </div>
            </div>
          </el-upload>
          <div v-if="shareForm.fileHash" class="hash-preview">
            <span>SHA-256:</span>
            <code>{{ shareForm.fileHash }}</code>
          </div>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="submitting"
          @click="handleSubmitShare"
        >
          {{ submitting ? '发布中...' : '发布并进入批次' }}
        </el-button>
        <div style="font-size: 12px; color: #909399; margin-top: 8px;">
          发布时需要使用 MetaMask 签名确认，签名不会产生链上费用
        </div>
      </span>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer
      title="内容详情"
      :visible.sync="detailVisible"
      size="40%"
    >
      <div v-if="currentShare" class="detail-panel">
        <div class="detail-media">
          <img
            v-if="currentShare.mediaType === 'IMAGE'"
            :src="currentShare.mediaUrl"
            :alt="currentShare.title"
          />
          <video
            v-else
            controls
            preload="metadata"
            playsinline
            webkit-playsinline
            :type="getVideoType(currentShare)"
            :src="currentShare.mediaUrl"
            @error="handleVideoError"
            @loadeddata="handleVideoLoaded"
            style="width: 100%; max-height: 400px;"
            :_share="currentShare"
          >
            您的浏览器不支持此视频格式，请下载观看或使用 MP4 格式
          </video>
          <div v-if="ipfsCid(currentShare)" class="ipfs-cid-overlay">
            <a
              :href="ipfsCidUrl(ipfsCid(currentShare))"
              target="_blank"
              rel="noopener"
              @click.stop
            >
              IPFS CID: {{ ipfsCid(currentShare) }}
            </a>
          </div>
        </div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="标题">{{ currentShare.title }}</el-descriptions-item>
          <el-descriptions-item label="描述">{{ currentShare.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文件哈希">
            <div class="hash-row">
              <span>{{ currentShare.fileHash || '-' }}</span>
              <el-button
                v-if="currentShare.fileHash"
                type="text"
                size="mini"
                @click="copyHash(currentShare.fileHash)"
              >
                复制
              </el-button>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="IPFS CID">
            <a
              v-if="ipfsCid(currentShare)"
              :href="ipfsCidUrl(ipfsCid(currentShare))"
              target="_blank"
              rel="noopener"
            >
              {{ ipfsCid(currentShare) }}
            </a>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ currentShare.createdAt }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>

    <!-- 打赏对话框 -->
    <el-dialog
      title="打赏创作者"
      :visible.sync="tipDialogVisible"
      width="450px"
      @closed="tipForm = { amount: 1, customAmount: '' }"
    >
      <div v-if="currentShare" class="tip-content">
        <div class="tip-info">
          <h4>{{ currentShare.title }}</h4>
          <p>向创作者打赏 WEE 代币，支持优秀内容创作</p>
        </div>

        <el-form :model="tipForm" label-width="80px">
          <el-form-item label="打赏金额">
            <el-radio-group v-model="tipForm.amount" size="medium">
              <el-radio-button :label="1">1 WEE</el-radio-button>
              <el-radio-button :label="5">5 WEE</el-radio-button>
              <el-radio-button :label="10">10 WEE</el-radio-button>
              <el-radio-button :label="0">自定义</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="tipForm.amount === 0" label="自定义金额">
            <el-input-number
              v-model="tipForm.customAmount"
              :min="0.1"
              :step="0.1"
              :precision="1"
              placeholder="输入打赏金额"
              style="width: 100%"
            />
            <span class="input-suffix">WEE</span>
          </el-form-item>
        </el-form>
      </div>

      <span slot="footer">
        <el-button @click="tipDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="tipping"
          @click="submitTip"
        >
          {{ tipping ? '打赏中...' : '确认打赏' }}
        </el-button>
      </span>
    </el-dialog>

    <!-- 评论对话框 -->
    <el-dialog
      title="内容评论"
      :visible.sync="commentDialogVisible"
      width="800px"
      class="comment-dialog"
    >
      <ContentComments v-if="currentShare" :share="currentShare" />
      <span slot="footer">
        <el-button @click="commentDialogVisible = false">关闭</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import axios from 'axios'
  import { calculateFileHash } from '@/utils/hash'
  import {
    getContentShareUploadUrl,
    uploadContentShareFile,
    createContentShare,
    fetchContentShares,
    submitContentShareConsent as submitContentShareConsentApi
  } from '@/api/contentShare'
import contentTipApi from '@/api/contentTip'
import contentPinApi from '@/api/contentPin'
import web3Service from '@/utils/web3'
import ContentComments from '@/components/ContentComments.vue'
import SliderCaptcha from '@/components/SliderCaptcha.vue'
export default {
  name: 'ContentShare',
  components: {
    ContentComments,
    SliderCaptcha
  },
  data() {
    return {
      loading: false,
      shares: [],
      total: 0,
      pagination: {
        current: 1,
        size: 12
      },
      // 媒体类型筛选：ALL / IMAGE / VIDEO / WORLD_MODEL
      filterType: 'ALL',
      dialogVisible: false,
      detailVisible: false,
      shareForm: {
        title: '',
        description: '',
        mediaType: 'IMAGE',
        file: null,
        fileHash: ''
      },
      shareRules: {
        title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
        mediaType: [{ required: true, message: '请选择媒体类型', trigger: 'change' }],
        file: [{ required: true, message: '请上传文件', trigger: 'change' }]
      },
      uploadFileList: [],
      submitting: false,
      uploadingHash: false,
      // 图片加载状态
      loadingImages: new Set(),
      // 智能预加载配置
      preloadQueue: [],
      preloadingImages: new Set(),
      maxConcurrentLoads: 3, // 最多同时加载3张图片
      currentShare: null,
      // 打赏相关
      tipDialogVisible: false,
      tipForm: {
        amount: 1,
        customAmount: ''
      },
      tipping: false,
      // 置顶相关
      pinning: false,
      // 评论相关
      commentDialogVisible: false,
      commentForm: {
        content: ''
      },
      commenting: false,
      relatedPosts: [],
      comments: [],
      userWallet: null,
      // 验证码相关
      captchaToken: null,
      pendingAction: null, // 'submitShare' | 'purchasePin'
      pendingShare: null
    }
  },
  computed: {
    ...mapGetters('user', ['isLoggedIn', 'userId']),
    isAdmin() {
      return this.$store.getters['user/role'] === 'ADMIN'
    },
    // 按导航筛选后的内容
    filteredShares() {
      return this.shares.filter(item => {
        switch (this.filterType) {
          case 'IMAGE':
            return item.mediaType === 'IMAGE'
          case 'VIDEO':
            return item.mediaType === 'VIDEO'
          case 'WORLD_MODEL':
            // 预留世界模型分类，目前还没有上传
            return item.mediaType === 'WORLD_MODEL' || item.category === 'WORLD_MODEL'
          default:
            return true
        }
      })
    },
    pinnedShares() {
      return this.filteredShares.filter(item => item.pinned)
    },
    regularShares() {
      return this.filteredShares.filter(item => !item.pinned)
    },
    visibleCount() {
      return this.pinnedShares.length + this.regularShares.length
    },
    shareStats() {
      const total = this.shares.length
      const imageCount = this.shares.filter(item => item.mediaType === 'IMAGE').length
      const onChainCount = this.shares.filter(item => item.fiscoStatus === 2).length
      const pendingCount = this.shares.filter(item => item.fiscoStatus !== 2).length
      return {
        total,
        imageCount,
        onChainCount,
        pendingCount
      }
    }
  },
  created() {
    this.fetchShares()
  },
  methods: {
    async fetchShares() {
      this.loading = true
      try {
        const { data } = await fetchContentShares({
          current: this.pagination.current,
          size: this.pagination.size
        })
        this.shares = data.records || []
        this.total = data.total || 0

        // 启动智能预加载（延迟执行，避免阻塞渲染）
        if (data.records && data.records.length > 0) {
          this.$nextTick(() => {
            setTimeout(() => {
              this.initImagePreload()
            }, 1500) // 延迟1.5秒启动预加载
          })
        }
      } catch (error) {
        this.$message.error(error.message || '加载分享列表失败')
      } finally {
        this.loading = false
      }
    },
    openDialog() {
      if (!this.isLoggedIn) {
        this.$message.warning('请先登录')
        this.$router.push('/login')
        return
      }
      this.dialogVisible = true
    },
    openDetail(share) {
      this.$router.push({
        name: 'ContentShareDetail',
        params: { id: share.id }
      })
    },
    resetForm() {
      this.$refs.shareFormRef && this.$refs.shareFormRef.resetFields()
      this.shareForm.file = null
      this.shareForm.fileHash = ''
      this.uploadFileList = []
      this.uploadingHash = false
    },
    async handleFileChange(file) {
      this.shareForm.file = file.raw
      this.uploadFileList = [file]
      this.shareForm.fileHash = ''
      if (!file.raw) {
        this.$message.error('文件对象无效')
        return
      }
      this.uploadingHash = true
      try {
        console.log('开始计算文件哈希:', file.raw.name, '大小:', file.raw.size)
        const hash = await calculateFileHash(file.raw)
        this.shareForm.fileHash = hash
        console.log('前端哈希计算成功:', hash)
        this.$message.success('文件哈希计算成功')
      } catch (error) {
        console.error('前端哈希计算失败:', error)
        this.$message.error(`前端哈希计算失败: ${error.message}，将由后端自动计算`)
      } finally {
        this.uploadingHash = false
      }
    },

    // 智能图片预加载系统
    initImagePreload() {
      // 延迟启动预加载，避免阻塞初始渲染
      setTimeout(() => {
        this.startSmartPreload()
      }, 2000)
    },

    startSmartPreload() {
      // 只预加载前10张图片的缩略图
      const priorityImages = this.shares.slice(0, 10)

      priorityImages.forEach((share, index) => {
        if (share.mediaType === 'IMAGE' && share.mediaUrl) {
          // 延迟预加载，避免同时发起请求
          setTimeout(() => {
            this.preloadImage(share.mediaUrl, share.id)
          }, index * 500) // 每张图片间隔500ms
        }
      })
    },

    preloadImage(url, shareId) {
      // 检查是否已经在加载队列中
      if (this.preloadingImages.has(shareId) || this.loadingImages.has(shareId)) {
        return
      }

      // 检查并发限制
      if (this.preloadingImages.size >= this.maxConcurrentLoads) {
        this.preloadQueue.push({ url, shareId })
        return
      }

      this.preloadingImages.add(shareId)
      console.log('开始预加载图片:', shareId)

      const img = new Image()

      // 设置加载超时
      const timeout = setTimeout(() => {
        console.warn('图片预加载超时:', shareId)
        this.preloadingImages.delete(shareId)
        this.processNextInQueue()
      }, 10000) // 10秒超时

      img.onload = () => {
        clearTimeout(timeout)
        this.preloadingImages.delete(shareId)
        console.log('图片预加载完成:', shareId)
        this.processNextInQueue()
      }

      img.onerror = () => {
        clearTimeout(timeout)
        this.preloadingImages.delete(shareId)
        console.error('图片预加载失败:', shareId)
        this.processNextInQueue()
      }

      // 开始加载
      img.src = url
    },

    processNextInQueue() {
      if (this.preloadQueue.length > 0 && this.preloadingImages.size < this.maxConcurrentLoads) {
        const next = this.preloadQueue.shift()
        if (next) {
          this.preloadImage(next.url, next.shareId)
        }
      }
    },

    // 图片加载事件
    onImageLoad(event) {
      const img = event.target
      const shareId = img.getAttribute('data-share-id')
      if (shareId) {
        this.loadingImages.delete(shareId)
      }
      console.log('图片加载完成:', img.src)
    },

    onImageError(event) {
      const img = event.target
      console.error('图片加载失败:', img.src)
      img.style.display = 'none'
      // 可以显示错误占位符
    },
    // 发布按钮入口：先做人机验证，再真正发布
    async handleSubmitShare() {
      if (!this.isLoggedIn) {
        this.$message.warning('请先登录')
        return
      }
      // 不在这里做复杂校验，交给 submitShare 再做一次完整校验
      this.pendingAction = 'submitShare'
      this.$refs.sliderCaptcha && this.$refs.sliderCaptcha.show()
    },
    async submitShare() {
      if (!this.isLoggedIn) {
        this.$message.warning('请先登录')
        return
      }
      this.$refs.shareFormRef.validate(async (valid) => {
        if (!valid) {
          return
        }
        if (!this.shareForm.file) {
          this.$message.warning('请先选择文件')
          return
        }
          if (!this.shareForm.fileHash) {
            this.$message.warning('前端哈希计算失败，将由后端自动完成')
          }
          this.submitting = true
          try {
            // Step 1: 上传文件（MinIO 预签名直传 / IPFS 直传后端）
            this.$message.info('正在上传文件...')
            const { data: presign } = await getContentShareUploadUrl({
              mediaType: this.shareForm.mediaType,
              fileName: this.shareForm.file.name
            })
            let mediaUrl
            let bucket = presign.bucket
            let objectName = presign.objectName
            let storageProvider = presign.storageProvider
            let cid
            const isAbsoluteUploadUrl = typeof presign.uploadUrl === 'string' && /^https?:\/\//i.test(presign.uploadUrl)
            const uploadUrl = presign.uploadUrl || ''
            const isDirectUpload = presign.uploadMethod === 'direct'
              || !uploadUrl
              || !isAbsoluteUploadUrl
              || uploadUrl.includes('/content-shares/upload')
            if (isDirectUpload) {
              const { data: uploadResp } = await uploadContentShareFile({
                mediaType: this.shareForm.mediaType,
                file: this.shareForm.file
              })
              mediaUrl = uploadResp.mediaUrl
              bucket = uploadResp.bucket
              objectName = uploadResp.objectName
              storageProvider = uploadResp.storageProvider || storageProvider
              cid = uploadResp.cid
            } else {
              await axios.put(presign.uploadUrl, this.shareForm.file, {
                headers: {
                  'Content-Type': this.shareForm.file.type || 'application/octet-stream'
                }
              })
              mediaUrl = presign.uploadUrl.split('?')[0]
            }

            // Step 2: 创建内容记录
            this.$message.info('正在创建内容记录...')
            const payload = {
              userId: this.userId,
              title: this.shareForm.title,
              description: this.shareForm.description,
              mediaType: this.shareForm.mediaType,
              mediaUrl,
              hashAlgorithm: 'SHA256',
              fileHash: this.shareForm.fileHash || undefined,
              metadata: JSON.stringify({
                objectName,
                bucket,
                storageProvider: storageProvider || undefined,
                cid: cid || undefined,
                originalName: this.shareForm.file.name,
                size: this.shareForm.file.size,
                contentType: this.shareForm.file.type
              })
            }
          const { data: createResp } = await createContentShare(payload)
          const shareId = createResp.id
          
          this.$message.success('内容已提交，等待管理员审核（审核通过后会在画廊展示）')

          // Step 3: 用户签名确认（不直接上链）
          this.$message.info('请签名确认本次提交，签名不会产生链上费用')

          try {
            await this.submitContentShareConsent(shareId)
            this.$message.success('✅ 已提交签名，等待批次上链后系统自动发放奖励')
          } catch (consentError) {
            console.error('提交签名失败:', consentError)
            if (consentError.message?.includes('用户取消')) {
              this.$message.warning('您取消了签名，内容已创建但未进入奖励批次')
            } else {
              this.$message.warning('签名提交失败，内容已创建但未进入奖励批次')
            }
          }
          
          this.dialogVisible = false
          this.fetchShares()
          this.$router.push({ name: 'ContentShareDetail', params: { id: shareId } })
          
        } catch (error) {
          this.$message.error(error.message || '发布失败，请重试')
        } finally {
          this.submitting = false
        }
      })
    },
    ipfsCidUrl(cid) {
      if (!cid) return '#'
      const gateway = (process.env.VUE_APP_IPFS_PUBLIC_GATEWAY || 'https://ipfs.4everland.io').replace(/\/+$/, '')
      return `${gateway}/ipfs/${cid}`
    },
    ipfsCid(share) {
      if (!share) return ''
      const url = share.mediaUrl || ''
      const marker = '/ipfs/'
      if (typeof url === 'string' && url.includes(marker)) {
        const after = url.substring(url.lastIndexOf(marker) + marker.length)
        const q = after.indexOf('?')
        const cid = (q >= 0 ? after.substring(0, q) : after).split('/')[0]
        return cid || ''
      }
      if (share.metadata) {
        try {
          const meta = JSON.parse(share.metadata)
          return meta && meta.cid ? String(meta.cid) : ''
        } catch (e) {
          return ''
        }
      }
      return ''
    },
    copyHash(hash) {
      navigator.clipboard.writeText(hash).then(() => {
        this.$message.success('哈希已复制')
      }).catch(() => {
        this.$message.error('复制失败')
      })
    },
    isOwnContent(share) {
      return share && share.userId === this.userId
    },
    async submitContentShareConsent(shareId) {
      await web3Service.connectMetaMask()
      const userAddress = web3Service.currentAddress
      if (!userAddress) {
        throw new Error('钱包未连接')
      }
      const fileHash = this.shareForm.fileHash || ''
      const message = `WEE_CONTENT_SHARE:${shareId}:${fileHash}:${userAddress.toLowerCase()}`
      const signature = await web3Service.signMessage(message)
      await submitContentShareConsentApi(shareId, {
        userAddress,
        signature
      })
      return signature
    },
    getVideoType(share) {
      // 从metadata中获取文件类型
      if (share.metadata) {
        try {
          const metadata = JSON.parse(share.metadata)
          if (metadata.contentType) {
            // 但是要验证是否与实际文件格式匹配
            const contentType = metadata.contentType
            const fileName = share.mediaUrl || ''

            // 如果metadata说是mp4，但文件名显示可能是其他格式，优先信任文件名
            if (contentType === 'video/mp4' && !fileName.includes('.mp4')) {
              return 'video/x-ms-asf' // 可能是伪装的格式
            }

            return contentType
          }
        } catch (e) {
          console.warn('Failed to parse metadata:', e)
        }
      }

      // 根据文件扩展名判断
      const fileName = share.mediaUrl || ''
      if (fileName.includes('.mp4')) return 'video/mp4'
      if (fileName.includes('.webm')) return 'video/webm'
      if (fileName.includes('.ogg') || fileName.includes('.ogv')) return 'video/ogg'
      if (fileName.includes('.mov')) return 'video/quicktime'
      if (fileName.includes('.avi')) return 'video/x-msvideo'
      if (fileName.includes('.wmv')) return 'video/x-ms-wmv'
      if (fileName.includes('.asf')) return 'video/x-ms-asf'
      if (fileName.includes('.flv')) return 'video/x-flv'
      if (fileName.includes('.m4v')) return 'video/x-m4v'

      // 默认返回mp4
      return 'video/mp4'
    },
    handleVideoError(event) {
      console.warn('Video playback error:', event)
      const video = event.target
      const fileName = video.src || ''
      const currentShare = event.target._share || {}

      // 检查是否是WMV等不支持的格式
      if (fileName.includes('.wmv') || fileName.includes('.avi') || fileName.includes('.flv') || fileName.includes('.asf')) {
        this.$message.warning('此视频格式 (WMV/ASF/AVI/FLV) 在浏览器中支持度较差，建议转换为 MP4 格式')
      } else {
        // 检查是否是伪装的MP4（实际上是其他格式）
        if (currentShare.metadata) {
          try {
            const metadata = JSON.parse(currentShare.metadata)
            if (metadata.contentType === 'video/mp4' && !fileName.includes('.mp4')) {
              this.$message.warning('检测到文件格式与扩展名不符，这可能是伪装成MP4的其他格式，请使用真正的MP4文件')
              return
            }
          } catch (e) {
            // 忽略解析错误
          }
        }
        this.$message.error('视频加载失败，请检查网络连接或文件完整性')
      }
    },
    handleVideoLoaded(event) {
      console.log('Video loaded successfully:', event.target.src)
    },
  
    // 打赏相关方法
    openTipDialog(share) {
      if (!this.isLoggedIn) {
        this.$message.warning('请先登录')
        return
      }
      // 检查是否是自己的内容
      if (share.userId === this.userId) {
        this.$message.warning('不能给自己的内容打赏哦~')
        return
      }
      this.currentShare = share
      this.tipDialogVisible = true
    },

    async submitTip() {
      if (!this.currentShare || !this.userId) return

      // 再次检查是否是自己的内容（双重保险）
      if (this.currentShare.userId === this.userId) {
        this.$message.warning('不能给自己的内容打赏')
        return
      }

      const amount = this.tipForm.customAmount || this.tipForm.amount
      if (!amount || amount <= 0) {
        this.$message.warning('请输入有效的打赏金额')
        return
      }

      if (!this.currentShare.authorWalletAddress) {
        this.$message.error('创作者未绑定钱包地址，暂无法打赏')
        return
      }

      this.tipping = true
      try {
        await this.ensureWalletConnected()
        const { txHash, blockNumber } = await web3Service.tipContent({
          creatorAddress: this.currentShare.authorWalletAddress,
          amount,
          contentType: 'CONTENT_SHARE',
          contentId: this.currentShare.id
        })
        await contentTipApi.createTip({
          tipperId: this.userId,
          creatorId: this.currentShare.userId,
          contentType: 'CONTENT_SHARE',
          contentId: this.currentShare.id,
          amount: parseFloat(amount),
          txHash,
          blockNumber
        })
        this.$message.success(`打赏成功！已向创作者转账 ${amount} WEE`)
        this.tipDialogVisible = false
        this.tipForm = { amount: 1, customAmount: '' }
        this.fetchShares()
      } catch (error) {
        // 使用友好的错误处理器
        this.$handleTxError(error)
      } finally {
        this.tipping = false
      }
    },

    // 置顶相关方法
    // 点击置顶按钮入口：先做人机验证，再真正调用 purchasePin
    async handlePurchasePin(share) {
      if (!this.isLoggedIn) {
        this.$message.warning('请先登录')
        return
      }
      this.pendingShare = share
      this.pendingAction = 'purchasePin'
      this.$refs.sliderCaptcha && this.$refs.sliderCaptcha.show()
    },

    async purchasePin(share) {
      if (!this.isLoggedIn) {
        this.$message.warning('请先登录')
        return
      }
      // 提示用户需要钱包授权与扣费
      this.pinning = true
      try {
        // 1. 确保钱包已连接（与打赏复用逻辑）
        await this.ensureWalletConnected()

        // 2. 调用链上合约，由用户钱包支付 50 WEE + Gas
        this.$message.info('正在发起置顶交易，请在钱包中确认授权和支付 Gas')
        const { txHash, blockNumber } = await web3Service.purchasePinPost(share.id, '50')

        // 3. 调用后端接口，仅做记录与状态更新（不再在后端发起链上交易）
        await contentPinApi.purchasePin({
          userId: this.userId,
          contentType: 'CONTENT_SHARE',
          contentId: share.id,
          txHash,
          blockNumber
        })

        this.$message.success('置顶成功！内容将在24小时内置顶显示')
        this.fetchShares()
      } catch (error) {
        // 使用友好的错误处理器
        this.$handleTxError(error)
      } finally {
        this.pinning = false
      }
    },

    // 滑块验证码成功回调
    async onCaptchaSuccess(token) {
      this.captchaToken = token
      try {
        if (this.pendingAction === 'submitShare') {
          await this.submitShare()
        } else if (this.pendingAction === 'purchasePin' && this.pendingShare) {
          await this.purchasePin(this.pendingShare)
        }
      } finally {
        this.captchaToken = null
        this.pendingAction = null
        this.pendingShare = null
      }
    },

    // 滑块验证码取消回调
    onCaptchaCancel() {
      this.captchaToken = null
      this.pendingAction = null
      this.pendingShare = null
      this.$message.info('已取消操作')
    },

    // 评论相关方法
    async openCommentDialog(share) {
      this.currentShare = share
      this.commentDialogVisible = true
    },

    async ensureWalletConnected() {
      if (web3Service.isWalletConnected && web3Service.isWalletConnected()) {
        return
      }
      const result = await web3Service.connectMetaMask()
      if (!result.success) {
        throw new Error(result.error || '连接钱包失败')
      }
    },

    // 其余方法保持不变（打赏、置顶、批次签名等）
  }
}
</script>

<style lang="scss" scoped>
.content-share-page {
  background: #eef1f7;
  min-height: 100vh;
  padding: 32px 0 60px;
}

.content-share-container {
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

.share-hero {
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
  gap: 16px;
  flex-wrap: wrap;
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
  }

  .metric-hint {
    font-size: 13px;
    color: rgba(255, 255, 255, 0.75);
  }
}

.share-body {
  min-height: 200px;
}

.share-filter-tabs {
  display: flex;
  justify-content: flex-start;
  margin-bottom: 12px;
}

// 确保栅格行高度一致
.el-row {
  display: flex;
  flex-wrap: wrap;

  .el-col {
    display: flex;
    margin-bottom: 24px;
  }
}

.share-card {
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
  border-radius: 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.04), 
              0 1px 3px rgba(0, 0, 0, 0.02);
  margin-bottom: 24px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  height: 100%;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(0, 0, 0, 0.02);
  position: relative;

  /* 微妙的顶部高光 */
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 2px;
    background: linear-gradient(90deg, 
      #667eea 0%, 
      #764ba2 50%, 
      #f093fb 100%);
    opacity: 0;
    transition: opacity 0.3s ease;
  }

  &:hover {
    transform: translateY(-8px) scale(1.02);
    box-shadow: 0 16px 40px rgba(0, 0, 0, 0.12), 
                0 4px 12px rgba(0, 0, 0, 0.06);
    border-color: rgba(102, 126, 234, 0.2);
    
    &::before {
      opacity: 1;
    }
  }

  /* 活跃状态 */
  &:active {
    transform: translateY(-6px) scale(1.01);
  }

  &.skeleton-card {
    cursor: default;
    
    &:hover {
      transform: none;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
    }
    
    .skeleton-media {
      padding: 12px;
    }
  }

  .media-wrapper {
    width: 100%;
    height: 220px;
    background: linear-gradient(135deg, #f5f7fa 0%, #e8eaf6 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    position: relative;
    overflow: hidden;

    &::after {
      content: '';
      position: absolute;
      inset: 0;
      background: linear-gradient(180deg, 
        transparent 0%, 
        rgba(0, 0, 0, 0.02) 100%);
      pointer-events: none;
    }

    &.video {
      background: linear-gradient(135deg, #1a1a1a 0%, #000 100%);
    }

    img,
    video {
      width: 100%;
      height: 100%;
      object-fit: cover;
      transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    }
  }

  &:hover .media-wrapper {
    img,
    video {
      transform: scale(1.05);
    }
  }

  .card-body {
    padding: 16px;
    flex: 1; /* 占满剩余空间 */
    display: flex;
    flex-direction: column;

    .title {
      margin: 0;
      font-size: 18px;
      color: #303133;
      line-height: 1.4;
      height: 2.8em; /* 固定标题高度，大约2行 */
      overflow: hidden;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
    }

    .desc {
      margin: 8px 0 16px;
      color: #909399;
      height: 40px; /* 固定描述高度 */
      overflow: hidden;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      line-height: 1.4;
      flex: 1; /* 占满剩余空间 */
    }

    .card-actions {
      margin-top: auto;
      text-align: center;
      border-top: 1px solid #f0f0f0;
      padding-top: 8px;
    }
  }
}

.pagination {
  margin-top: 24px;
  text-align: center;
}

.hash-preview {
  margin-top: 12px;
  font-size: 12px;
  color: #606266;

  code {
    display: block;
    margin-top: 4px;
    padding: 8px;
    background: #f5f7fa;
    border-radius: 8px;
    word-break: break-all;
  }
}

.pinned-wrapper {
  margin-bottom: 24px;
  padding-bottom: 12px;
}

.pinned-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.pinned-title {
  font-size: 16px;
  font-weight: 600;
  color: #f56c6c;
}

.pinned-count {
  font-size: 13px;
  color: #909399;
}

.share-card.pinned {
  border: 1px solid rgba(245, 194, 124, 0.6);
  box-shadow: 0 6px 18px rgba(255, 171, 64, 0.25);
}

.ipfs-cid-overlay {
  position: absolute;
  left: 10px;
  bottom: 10px;
  max-width: calc(100% - 20px);
  padding: 6px 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.58);
  backdrop-filter: blur(6px);
  font-size: 12px;
  line-height: 1.2;
  pointer-events: none;
  z-index: 2;

  a {
    display: block;
    color: #fff;
    text-decoration: none;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    pointer-events: auto;
  }

  a:hover {
    text-decoration: underline;
  }
}

.detail-panel {
  padding-right: 12px;

  .detail-media {
    margin-bottom: 16px;
    position: relative;

    img,
    video {
      width: 100%;
      border-radius: 12px;
    }
  }

  .hash-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    word-break: break-all;
  }

  .tx-hash {
    margin-top: 8px;
    font-size: 12px;
    color: #409EFF;
    word-break: break-all;
  }
}

// 新增样式
.pin-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  background: linear-gradient(45deg, #f39c12, #e74c3c);
  color: white;
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: bold;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 4px;
  box-shadow: 0 2px 8px rgba(231, 76, 60, 0.3);

  i {
    font-size: 14px;
  }
}

.content-stats {
  display: flex;
  gap: 16px;
  margin: 12px 0;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;

  .stat-item {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
    color: #666;

    i {
      color: #409EFF;
    }
  }
}

.card-actions {
  margin-top: 12px;

  .el-button-group {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;

    .el-button {
      flex: 1;
      min-width: 60px;
      font-size: 12px;
      padding: 8px 12px;
    }
  }
}

// 打赏对话框样式
.tip-content {
  .tip-info {
    text-align: center;
    margin-bottom: 24px;
    padding: 16px;
    background: #f8f9fa;
    border-radius: 8px;

    h4 {
      margin: 0 0 8px;
      color: #303133;
    }

    p {
      margin: 0;
      color: #666;
      font-size: 14px;
    }
  }

  .input-suffix {
    margin-left: 8px;
    color: #909399;
    font-size: 14px;
  }
}

// 评论对话框样式
.comment-dialog {
  .comment-content {
    .content-preview {
      padding: 16px;
      background: #f8f9fa;
      border-radius: 8px;
      margin-bottom: 20px;

      h4 {
        margin: 0 0 8px;
        color: #303133;
      }

      p {
        margin: 0;
        color: #666;
        font-size: 14px;
      }
    }

    .comment-input-section {
      margin-bottom: 24px;

      .comment-actions {
        margin-top: 12px;
        text-align: right;
      }
    }

    .comments-section {
      h5 {
        margin: 0 0 16px;
        color: #303133;
        font-size: 16px;
      }

      .comment-list {
        max-height: 300px;
        overflow-y: auto;

        .comment-item {
          padding: 12px 0;
          border-bottom: 1px solid #f0f0f0;

          &:last-child {
            border-bottom: none;
          }

          .comment-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 8px;

            .comment-author {
              font-weight: bold;
              color: #409EFF;
            }

            .comment-time {
              font-size: 12px;
              color: #999;
            }
          }

          .comment-body {
            color: #333;
            line-height: 1.5;
          }
        }
      }
    }

    .no-comments {
      text-align: center;
      padding: 40px;
      color: #999;
    }
  }
}

@media (max-width: 768px) {
  .surface-card {
    padding: 24px;
  }

  .hero-left h1 {
    font-size: 28px;
  }

  .card-actions .el-button-group .el-button {
    font-size: 11px;
    padding: 6px 8px;
  }

  .comment-dialog {
    .el-dialog {
      width: 95% !important;
    }
  }
}
</style>
