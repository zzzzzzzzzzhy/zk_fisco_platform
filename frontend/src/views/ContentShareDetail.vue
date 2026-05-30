<template>
  <div class="share-detail-page" v-loading="loading">
    <div v-if="share" class="share-detail-container">
      <div class="detail-main surface-card">
        <div class="player-wrapper">
          <VideoPlayer
            v-if="share.mediaType === 'VIDEO'"
            :src="share.mediaUrl"
          />
          <img
            v-else
            :src="displayMediaUrl(share)"
            :alt="share.title"
          />
          <div v-if="ipfsCid(share)" class="ipfs-cid-overlay">
            <a
              :href="ipfsCidUrl(ipfsCid(share))"
              target="_blank"
              rel="noopener"
            >
              IPFS CID: {{ ipfsCid(share) }}
            </a>
          </div>
        </div>

        <div class="title-row">
          <h1 class="title">{{ share.title }}</h1>
          <el-button
            type="text"
            size="mini"
            class="report-btn"
            @click="openReportDialog"
          >
            <i class="el-icon-warning-outline"></i>
            举报
          </el-button>
        </div>
        <div class="meta-row">
          <span>发布者：{{ share.username || '匿名用户' }}</span>
          <span>发布时间：{{ share.createdAt }}</span>
        </div>
        <p class="description">{{ share.description || '暂无介绍' }}</p>

        <el-descriptions :column="2" border size="small" class="chain-info">
          <el-descriptions-item label="IPFS CID" :span="2">
            <a
              v-if="ipfsCid(share)"
              :href="ipfsCidUrl(ipfsCid(share))"
              target="_blank"
              rel="noopener"
            >
              {{ ipfsCid(share) }}
            </a>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 评论区（与列表页弹窗共用逻辑） -->
        <ContentComments :share="share" />

        <el-button type="text" @click="$router.push({ name: 'ContentShare' })">
          返回内容画廊
        </el-button>
      </div>
    </div>

    <el-empty v-else-if="!loading" description="未找到该内容或已被删除">
      <el-button type="primary" @click="$router.push({ name: 'ContentShare' })">
        返回内容画廊
      </el-button>
    </el-empty>

    <!-- 举报弹窗 -->
    <el-dialog
      title="举报内容"
      :visible.sync="reportDialogVisible"
      width="460px"
    >
      <el-form :model="reportForm" label-width="80px">
        <el-form-item label="类型">
          <el-radio-group v-model="reportForm.reasonCode">
            <el-radio label="SPAM">垃圾信息</el-radio>
            <el-radio label="ILLEGAL">违法违规</el-radio>
            <el-radio label="INFRINGE">侵权</el-radio>
            <el-radio label="OTHER">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input
            v-model="reportForm.reasonText"
            type="textarea"
            :rows="3"
            placeholder="请简单描述问题（可选）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button @click="reportDialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="reportSubmitting" @click="submitReport">
          提 交
        </el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import { getContentShareById, reportContentShare } from '@/api/contentShare'
import VideoPlayer from '@/components/VideoPlayer.vue'
import ContentComments from '@/components/ContentComments.vue'

export default {
  name: 'ContentShareDetail',
  components: {
    VideoPlayer,
    ContentComments
  },
  data() {
    return {
      loading: false,
      share: null,
      reportDialogVisible: false,
      reportSubmitting: false,
      reportForm: {
        reasonCode: 'SPAM',
        reasonText: ''
      }
    }
  },
  created() {
    this.fetchShare()
  },
  watch: {
    '$route.params.id': 'fetchShare'
  },
  methods: {
    async fetchShare() {
      const id = this.$route.params.id
      if (!id) return
      this.loading = true
      try {
        const { data } = await getContentShareById(id)
        this.share = data
      } catch (error) {
        this.$message.error(error.message || '加载内容失败')
      } finally {
        this.loading = false
      }
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
    displayMediaUrl(share) {
      if (!share) return ''
      if (share.mediaType === 'IMAGE' && share.id) {
        return `/api/content-shares/${share.id}/media`
      }
      return share.mediaUrl || ''
    },
    openReportDialog() {
      if (!this.share) return
      this.reportDialogVisible = true
    },
    async submitReport() {
      if (!this.share) return
      this.reportSubmitting = true
      try {
        await reportContentShare(this.share.id, {
          reasonCode: this.reportForm.reasonCode || 'OTHER',
          reasonText: this.reportForm.reasonText || ''
        })
        this.$message.success('举报已提交，感谢你的反馈')
        this.reportDialogVisible = false
        this.reportForm.reasonText = ''
      } catch (error) {
        this.$message.error(error.message || '举报提交失败，请稍后重试')
      } finally {
        this.reportSubmitting = false
      }
    }
  }
}
</script>

<style scoped>
.share-detail-page {
  padding: 24px;
}

.share-detail-container {
  max-width: 1200px;
  margin: 0 auto;
}

.surface-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  padding: 20px 24px;
}

.player-wrapper {
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
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
}

.ipfs-cid-overlay a {
  display: block;
  color: #fff;
  text-decoration: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: auto;
}

.ipfs-cid-overlay a:hover {
  text-decoration: underline;
}

.player-wrapper video,
.player-wrapper img {
  width: 100%;
  max-height: 540px;
  display: block;
  object-fit: contain;
}

.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 16px 0 8px;
}

.title {
  font-size: 22px;
  font-weight: 600;
  margin: 0;
}

.report-btn {
  color: #f56c6c;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.description {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 16px;
}

.chain-info {
  margin-bottom: 12px;
}
</style>
