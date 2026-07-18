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
      <VideoStream
        v-for="slot in videoSlots"
        :key="slot.label"
        :url="slot.enabled ? streamUrl : ''"
        :label="slot.label"
        :placeholder-text="slot.enabled ? '暂无画面' : '待开发'"
      />
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
  { label: '主画面', enabled: true },
  { label: '监控 1', enabled: false },
  { label: '监控 2', enabled: false },
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
</style>
