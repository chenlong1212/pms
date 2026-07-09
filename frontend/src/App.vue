<template>
  <div class="app-layout">
    <header class="app-header">
      <h1>水质监测系统</h1>
    </header>

    <main v-loading="loading" class="app-main">
      <section
        class="metrics-panel clickable"
        @click="openTrend"
      >
        <div class="metrics-row">
          <div class="metric-item">
            <div class="metric-label">溶解氧</div>
            <div class="metric-value dox">
              {{ latestData?.dox ?? '--' }}
              <span class="metric-unit">mg/L</span>
            </div>
          </div>
          <div class="metric-item">
            <div class="metric-label">pH</div>
            <div class="metric-value ph">{{ latestData?.ph ?? '--' }}</div>
          </div>
          <div class="metric-item">
            <div class="metric-label">水温</div>
            <div class="metric-value thw">
              {{ latestData?.thw ?? '--' }}
              <span class="metric-unit">℃</span>
            </div>
          </div>
        </div>
        <div class="collect-time">
          最新采集：{{ latestData?.collectTimeStr ?? '暂无数据' }}
        </div>
      </section>

      <section class="placeholder-section">
        <VideoPlayer />
      </section>

      <section class="placeholder-section">
        <div class="placeholder-box large">
          <span>预留功能区域</span>
        </div>
      </section>
    </main>

    <!-- 趋势图全屏弹窗 -->
    <teleport to="body">
      <transition name="trend-fade">
        <div v-if="trendDialogVisible" class="trend-overlay" @click.self="trendDialogVisible = false">
          <div class="trend-panel">
            <div class="trend-header">
              <h2>水质趋势</h2>
              <div class="trend-toolbar">
                <el-radio-group v-model="trendHours" size="small" @change="fetchTrend">
                  <el-radio-button :value="6">6小时</el-radio-button>
                  <el-radio-button :value="12">12小时</el-radio-button>
                  <el-radio-button :value="24">24小时</el-radio-button>
                  <el-radio-button :value="72">3天</el-radio-button>
                  <el-radio-button :value="168">7天</el-radio-button>
                </el-radio-group>
                <button class="trend-close" @click="trendDialogVisible = false" aria-label="关闭">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                </button>
              </div>
            </div>
            <div class="trend-body">
              <TrendChart :data="trendData" />
            </div>
          </div>
        </div>
      </transition>
    </teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import TrendChart from './components/TrendChart.vue'
import VideoPlayer from './components/VideoPlayer.vue'
import { getLatest, getTrend, type DeviceData } from './api/device'

const loading = ref(false)
const latestData = ref<DeviceData | null>(null)
const trendDialogVisible = ref(false)
const trendData = ref<DeviceData[]>([])
const trendHours = ref(24)

let refreshTimer: ReturnType<typeof setInterval> | null = null

async function fetchLatest() {
  try {
    const res = await getLatest()
    latestData.value = res.data.data
  } catch {
    ElMessage.error('获取最新数据失败')
  }
}

async function fetchTrend() {
  try {
    const res = await getTrend(trendHours.value)
    trendData.value = res.data.data
  } catch {
    ElMessage.error('获取趋势数据失败')
  }
}

function openTrend() {
  trendDialogVisible.value = true
  fetchTrend()
}

onMounted(() => {
  loading.value = true
  fetchLatest().finally(() => { loading.value = false })
  refreshTimer = setInterval(fetchLatest, 60_000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style>
:root {
  --app-bg: #0d1117;
  --panel-bg: #161b22;
  --border-color: #30363d;
  --text-primary: #e6edf3;
  --text-secondary: #8b949e;
}

html, body, #app {
  margin: 0;
  padding: 0;
  width: 100%;
  min-height: 100vh;
  background: var(--app-bg);
  color: var(--text-primary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

* {
  box-sizing: border-box;
}
</style>

<style scoped>
.app-layout {
  min-height: 100vh;
  width: 100%;
}

.app-header {
  display: flex;
  align-items: center;
  padding: 16px 24px;
  background: var(--panel-bg);
  border-bottom: 1px solid var(--border-color);
}

.app-header h1 {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.app-main {
  padding: 20px 24px;
  min-height: calc(100vh - 60px);
}

.metrics-panel {
  margin-bottom: 20px;
  padding: 24px 16px;
  background: var(--panel-bg);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  transition: border-color 0.2s, transform 0.15s;
}

.metrics-panel.clickable {
  cursor: pointer;
}

.metrics-panel.clickable:hover {
  border-color: #58a6ff;
  transform: translateY(-2px);
}

.metrics-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.metric-item {
  flex: 1;
  text-align: center;
}

.metric-label {
  font-size: 14px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.metric-value {
  font-size: 36px;
  font-weight: 600;
}

.metric-unit {
  font-size: 16px;
  font-weight: 400;
  color: var(--text-secondary);
  margin-left: 4px;
}

.dox { color: #58a6ff; }
.ph { color: #3fb950; }
.thw { color: #d29922; }

.collect-time {
  font-size: 13px;
  color: var(--text-secondary);
  text-align: center;
}

.placeholder-section {
  margin-bottom: 20px;
}

.placeholder-box {
  min-height: 200px;
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--panel-bg);
  color: var(--text-secondary);
  font-size: 14px;
}

.placeholder-box.large {
  min-height: 320px;
}

.trend-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
}

.trend-panel {
  width: 70vw;
  height: 60vh;
  background: var(--panel-bg);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.trend-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.trend-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.trend-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
}

.trend-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.trend-close:hover {
  background: var(--border-color);
  color: var(--text-primary);
}

.trend-body {
  flex: 1;
  min-height: 0;
  padding: 16px;
}

.trend-fade-enter-active,
.trend-fade-leave-active {
  transition: opacity 0.25s ease;
}

.trend-fade-enter-from,
.trend-fade-leave-to {
  opacity: 0;
}
</style>
