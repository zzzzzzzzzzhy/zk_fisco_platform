<template>
  <div class="wee-price" :class="sizeClass">
    <div class="price-main" v-if="!loading">
      <span class="price-label">WEE</span>
      <span class="price-value">{{ formatUSD(price) }}</span>
      <span 
        class="price-change" 
        :class="priceChange >= 0 ? 'positive' : 'negative'"
      >
        {{ priceChangeIcon }} {{ formatPriceChange(priceChange) }}%
      </span>
    </div>
    <div class="price-loading" v-else>
      <el-skeleton :rows="1" animated />
    </div>
  </div>
</template>

<script>
import priceService from '@/services/priceService'

export default {
  name: 'WEEPrice',
  props: {
    size: {
      type: String,
      default: 'medium', // 'small', 'medium', 'large'
      validator: (value) => ['small', 'medium', 'large'].includes(value)
    },
    autoUpdate: {
      type: Boolean,
      default: true
    }
  },
  data() {
    return {
      price: 0,
      priceChange: 0,
      loading: true,
      unsubscribe: null
    }
  },
  computed: {
    sizeClass() {
      return `wee-price-${this.size}`
    },
    priceChangeIcon() {
      return priceService.getPriceChangeIcon()
    }
  },
  mounted() {
    if (this.autoUpdate) {
      // 订阅价格更新
      this.unsubscribe = priceService.subscribe(data => {
        this.price = data.price
        this.priceChange = data.priceChange24h
        this.loading = false
      })
    } else {
      // 只获取一次价格
      this.price = priceService.getPrice()
      this.priceChange = priceService.getPriceChange()
      this.loading = false
    }
  },
  beforeUnmount() {
    if (this.unsubscribe) {
      this.unsubscribe()
    }
  },
  methods: {
    formatUSD(value) {
      return priceService.formatUSD(value)
    },
    formatPriceChange(value) {
      const abs = Math.abs(value)
      return value >= 0 ? `+${abs.toFixed(2)}` : `-${abs.toFixed(2)}`
    }
  }
}
</script>

<style scoped>
.wee-price {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.price-main {
  display: flex;
  align-items: center;
  gap: 6px;
}

.price-label {
  font-weight: 600;
  color: #303133;
}

.price-value {
  font-weight: 600;
  color: #409eff;
}

.price-change {
  font-size: 0.85em;
  font-weight: 500;
  padding: 2px 6px;
  border-radius: 4px;
}

.price-change.positive {
  color: #67c23a;
  background: #f0f9ff;
}

.price-change.negative {
  color: #f56c6c;
  background: #fef0f0;
}

/* 尺寸变体 */
.wee-price-small .price-label,
.wee-price-small .price-value {
  font-size: 12px;
}

.wee-price-small .price-change {
  font-size: 11px;
}

.wee-price-medium .price-label,
.wee-price-medium .price-value {
  font-size: 14px;
}

.wee-price-medium .price-change {
  font-size: 12px;
}

.wee-price-large .price-label,
.wee-price-large .price-value {
  font-size: 18px;
}

.wee-price-large .price-change {
  font-size: 14px;
}

.price-loading {
  min-width: 150px;
}
</style>

