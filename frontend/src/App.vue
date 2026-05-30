<template>
  <div id="app">
    <transition name="page-fade" mode="out-in">
      <keep-alive :include="cachedViews">
        <router-view :key="$route.fullPath"/>
      </keep-alive>
    </transition>
  </div>
</template>

<script>
export default {
  name: 'App',
  data() {
    return {
      // 需要缓存的组件名称（对应组件的 name 属性）
      cachedViews: [
        'ContentShare',    // 内容分享列表
        'ForumPage',       // 论坛
        'Competitions',    // 竞赛列表
        'Governance'       // 提案列表
      ]
    }
  },
  mounted() {
    // 监听路由变化，动态管理缓存
    this.$router.afterEach((to, from) => {
      // 从详情页返回列表页时，保持列表页状态
      if (this.isDetailPage(from) && this.isListPage(to)) {
        console.log('🔄 保持列表页缓存')
      }
    })
  },
  methods: {
    isDetailPage(route) {
      return route.path.includes('/proposals/') || 
             route.path.includes('/competitions/') ||
             route.path.includes('/content-share/')
    },
    isListPage(route) {
      return route.path === '/content-share' ||
             route.path === '/forum' ||
             route.path === '/competitions' ||
             route.path === '/governance'
    }
  }
}
</script>

<style lang="scss">
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  min-height: 100vh;
  background-color: #f5f7fa;
}

/* ============================================
   图片懒加载占位效果
   ============================================ */

img[lazy=loading] {
  background: linear-gradient(
    90deg,
    #f0f0f0 25%,
    #e0e0e0 50%,
    #f0f0f0 75%
  );
  background-size: 200% 100%;
  animation: loading-shimmer 1.5s infinite;
}

img[lazy=error] {
  background: #f5f5f5;
  position: relative;
}

img[lazy=loaded] {
  animation: fade-in 0.3s ease-in;
}

@keyframes loading-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

@keyframes fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* ============================================
   全局按钮样式优化 - 更现代的视觉效果
   ============================================ */

/* 主要按钮增强 */
.el-button--primary {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  
  &:hover,
  &:focus {
    background: linear-gradient(135deg, #5568d3 0%, #6a428f 100%);
    box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
    transform: translateY(-2px);
  }
  
  &:active {
    transform: translateY(0);
    box-shadow: 0 2px 10px rgba(102, 126, 234, 0.3);
  }
  
  &.is-loading {
    opacity: 0.85;
  }
}

/* 成功按钮增强 */
.el-button--success {
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(17, 153, 142, 0.3);
  
  &:hover,
  &:focus {
    background: linear-gradient(135deg, #0d7a72 0%, #2dd46b 100%);
    box-shadow: 0 6px 20px rgba(17, 153, 142, 0.4);
    transform: translateY(-2px);
  }
}

/* 警告按钮增强 */
.el-button--warning {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(240, 147, 251, 0.3);
  
  &:hover,
  &:focus {
    background: linear-gradient(135deg, #e478ea 0%, #e04257 100%);
    box-shadow: 0 6px 20px rgba(240, 147, 251, 0.4);
    transform: translateY(-2px);
  }
}

/* 朴素按钮增强 */
.el-button--primary.is-plain {
  background: rgba(102, 126, 234, 0.1);
  border-color: #667eea;
  color: #667eea;
  transition: all 0.3s ease;
  
  &:hover,
  &:focus {
    background: rgba(102, 126, 234, 0.2);
    border-color: #5568d3;
    color: #5568d3;
    transform: translateY(-2px);
  }
}

/* 默认按钮增强 */
.el-button--default {
  background: #fff;
  border-color: #dcdfe6;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  
  &:hover,
  &:focus {
    background: #f5f7fa;
    border-color: #c0c4cc;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    transform: translateY(-2px);
  }
}

/* 禁用状态 */
.el-button.is-disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none !important;
  box-shadow: none !important;
}

/* 按钮组优化 */
.el-button-group .el-button {
  &:not(:first-child):not(:last-child) {
    border-radius: 0;
  }
  
  &:first-child {
    border-top-right-radius: 0;
    border-bottom-right-radius: 0;
  }
  
  &:last-child {
    border-top-left-radius: 0;
    border-bottom-left-radius: 0;
  }
}

/* 加载状态优化 */
.el-button.is-loading::before {
  background: rgba(255, 255, 255, 0.3);
}

/* ============================================
   消息提示样式优化
   ============================================ */

.el-message {
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
  border: none;
  padding: 16px 20px;
  font-size: 15px;
  font-weight: 500;
  max-width: 500px;
  
  /* 防止显示过长的内容 */
  .el-message__content {
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    line-height: 1.6;
    
    strong {
      display: block;
      margin-bottom: 6px;
      font-size: 16px;
    }
  }
  
  /* 友好错误提示的特殊样式 */
  &.friendly-error-message {
    .el-message__content {
      white-space: normal;
      word-break: break-word;
    }
  }
  
  &.el-message--success {
    background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
    color: #fff;
    
    .el-message__content {
      color: #fff;
      text-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
      font-weight: 500;
    }
    
    .el-message__icon {
      color: #fff;
      filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.15));
    }
  }
  
  &.el-message--warning {
    background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
    color: #fff;
    
    .el-message__content {
      color: #fff;
      text-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
      font-weight: 500;
    }
    
    .el-message__icon {
      color: #fff;
      filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.15));
    }
  }
  
  &.el-message--error {
    background: linear-gradient(135deg, #eb3349 0%, #f45c43 100%);
    color: #fff;
    
    .el-message__content {
      color: #fff;
      text-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
      font-weight: 500;
    }
    
    .el-message__icon {
      color: #fff;
      filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.15));
    }
  }
  
  &.el-message--info {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: #fff;
    
    .el-message__content {
      color: #fff;
      text-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
      font-weight: 500;
    }
    
    .el-message__icon {
      color: #fff;
      filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.15));
    }
  }
}

/* ============================================
   表单输入框优化
   ============================================ */

.el-input__inner {
  border-radius: 10px;
  border-color: #e4e7ed;
  transition: all 0.3s ease;
  
  &:focus {
    border-color: #667eea;
    box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
  }
}

.el-textarea__inner {
  border-radius: 10px;
  border-color: #e4e7ed;
  transition: all 0.3s ease;
  
  &:focus {
    border-color: #667eea;
    box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
  }
}

/* ============================================
   对话框优化
   ============================================ */

.el-dialog {
  border-radius: 20px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  
  .el-dialog__header {
    border-bottom: 1px solid #f0f2f5;
  }
  
  .el-dialog__footer {
    border-top: 1px solid #f0f2f5;
  }
}

/* ============================================
   标签优化
   ============================================ */

.el-tag {
  border-radius: 8px;
  border: none;
  font-weight: 500;
  padding: 0 12px;
  
  &.el-tag--success {
    background: linear-gradient(135deg, rgba(17, 153, 142, 0.15) 0%, rgba(56, 239, 125, 0.15) 100%);
    color: #11998e;
  }
  
  &.el-tag--warning {
    background: linear-gradient(135deg, rgba(240, 147, 251, 0.15) 0%, rgba(245, 87, 108, 0.15) 100%);
    color: #f093fb;
  }
  
  &.el-tag--danger {
    background: linear-gradient(135deg, rgba(235, 51, 73, 0.15) 0%, rgba(244, 92, 67, 0.15) 100%);
    color: #eb3349;
  }
  
  &.el-tag--info {
    background: linear-gradient(135deg, rgba(102, 126, 234, 0.15) 0%, rgba(118, 75, 162, 0.15) 100%);
    color: #667eea;
  }
}

/* ============================================
   页面过渡动画
   ============================================ */

/* 页面淡入淡出 + 轻微位移 */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.page-fade-enter {
  opacity: 0;
  transform: translateY(20px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

/* 路由切换时的平滑过渡 */
.page-fade-enter-active {
  transition-delay: 0.1s;
}

/* 针对不同内容类型的过渡优化 */
#app {
  /* 防止切换时页面抖动 */
  min-height: 100vh;
  position: relative;
}

/* 列表项渐入动画 */
@keyframes item-fade-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 为列表项添加延迟渐入效果 */
.proposal-card,
.share-card,
.post-card {
  animation: item-fade-in 0.4s ease-out backwards;
}

.proposal-card:nth-child(1) { animation-delay: 0.05s; }
.proposal-card:nth-child(2) { animation-delay: 0.1s; }
.proposal-card:nth-child(3) { animation-delay: 0.15s; }
.proposal-card:nth-child(4) { animation-delay: 0.2s; }
.proposal-card:nth-child(5) { animation-delay: 0.25s; }
.proposal-card:nth-child(6) { animation-delay: 0.3s; }

.share-card:nth-child(1) { animation-delay: 0.05s; }
.share-card:nth-child(2) { animation-delay: 0.1s; }
.share-card:nth-child(3) { animation-delay: 0.15s; }
.share-card:nth-child(4) { animation-delay: 0.2s; }

.post-card:nth-child(1) { animation-delay: 0.05s; }
.post-card:nth-child(2) { animation-delay: 0.1s; }
.post-card:nth-child(3) { animation-delay: 0.15s; }
.post-card:nth-child(4) { animation-delay: 0.2s; }
.post-card:nth-child(5) { animation-delay: 0.25s; }

/* ============================================
   移动端响应式优化
   ============================================ */

@media (max-width: 768px) {
  /* 通用卡片优化 */
  .proposal-card,
  .share-card,
  .post-card {
    margin-bottom: 16px;
    border-radius: 12px;
  }
  
  /* 按钮优化 */
  .el-button {
    padding: 10px 16px;
    font-size: 14px;
  }
  
  .el-button--medium {
    padding: 10px 16px;
  }
  
  .el-button--small {
    padding: 8px 12px;
    font-size: 13px;
  }
  
  /* 表单输入框 */
  .el-input__inner,
  .el-textarea__inner {
    font-size: 16px; /* 防止 iOS 自动缩放 */
  }
  
  /* 对话框 */
  .el-dialog {
    width: 95% !important;
    margin: 20px auto !important;
    border-radius: 16px;
  }
  
  .el-dialog__header {
    padding: 16px;
  }
  
  .el-dialog__body {
    padding: 16px;
    max-height: 70vh;
    overflow-y: auto;
  }
  
  .el-dialog__footer {
    padding: 12px 16px;
  }
  
  /* 分页器 */
  .el-pagination {
    text-align: center;
    padding: 16px 0;
    
    .el-pager li {
      min-width: 32px;
      height: 32px;
      line-height: 32px;
      font-size: 14px;
    }
    
    .btn-prev,
    .btn-next {
      min-width: 32px;
      height: 32px;
      line-height: 32px;
    }
  }
  
  /* 标签 */
  .el-tag {
    padding: 0 8px;
    height: 24px;
    line-height: 22px;
    font-size: 12px;
  }
  
  /* 消息提示 */
  .el-message {
    min-width: 280px;
    max-width: calc(100vw - 40px);
    padding: 12px 16px;
  }
  
  /* 表格优化 */
  .el-table {
    font-size: 13px;
    
    th, td {
      padding: 8px 0;
    }
  }
  
  /* 卡片内容间距调整 */
  .surface-card {
    padding: 16px;
    margin-bottom: 16px;
  }
  
  /* 标题字体大小 */
  h1 {
    font-size: 24px !important;
  }
  
  h2 {
    font-size: 20px !important;
  }
  
  h3 {
    font-size: 18px !important;
  }
  
  h4 {
    font-size: 16px !important;
  }
  
  /* 隐藏长文本溢出 */
  .text-overflow {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  
  /* Flex 布局优化 */
  .flex-mobile-column {
    flex-direction: column !important;
  }
  
  /* 间距调整 */
  .el-row {
    margin-left: -8px !important;
    margin-right: -8px !important;
  }
  
  .el-col {
    padding-left: 8px !important;
    padding-right: 8px !important;
  }
}

/* 小屏手机优化 (≤375px) */
@media (max-width: 375px) {
  .el-button {
    padding: 8px 12px;
    font-size: 13px;
  }
  
  .el-dialog {
    width: 100% !important;
    margin: 0 !important;
    border-radius: 0;
    height: 100vh;
  }
  
  h1 {
    font-size: 20px !important;
  }
  
  h2 {
    font-size: 18px !important;
  }
  
  .surface-card {
    padding: 12px;
  }
}

/* 平板优化 (768px - 1024px) */
@media (min-width: 769px) and (max-width: 1024px) {
  .el-col-md-8 {
    width: 50% !important;
  }
  
  .el-col-md-12 {
    width: 100% !important;
  }
}

/* 横屏手机优化 */
@media (max-height: 500px) and (orientation: landscape) {
  .el-dialog {
    max-height: 90vh;
  }
  
  .el-dialog__body {
    max-height: 60vh;
    overflow-y: auto;
  }
}

/* 触摸设备优化 */
@media (hover: none) and (pointer: coarse) {
  /* 增大可点击区域 */
  .el-button,
  a,
  .clickable {
    min-height: 44px; /* iOS 推荐的最小触摸目标 */
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
  
  /* 移除 hover 效果 */
  .proposal-card:hover,
  .share-card:hover,
  .post-card:hover {
    transform: none;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
  }
  
  /* 添加点击效果 */
  .proposal-card:active,
  .share-card:active,
  .post-card:active {
    transform: scale(0.98);
    transition: transform 0.1s;
  }
}
</style>
