<template>
  <section class="video-section panel" :class="{ 'video-section--compact': compact }">
    <div class="video-header">
      <span class="video-title">实时监控</span>
      <span v-if="streamUrl" class="live-badge">
        <span class="live-badge__dot" />
        直播中
      </span>
    </div>

    <div class="video-layout" v-loading="pageLoading">
      <template v-for="slot in videoSlots" :key="slot.label">
        <VideoStream
          v-if="slot.type === 'stream'"
          :url="streamUrl"
          :label="slot.label"
          placeholder-text="暂无画面"
        />
        <div v-else class="embedded-monitor">
          <div class="embedded-monitor__label">{{ slot.label }}</div>
          <iframe
            class="embedded-monitor__frame"
            :src="slot.url"
            :title="slot.label"
            loading="lazy"
          />
        </div>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getVideoStreamUrl } from '../api/video'
import VideoStream from './VideoStream.vue'

defineProps<{
  compact?: boolean
}>()

const streamUrl = ref('')
const pageLoading = ref(false)

const videoSlots = [
  { label: '主画面', type: 'stream' as const },
  {
    label: '视频展示',
    type: 'iframe' as const,
    url: 'http://146.56.204.72:8005/ffia_show',
  },
  {
    label: '图片轮播',
    type: 'iframe' as const,
    url: 'http://146.56.204.72:8005/fby_show',
  },
]

onMounted(async () => {
  pageLoading.value = true
  try {
    const res = await getVideoStreamUrl()
    streamUrl.value = res.data.data ?? ''
  } catch {
    streamUrl.value = ''
  } finally {
    pageLoading.value = false
  }
})
</script>

<style scoped>
.video-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.video-section--compact {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.video-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.video-title {
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.video-layout {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 6px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.video-layout > :deep(.video-stream) {
  flex: 1;
  min-height: 0;
}

.embedded-monitor {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: #050a10;
}

.embedded-monitor__label {
  padding: 4px 8px;
  font-size: 10px;
  color: var(--text-muted);
  background: var(--panel-elevated);
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0;
}

.embedded-monitor__frame {
  width: 100%;
  flex: 1;
  min-height: 0;
  border: 0;
  background: #050a10;
}
</style>
