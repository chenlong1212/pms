<template>
  <div class="video-stream" v-loading="loading">
    <div v-if="label" class="video-stream__label">{{ label }}</div>
    <div class="video-stream__container">
      <video
        ref="videoRef"
        class="video-stream__player"
        controls
        playsinline
        autoplay
        muted
      />
      <div v-if="!url && !loading" class="video-stream__placeholder">
        <span>{{ placeholderText }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import Hls from 'hls.js'

const props = withDefaults(defineProps<{
  url: string
  label?: string
  placeholderText?: string
}>(), {
  placeholderText: '暂无画面',
})

const videoRef = ref<HTMLVideoElement>()
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
  cleanup()
  if (!props.url || !videoRef.value) return

  loading.value = true
  const video = videoRef.value

  if (Hls.isSupported()) {
    hls = new Hls({
      enableWorker: true,
      lowLatencyMode: false,
    })
    hls.loadSource(props.url)
    hls.attachMedia(video)
    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      loading.value = false
      video.play().catch(() => { loading.value = false })
    })
    hls.on(Hls.Events.ERROR, (_, data) => {
      if (data.fatal) {
        loading.value = false
        cleanup()
      }
    })
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = props.url
    video.addEventListener('loadedmetadata', () => {
      loading.value = false
      video.play().catch(() => { loading.value = false })
    }, { once: true })
  } else {
    loading.value = false
  }
}

watch(() => props.url, (newUrl) => {
  if (newUrl) startPlay()
  else cleanup()
}, { immediate: true })

onBeforeUnmount(() => {
  cleanup()
})
</script>

<style scoped>
.video-stream {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.video-stream__label {
  padding: 4px 8px;
  font-size: 10px;
  color: var(--text-muted);
  background: var(--panel-elevated);
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0;
}

.video-stream__container {
  position: relative;
  width: 100%;
  flex: 1;
  min-height: 0;
  background: #050a10;
}

.video-stream__player {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.video-stream__placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  font-size: 11px;
  pointer-events: none;
}
</style>
