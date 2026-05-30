<template>
  <!-- eslint-disable vue/no-unused-components -->
  <div class="forum-page">
    <div class="forum-container">
      <section class="forum-hero surface-card">
        <div class="hero-grid">
          <div class="hero-left">
            <p class="hero-eyebrow">Community Hub · Web3 协作</p>
            <h1>竞赛社区讨论区</h1>
            <p class="hero-lead">
              围绕赛事、链上激励与落地经验的实时讨论区，和参赛者、评委以及运营团队保持同频。
            </p>
            <div class="hero-actions">
              <el-button type="primary" size="medium" icon="el-icon-edit" @click="handleCreateTopic">
                发起新话题
              </el-button>
              <!-- 草稿箱功能开发中，暂时隐藏 -->
              <!-- <el-button plain size="medium" icon="el-icon-document" @click="handleDrafts">
                草稿箱
              </el-button> -->
            </div>
          </div>
          <div class="hero-right">
            <div class="hero-metrics">
              <div class="metric-card">
                <p class="metric-label">帖子总数</p>
                <p class="metric-value">{{ forumStats.totalPosts || 0 }}</p>
                <span class="metric-hint">本周新增 18</span>
              </div>
              <div class="metric-card">
                <p class="metric-label">评论互动</p>
                <p class="metric-value">{{ forumStats.totalComments || 0 }}</p>
                <span class="metric-hint">互动热度 +128</span>
              </div>
              <div class="metric-card">
                <p class="metric-label">活跃作者</p>
                <p class="metric-value">{{ forumStats.activeUsers || 0 }}</p>
                <span class="metric-hint">高峰在线 32</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="control-deck surface-card">
        <div class="scope-tabs">
          <button
            v-for="scope in forumScopes"
            :key="scope.key"
            class="scope-tab"
            :class="{ active: activeScope === scope.key }"
            @click="setScope(scope.key)"
          >
            {{ scope.label }}
            <span v-if="scope.badge" class="tab-badge">{{ scope.badge }}</span>
          </button>
        </div>

        <div class="control-filters">
          <el-input
            v-model="keyword"
            placeholder="搜索标题 / 作者 / 关键字"
            prefix-icon="el-icon-search"
            clearable
            @clear="handleSearch"
            @keyup.enter.native="handleSearch"
          />

          <el-select
            v-model="selectedCompetition"
            placeholder="关联竞赛"
            clearable
            @change="handleSearch"
          >
            <el-option
              v-for="comp in competitionOptions"
              :key="comp.value"
              :label="comp.label"
              :value="comp.value"
            />
          </el-select>

          <el-button icon="el-icon-refresh" @click="handleSearch">刷新列表</el-button>
        </div>

        <div class="category-pills">
          <span class="pill-label">热门话题</span>
          <button
            v-for="category in categories"
            :key="category.key"
            class="category-pill"
            :class="{ active: activeCategory === category.key }"
            @click="setCategory(category.key)"
          >
            {{ category.label }}
          </button>
        </div>
      </section>

      <div class="forum-shell">
        <main class="post-column">
          <!-- 骨架屏加载效果 -->
          <div class="post-stream" v-if="listLoading">
            <article
              v-for="i in 5"
              :key="'skeleton-' + i"
              class="post-card surface-card skeleton-card"
            >
              <el-skeleton :rows="3" animated>
                <template slot="template">
                  <div style="display: flex; align-items: center; margin-bottom: 16px;">
                    <el-skeleton-item variant="circle" style="width: 50px; height: 50px; margin-right: 12px;" />
                    <div style="flex: 1;">
                      <el-skeleton-item variant="h3" style="width: 50%; margin-bottom: 8px;" />
                      <el-skeleton-item variant="text" style="width: 30%;" />
                    </div>
                  </div>
                  <el-skeleton-item variant="text" style="width: 100%; margin-bottom: 8px;" />
                  <el-skeleton-item variant="text" style="width: 80%; margin-bottom: 8px;" />
                  <el-skeleton-item variant="text" style="width: 60%;" />
                </template>
              </el-skeleton>
            </article>
          </div>

          <div class="post-stream" v-else>
            <article
              v-for="post in displayedPosts"
              :key="post.id"
              class="post-card surface-card"
              @click="openPostDetail(post)"
            >
              <div class="post-badges">
                <span v-if="post.pinned" class="badge badge-pinned">置顶</span>
                <span class="badge badge-competition">{{ competitionLabel(post.competitionId) }}</span>
                <span class="badge badge-type">{{ post.tag || post.category || '讨论' }}</span>
              </div>
              <h3>{{ post.title }}</h3>
              <p class="post-excerpt">{{ post.excerpt || post.content?.slice(0, 96) || '点击查看详情' }}</p>
              <div class="post-meta">
                <div class="author-block">
                  <div class="avatar" :style="{ background: getAvatarColor(post.authorId) }">
                    {{ authorDisplay(post.authorId, post.authorName).charAt(0).toUpperCase() }}
                  </div>
                  <div>
                    <strong>{{ authorDisplay(post.authorId, post.authorName) }}</strong>
                    <p>#{{ post.authorId }} · {{ formatTimestamp(post.lastReplyAt || post.updatedAt) }}</p>
                  </div>
                </div>
                <div class="post-stats">
                  <span><i class="el-icon-chat-dot-square"></i>{{ post.replyCount || 0 }}</span>
                  <span><i class="el-icon-star-off"></i>{{ post.likeCount || 0 }}</span>
                  <span><i class="el-icon-view"></i>{{ post.viewCount || 0 }}</span>
                </div>
              </div>
            </article>

            <el-empty v-if="!displayedPosts.length" description="暂无符合条件的话题" />
          </div>

          <div class="post-pagination surface-card" v-if="pagination.total > pagination.size">
            <el-pagination
              layout="prev, pager, next"
              :current-page="pagination.current"
              :page-size="pagination.size"
              :total="pagination.total"
              @current-change="handlePageChange"
            />
          </div>
        </main>

        <aside class="insight-column">
          <section class="insight-card surface-card web3-card" ref="tokenCenterCard">
            <div class="insight-header">
              <p class="eyebrow">Web3 激励</p>
              <strong>WEE 签到中心</strong>
            </div>
            <ForumTokenBalance />
          </section>

          <section class="insight-card surface-card token-guide-card">
            <div class="insight-header">
              <p class="eyebrow">代币使用指南</p>
              <strong>WEE 激励闭环</strong>
            </div>
            <ul class="token-guide-list">
              <li v-for="(step, index) in tokenGuideSteps" :key="step.title">
                <span class="step-index">0{{ index + 1 }}</span>
                <div class="step-content">
                  <p class="step-title">{{ step.title }}</p>
                  <small>{{ step.desc }}</small>
                </div>
                <el-tag size="mini" :type="step.tagType" effect="dark">{{ step.tag }}</el-tag>
              </li>
            </ul>
            <el-button type="primary" plain icon="el-icon-document" @click="openTokenGuide">
              定位至签到中心
            </el-button>
          </section>

          <section class="insight-card surface-card">
            <div class="insight-header">
              <p class="eyebrow">实时趋势</p>
              <strong>论坛热门话题</strong>
            </div>
            <div class="trending-list">
              <button
                v-for="topic in trendingTopics"
                :key="topic.id"
                class="trending-item"
                @click="handleTopicClick(topic)"
              >
                <div class="trend-icon"><i class="el-icon-top"></i></div>
                <div class="trend-content">
                  <p>{{ topic.title }}</p>
                  <small>{{ topic.competition }} · {{ topic.activity }}</small>
                </div>
                <span class="trend-score">{{ topic.hotValue }}</span>
              </button>
            </div>
          </section>

          <section class="insight-card surface-card">
            <div class="insight-header">
              <p class="eyebrow">快捷入口</p>
              <strong>常用操作</strong>
            </div>
            <div class="quick-links">
              <button
                v-for="link in quickLinks"
                :key="link.key"
                class="quick-link"
                @click="handleQuickLink(link.key)"
              >
                <div class="quick-icon"><i :class="link.icon"></i></div>
                <div class="quick-content">
                  <p>{{ link.title }}</p>
                  <small>{{ link.desc }}</small>
                </div>
                <i class="el-icon-arrow-right quick-arrow"></i>
              </button>
            </div>
          </section>

          <section class="insight-card surface-card guidelines-card">
            <div class="insight-header">
              <p class="eyebrow">社区守则</p>
              <strong>发帖小贴士</strong>
            </div>
            <ul>
              <li v-for="(tip, index) in guidelines" :key="tip">
                <span class="tip-index">{{ index + 1 }}</span>
                <span>{{ tip }}</span>
              </li>
            </ul>
          </section>
        </aside>
      </div>

      <el-dialog
        title="发起新话题"
        :visible.sync="showCreateDialog"
        width="640px"
        @close="resetCreateForm"
      >
        <el-form ref="createForm" :model="createForm" :rules="createRules" label-width="90px">
          <el-form-item label="标题" prop="title">
            <el-input v-model="createForm.title" maxlength="120" show-word-limit placeholder="一句话描述你的话题" />
          </el-form-item>

          <el-form-item label="关联竞赛">
            <el-select v-model="createForm.competitionId" placeholder="可选" clearable filterable style="width: 100%">
              <el-option
                v-for="comp in competitionOptions"
                :key="comp.value"
                :label="comp.label"
                :value="comp.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="分类" prop="category">
            <el-select v-model="createForm.category" style="width: 100%">
              <el-option
                v-for="category in categories.filter(item => item.key !== 'all')"
                :key="category.key"
                :label="category.label"
                :value="category.key"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="标签">
            <el-input v-model="createForm.tags" placeholder="可输入多个，用逗号分隔" />
          </el-form-item>

          <el-form-item label="正文" prop="content">
            <el-input
              v-model="createForm.content"
              type="textarea"
              :rows="6"
              maxlength="3000"
              show-word-limit
              placeholder="分享思路、问题描述或经验"
            />
          </el-form-item>
        </el-form>
        <span slot="footer">
          <el-button @click="showCreateDialog = false">取 消</el-button>
          <el-button type="primary" :loading="creating" @click="submitCreateForm">发 布</el-button>
        </span>
      </el-dialog>

      <el-drawer
        :visible.sync="showDetailDrawer"
        size="60%"
        :with-header="false"
        :append-to-body="false"
        custom-class="detail-drawer"
      >
        <div v-if="detailLoading" class="drawer-loading">
          <el-skeleton :rows="6" animated />
        </div>
        <div v-else-if="activePost" class="post-detail">
          <div class="detail-header">
            <div class="detail-title-block">
              <div class="tag-line">
                <span class="detail-tag">{{ activePost.category || '讨论' }}</span>
                <span class="detail-competition">{{ competitionLabel(activePost.competitionId) }}</span>
              </div>
              <h3>{{ activePost.title }}</h3>
              <p class="detail-meta">
                <span>{{ authorDisplay(activePost.authorId, activePost.authorName) }}</span>
                <span>·</span>
                <span>{{ formatTimestamp(activePost.createdAt) }}</span>
              </p>
              <div class="tag-pills" v-if="postTags(activePost).length">
                <span class="pill" v-for="tag in postTags(activePost)" :key="tag">{{ tag }}</span>
              </div>
            </div>
            <div class="detail-stats">
              <div class="stat-chip">
                <i class="el-icon-view"></i>
                <div>
                  <strong>{{ activePost.viewCount || 0 }}</strong>
                  <span>浏览</span>
                </div>
              </div>
              <div class="stat-chip">
                <i class="el-icon-chat-dot-square"></i>
                <div>
                  <strong>{{ activePost.replyCount || 0 }}</strong>
                  <span>讨论</span>
                </div>
              </div>
              <div class="stat-chip">
                <i class="el-icon-star-off"></i>
                <div>
                  <strong>{{ activePost.likeCount || 0 }}</strong>
                  <span>点赞</span>
                </div>
              </div>
            </div>
          </div>
          <div class="post-detail-content">
            {{ activePost.content }}
          </div>

          <!-- 关联内容分享跳转入口（如果有的话） -->
          <div
            v-if="activePost && activePost.relatedContentShareCount && activePost.relatedContentShareIds"
            class="linked-share card"
          >
            <div class="linked-share-header">
              <div>
                <p class="eyebrow">来自内容分享</p>
                <strong>这条讨论关联了一条内容分享作品</strong>
              </div>
              <el-button
                type="primary"
                size="mini"
                icon="el-icon-link"
                @click="openRelatedContent(activePost.relatedContentShareIds)"
              >
                前往内容详情
              </el-button>
            </div>
            <p class="linked-share-desc">
              点击「前往内容详情」可跳转到内容分享大屏页面，查看原图 / 视频以及完整区块链状态。
            </p>
          </div>

          <div class="comment-section card">
            <div class="comment-header">
              <div>
                <h4>讨论区</h4>
                <small>{{ commentCount }} 条回复</small>
              </div>
              <el-button v-if="commentPagination.total" type="text" @click="handleCommentPageChange(1)">
                返回首条
              </el-button>
            </div>
            <div class="comment-list" v-loading="commentLoading">
              <template v-if="comments.length">
                <div v-for="comment in comments" :key="comment.id" class="comment-item">
                  <div class="comment-avatar">
                    {{ comment.authorId?.toString().slice(-2) || '--' }}
                  </div>
                  <div class="comment-body">
                    <div class="comment-meta">
                      <div>
                        <strong>{{ authorDisplay(comment.authorId, comment.authorName) }}</strong>
                        <span class="comment-date">{{ formatTimestamp(comment.createdAt) }}</span>
                      </div>
                      <el-tag size="mini" type="info" effect="plain">#{{ comment.id }}</el-tag>
                    </div>
                    <p class="comment-text">{{ comment.content }}</p>
                  </div>
                </div>
              </template>
              <el-empty v-else description="暂无评论" />
            </div>
            <div class="comment-pagination" v-if="commentPagination.total > commentPagination.size">
              <el-pagination
                layout="prev, pager, next"
                :current-page="commentPagination.current"
                :page-size="commentPagination.size"
                :total="commentPagination.total"
                @current-change="handleCommentPageChange"
              />
            </div>
            <div v-if="isLoggedIn" class="comment-editor">
              <el-input
                v-model="commentForm.content"
                type="textarea"
                :rows="4"
                maxlength="1000"
                show-word-limit
                placeholder="发表你的看法..."
              />
              <div class="editor-actions">
                <el-button type="primary" :loading="commentSubmitting" @click="submitComment">
                  发布评论
                </el-button>
              </div>
            </div>
            <el-alert
              v-else
              type="info"
              show-icon
              title="登录后即可参与讨论"
              :closable="false"
            />
          </div>
        </div>
        <div v-else class="drawer-empty">
          <el-empty description="请选择一个话题查看详情" />
        </div>
      </el-drawer>

    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import {
  getForumPosts,
  getForumPostDetail,
  createForumPost,
  getForumComments,
  createForumComment,
  submitCommentConsent
} from '@/api/forum'
import { getCompetitions } from '@/api/competition'
import ForumTokenBalance from '@/components/ForumToken/ForumTokenBalance.vue'
import web3Service from '@/utils/web3'

export default {
  name: 'ForumPage',
  components: {
    ForumTokenBalance
  },
  data() {
    return {
      keyword: '',
      activeScope: 'all',
      activeCategory: 'all',
      selectedCompetition: '',
      forumStats: {
        totalPosts: 156,
        totalComments: 1024,
        activeUsers: 89
      },
      forumScopes: [
        { key: 'all', label: '全部' },
        { key: 'following', label: '我关注的', badge: 4 },
        { key: 'unread', label: '未读', badge: 12 }
      ],
      categories: [
        { key: 'all', label: '全部' },
        { key: 'question', label: '技术提问' },
        { key: 'discussion', label: '思路讨论' },
        { key: 'announcement', label: '官方公告' },
        { key: 'share', label: '经验分享' },
        { key: 'resource', label: '资料资源' }
      ],
      competitionOptions: [],
      competitionMap: {},
      posts: [],
      pagination: {
        current: 1,
        size: 10,
        total: 0
      },
      listLoading: false,
      showCreateDialog: false,
      createForm: {
        title: '',
        competitionId: null,
        category: 'discussion',
        tags: '',
        content: ''
      },
      createRules: {
        title: [
          { required: true, message: '请输入标题', trigger: 'blur' },
          { min: 4, message: '标题至少4个字', trigger: 'blur' }
        ],
        category: [
          { required: true, message: '请选择分类', trigger: 'change' }
        ],
        content: [
          { required: true, message: '请输入正文', trigger: 'blur' },
          { min: 10, message: '正文至少10个字', trigger: 'blur' }
        ]
      },
      creating: false,
      showDetailDrawer: false,
      detailLoading: false,
      activePost: null,
      comments: [],
      commentPagination: {
        current: 1,
        size: 10,
        total: 0
      },
      commentLoading: false,
      commentForm: {
        content: ''
      },
      commentSubmitting: false,
      trendingTopics: [
        { id: 't1', title: '链上证据如何与评测结果绑定', competition: 'AI+安全创新赛', activity: '52 条回复', hotValue: 'Hot 96' },
        { id: 't2', title: '官方评分脚本开源讨论', competition: 'AI 治理挑战赛', activity: '39 条回复', hotValue: 'Hot 88' },
        { id: 't3', title: '榜单冻结后的复核流程', competition: '全站', activity: '18 条回复', hotValue: 'Hot 74' }
      ],
      quickLinks: [
        { key: 'create', title: '发起讨论', desc: '技术问答 / 经验分享', icon: 'el-icon-edit' },
        { key: 'announcement', title: '官方公告', desc: '查看最新通知', icon: 'el-icon-bell' },
        { key: 'resources', title: '资料仓库', desc: '模板 · 数据 · 工具', icon: 'el-icon-collection' }
      ],
      tokenGuideSteps: [
        { title: '连接钱包领取参赛礼包', desc: '绑定 MetaMask 并在 WEE 弹窗查看初始额度', tag: '准备', tagType: 'info' },
        { title: '发帖/评论获得互动积分', desc: '优质讨论、被点赞可触发代币加速', tag: '互动', tagType: 'success' },
        { title: '用 WEE 兑换权益', desc: '支持兑换榜单置顶、徽章或赞助报名费', tag: '激励', tagType: 'warning' }
      ],
      guidelines: [
        '标题描述清晰，便于检索',
        '贴上竞赛或赛道标签',
        '技术问题附带复现信息',
        '分享经验请注明版本和依赖',
        '尊重他人，遵守社区守则'
      ]
    }
  },
  computed: {
    ...mapGetters('user', ['isLoggedIn', 'userId', 'userRole']),
    displayedPosts() {
      if (this.activeScope === 'following' && this.isLoggedIn) {
        return this.posts.filter(post => post.authorId === this.userId)
      }
      return this.posts
    },
    commentCount() {
      return Math.max(this.commentPagination.total || 0, this.comments.length || 0)
    }
  },
  created() {
    this.fetchCompetitions()
    this.fetchPosts()
  },
  methods: {
    async fetchCompetitions() {
      try {
        const res = await getCompetitions({ current: 1, size: 100 })
        if (res.code === 200) {
          this.competitionOptions = (res.data.records || []).map(comp => ({
            value: comp.id,
            label: comp.title
          }))
          this.competitionMap = this.competitionOptions.reduce((acc, cur) => {
            acc[cur.value] = cur.label
            return acc
          }, {})
        }
      } catch (error) {
        console.error('加载竞赛列表失败', error)
      }
    },
    async fetchPosts() {
      this.listLoading = true
      try {
        const params = {
          current: this.pagination.current,
          size: this.pagination.size
        }
        if (this.selectedCompetition) {
          params.competitionId = this.selectedCompetition
        }
        if (this.activeCategory !== 'all') {
          params.category = this.activeCategory
        }
        if (this.keyword) {
          params.keyword = this.keyword.trim()
        }

        const res = await getForumPosts(params)
        if (res.code === 200) {
          this.posts = res.data.records || []
          this.pagination.total = res.data.total || 0
        }
      } catch (error) {
        console.error('加载帖子列表失败', error)
      } finally {
        this.listLoading = false
      }
    },
    handleSearch() {
      this.pagination.current = 1
      this.fetchPosts()
    },
    handlePageChange(page) {
      this.pagination.current = page
      this.fetchPosts()
    },
    setScope(scope) {
      this.activeScope = scope
      if (!this.isLoggedIn && scope !== 'all') {
        this.$message.info('登录后可查看关注或未读列表')
      }
    },
    setCategory(category) {
      this.activeCategory = category
      this.handleSearch()
    },
    competitionLabel(id) {
      if (!id) return '全站讨论'
      return this.competitionMap[id] || `竞赛 #${id}`
    },
    authorDisplay(authorId, authorName) {
      if (authorName) {
        return authorName
      }
      return authorId ? `用户 #${authorId}` : '匿名用户'
    },
    postTags(post) {
      if (!post || !post.tags) return []
      return post.tags.split(',').map(tag => tag.trim()).filter(Boolean)
    },
    getAvatarColor(authorId) {
      const palette = ['#7b5bff', '#00c9a7', '#ff9f43', '#4b7bec', '#f368e0', '#10ac84']
      if (!authorId) {
        return palette[0]
      }
      return palette[authorId % palette.length]
    },
    formatTimestamp(value) {
      if (!value) return '刚刚'
      try {
        return new Date(value).toLocaleString()
      } catch (e) {
        return value
      }
    },
    openPostDetail(post) {
      this.showDetailDrawer = true
      this.detailLoading = true
      this.activePost = null
      this.commentPagination.current = 1
      this.comments = []
      this.fetchPostDetail(post.id)
    },
    async fetchPostDetail(id) {
      try {
        const res = await getForumPostDetail(id)
        if (res.code === 200) {
          this.activePost = res.data
          await this.fetchComments(id)
        } else {
          this.$message.error(res.message || '加载话题详情失败')
        }
      } catch (error) {
        console.error('加载帖子详情失败', error)
        this.$message.error('加载话题详情失败')
      } finally {
        this.detailLoading = false
      }
    },
    async fetchComments(postId, page = this.commentPagination.current) {
      this.commentLoading = true
      try {
        const res = await getForumComments(postId, {
          current: page,
          size: this.commentPagination.size
        })
        if (res.code === 200) {
          this.comments = res.data.records || []
          this.commentPagination.total = res.data.total || 0
          this.commentPagination.current = res.data.current || page
        }
      } catch (error) {
        console.error('加载评论失败', error)
      } finally {
        this.commentLoading = false
      }
    },
    handleCommentPageChange(page) {
      if (!this.activePost) return
      this.commentPagination.current = page
      this.fetchComments(this.activePost.id, page)
    },
    handleCreateTopic() {
      if (!this.isLoggedIn) {
        this.$message.info('请先登录再发帖')
        this.$router.push({ name: 'Login', query: { redirect: this.$route.fullPath } })
        return
      }
      this.showCreateDialog = true
    },
    resetCreateForm() {
      this.$nextTick(() => {
        if (this.$refs.createForm) {
          this.$refs.createForm.resetFields()
        }
        this.createForm.category = 'discussion'
      })
    },
    submitCreateForm() {
      if (!this.isLoggedIn) {
        this.$message.error('请先登录')
        return
      }
      this.$refs.createForm.validate(async (valid) => {
        if (!valid) return
        this.creating = true
        try {
          const payload = {
            title: this.createForm.title.trim(),
            competitionId: this.createForm.competitionId,
            category: this.createForm.category,
            tags: this.createForm.tags,
            content: this.createForm.content.trim(),
            authorId: this.userId
          }
          if (!payload.competitionId) {
            delete payload.competitionId
          }
          if (!payload.tags) {
            delete payload.tags
          }
          const res = await createForumPost(payload)
          if (res.code === 200) {
            this.$message.success('发帖成功')
            this.showCreateDialog = false
            this.resetCreateForm()
            this.handleSearch()
          }
        } catch (error) {
          console.error('发帖失败', error)
          this.$message.error('发帖失败，请稍后重试')
        } finally {
          this.creating = false
        }
      })
    },
    async submitComment() {
      if (!this.isLoggedIn) {
        this.$message.info('登录后才能发表评论')
        return
      }
      if (!this.activePost) return
      if (!this.commentForm.content.trim()) {
        this.$message.warning('请输入评论内容')
        return
      }
      this.commentSubmitting = true
      try {
        const res = await createForumComment(this.activePost.id, {
          authorId: this.userId,
          content: this.commentForm.content.trim()
        })
        if (res.code === 200) {
          this.$message.success('评论成功')
          const commentId = res.data && res.data.id
          if (commentId) {
            try {
              await web3Service.connectMetaMask()
              const userAddress = web3Service.currentAddress
              if (!userAddress) {
                throw new Error('钱包未连接')
              }
              const message = `WEE_COMMENT:${commentId}:${userAddress.toLowerCase()}`
              const signature = await web3Service.signMessage(message)
              await submitCommentConsent(this.activePost.id, commentId, {
                userAddress,
                signature
              })
              this.$message.success('✅ 已提交签名，等待批次上链后系统自动发放奖励')
            } catch (consentError) {
              console.error('评论签名失败:', consentError)
              if (consentError.message?.includes('用户取消')) {
                this.$message.warning('您取消了签名，评论已发布但未进入奖励批次')
              } else {
                this.$message.warning('签名提交失败，评论已发布但未进入奖励批次')
              }
            }
          }
          this.commentForm.content = ''
          this.commentPagination.current = 1
          await this.fetchPostDetail(this.activePost.id)
        }
      } catch (error) {
        console.error('评论失败', error)
        this.$message.error('评论失败，请稍后重试')
      } finally {
        this.commentSubmitting = false
      }
    },
    handleDrafts() {
      this.$message.info('草稿箱功能开发中')
    },
    handleQuickLink(key) {
      const map = {
        create: '打开发帖流程',
        announcement: '跳转至公告列表',
        resources: '跳转至资料集合'
      }
      if (key === 'create') {
        this.handleCreateTopic()
        return
      }
      this.$message({
        type: 'success',
        message: `原型：${map[key] || '即将上线'}`
      })
    },
    openTokenGuide() {
      this.$nextTick(() => {
        const el = this.$refs.tokenCenterCard
        if (el && typeof el.scrollIntoView === 'function') {
          el.scrollIntoView({ behavior: 'smooth', block: 'start' })
        }
      })
      this.$message.success('已定位到 WEE 签到中心，可直接连接钱包并签到')
    },
    handleTopicClick(topic) {
      this.$message.info(`原型：查看话题「${topic.title}」`)
    },
    handleViewAll(section) {
      this.$message.info(`原型：查看 ${section} 详情`)
    },
    openRelatedContent(relatedIds) {
      if (!relatedIds) return
      // 后端返回的是逗号分隔的 ID 列表，这里只取第一个
      const firstId = relatedIds.split(',')[0].trim()
      if (!firstId) return

      // 关闭当前帖子抽屉，再跳转到内容分享详情页
      this.showDetailDrawer = false
      this.$router.push({
        name: 'ContentShareDetail',
        params: { id: firstId }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.forum-page {
  background: #eef1f7;
  min-height: 100vh;
  padding: 32px 0 60px;
}

.forum-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
}

.surface-card {
  background: #fff;
  border-radius: 28px;
  padding: 32px;
  box-shadow: 0 25px 60px rgba(15, 23, 42, 0.08);
  border: 1px solid rgba(226, 232, 240, 0.7);
}

.card {
  background: #fff;
  border-radius: 20px;
  padding: 24px;
  box-shadow: 0 20px 45px rgba(15, 23, 42, 0.08);
}

.forum-container > section:not(:first-child) {
  margin-top: 24px;
}

.forum-hero {
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
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.85);
}

.hero-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.hero-actions .el-button.is-plain {
  color: #fff;
  border-color: rgba(255, 255, 255, 0.4);
}

.hero-right {
  display: flex;
  align-items: center;
}

.hero-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 16px;
  width: 100%;
}

.metric-card {
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  padding: 18px;
  backdrop-filter: blur(8px);
}

.metric-label {
  margin: 0;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}

.metric-value {
  margin: 8px 0 4px;
  font-size: 28px;
  font-weight: 700;
}

.metric-hint {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.control-deck {
  background: #fdfdfd;
}

.scope-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.scope-tab {
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 999px;
  padding: 10px 20px;
  background: #fff;
  font-weight: 600;
  color: #4c1d95;
  cursor: pointer;
  transition: all 0.2s ease;
}

.scope-tab.active {
  background: linear-gradient(120deg, #5b4cfa, #8b5cf6);
  border-color: transparent;
  color: #fff;
  box-shadow: 0 10px 25px rgba(91, 76, 250, 0.35);
}

.tab-badge {
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.2);
  font-size: 12px;
}

.control-filters {
  margin: 24px 0 16px;
  display: grid;
  grid-template-columns: 1.4fr 0.8fr auto;
  gap: 14px;
  align-items: center;
}

.control-filters .el-select,
.control-filters .el-input {
  width: 100%;
}

/* 搜索框增强样式 */
.control-filters .el-input {
  ::v-deep .el-input__inner {
    border: 2px solid #e5e7eb;
    border-radius: 12px;
    padding-left: 40px;
    font-size: 15px;
    height: 44px;
    background: #fff;
    transition: all 0.3s ease;
    
    &:hover {
      border-color: #c7d2fe;
      background: #fafbff;
    }
    
    &:focus {
      border-color: #6366f1;
      background: #fff;
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
    }
    
    &::placeholder {
      color: #9ca3af;
      font-size: 14px;
    }
  }
  
  ::v-deep .el-input__prefix {
    left: 12px;
    font-size: 18px;
    color: #6366f1;
  }
  
  ::v-deep .el-input__suffix {
    right: 12px;
  }
}

/* 选择框增强样式 */
.control-filters .el-select {
  ::v-deep .el-input__inner {
    border: 2px solid #e5e7eb;
    border-radius: 12px;
    height: 44px;
    font-size: 14px;
    background: #fff;
    transition: all 0.3s ease;
    
    &:hover {
      border-color: #c7d2fe;
      background: #fafbff;
    }
    
    &:focus {
      border-color: #6366f1;
      box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
    }
  }
}

/* 搜索按钮优化 */
.control-filters .el-button {
  height: 44px;
  border-radius: 12px;
  padding: 0 24px;
  font-size: 15px;
  font-weight: 600;
}

.category-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.pill-label {
  font-weight: 600;
  color: #475569;
}

.category-pill {
  border: none;
  background: #f3f4f6;
  color: #4c1d95;
  padding: 8px 16px;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.2s ease;
}

.category-pill.active {
  background: rgba(91, 76, 250, 0.12);
  color: #5b4cfa;
}

.forum-shell {
  display: flex;
  gap: 32px;
  margin-top: 32px;
}

.post-column {
  flex: 1;
  min-width: 0;
}

.post-stream {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.post-card {
  cursor: pointer;
  background: linear-gradient(135deg, #ffffff 0%, #f8f9fa 100%);
  border: 1px solid rgba(0, 0, 0, 0.02);
  border-radius: 16px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
  
  /* 左侧彩色条 */
  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 4px;
    background: linear-gradient(180deg, 
      #667eea 0%, 
      #764ba2 50%, 
      #f093fb 100%);
    opacity: 0;
    transition: opacity 0.3s ease;
  }
}

.post-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.12),
              0 4px 12px rgba(15, 23, 42, 0.06);
  border-color: rgba(102, 126, 234, 0.2);
  
  &::before {
    opacity: 1;
  }
}

.post-card.skeleton-card {
  cursor: default;
  &:hover {
    transform: none;
    box-shadow: none;
  }
}

.post-card h3 {
  margin: 12px 0 8px;
  font-size: 22px;
  color: #0f172a;
}

.post-excerpt {
  margin: 0 0 18px;
  color: #475569;
  line-height: 1.6;
}

.post-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.badge {
  font-size: 12px;
  padding: 4px 12px;
  border-radius: 999px;
  background: #f4f4f5;
  color: #475569;
}

.badge-pinned {
  background: rgba(255, 159, 67, 0.15);
  color: #b45309;
}

.badge-competition {
  background: rgba(91, 76, 250, 0.15);
  color: #4c1d95;
}

.badge-type {
  background: rgba(16, 185, 129, 0.12);
  color: #047857;
}

.post-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  border-top: 1px solid #edf0f7;
  padding-top: 18px;
}

.author-block {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
}

.author-block p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.post-stats {
  display: flex;
  gap: 14px;
  color: #475569;
}

.post-stats span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.post-stats i {
  color: #94a3b8;
}

.post-pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

.insight-column {
  width: 480px;
  max-width: 100%;
  flex-basis: 480px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.insight-card {
  padding: 24px;
}

.insight-header {
  margin-bottom: 16px;
}

.eyebrow {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: #94a3b8;
  margin: 0 0 6px;
}

.trending-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.trending-item {
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 18px;
  padding: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: #f9fafb;
  cursor: pointer;
  transition: transform 0.2s ease, background 0.2s ease;
}

.trending-item:hover {
  background: #eef2ff;
  transform: translateX(4px);
}

.trend-icon {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: linear-gradient(135deg, #ff9f43, #f368e0);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.trend-content p {
  margin: 0;
  font-weight: 600;
  color: #0f172a;
}

.trend-content small {
  color: #64748b;
}

.trend-score {
  margin-left: auto;
  font-size: 12px;
  font-weight: 700;
  color: #f97316;
}

.quick-links {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.token-guide-card {
  .token-guide-list {
    list-style: none;
    padding: 0;
    margin: 0 0 16px;
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  li {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 12px;
    border: 1px solid rgba(226, 232, 240, 0.8);
    border-radius: 16px;
    background: #f8fafc;
  }

  .step-index {
    font-weight: 700;
    color: #5b4cfa;
    font-size: 12px;
    letter-spacing: 0.08em;
  }

  .step-content {
    flex: 1;

    .step-title {
      margin: 0;
      font-weight: 600;
      color: #0f172a;
    }

    small {
      color: #94a3b8;
    }
  }

  .el-button {
    width: 100%;
    margin-top: 4px;
  }
}

.quick-link {
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 18px;
  padding: 12px;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: box-shadow 0.2s ease;
}

.quick-link:hover {
  box-shadow: 0 12px 25px rgba(15, 23, 42, 0.1);
}

.quick-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: rgba(91, 76, 250, 0.12);
  color: #5b4cfa;
  display: flex;
  align-items: center;
  justify-content: center;
}

.quick-content p {
  margin: 0;
  font-weight: 600;
}

.quick-content small {
  color: #94a3b8;
}

.quick-arrow {
  margin-left: auto;
  color: #cbd5f5;
}

.guidelines-card ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.guidelines-card li {
  display: flex;
  gap: 10px;
  align-items: center;
  color: #475569;
}

.tip-index {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: rgba(91, 76, 250, 0.12);
  color: #5b4cfa;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
}

.web3-card .el-alert {
  border-radius: 16px;
}

@media (max-width: 1024px) {
  .forum-shell {
    flex-direction: column;
  }

  .insight-column {
    width: 100%;
    flex-direction: row;
    flex-wrap: wrap;
  }

  .insight-card {
    flex: 1 1 280px;
  }
}

@media (max-width: 768px) {
  .forum-container {
    padding: 0 16px;
  }

  .surface-card {
    padding: 24px;
    border-radius: 20px;
  }

  .control-filters {
    grid-template-columns: 1fr;
  }

  .post-meta {
    flex-direction: column;
    align-items: flex-start;
  }

  .insight-column {
    flex-direction: column;
  }
}
</style>
<style lang="scss" src="./ForumDrawerStyles.scss"></style>
