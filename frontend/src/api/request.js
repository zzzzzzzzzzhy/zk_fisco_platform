import axios from 'axios'
import { Message } from 'element-ui'
import store from '@/store'
import router from '@/router'

const request = axios.create({
  baseURL: process.env.VUE_APP_API_BASE_URL || '/api',
  timeout: 30000
})

// 重试配置
request.defaults.retry = 3  // 重试次数
request.defaults.retryDelay = 1000  // 重试延迟（毫秒）

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = store.state.user.token
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }

    // 添加用户ID到请求头，用于后端识别用户
    const userId = store.state.user.userId || localStorage.getItem('userId') || '1'
    config.headers['X-User-Id'] = userId

    return config
  },
  error => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    const res = response.data

    if (res.code !== 200) {
      Message.error(res.message || '请求失败')

      if (res.code === 401) {
        store.dispatch('user/logout')
        router.push({ name: 'Login' })
      }

      return Promise.reject(new Error(res.message || 'Error'))
    }

    return res
  },
  error => {
    console.error('Response error:', error)
    
    const config = error.config
    
    // 如果配置不存在或没有重试设置，使用默认值
    if (!config || !config.retry) {
      config.retry = request.defaults.retry
      config.retryDelay = request.defaults.retryDelay
      config.__retryCount = config.__retryCount || 0
    }
    
    // 判断是否应该重试
    const shouldRetry = (
      // 网络错误或超时
      (!error.response || error.code === 'ECONNABORTED') &&
      // 还有重试次数
      config.__retryCount < config.retry &&
      // 不是认证错误
      error.response?.status !== 401 &&
      error.response?.status !== 403
    )
    
    if (shouldRetry) {
      config.__retryCount += 1
      
      console.log(`🔄 重试请求 (${config.__retryCount}/${config.retry}): ${config.url}`)
      
      // 创建延迟 Promise
      const delay = new Promise(resolve => {
        setTimeout(resolve, config.retryDelay)
      })
      
      // 延迟后重试请求
      return delay.then(() => request(config))
    }
    
    // 不重试，显示错误消息
    if (error.response) {
      const status = error.response.status
      const message = error.response.data?.message || '请求失败'

      if (status === 401) {
        Message.error('登录已过期，请重新登录')
        store.dispatch('user/logout')
        router.push({ name: 'Login' })
      } else if (status === 403) {
        Message.error('您没有权限执行此操作')
      } else if (status >= 500) {
        Message.error('服务器错误，请稍后重试')
      } else {
        Message.error(message)
      }
    } else if (error.code === 'ECONNABORTED') {
      Message.error('请求超时，请检查网络连接')
    } else {
      Message.error('网络错误，请检查网络连接')
    }

    return Promise.reject(error)
  }
)

export default request
