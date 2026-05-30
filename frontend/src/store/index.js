import Vue from 'vue'
import Vuex from 'vuex'
import user from './modules/user'
import competition from './modules/competition'
import governance from './modules/governance'

Vue.use(Vuex)

export default new Vuex.Store({
  modules: {
    user,
    competition,
    governance
  }
})
