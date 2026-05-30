import { login, register } from '@/api/auth'

const state = {
  token: localStorage.getItem('token') || '',
  userInfo: JSON.parse(localStorage.getItem('userInfo') || '{}')
}

const mutations = {
  SET_TOKEN(state, token) {
    state.token = token
    localStorage.setItem('token', token)
  },
  SET_USER_INFO(state, userInfo) {
    state.userInfo = userInfo
    localStorage.setItem('userInfo', JSON.stringify(userInfo))
  },
  LOGOUT(state) {
    state.token = ''
    state.userInfo = {}
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }
}

const actions = {
  async login({ commit }, credentials) {
    const res = await login(credentials)
    if (res.code === 200) {
      commit('SET_TOKEN', res.data.token)
      commit('SET_USER_INFO', {
        userId: res.data.userId,
        username: res.data.username,
        email: res.data.email,
        role: res.data.role || 'USER',
        walletAddress: res.data.walletAddress
      })
      return res.data
    }
    throw new Error(res.message)
  },
  async register(context, userData) {
    const res = await register(userData)
    if (res.code === 200) {
      return res.data
    }
    throw new Error(res.message)
  },
  logout({ commit }) {
    commit('LOGOUT')
  }
}

const getters = {
  isLoggedIn: state => !!state.token,
  userId: state => state.userInfo.userId,
  username: state => state.userInfo.username,
  userRole: state => state.userInfo.role || 'USER',
  walletAddress: state => state.userInfo.walletAddress
}

export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters
}
