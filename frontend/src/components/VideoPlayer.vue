<template>
  <div class="video-player">
    <video
      ref="videoRef"
      class="video-js vjs-big-play-centered"
      playsinline
      webkit-playsinline
    />
  </div>
</template>

<script>
import videojs from 'video.js'
import 'video.js/dist/video-js.css'
import Hls from 'hls.js'

export default {
  name: 'VideoPlayer',
  props: {
    src: {
      type: String,
      required: true
    },
    poster: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      player: null
    }
  },
  mounted() {
    this.initPlayer()
  },
  beforeDestroy() {
    if (this.player) {
      this.player.dispose()
      this.player = null
    }
  },
  watch: {
    src(newVal) {
      if (this.player && newVal) {
        this.setSource(newVal)
      }
    }
  },
  methods: {
    initPlayer() {
      if (!this.$refs.videoRef) return

      this.player = videojs(this.$refs.videoRef, {
        autoplay: false,
        controls: true,
        preload: 'metadata',
        fluid: true,
        poster: this.poster || ''
      })

      this.setSource(this.src)
    },
    setSource(url) {
      if (!url || !this.player) return

      // HLS 流用 hls.js 处理，其余交给 video.js 默认逻辑
      if (url.includes('.m3u8')) {
        if (Hls.isSupported()) {
          const hls = new Hls()
          hls.loadSource(url)
          hls.attachMedia(this.$refs.videoRef)
        } else {
          this.player.src({
            src: url,
            type: 'application/vnd.apple.mpegurl'
          })
        }
      } else {
        this.player.src({
          src: url,
          type: 'video/mp4'
        })
      }
    }
  }
}
</script>

<style scoped>
.video-player {
  width: 100%;
}
</style>


