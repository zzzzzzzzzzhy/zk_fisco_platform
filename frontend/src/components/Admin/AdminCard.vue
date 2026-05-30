<template>
  <div class="admin-card" :class="{ 'admin-card--hover': hoverable, 'admin-card--loading': loading }">
    <!-- 卡片头部 -->
    <div v-if="title || $slots.header" class="admin-card__header">
      <slot name="header">
        <div class="admin-card__title">
          <h3>{{ title }}</h3>
          <div v-if="subtitle" class="admin-card__subtitle">{{ subtitle }}</div>
        </div>
        <div v-if="$slots.actions" class="admin-card__actions">
          <slot name="actions"></slot>
        </div>
      </slot>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="admin-card__loading">
      <el-skeleton :rows="3" animated />
    </div>

    <!-- 卡片内容 -->
    <div v-else class="admin-card__body">
      <slot></slot>
    </div>

    <!-- 卡片底部 -->
    <div v-if="$slots.footer" class="admin-card__footer">
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AdminCard',
  props: {
    title: {
      type: String,
      default: ''
    },
    subtitle: {
      type: String,
      default: ''
    },
    hoverable: {
      type: Boolean,
      default: false
    },
    loading: {
      type: Boolean,
      default: false
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/admin-design-system.scss';

.admin-card {
  background: var(--bg-primary);
  border-radius: var(--border-radius-xl);
  box-shadow: var(--shadow-light);
  transition: all $transition-base;
  overflow: hidden;
  position: relative;

  &--hover {
    &:hover {
      transform: translateY(-2px);
      box-shadow: var(--shadow-base);
    }
  }

  &--loading {
    pointer-events: none;
    opacity: 0.8;
  }

  &__header {
    padding: var(--spacing-lg) var(--spacing-xl);
    background: var(--bg-secondary);
    border-bottom: 1px solid var(--border-light);
    @include flex-between;
    align-items: flex-start;
    min-height: 64px;
  }

  &__title {
    h3 {
      margin: 0;
      font-size: $font-size-lg;
      font-weight: $font-weight-semibold;
      color: var(--text-primary);
      line-height: $line-height-tight;
    }
  }

  &__subtitle {
    margin-top: var(--spacing-xs);
    font-size: $font-size-sm;
    color: var(--text-secondary);
    line-height: $line-height-base;
  }

  &__actions {
    @include flex-center;
    gap: var(--spacing-sm);
  }

  &__body {
    padding: var(--spacing-xl);
  }

  &__footer {
    padding: var(--spacing-lg) var(--spacing-xl);
    background: var(--bg-secondary);
    border-top: 1px solid var(--border-light);
  }

  &__loading {
    padding: var(--spacing-xl);
  }

  // 响应式设计
  @include respond-to(sm) {
    &__header,
    &__body,
    &__footer {
      padding-left: var(--spacing-md);
      padding-right: var(--spacing-md);
    }

    &__body {
      padding-top: var(--spacing-lg);
      padding-bottom: var(--spacing-lg);
    }

    &__header {
      flex-direction: column;
      align-items: stretch;
      gap: var(--spacing-md);
      min-height: auto;
    }

    &__actions {
      justify-content: flex-start;
    }
  }

  @include respond-to(xs) {
    &__header,
    &__body,
    &__footer {
      padding-left: var(--spacing-sm);
      padding-right: var(--spacing-sm);
    }
  }
}

// Element UI 样式覆盖
.admin-card {
  :deep(.el-skeleton) {
    .el-skeleton__item {
      background: linear-gradient(90deg, var(--bg-secondary) 25%, var(--bg-tertiary) 50%, var(--bg-secondary) 75%);
      background-size: 200% 100%;
      animation: loading 1.5s infinite;
    }
  }
}

@keyframes loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
</style>