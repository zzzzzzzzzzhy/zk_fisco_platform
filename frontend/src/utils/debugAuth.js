/**
 * 认证调试工具
 * 用于诊断403权限错误
 */

class AuthDebugger {
  constructor() {
    this.baseURL = 'http://localhost:8080/api'
  }

  // 获取当前用户信息
  async getCurrentUser() {
    try {
      const token = localStorage.getItem('token') || sessionStorage.getItem('token') || localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token')

      if (!token) {
        console.error('❌ 没有找到认证token')
        console.log('💡 请先在登录页面登录')
        return null
      }

      console.log('🔑 Token存在:', token.substring(0, 20) + '...')

      const response = await fetch(`${this.baseURL}/auth/current-user`, {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'application/json'
        }
      })

      if (response.ok) {
        const user = await response.json()
        console.log('✅ 当前用户信息:', user)
        return user
      } else {
        console.error('❌ 获取用户信息失败:', response.status, response.statusText)
        const errorText = await response.text()
        console.error('错误详情:', errorText)
        return null
      }
    } catch (error) {
      console.error('❌ 请求失败:', error)
      return null
    }
  }

  // 测试内容分享API权限
  async testContentSharePermissions() {
    console.log('🔍 开始测试内容分享API权限...')

    const endpoints = [
      { method: 'GET', path: '/content-shares', description: '获取内容分享列表' },
      { method: 'GET', path: '/content-shares/admin/list', description: '管理员获取内容分享列表' },
      { method: 'GET', path: '/content-shares/presigned-url?mediaType=IMAGE&fileName=test.jpg', description: '获取预签名上传URL' }
    ]

    const token = localStorage.getItem('token') || sessionStorage.getItem('token') || localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token')

    for (const endpoint of endpoints) {
      try {
        console.log(`\n📡 测试: ${endpoint.description}`)
        console.log(`🔗 请求: ${endpoint.method} ${endpoint.path}`)

        const response = await fetch(`${this.baseURL}${endpoint.path}`, {
          method: endpoint.method,
          headers: {
            'Authorization': token ? `Bearer ${token}` : '',
            'Content-Type': 'application/json'
          }
        })

        console.log(`📊 响应状态: ${response.status} ${response.statusText}`)

        if (response.ok) {
          console.log('✅ 请求成功')
          try {
            const data = await response.json()
            console.log('📄 响应数据:', data)
          } catch (e) {
            console.log('📄 响应不是JSON格式')
          }
        } else {
          console.log('❌ 请求失败')
          try {
            const errorText = await response.text()
            console.log('📄 错误详情:', errorText)
          } catch (e) {
            console.log('📄 无法读取错误详情')
          }
        }
      } catch (error) {
        console.error('❌ 网络错误:', error.message)
      }
    }
  }

  // 检查token有效性
  async checkTokenValidity() {
    console.log('🔍 检查token有效性...')

    const token = localStorage.getItem('token') || sessionStorage.getItem('token') || localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token')

    if (!token) {
      console.error('❌ 没有找到token')
      console.log('💡 请先访问登录页面进行登录')
      console.log('🔗 登录页面地址:', window.location.origin + '/login')
      return false
    }

    try {
      // 解析JWT token
      const parts = token.split('.')
      if (parts.length === 3) {
        const payload = JSON.parse(atob(parts[1]))
        console.log('📋 Token信息:')
        console.log('  - 用户ID:', payload.sub || payload.userId)
        console.log('  - 用户名:', payload.username || payload.name)
        console.log('  - 角色:', payload.role || payload.authorities)
        console.log('  - 过期时间:', new Date(payload.exp * 1000).toLocaleString())
        console.log('  - 签发时间:', new Date(payload.iat * 1000).toLocaleString())

        const now = Date.now() / 1000
        if (payload.exp < now) {
          console.error('❌ Token已过期')
          return false
        }

        console.log('✅ Token格式正确且未过期')
        return true
      } else {
        console.error('❌ Token格式不正确')
        return false
      }
    } catch (error) {
      console.error('❌ 解析token失败:', error)
      return false
    }
  }

  // 清除认证信息
  clearAuth() {
    localStorage.removeItem('token')
    localStorage.removeItem('auth_token')
    localStorage.removeItem('userInfo')
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('auth_token')
    console.log('🧹 已清除所有认证信息')
  }

  // 完整的诊断流程
  async runDiagnosis() {
    console.log('🏥 开始完整认证诊断...')
    console.log('=' .repeat(50))

    // 1. 检查token
    const tokenValid = await this.checkTokenValidity()

    // 2. 获取用户信息
    const user = await this.getCurrentUser()

    if (user) {
      // 3. 检查用户角色
      console.log('\n👤 用户角色分析:')
      if (user.role === 'ADMIN') {
        console.log('✅ 用户具有ADMIN权限')
      } else if (user.role === 'USER') {
        console.log('⚠️ 用户只有USER权限')
        console.log('❌ 这可能是403错误的原因 - 管理员接口需要ADMIN角色')
      } else {
        console.log('❓ 未知角色:', user.role)
      }

      // 4. 检查权限列表
      if (user.authorities) {
        console.log('🔐 用户权限列表:')
        user.authorities.forEach(auth => {
          console.log('  -', auth.authority || auth)
        })
      }
    }

    // 5. 测试API权限
    await this.testContentSharePermissions()

    console.log('\n' + '=' .repeat(50))
    console.log('🏥 诊断完成')

    return {
      tokenValid,
      user,
      suggestions: this.getSuggestions(tokenValid, user)
    }
  }

  // 获取修复建议
  getSuggestions(tokenValid, user) {
    const suggestions = []

    if (!tokenValid) {
      suggestions.push('🔄 重新登录获取新的token')
    }

    if (!user) {
      suggestions.push('🔐 检查登录状态，可能需要重新登录')
    } else if (user.role !== 'ADMIN') {
      suggestions.push('👑 联系管理员获取ADMIN权限')
      suggestions.push('📝 或者使用普通用户接口而非管理员接口')
    }

    if (suggestions.length === 0) {
      suggestions.push('✅ 认证状态正常，可能是其他问题导致的403错误')
    }

    return suggestions
  }
}

// 创建全局实例
const authDebugger = new AuthDebugger()

// 暴露到全局
if (typeof window !== 'undefined') {
  window.authDebugger = authDebugger

  // 自动诊断
  console.log('🔧 认证调试工具已加载')
  console.log('💡 使用方法:')
  console.log('  - authDebugger.runDiagnosis() - 运行完整诊断')
  console.log('  - authDebugger.getCurrentUser() - 获取当前用户')
  console.log('  - authDebugger.testContentSharePermissions() - 测试API权限')
  console.log('  - authDebugger.clearAuth() - 清除认证信息')
  console.log('  - authDebugger.goToLogin() - 跳转到登录页面')

  // 添加快速跳转登录方法
  authDebugger.goToLogin = function() {
    console.log('🔗 跳转到登录页面...')
    window.location.href = '/login'
  }

  // 如果在内容分享页面，自动运行诊断
  if (window.location.href.includes('content-share') ||
      window.location.href.includes('admin')) {
    setTimeout(() => {
      console.log('🚀 检测到内容分享页面，自动运行认证诊断...')
      authDebugger.runDiagnosis().then(result => {
        if (!result.tokenValid || !result.user) {
          console.log('\n❌ 检测到未登录状态')
          console.log('💡 3秒后自动跳转到登录页面...')
          setTimeout(() => {
            authDebugger.goToLogin()
          }, 3000)
        }
      })
    }, 2000)
  }
}

export default authDebugger