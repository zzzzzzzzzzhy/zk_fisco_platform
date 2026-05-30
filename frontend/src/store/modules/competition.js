import { getCompetitions, getCompetitionById } from '@/api/competition'

const state = {
  competitions: [],
  currentCompetition: null,
  total: 0,
  loading: false
}

const mutations = {
  SET_COMPETITIONS(state, competitions) {
    state.competitions = competitions
  },
  SET_CURRENT_COMPETITION(state, competition) {
    state.currentCompetition = competition
  },
  SET_TOTAL(state, total) {
    state.total = total
  },
  SET_LOADING(state, loading) {
    state.loading = loading
  }
}

const actions = {
  async fetchCompetitions({ commit }, params) {
    commit('SET_LOADING', true)
    try {
      const res = await getCompetitions(params)
      if (res.code === 200) {
        commit('SET_COMPETITIONS', res.data.records)
        commit('SET_TOTAL', res.data.total)
      }
    } finally {
      commit('SET_LOADING', false)
    }
  },
  async fetchCompetitionById({ commit }, id) {
    commit('SET_LOADING', true)
    try {
      const res = await getCompetitionById(id)
      if (res.code === 200) {
        commit('SET_CURRENT_COMPETITION', res.data)
      }
    } finally {
      commit('SET_LOADING', false)
    }
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
