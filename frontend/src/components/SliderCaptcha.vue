<template>
  <div class="slider-captcha-wrapper">
    <!-- 触发按钮（可选，可以由父组件控制显示） -->
    <div v-if="!isVerified && showTrigger" class="captcha-trigger" @click="showCaptcha">
      <i class="el-icon-lock"></i>
      点击进行安全验证
    </div>

    <!-- 验证成功提示 -->
    <div v-if="isVerified" class="captcha-success">
      <i class="el-icon-success"></i>
      <span>验证通过</span>
    </div>

    <!-- 滑块验证码（直接使用组件自带的弹窗） -->
    <Vcode
      :show="showCaptchaDialog"
      @success="onSuccess"
      @close="onClose"
      :imgs="captchaImages"
      :canvasWidth="310"
      :canvasHeight="155"
      :sliderSize="42"
      :accuracy="5"
    />
  </div>
</template>

<script>
import Vcode from 'vue-puzzle-vcode'

export default {
  name: 'SliderCaptcha',
  components: {
    Vcode
  },
  props: {
    // 是否显示触发按钮（如果为 false，需要父组件调用 show() 方法）
    showTrigger: {
      type: Boolean,
      default: false
    },
    // 是否自动显示
    autoShow: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      showCaptchaDialog: false,
      isVerified: false,
      captchaToken: '',
      // 使用在线图片作为背景
      captchaImages: [
        'https://picsum.photos/310/155?random=1',
        'https://picsum.photos/310/155?random=2',
        'https://picsum.photos/310/155?random=3',
        'https://picsum.photos/310/155?random=4',
        'https://picsum.photos/310/155?random=5'
      ]
    }
  },
  mounted() {
    if (this.autoShow) {
      this.showCaptcha()
    }
  },
  methods: {
    /**
     * 显示验证码（供父组件调用）
     */
    show() {
      this.showCaptcha()
    },

    /**
     * 显示验证码弹窗
     */
    showCaptcha() {
      if (this.isVerified) {
        // 如果已经验证过，直接触发成功事件
        this.$emit('success', this.captchaToken)
        return
      }
      this.showCaptchaDialog = true
    },

    /**
     * 验证成功回调
     */
    onSuccess() {
      // 生成验证令牌（这里用时间戳 + 随机数，实际生产中应该由后端生成）
      this.captchaToken = `captcha_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`
      this.isVerified = true
      this.showCaptchaDialog = false

      // 触发成功事件，传递令牌给父组件
      this.$emit('success', this.captchaToken)
      
      this.$message.success('验证成功！')
    },

    /**
     * 关闭验证码
     */
    onClose() {
      this.showCaptchaDialog = false
      this.$emit('cancel')
    },

    /**
     * 重置验证状态（供父组件调用）
     */
    reset() {
      this.isVerified = false
      this.captchaToken = ''
    },

    /**
     * 获取验证令牌（供父组件调用）
     */
    getToken() {
      return this.captchaToken
    }
  }
}
</script>

<style scoped>
.slider-captcha-wrapper {
  display: inline-block;
}

.captcha-trigger {
  display: inline-flex;
  align-items: center;
  padding: 8px 16px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
  font-size: 14px;
}

.captcha-trigger:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
}

.captcha-trigger i {
  margin-right: 6px;
}

.captcha-success {
  display: inline-flex;
  align-items: center;
  padding: 8px 16px;
  background: #f0f9ff;
  color: #67c23a;
  border: 1px solid #c6e2ff;
  border-radius: 4px;
  font-size: 14px;
}

.captcha-success i {
  margin-right: 6px;
  font-size: 16px;
}
</style>

<!-- 全局样式：提升滑块验证码弹层的层级，避免被 el-dialog 遮挡 -->
<style>
/* vue-puzzle-vcode 根容器 */
.vue-puzzle-vcode {
  z-index: 4000 !important; /* 高于 ElementUI 的 2000+ 层级 */
}

/* 内部弹框 */
.vue-auth-box_ {
  z-index: 4001 !important;
}
</style>

