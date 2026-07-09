<template>
  <section class="video-section" v-loading="loading">
    <div class="video-header">
      <span class="video-title">实时监控</span>
    </div>

    <div class="video-container">
      <video
        ref="videoRef"
        class="video-player"
        controls
        playsinline
        autoplay
        muted
      />
      <div v-if="!streamUrl && !loading" class="video-placeholder">
        <span>未配置视频流地址</span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import Hls from 'hls.js'
import { getVideoStreamUrl } from '../api/video'

const videoRef = ref<HTMLVideoElement>()
const streamUrl = ref('')
const loading = ref(false)

let hls: Hls | null = null

function cleanup() {
  if (hls) {
    hls.destroy()
    hls = null
  }
  const video = videoRef.value
  if (video) {
    video.pause()
    video.removeAttribute('src')
    video.load()
  }
}

function startPlay() {
  if (!streamUrl.value || !videoRef.value) return

  loading.value = true
  const video = videoRef.value

  if (Hls.isSupported()) {
    hls = new Hls({
      enableWorker: true,
      lowLatencyMode: false,
    })
    hls.loadSource(streamUrl.value)
    hls.attachMedia(video)
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      loading.value = false
      video.play().catch(() => {
        loading.value = false
      })
    })
    hls.on(Hls.Events.ERROR, (_, data) => {
      if (data.fatal) {
        loading.value = false
        cleanup()
      }
    })
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = streamUrl.value
    video.addEventListener('loadedmetadata', () => {
      loading.value = false
      video.play().catch(() => {
        loading.value = false
      })
    }, { once: true })
  } else {
    loading.value = false
  }
}

function handlePageExit() {
  cleanup()
}

onMounted(async () => {
  try {
    const res = await getVideoStreamUrl()
    streamUrl.value = res.data.data ?? ''
    if (streamUrl.value) {
      startPlay()
    }
  } catch {
    streamUrl.value = ''
  }
  window.addEventListener('beforeunload', handlePageExit)
  window.addEventListener('pagehide', handlePageExit)
})

onBeforeUnmount(() => {
  cleanup()
  window.removeEventListener('beforeunload', handlePageExit)
  window.removeEventListener('pagehide', handlePageExit)
})
</script>

<style scoped>
.video-section {
  background: var(--panel-bg);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  overflow: hidden;
}

.video-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
}

.video-title {
  font-size: 14px;
  color: var(--text-secondary);
}

.video-container {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: #000;
}

.video-player {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.video-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  font-size: 14px;
  pointer-events: none;
}
</style>
