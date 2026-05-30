<template>
  <div class="admin-stats">
    <div class="stats-cards">
      <div
        v-for="(stat, index) in stats"
        :key="index"
        class="stat-card"
        :class="`stat-card--${stat.type || 'primary'}`"
      >
        <div class="stat-card__icon">
          <i :class="stat.icon"></i>
        </div>
        <div class="stat-card__content">
          <div class="stat-card__value">
            <CountUp :end-value="stat.value" :duration="2" />
            <span v-if="stat.suffix" class="stat-card__suffix">{{ stat.suffix }}</span>
          </div>
          <div class="stat-card__label">{{ stat.label }}</div>
          <div v-if="stat.change" class="stat-card__change">
            <i :class="stat.change > 0 ? 'el-icon-top' : 'el-icon-bottom'"></i>
            <span :class="{ 'positive': stat.change > 0, 'negative': stat.change < 0 }">
              {{ Math.abs(stat.change) }}%
            </span>
          </div>
        </div>
        <div v-if="stat.chart" class="stat-card__chart">
          <MiniChart :data="stat.chart" :color="stat.type || 'primary'" />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import CountUp from 'vue-countup-v2'
import MiniChart from './MiniChart.vue'

export default {
  name: 'AdminStats',
  components: {
    CountUp,
    MiniChart
  },
  props: {
    stats: {
      type: Array,
      required: true,
      default: () => []
    }
  }
}
</script>

<style lang="scss" scoped>
@import '@/styles/admin-design-system.scss';

.admin-stats {
  margin-bottom: var(--spacing-xl);
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: var(--spacing-lg);

  @include respond-to(sm) {
    grid-template-columns: 1fr;
    gap: var(--spacing-md);
  }
}

.stat-card {
  background: var(--bg-primary);
  border-radius: var(--border-radius-xl);
  box-shadow: var(--shadow-light);
  padding: var(--spacing-lg);
  position: relative;
  overflow: hidden;
  transition: all $transition-base;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-base);
  }

  &__icon {
    width: 48px;
    height: 48px;
    border-radius: $border-radius-lg;
    @include flex-center;
    margin-bottom: var(--spacing-md);
    font-size: 20px;
    color: var(--bg-primary);
    position: relative;
    z-index: 2;

    &::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      border-radius: $border-radius-lg;
      opacity: 0.1;
      z-index: -1;
    }
  }

  &__content {
    position: relative;
    z-index: 2;
  }

  &__value {
    font-size: $font-size-3xl;
    font-weight: $font-weight-bold;
    color: var(--text-primary);
    margin-bottom: var(--spacing-xs);
    @include flex-center;
    align-items: baseline;
    gap: var(--spacing-xs);
  }

  &__suffix {
    font-size: $font-size-lg;
    font-weight: $font-weight-normal;
    color: var(--text-secondary);
  }

  &__label {
    font-size: $font-size-sm;
    color: var(--text-secondary);
    margin-bottom: var(--spacing-sm);
    line-height: $line-height-base;
  }

  &__change {
    @include flex-center;
    gap: var(--spacing-xs);
    font-size: $font-size-xs;
    font-weight: $font-weight-medium;

    i {
      font-size: 12px;
    }

    .positive {
      color: var(--success-color);
    }

    .negative {
      color: var(--danger-color);
    }
  }

  &__chart {
    position: absolute;
    top: var(--spacing-sm);
    right: var(--spacing-sm);
    width: 60px;
    height: 30px;
    opacity: 0.3;
  }

  // 不同类型的颜色主题
  &--primary {
    .stat-card__icon {
      background: var(--primary-color);
      &::before {
        background: var(--primary-color);
      }
    }
    .stat-card__chart {
      color: var(--primary-color);
    }
  }

  &--success {
    .stat-card__icon {
      background: var(--success-color);
      &::before {
        background: var(--success-color);
      }
    }
    .stat-card__chart {
      color: var(--success-color);
    }
  }

  &--warning {
    .stat-card__icon {
      background: var(--warning-color);
      &::before {
        background: var(--warning-color);
      }
    }
    .stat-card__chart {
      color: var(--warning-color);
    }
  }

  &--danger {
    .stat-card__icon {
      background: var(--danger-color);
      &::before {
        background: var(--danger-color);
      }
    }
    .stat-card__chart {
      color: var(--danger-color);
    }
  }

  &--info {
    .stat-card__icon {
      background: var(--info-color);
      &::before {
        background: var(--info-color);
      }
    }
    .stat-card__chart {
      color: var(--info-color);
    }
  }

  // 响应式设计
  @include respond-to(sm) {
    padding: var(--spacing-md);

    &__icon {
      width: 40px;
      height: 40px;
      font-size: 18px;
    }

    &__value {
      font-size: $font-size-2xl;
    }

    &__chart {
      width: 50px;
      height: 25px;
    }
  }
}
</style>