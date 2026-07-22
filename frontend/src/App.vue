<template>
  <div class="app-layout">
    <header class="app-header">
      <h1 class="app-header__title">池塘生产管理系统</h1>
      <div class="app-header__waterline" aria-hidden="true" />
    </header>

    <main v-loading="loading" class="app-body">
      <aside class="sidebar">
        <p class="collect-time">
          最新采集时间：{{ latestData?.collectTimeStr ?? '暂无数据' }}
        </p>

        <div class="metric-cards">
          <div
            v-for="metric in metrics"
            :key="metric.key"
            class="metric-card panel metric-card--clickable"
            :class="`metric-card--${metric.key}`"
            @click="openTrend"
          >
            <div class="metric-card__current">
              <span class="metric-card__label">{{ metric.label }}</span>
              <span class="metric-card__value" :class="metric.key">
                {{ metric.value }}
                <span v-if="metric.unit" class="metric-card__unit">{{ metric.unit }}</span>
              </span>
            </div>
            <div class="metric-card__predictions">
              <div
                v-for="min in predictMinutes"
                :key="`${metric.key}-${min}`"
                class="predict-row"
              >
                <span class="predict-label">{{ min }}分钟后</span>
                <span class="predict-value" :class="metric.key">
                  {{ formatPredictValue(predictions[metric.key][min], metric.precision) }}
                  <span v-if="metric.unit" class="predict-unit">{{ metric.unit }}</span>
                </span>
              </div>
            </div>
          </div>
        </div>

        <VideoPlayer class="sidebar-video" compact />
      </aside>

      <div class="main-content">
        <section class="biomass-section panel panel--padded">
          <div class="section-header">
            <h2 class="section-title">生物量估计与计数</h2>
            <div class="section-toolbar">
              <el-select
                v-model="selectedPondId"
                placeholder="选择池塘"
                style="width: 200px"
                @change="fetchBiomassTrend"
              >
                <el-option
                  v-for="pond in ponds"
                  :key="pond.id"
                  :label="`${pond.name}（${pond.fishSpecies}）`"
                  :value="pond.id"
                />
              </el-select>
              <el-radio-group v-model="biomassDays" size="small" @change="fetchBiomassTrend">
                <el-radio-button :value="7">7天</el-radio-button>
                <el-radio-button :value="30">30天</el-radio-button>
                <el-radio-button :value="90">90天</el-radio-button>
                <el-radio-button :value="180">180天</el-radio-button>
                <el-radio-button :value="365">365天</el-radio-button>
              </el-radio-group>
            </div>
          </div>
          <div v-loading="biomassLoading" class="biomass-body">
            <BiomassTrendChart v-if="biomassTrendData" :data="biomassTrendData" />
            <div v-else class="empty-state">暂无生物量数据</div>
          </div>
        </section>

        <div class="modules-grid">
          <div class="modules-col modules-col--left">
            <FeedingRecordSection compact />
            <section class="system-entry panel">
              <h2 class="system-entry__title">智能分析系统</h2>
              <div class="system-entry__buttons">
                <button
                  v-for="system in externalSystems"
                  :key="system.url"
                  class="system-entry__button"
                  type="button"
                  @click="openExternalSystem(system)"
                >
                  {{ system.name }}
                </button>
              </div>
            </section>
          </div>
          <div class="module-placeholder module-placeholder--tall">待开发模块 3</div>
          <ChatPanel class="module-chat" />
        </div>
      </div>
    </main>

    <teleport to="body">
      <transition name="trend-fade">
        <div v-if="trendDialogVisible" class="trend-overlay" @click.self="trendDialogVisible = false">
          <div class="trend-panel panel">
            <div class="trend-header">
              <h2 class="section-title">水质趋势</h2>
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

    <teleport to="body">
      <transition name="trend-fade">
        <div v-if="activeSystem" class="system-window-overlay" @click.self="closeExternalSystem">
          <section class="system-window panel" role="dialog" aria-modal="true" :aria-label="activeSystem.name">
            <header class="system-window__header">
              <h2 class="section-title">{{ activeSystem.name }}</h2>
              <button class="trend-close" type="button" aria-label="关闭" @click="closeExternalSystem">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                </svg>
              </button>
            </header>
            <iframe
              class="system-window__frame"
              :src="activeSystem.url"
              :title="activeSystem.name"
            />
          </section>
        </div>
      </transition>
    </teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import TrendChart from './components/TrendChart.vue'
import VideoPlayer from './components/VideoPlayer.vue'
import BiomassTrendChart from './components/BiomassTrendChart.vue'
import FeedingRecordSection from './components/FeedingRecordSection.vue'
import ChatPanel from './components/ChatPanel.vue'
import { getLatest, getTrend, type DeviceData } from './api/device'
import { getPonds, getBiomassTrend, type Pond, type BiomassTrend } from './api/biomass'
import { linearPredict, parseCollectTime, formatPredictValue } from './utils/predict'

const predictMinutes = [10, 30, 60] as const
type MetricKey = 'dox' | 'ph' | 'thw'
type PredictMap = Record<(typeof predictMinutes)[number], number | null>
type ExternalSystem = { name: string; url: string }

const externalSystems: ExternalSystem[] = [
  { name: '金鲳鱼分析', url: 'http://146.56.204.72:8002/' },
  { name: '智慧水产监控', url: 'http://146.56.204.72:8005/index' },
  { name: '鱼体质量估算', url: 'http://146.56.204.72:8008/' },
  { name: '声呐鱼类计数', url: 'http://146.56.204.72:8020/' },
  { name: '白鱼识别', url: 'http://146.56.204.72:8017/' },
  { name: '水产养殖系统', url: 'http://146.56.204.72:8099/' },
]

const loading = ref(false)
const latestData = ref<DeviceData | null>(null)
const trendDialogVisible = ref(false)
const trendData = ref<DeviceData[]>([])
const predictTrendData = ref<DeviceData[]>([])
const trendHours = ref(24)
const ponds = ref<Pond[]>([])
const selectedPondId = ref<number | null>(null)
const biomassDays = ref(30)
const biomassTrendData = ref<BiomassTrend | null>(null)
const biomassLoading = ref(false)
const activeSystem = ref<ExternalSystem | null>(null)

let refreshTimer: ReturnType<typeof setInterval> | null = null

const metrics = computed(() => [
  {
    key: 'dox' as const,
    label: '溶解氧 mg/L',
    value: latestData.value?.dox ?? '--',
    unit: '',
    precision: 2,
  },
  {
    key: 'ph' as const,
    label: 'pH',
    value: latestData.value?.ph ?? '--',
    unit: '',
    precision: 2,
  },
  {
    key: 'thw' as const,
    label: '水温 ℃',
    value: latestData.value?.thw ?? '--',
    unit: '',
    precision: 1,
  },
])

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

async function fetchPredictTrend() {
  try {
    const res = await getTrend(6)
    predictTrendData.value = res.data.data
  } catch {
    // 预测数据获取失败时静默处理
  }
}

function buildMetricPredictions(key: MetricKey): PredictMap {
  const recent = predictTrendData.value.slice(-12)
  const points = recent
    .map((d) => ({ time: parseCollectTime(d.collectTimeStr), value: d[key] }))
    .filter((p) => !Number.isNaN(p.time) && p.value != null)

  return Object.fromEntries(
    predictMinutes.map((min) => [min, linearPredict(points, min)]),
  ) as PredictMap
}

const predictions = computed(() => ({
  dox: buildMetricPredictions('dox'),
  ph: buildMetricPredictions('ph'),
  thw: buildMetricPredictions('thw'),
}))

function openTrend() {
  trendDialogVisible.value = true
  fetchTrend()
}

function openExternalSystem(system: ExternalSystem) {
  activeSystem.value = system
}

function closeExternalSystem() {
  activeSystem.value = null
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && activeSystem.value) closeExternalSystem()
}

async function fetchPonds() {
  try {
    const res = await getPonds()
    ponds.value = res.data.data
    if (ponds.value.length && selectedPondId.value == null) {
      selectedPondId.value = ponds.value[0].id
      await fetchBiomassTrend()
    }
  } catch {
    ElMessage.error('获取鱼塘列表失败')
  }
}

async function fetchBiomassTrend() {
  if (selectedPondId.value == null) return
  biomassLoading.value = true
  try {
    const res = await getBiomassTrend(selectedPondId.value, biomassDays.value)
    biomassTrendData.value = res.data.data
  } catch {
    ElMessage.error('获取生物量趋势失败')
  } finally {
    biomassLoading.value = false
  }
}

async function refreshData() {
  await Promise.all([fetchLatest(), fetchPredictTrend()])
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
  loading.value = true
  Promise.all([refreshData(), fetchPonds()]).finally(() => { loading.value = false })
  refreshTimer = setInterval(refreshData, 60_000)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.app-layout {
  height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.app-header {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: var(--header-height);
  padding: 0 24px;
  background: var(--panel-bg);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.app-header__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--text-primary);
}

.app-header__waterline {
  position: absolute;
  bottom: 0;
  left: 10%;
  right: 10%;
  height: 2px;
  background: var(--waterline);
  opacity: 0.7;
}

.app-body {
  flex: 1;
  display: flex;
  gap: var(--section-gap);
  padding: var(--section-gap);
  width: 100%;
  min-height: 0;
  overflow: hidden;
}

/* Sidebar */
.sidebar {
  width: var(--sidebar-width);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow: hidden;
}

.collect-time {
  margin: 0;
  padding: 8px 12px;
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--panel-bg);
  border: 1px solid var(--border-color);
  border-radius: var(--panel-radius);
  flex-shrink: 0;
}

.metric-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
}

.metric-card {
  display: flex;
  align-items: stretch;
  gap: 12px;
  padding: 10px 14px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.metric-card--clickable {
  cursor: pointer;
}

.metric-card--clickable:hover {
  border-color: var(--color-dox);
  box-shadow: 0 0 0 1px rgba(86, 180, 233, 0.15);
}

.metric-card--dox { background: linear-gradient(135deg, var(--panel-bg) 60%, var(--color-dox-dim)); }
.metric-card--ph { background: linear-gradient(135deg, var(--panel-bg) 60%, var(--color-ph-dim)); }
.metric-card--thw { background: linear-gradient(135deg, var(--panel-bg) 60%, var(--color-thw-dim)); }

.metric-card__current {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 90px;
}

.metric-card__label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
  white-space: nowrap;
}

.metric-card__value {
  font-family: var(--font-mono);
  font-size: 24px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.metric-card__unit {
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
  margin-left: 2px;
}

.dox { color: var(--color-dox); }
.ph { color: var(--color-ph); }
.thw { color: var(--color-thw); }

.metric-card__predictions {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding-left: 12px;
  border-left: 1px solid var(--border-subtle);
}

.predict-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  font-size: 11px;
  line-height: 1.9;
}

.predict-label {
  color: var(--text-muted);
  flex-shrink: 0;
}

.predict-value {
  font-family: var(--font-mono);
  font-weight: 500;
  font-size: 12px;
}

.predict-unit {
  font-weight: 400;
  color: var(--text-muted);
  font-size: 10px;
}

.sidebar-video {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* Main content */
.main-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: var(--section-gap);
  overflow: hidden;
}

.biomass-section {
  flex: 3;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 12px 16px !important;
}

.biomass-section .section-header {
  margin-bottom: 8px;
  flex-shrink: 0;
}

.biomass-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.modules-grid {
  flex: 7;
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: var(--section-gap);
  min-height: 0;
  overflow: hidden;
}

.modules-col--left {
  display: grid;
  grid-template-rows: 2fr 1fr;
  gap: var(--section-gap);
  min-height: 0;
  overflow: hidden;
}

.modules-col--left > :first-child {
  min-height: 0;
  overflow: hidden;
}

.module-chat {
  min-height: 0;
  height: 100%;
  overflow: hidden;
}

.system-entry {
  min-height: 0;
  padding: 10px 12px;
}

.system-entry__title {
  margin: 0 0 8px;
  font-family: var(--font-display);
  font-size: 13px;
  font-weight: 600;
}

.system-entry__buttons {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 7px;
}

.system-entry__button {
  min-width: 0;
  padding: 7px 6px;
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--panel-elevated);
  color: var(--text-secondary);
  font-family: var(--font-body);
  font-size: 11px;
  white-space: nowrap;
  text-overflow: ellipsis;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, color 0.2s;
}

.system-entry__button:hover {
  border-color: var(--color-dox);
  background: var(--color-dox-dim);
  color: var(--color-dox);
}

.system-entry__button:focus-visible {
  outline: 2px solid var(--color-dox);
  outline-offset: 2px;
}

.system-window-overlay {
  position: fixed;
  inset: 0;
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(24, 50, 71, 0.38);
  backdrop-filter: blur(4px);
}

.system-window {
  width: 70vw;
  height: 70vh;
  display: flex;
  flex-direction: column;
}

.system-window__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.system-window__frame {
  width: 100%;
  flex: 1;
  min-height: 0;
  border: 0;
  background: #fff;
}

/* Trend modal */
.trend-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(24, 50, 71, 0.32);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.trend-panel {
  width: min(900px, 88vw);
  height: min(560px, 72vh);
  display: flex;
  flex-direction: column;
}

.trend-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.trend-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
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
  background: var(--border-subtle);
  color: var(--text-primary);
}

.trend-close:focus-visible {
  outline: 2px solid var(--color-dox);
  outline-offset: 2px;
}

.trend-body {
  flex: 1;
  min-height: 0;
  padding: 12px 16px 16px;
}

.trend-fade-enter-active,
.trend-fade-leave-active {
  transition: opacity 0.25s ease;
}

.trend-fade-enter-from,
.trend-fade-leave-to {
  opacity: 0;
}

@media (max-width: 1100px) {
  .app-layout {
    height: auto;
    min-height: 100vh;
    overflow: visible;
  }

  .app-body {
    flex-direction: column;
    overflow: visible;
    height: auto;
  }

  .sidebar {
    width: 100%;
    min-height: 480px;
    overflow: visible;
  }

  .sidebar-video {
    flex: none;
    min-height: 220px;
  }

  .metric-cards {
    flex-direction: row;
    flex-wrap: wrap;
  }

  .metric-card {
    flex: 1;
    min-width: 240px;
  }

  .main-content {
    overflow: visible;
    height: auto;
  }

  .biomass-section {
    flex: none;
    min-height: 720px;
    height: auto;
    overflow: visible;
  }

  .biomass-body {
    min-height: 640px;
    overflow: visible;
  }

  .modules-grid {
    flex: none;
    grid-template-columns: 1fr;
    min-height: auto;
    overflow: visible;
  }

  .modules-col--left {
    grid-template-rows: auto auto;
    overflow: visible;
  }

  .modules-col--left > :first-child {
    min-height: 360px;
    height: auto;
    overflow: visible;
  }

  .module-placeholder,
  .module-placeholder--tall {
    min-height: 160px;
    height: auto;
  }

  .module-chat {
    min-height: 420px;
    height: auto;
  }

  .system-window {
    width: 92vw;
    height: 82vh;
  }
}
</style>
