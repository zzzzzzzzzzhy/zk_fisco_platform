<template>
  <div class="content-comments" v-if="share">
    <!-- 内容预览（可选，简单版先总是展示） -->
    <div class="content-preview">
      <h4>{{ share.title }}</h4>
      <p>{{ share.description || '暂无描述' }}</p>
    </div>

    <!-- 评论输入 -->
    <div class="comment-input-section">
      <el-input
        v-model="commentForm.content"
        type="textarea"
        :rows="3"
        placeholder="发表你的看法..."
        maxlength="500"
        show-word-limit
      />
      <div class="comment-actions">
        <el-button
          type="primary"
          size="small"
          :loading="commenting"
          :disabled="!isLoggedIn"
          @click="handleSubmitComment"
        >
          {{ commenting ? '发表中...' : (isLoggedIn ? '发表评论' : '请先登录') }}
        </el-button>
      </div>
    </div>

    <!-- 评论用滑块验证码 -->
    <SliderCaptcha
      ref="sliderCaptcha"
      @success="onCaptchaSuccess"
      @cancel="onCaptchaCancel"
    />

    <!-- 评论列表 -->
    <div class="comments-section" v-if="comments.length > 0">
      <h5>全部评论 ({{ comments.length }})</h5>
      <div class="comment-list">
        <div
          v-for="comment in comments"
          :key="comment.id"
          class="comment-item"
        >
          <div class="comment-header">
            <span class="comment-author">{{ comment.authorName || '匿名用户' }}</span>
            <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
          </div>
          <div class="comment-body">{{ comment.content }}</div>
        </div>
      </div>
    </div>

    <div v-else class="no-comments">
      <p>还没有评论，快来发表第一条评论吧！</p>
    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import SliderCaptcha from '@/components/SliderCaptcha.vue'
import {
  getPostsByContentShare,
  createContentSharePost,
  createForumComment,
  getForumComments,
  submitCommentConsent
} from '@/api/forum'
import web3Service from '@/utils/web3'

export default {
  name: 'ContentComments',
  props: {
    share: {
      type: Object,
      required: true
    }
  },
  components: {
    SliderCaptcha
  },
  data() {
    return {
      commentForm: {
        content: ''
      },
      commenting: false,
      relatedPosts: [],
      comments: [],
      // 验证码相关
      captchaToken: null,
      pending: false
    }
  },
  computed: {
    ...mapGetters('user', ['isLoggedIn', 'userId'])
  },
  watch: {
    share: {
      immediate: true,
      handler(newVal) {
        if (newVal && newVal.id) {
          this.initComments()
        }
      }
    }
  },
  methods: {
    async initComments() {
      await this.loadRelatedPosts(this.share.id)
      await this.loadComments()
    },
    async loadRelatedPosts(contentShareId) {
      try {
        const { data } = await getPostsByContentShare(contentShareId)
        this.relatedPosts = data || []
      } catch (error) {
        console.error('加载关联帖子失败:', error)
      }
    },
    async loadComments() {
      if (this.relatedPosts.length === 0) {
        this.comments = []
        return
      }

      try {
        const all = []
        for (const post of this.relatedPosts) {
          const { data } = await getForumComments(post.id, { current: 1, size: 50 })
          if (data && Array.isArray(data.records)) {
            all.push(...data.records)
          }
        }
        // 按时间排序，新的在前
        all.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
        this.comments = all
      } catch (error) {
        console.error('加载评论失败:', error)
      }
    },
    async createDiscussionPost() {
      if (!this.share || !this.userId) return

      try {
        const postData = {
          authorId: this.userId,
          title: `关于《${this.share.title}》的讨论`,
          content: '欢迎大家在此内容下分享看法和讨论！',
          competitionId: this.share.competitionId,
          relatedContentShareId: this.share.id
        }

        const { data } = await createContentSharePost(postData)
        this.relatedPosts.unshift(data)
        this.$message.success('讨论帖创建成功')
      } catch (error) {
        this.$message.error(error.message || '创建讨论帖失败')
      }
    },
    // 按钮入口：先做人机验证，再真正发表评论
    async handleSubmitComment() {
      if (!this.isLoggedIn) {
        this.$message.warning('请先登录后再发表评论')
        return
      }

      if (!this.commentForm.content.trim()) {
        this.$message.warning('请输入评论内容')
        return
      }

      this.pending = true
      this.$refs.sliderCaptcha && this.$refs.sliderCaptcha.show()
    },

    // 实际发表评论逻辑（滑块验证通过后调用）
    async submitComment() {
      if (!this.isLoggedIn) {
        this.$message.warning('请先登录后再发表评论')
        return
      }

      if (!this.commentForm.content.trim()) {
        this.$message.warning('请输入评论内容')
        return
      }

      if (this.relatedPosts.length === 0) {
        await this.createDiscussionPost()
      }

      this.commenting = true
      try {
        const postId = this.relatedPosts[0].id
        const res = await createForumComment(postId, {
          authorId: this.userId,
          content: this.commentForm.content
        })
        this.$message.success('评论发表成功')

        // 评论奖励：提交签名进入 rollup 批次（不直接上链）
        try {
          const commentId = res && res.data && res.data.id
          if (commentId) {
            await web3Service.connectMetaMask()
            const userAddress = web3Service.currentAddress
            if (!userAddress) {
              throw new Error('钱包未连接')
            }
            const message = `WEE_COMMENT:${commentId}:${userAddress.toLowerCase()}`
            const signature = await web3Service.signMessage(message)
            await submitCommentConsent(postId, commentId, {
              userAddress,
              signature
            })
            this.$message.success('✅ 已提交签名，等待批次上链后系统自动发放奖励')
          }
        } catch (consentError) {
          console.error('评论签名失败:', consentError)
          if (consentError.message?.includes('用户取消')) {
            this.$message.warning('您取消了签名，评论已发布但未进入奖励批次')
          } else {
            this.$message.warning('签名提交失败，评论已发布但未进入奖励批次')
          }
        }

        this.commentForm.content = ''
        await this.loadComments()
      } catch (error) {
        this.$message.error(error.message || '评论发表失败')
      } finally {
        this.commenting = false
      }
    },

    // 滑块成功回调
    async onCaptchaSuccess(token) {
      this.captchaToken = token
      this.pending = false
      try {
        await this.submitComment()
      } finally {
        this.captchaToken = null
      }
    },

    // 滑块取消
    onCaptchaCancel() {
      this.pending = false
      this.captchaToken = null
      this.$message.info('已取消评论')
    },
    formatTime(time) {
      if (!time) return ''
      return new Date(time).toLocaleString('zh-CN')
    }
  }
}
</script>

<style scoped>
.content-comments {
  margin-top: 16px;
}

.content-preview h4 {
  margin: 0 0 4px;
}

.content-preview p {
  margin: 0 0 12px;
  font-size: 13px;
  color: #909399;
}

.comment-input-section {
  margin-bottom: 12px;
}

.comment-actions {
  margin-top: 8px;
  text-align: right;
}

.comments-section h5 {
  margin: 12px 0;
  font-size: 14px;
}

.comment-item {
  padding: 8px 0;
  border-bottom: 1px solid #ebeef5;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.comment-body {
  font-size: 14px;
  color: #303133;
}

.no-comments {
  font-size: 13px;
  color: #909399;
  margin-top: 8px;
}
</style>

