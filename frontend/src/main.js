import Vue from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
import './assets/styles/global.scss'
// import VueLazyload from 'vue-lazyload' // 禁用懒加载
import txNotify from './utils/transactionNotification'
import VueVirtualScroller from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
import errorHandler from './utils/errorHandler'

// 在开发环境下导入调试工具
if (process.env.NODE_ENV === 'development') {
  import('./utils/web3Debug').then(() => {
    console.log('🔧 Web3 调试工具已加载')
  })

  import('./utils/debugAuth').then(() => {
    console.log('🔧 认证调试工具已加载')
  })
}

// 导入钱包状态管理器
import('./utils/walletStateManager').then(() => {
  console.log('🌐 钱包状态管理器已加载')
})

Vue.config.productionTip = false

Vue.use(ElementUI)

// 禁用图片懒加载以解决渐进式加载问题
// Vue.use(VueLazyload, {
//   preLoad: 1.3,        // 预加载高度比例
//   attempt: 1,          // 尝试加载次数
//   lazyComponent: true, // 支持组件懒加载
//   observer: false,     // 禁用 Intersection Observer，立即加载所有图片
//   observerOptions: {
//     rootMargin: '1000px', // 大的预加载距离
//     threshold: 0.0
//   }
// })

// 注册全局交易通知
Vue.prototype.$txNotify = txNotify

// 注册全局错误处理器
Vue.prototype.$handleError = errorHandler.handleError
Vue.prototype.$handleTxError = errorHandler.handleTransactionError
Vue.prototype.$handleApiError = errorHandler.handleApiError
Vue.prototype.$handleContractError = errorHandler.handleContractError

// 注册虚拟滚动
Vue.use(VueVirtualScroller)

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app')
