<template>
  <div class="app-layout" :class="{ 'theme-light': isLightTheme }">
    <header class="app-header">
      <time class="app-header__clock">{{ currentTime }}</time>
      <h1 class="app-header__title">池塘生产管理系统</h1>
      <button
        class="app-header__theme"
        type="button"
        :aria-label="isLightTheme ? '切换为深色主题' : '切换为浅色主题'"
        :title="isLightTheme ? '切换为深色主题' : '切换为浅色主题'"
        @click="toggleTheme"
      >
        <svg v-if="isLightTheme" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.93 4.93l1.42 1.42M17.65 17.65l1.42 1.42M2 12h2M20 12h2M4.93 19.07l1.42-1.42M17.65 6.35l1.42-1.42" />
        </svg>
        <svg v-else viewBox="0 0 24 24" aria-hidden="true">
          <path d="M20.2 15.2A8.4 8.4 0 0 1 8.8 3.8 8.5 8.5 0 1 0 20.2 15.2Z" />
        </svg>
      </button>
      <div class="app-header__waterline" aria-hidden="true" />
    </header>

    <main v-loading="loading" class="app-body dashboard-grid">
      <aside class="water-column">
        <div class="collect-toolbar">
          <p class="collect-time">
            最新采集时间：{{ latestData?.collectTimeStr ?? '暂无数据' }}
          </p>
        </div>

        <div class="water-section-title panel">
          <div class="water-section-title__label">
            <span aria-hidden="true">◆</span>
            <strong>水质预测预警</strong>
          </div>
          <el-select
            v-model="selectedPondId"
            placeholder="选择池塘"
            class="collect-toolbar__pond"
            @change="fetchBiomassTrend"
          >
            <el-option
              v-for="pond in ponds"
              :key="pond.id"
              :label="`${pond.name}（${pond.fishSpecies}）`"
              :value="pond.id"
            />
          </el-select>
        </div>

        <div class="metric-cards">
          <div
            v-for="metric in metrics"
            :key="metric.key"
            class="metric-card panel metric-card--clickable"
            :class="`metric-card--${metric.key}`"
            @click="openTrend"
          >
            <div class="metric-card__current">
              <div class="metric-card__reading">
                <svg v-if="metric.key === 'dox'" class="metric-card__icon" viewBox="0 0 40 40" aria-hidden="true">
                  <path d="M8 26h24M11 31h18M14 21V12a6 6 0 0 1 12 0v9M27 13h5M30 10v6" />
                </svg>
                <svg v-else-if="metric.key === 'ph'" class="metric-card__icon" viewBox="0 0 40 40" aria-hidden="true">
                  <circle cx="20" cy="20" r="14" /><text x="20" y="25" text-anchor="middle">Ph</text>
                </svg>
                <svg v-else class="metric-card__icon" viewBox="0 0 40 40" aria-hidden="true">
                  <path d="M17 23V9a4 4 0 0 1 8 0v14a8 8 0 1 1-8 0Z" /><path d="M21 14v14" /><circle cx="21" cy="29" r="3" />
                </svg>
                <span class="metric-card__value">
                  {{ metric.value }}
                  <span v-if="metric.unit" class="metric-card__unit">{{ metric.unit }}</span>
                </span>
              </div>
              <span class="metric-card__label">{{ metric.label }}</span>
              <span class="metric-card__grade" :class="`grade--${metricGrade(metric.key, metric.value).tone}`">
                等级：{{ metricGrade(metric.key, metric.value).label }}
              </span>
            </div>
            <div class="metric-card__predictions">
              <div
                v-for="min in predictMinutes"
                :key="`${metric.key}-${min}`"
                class="predict-row"
              >
                <span class="predict-value" :class="metric.key">
                  {{ formatPredictValue(predictions[metric.key][min], metric.precision) }}
                  <span v-if="metric.unit" class="predict-unit">{{ metric.unit }}</span>
                </span>
                <span class="predict-label">{{ predictionTimeLabel(min) }}<small>预测值</small></span>
              </div>
            </div>
          </div>

          <div class="metric-card quality-assessment panel">
            <div class="metric-card__current">
              <span class="metric-card__label">水质综合评估</span>
              <strong class="quality-assessment__value" :class="`grade--${currentQualityGrade.tone}`">
                {{ currentQualityGrade.label }}
              </strong>
              <span class="quality-assessment__hint">基于溶解氧、pH、水温</span>
            </div>
            <div class="metric-card__predictions quality-assessment__predictions">
              <div v-for="min in predictMinutes" :key="`quality-${min}`" class="predict-row">
                <span class="predict-value" :class="`grade--${qualityPredictions[min].tone}`">{{ qualityPredictions[min].label }}</span>
                <span class="predict-label">{{ predictionTimeLabel(min) }}<small>预测等级</small></span>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <section class="biomass-column biomass-section panel panel--padded">
        <div class="section-header">
          <h2 class="section-title biomass-title">
            <svg viewBox="0 0 32 32" aria-hidden="true">
              <path d="M10 10h12l2 5 3 11H5l3-11 2-5Z" />
              <path d="M12 10a4 4 0 0 1 8 0" />
            </svg>
            生物量估计与计数
          </h2>
          <div class="biomass-day-tabs" role="group" aria-label="生物量趋势时间范围">
            <button
              v-for="days in [7, 30, 90, 180, 365]"
              :key="days"
              type="button"
              :class="{ active: biomassDays === days }"
              :aria-pressed="biomassDays === days"
              @click="biomassDays = days; fetchBiomassTrend()"
            >{{ days }}天</button>
          </div>
        </div>
        <div class="feeding-strategy-row">
          <span class="feeding-strategy-row__label">投喂策略</span>
          <strong v-if="feedingStrategy?.available && feedingStrategy.dailyFeedKg != null">
            建议投喂量 {{ feedingStrategy.dailyFeedKg }} kg
          </strong>
          <span v-else>暂无可用策略</span>
          <small v-if="feedingStrategy?.dailyRate != null">日投喂率 {{ feedingStrategy.dailyRate }}%</small>
        </div>
        <div v-loading="biomassLoading" class="biomass-body">
          <BiomassTrendChart v-if="biomassTrendData" :data="biomassTrendData" :light-mode="isLightTheme" />
          <div v-else class="empty-state">暂无生物量数据</div>
        </div>
        <FeedingRecordSection
          class="biomass-feeding-history"
          :ponds="ponds"
          :selected-pond-id="selectedPondId"
          title="历史投喂记录"
          hide-pond-select
          hide-strategy-button
          compact
        />
      </section>

      <section class="center-hub">
        <div class="aerial-hub panel">
          <img :src="pondAerialImage" alt="池塘养殖基地航拍实景" />
          <section class="system-entry aerial-hub__systems">
            <div class="system-entry__buttons">
              <button
                v-for="system in externalSystems"
                :key="system.url"
                class="system-entry__button"
                type="button"
                @click="openExternalSystem(system)"
              >{{ system.name }}</button>
            </div>
          </section>
        </div>

        <div class="center-hub__bottom">
          <ProductionReportSection
            class="daily-report-module"
            :ponds="ponds"
            :selected-pond-id="selectedPondId"
            hide-pond-select
            compact
            header-type-tabs
          />
          <ChatPanel class="module-chat hub-chat" />
        </div>
      </section>

      <VideoPlayer class="module-video video-column" compact />

    </main>

    <HeaderReportDialog ref="reportDialogRef" :ponds="ponds" :selected-pond-id="selectedPondId" />

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
import HeaderReportDialog from './components/HeaderReportDialog.vue'
import ProductionReportSection from './components/ProductionReportSection.vue'
import pondAerialImage from './assets/pond-aerial.jpg'
import { getLatest, getTrend, type DeviceData } from './api/device'
import { getPonds, getBiomassTrend, type Pond, type BiomassTrend } from './api/biomass'
import { getFeedingStrategy, type FeedingStrategy } from './api/feeding'
import { linearPredict, parseCollectTime, formatPredictValue } from './utils/predict'

const predictMinutes = [30, 60, 90, 120] as const
type MetricKey = 'dox' | 'ph' | 'thw'
type PredictMap = Record<(typeof predictMinutes)[number], number | null>
type WaterGrade = { label: '优' | '中' | '差' | '--'; tone: 'good' | 'warn' | 'bad' | 'muted' }
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
const currentTime = ref('')
const reportDialogRef = ref<InstanceType<typeof HeaderReportDialog> | null>(null)
const feedingStrategy = ref<FeedingStrategy | null>(null)
const isLightTheme = ref(false)

let refreshTimer: ReturnType<typeof setInterval> | null = null
let clockTimer: ReturnType<typeof setInterval> | null = null

function updateClock() {
  const now = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  currentTime.value = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

function applyTheme() {
  document.documentElement.dataset.theme = isLightTheme.value ? 'light' : 'dark'
}

function toggleTheme() {
  isLightTheme.value = !isLightTheme.value
  localStorage.setItem('pms-theme', isLightTheme.value ? 'light' : 'dark')
  applyTheme()
}

const metrics = computed(() => [
  {
    key: 'thw' as const,
    label: '水温 ℃',
    value: latestData.value?.thw ?? '--',
    unit: '',
    precision: 1,
  },
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
])

function predictionTimeLabel(minutes: number) {
  if (minutes === 90) return '1.5小时后'
  if (minutes === 120) return '2小时后'
  return `${minutes}分钟后`
}

function metricGrade(key: MetricKey, rawValue: number | string | null): WaterGrade {
  if (rawValue == null || rawValue === '' || rawValue === '--') return { label: '--', tone: 'muted' }
  const value = Number(rawValue)
  if (!Number.isFinite(value)) return { label: '--', tone: 'muted' }
  if (key === 'dox') {
    if (value >= 5 && value <= 15) return { label: '优', tone: 'good' }
    if (value >= 3) return { label: '中', tone: 'warn' }
    return { label: '差', tone: 'bad' }
  }
  if (key === 'ph') {
    if (value >= 6.5 && value <= 8.5) return { label: '优', tone: 'good' }
    if (value >= 6 && value <= 9) return { label: '中', tone: 'warn' }
    return { label: '差', tone: 'bad' }
  }
  if (value >= 18 && value <= 30) return { label: '优', tone: 'good' }
  if (value >= 12 && value <= 34) return { label: '中', tone: 'warn' }
  return { label: '差', tone: 'bad' }
}

function combinedWaterGrade(dox: number | null | undefined, ph: number | null | undefined, thw: number | null | undefined): WaterGrade {
  const grades = [metricGrade('dox', dox ?? null), metricGrade('ph', ph ?? null), metricGrade('thw', thw ?? null)]
  if (grades.some(item => item.tone === 'muted')) return { label: '--', tone: 'muted' }
  if (grades.some(item => item.tone === 'bad')) return { label: '差', tone: 'bad' }
  if (grades.some(item => item.tone === 'warn')) return { label: '中', tone: 'warn' }
  return { label: '优', tone: 'good' }
}

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

const currentQualityGrade = computed(() => combinedWaterGrade(latestData.value?.dox, latestData.value?.ph, latestData.value?.thw))
const qualityPredictions = computed(() => Object.fromEntries(predictMinutes.map(min => [min, combinedWaterGrade(
  predictions.value.dox[min], predictions.value.ph[min], predictions.value.thw[min],
)])) as Record<(typeof predictMinutes)[number], WaterGrade>)

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
    const [trendRes, strategyRes] = await Promise.all([
      getBiomassTrend(selectedPondId.value, biomassDays.value),
      getFeedingStrategy(selectedPondId.value),
    ])
    biomassTrendData.value = trendRes.data.data
    feedingStrategy.value = strategyRes.data.data
  } catch {
    ElMessage.error('获取生物量或投喂策略失败')
  } finally {
    biomassLoading.value = false
  }
}

async function refreshData() {
  await Promise.all([fetchLatest(), fetchPredictTrend()])
}

onMounted(() => {
  isLightTheme.value = localStorage.getItem('pms-theme') === 'light'
  applyTheme()
  window.addEventListener('keydown', handleKeydown)
  loading.value = true
  Promise.all([refreshData(), fetchPonds()]).finally(() => { loading.value = false })
  refreshTimer = setInterval(refreshData, 60_000)
  updateClock()
  clockTimer = setInterval(updateClock, 1_000)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
  if (refreshTimer) clearInterval(refreshTimer)
  if (clockTimer) clearInterval(clockTimer)
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

.app-header__clock,
.app-header__reports,
.app-header__theme {
  position: relative;
  z-index: 5;
}

.app-header__theme {
  position: absolute;
  left: calc(50% + 176px);
  top: 50%;
  width: 28px;
  height: 28px;
  padding: 5px;
  transform: translateY(-50%);
  border: 1px solid rgba(73, 207, 224, 0.48);
  border-radius: 50%;
  background: rgba(5, 24, 52, 0.82);
  color: #9ff8f0;
  cursor: pointer;
}

.app-header__theme svg {
  display: block;
  width: 100%;
  height: 100%;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.app-header__theme:hover {
  border-color: #35dce6;
  color: #fff;
}

.app-header__clock {
  color: #f0f8ff;
  font-family: var(--font-mono);
  font-size: 18px;
  font-weight: 500;
  letter-spacing: 0.02em;
  white-space: nowrap;
  text-shadow: 0 0 10px rgba(66, 211, 232, 0.28);
}

.app-header__reports {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
}

.app-header__reports button {
  height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(81, 145, 197, 0.42);
  border-radius: 4px;
  background: rgba(9, 29, 58, 0.86);
  color: #edf7ff;
  font-family: var(--font-body);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.18s ease, background 0.18s ease, color 0.18s ease;
}

.app-header__reports button:hover {
  border-color: #34dfe9;
  background: rgba(25, 69, 103, 0.92);
  color: #b9fff4;
}

.app-header__reports button:focus-visible {
  outline: 2px solid #34dfe9;
  outline-offset: 2px;
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
  gap: 3px;
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

/* Desktop layout restored from layout-prototype/layout.json. */
.app-header {
  flex-direction: row;
  justify-content: space-between;
  height: var(--header-height);
  padding: 0 16px 0 24px;
  isolation: isolate;
  background: linear-gradient(180deg, rgba(16, 48, 85, 0.98), rgba(4, 20, 47, 0.98));
  border-bottom: 1px solid rgba(39, 214, 228, 0.72);
  overflow: hidden;
}

.app-header::before,
.app-header::after {
  content: "";
  position: absolute;
  left: 50%;
  top: 0;
  transform: translateX(-50%);
  clip-path: polygon(8% 0, 14% 74%, 25% 74%, 27% 88%, 73% 88%, 75% 74%, 86% 74%, 92% 0);
  pointer-events: none;
}

.app-header::before {
  z-index: 0;
  width: min(680px, 48vw);
  height: 52px;
  background: linear-gradient(90deg, #238ec4, #38e6ed 50%, #238ec4);
  filter: drop-shadow(0 0 7px rgba(43, 221, 235, 0.65));
}

.app-header::after {
  z-index: 1;
  width: min(672px, calc(48vw - 8px));
  height: 48px;
  background: linear-gradient(180deg, #173b68, #092349);
}

.app-header__title {
  position: absolute;
  left: 50%;
  z-index: 3;
  transform: translateX(-50%);
  color: #b9fff4;
  font-size: clamp(20px, 2vw, 27px);
  font-weight: 700;
  letter-spacing: 0.13em;
  white-space: nowrap;
  text-shadow: 0 0 4px #20dbe4, 0 0 12px rgba(32, 219, 228, 0.8), 2px 2px 0 rgba(14, 104, 141, 0.65);
}

.app-header__waterline {
  z-index: 4;
  left: 0;
  right: 0;
  opacity: 1;
  background: linear-gradient(90deg, transparent 2%, #16d9e7 18%, #7efff0 50%, #16d9e7 82%, transparent 98%);
  box-shadow: 0 0 8px rgba(22, 217, 231, 0.75);
}

.app-body {
  display: grid;
  grid-template-columns: minmax(300px, 320px) minmax(600px, 1fr) minmax(260px, 300px);
  gap: 8px;
  padding: 8px;
  overflow: hidden;
}

.desktop-column {
  min-width: 0;
  min-height: 0;
}

.desktop-column--left {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 224px) minmax(0, 96px);
  grid-template-rows: minmax(0, 320fr) minmax(0, 500fr);
  column-gap: 0;
  row-gap: 8px;
  overflow: hidden;
}

.desktop-column--left .collect-toolbar {
  position: absolute;
  z-index: 2;
  width: 100%;
  display: flex;
  align-items: stretch;
}

.desktop-column--left .collect-time {
  flex: 1;
  min-width: 0;
  margin: 0;
  padding: 8px 6px;
  border-radius: 0;
  border-width: 0 0 1px;
  font-size: 10px;
  white-space: nowrap;
  background: rgba(9, 29, 56, 0.94);
  color: var(--text-secondary);
}

.collect-toolbar__pond {
  width: 126px;
  flex: 0 0 126px;
}

.collect-toolbar__pond :deep(.el-select__wrapper) {
  min-height: 35px;
  border-radius: 0;
}

.desktop-column--left .metric-cards {
  grid-column: 1;
  grid-row: 1;
  min-height: 0;
  padding-top: 36px;
  display: grid;
  grid-template-rows: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.desktop-column--left .metric-card {
  --metric-accent: var(--color-dox);
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 88px;
  gap: 8px;
  min-height: 0;
  padding: 6px 10px 4px;
  border: 1px solid rgba(91, 128, 164, 0.3);
  border-radius: 10px;
  background:
    linear-gradient(125deg, rgba(54, 79, 112, 0.34), transparent 62%),
    #1d2d45;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.035);
}

.desktop-column--left .metric-card--ph {
  --metric-accent: var(--color-ph);
}

.desktop-column--left .metric-card--thw {
  --metric-accent: var(--color-thw);
}

.desktop-column--left .metric-card__current {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 0;
}

.desktop-column--left .metric-card__reading {
  display: flex;
  align-items: center;
  gap: 8px;
}

.desktop-column--left .metric-card__icon {
  width: 24px;
  height: 24px;
  flex: 0 0 24px;
  overflow: visible;
  fill: none;
  stroke: #3ee6ed;
  stroke-width: 2.3;
  stroke-linecap: round;
  stroke-linejoin: round;
  filter: drop-shadow(0 0 3px rgba(62, 230, 237, 0.52));
}

.desktop-column--left .metric-card__icon text {
  fill: #f5fbff;
  stroke: none;
  font-family: Arial, sans-serif;
  font-size: 12px;
  font-weight: 700;
}

.desktop-column--left .metric-card__label {
  margin: 0 0 0 32px;
  color: #f5f8fc;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.desktop-column--left .metric-card__value {
  color: #ff9d00;
  font-size: 24px;
  font-weight: 700;
  line-height: 1;
  text-shadow: 0 0 10px rgba(255, 157, 0, 0.12);
}

.desktop-column--left .metric-card__predictions {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  padding: 0 0 0 9px;
  border-top: 0;
  border-left: 1px solid rgba(157, 184, 211, 0.17);
}

.desktop-column--left .predict-row {
  display: flex;
  min-width: 0;
  flex-direction: row;
  align-items: baseline;
  justify-content: space-between;
  gap: 5px;
  border-right: 0;
  line-height: 1.55;
}

.desktop-column--left .predict-row:last-child {
  border-right: 0;
}

.desktop-column--left .predict-label {
  color: #8fa6bd;
  font-size: 8px;
  white-space: nowrap;
}

.desktop-column--left .predict-value {
  color: #c6f4f4;
  font-size: 10px;
  font-weight: 600;
}

.desktop-column--left .metric-card--clickable:hover {
  border-color: rgba(62, 230, 237, 0.48);
  box-shadow: 0 0 0 1px rgba(62, 230, 237, 0.08), inset 0 1px 0 rgba(255,255,255,0.05);
}

.desktop-column--center .biomass-section {
  min-height: 0;
  height: 100%;
  padding: 8px 10px !important;
}

.desktop-column--center .biomass-section .section-header {
  display: flex;
  margin-bottom: 5px;
}

.desktop-column--center .biomass-section .section-toolbar {
  flex-wrap: nowrap;
  overflow: hidden;
}

.biomass-title {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 16px;
  font-weight: 700;
}

.biomass-title svg {
  width: 19px;
  height: 19px;
  fill: #f4f8ff;
}

.desktop-column--center .biomass-body {
  flex: 1;
  min-height: 0;
}

.desktop-column--center {
  display: grid;
  grid-template-rows: 300px minmax(0, 1fr);
  gap: 8px;
  overflow: hidden;
}

.pond-aerial-stage {
  min-height: 0;
  overflow: hidden;
  background: #07172d;
}

.pond-aerial-stage img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 48%;
}

.module-video,
.desktop-column--right {
  min-height: 0 !important;
  height: 100% !important;
  overflow: hidden;
}

.feeding-entry-button {
  border-color: rgba(49, 210, 224, 0.55);
  background: rgba(18, 72, 104, 0.82);
  color: #dffcff;
}

.feeding-dialog-body {
  height: 62vh;
  min-height: 460px;
}

.desktop-column--left .module-video {
  grid-column: 1 / -1;
  grid-row: 2;
}

.desktop-column--left .system-entry {
  grid-column: 2;
  grid-row: 1;
  height: 100%;
  display: block;
  padding: 36px 6px 6px;
  overflow: hidden;
}

.desktop-column--left .system-entry__buttons {
  display: grid;
  grid-template-columns: 1fr;
  grid-template-rows: repeat(6, minmax(0, 1fr));
  gap: 5px;
  height: 100%;
  min-height: 0;
}

.desktop-column--left .system-entry__button {
  width: 100%;
  padding: 4px 2px;
  font-size: 10px;
  line-height: 1.2;
  white-space: normal;
  overflow-wrap: anywhere;
  background: rgba(51, 80, 116, 0.82);
  border-color: rgba(76, 137, 190, 0.48);
  color: #d7e8f7;
}

:global([data-theme='light']) .app-header {
  background: linear-gradient(180deg, rgba(226, 242, 250, 0.98), rgba(199, 226, 239, 0.98));
  border-bottom-color: rgba(20, 158, 184, 0.68);
}

:global([data-theme='light']) .app-header::after {
  background: linear-gradient(180deg, #e8f7fb, #c9e6f1);
}

:global([data-theme='light']) .app-header__title {
  color: #12677d;
  text-shadow: 0 0 8px rgba(31, 191, 203, 0.32);
}

:global([data-theme='light']) .app-header__clock {
  color: #244d64;
  text-shadow: none;
}

:global([data-theme='light']) .app-header__reports button,
:global([data-theme='light']) .app-header__theme {
  background: rgba(242, 250, 253, 0.94);
  border-color: rgba(38, 131, 165, 0.45);
  color: #245f78;
}

:global([data-theme='light']) .desktop-column--left .collect-time {
  background: rgba(244, 250, 253, 0.96);
}

:global([data-theme='light']) .desktop-column--left .metric-card {
  background: linear-gradient(125deg, rgba(213, 234, 243, 0.84), rgba(248, 252, 254, 0.98) 64%);
  border-color: rgba(62, 128, 163, 0.28);
}

:global([data-theme='light']) .desktop-column--left .metric-card__label {
  color: #284a60;
}

:global([data-theme='light']) .desktop-column--left .predict-value {
  color: #176f7a;
}

:global([data-theme='light']) .desktop-column--left .system-entry__button {
  background: rgba(218, 235, 244, 0.92);
  border-color: rgba(47, 124, 160, 0.36);
  color: #315b72;
}

:global([data-theme='light']) .biomass-section :deep(.chart-panel) {
  background: rgba(247, 251, 253, 0.96);
}

.theme-light .app-header {
  background: linear-gradient(180deg, rgba(226, 242, 250, 0.98), rgba(199, 226, 239, 0.98));
  border-bottom-color: rgba(20, 158, 184, 0.68);
}

.theme-light .app-header::after {
  background: linear-gradient(180deg, #e8f7fb, #c9e6f1);
}

.theme-light .app-header__title {
  color: #12677d;
  text-shadow: 0 0 8px rgba(31, 191, 203, 0.32);
}

.theme-light .app-header__clock {
  color: #244d64;
  text-shadow: none;
}

.theme-light .app-header__reports button,
.theme-light .app-header__theme {
  background: rgba(242, 250, 253, 0.94);
  border-color: rgba(38, 131, 165, 0.45);
  color: #245f78;
}

.theme-light .desktop-column--left .collect-time {
  background: rgba(244, 250, 253, 0.96);
}

.theme-light .desktop-column--left .metric-card {
  background: linear-gradient(125deg, rgba(213, 234, 243, 0.84), rgba(248, 252, 254, 0.98) 64%);
  border-color: rgba(62, 128, 163, 0.28);
}

.theme-light .desktop-column--left .metric-card__label {
  color: #284a60;
}

.theme-light .desktop-column--left .predict-value {
  color: #176f7a;
}

.theme-light .desktop-column--left .system-entry__button {
  background: rgba(218, 235, 244, 0.92);
  border-color: rgba(47, 124, 160, 0.36);
  color: #315b72;
}

.theme-light .feeding-entry-button {
  background: rgba(236, 248, 252, 0.96);
  border-color: rgba(35, 143, 174, 0.5);
  color: #17657d;
}

.theme-light .feeding-entry-button:hover {
  background: #d9f2f6;
  border-color: #1aaec0;
  color: #0a7182;
}

@media (max-width: 1100px) {
  .app-layout { min-width: 1100px; height: 100vh; overflow: hidden; }
  .app-body { height: calc(100vh - var(--header-height)); }
}

/* Four-column operations layout */
.app-body.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(210px, 16fr) minmax(280px, 21fr) minmax(500px, 47fr) minmax(190px, 16fr);
  grid-template-rows: minmax(0, 1fr);
  gap: 8px;
  padding: 8px;
  overflow: hidden;
}

.water-column,
.biomass-column,
.center-hub,
.video-column {
  min-width: 0;
  min-height: 0;
}

.water-column {
  display: grid;
  grid-template-rows: auto 48px minmax(0, 1fr);
  gap: 8px;
  overflow: hidden;
}

.water-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  min-width: 0;
  padding: 0 14px;
  overflow: hidden;
  border-width: 0 0 0 4px;
  border-color: #27eced;
  border-radius: 0;
  background: linear-gradient(90deg, rgba(18, 75, 99, .92), rgba(13, 54, 75, .82));
  color: #f1f7ff;
}

.water-section-title__label { display: flex; align-items: center; gap: 8px; min-width: 0; white-space: nowrap; }
.water-section-title span { color: #2aaaff; font-size: 9px; }
.water-section-title strong { font-size: 15px; letter-spacing: .02em; }

.water-column .collect-toolbar {
  display: block;
  min-width: 0;
}

.water-column .collect-time {
  min-width: 0;
  overflow: hidden;
  margin: 0;
  padding: 9px 12px;
  border: 1px solid rgba(59, 167, 198, .38);
  border-radius: 4px;
  white-space: nowrap;
  text-overflow: ellipsis;
  background: rgba(8, 35, 62, .76);
  color: var(--text-secondary);
  font-size: 12px;
}

.water-column .collect-toolbar__pond {
  width: 104px;
  flex: 0 0 104px;
}

.water-section-title .collect-toolbar__pond :deep(.el-select__wrapper) {
  min-height: 30px;
  border: 1px solid rgba(54, 218, 229, .52);
  background: rgba(5, 30, 54, .72);
  box-shadow: none;
}

.water-column .metric-cards {
  display: grid;
  grid-template-rows: repeat(4, minmax(0, 1fr));
  gap: 8px;
  min-height: 0;
}

.water-column .metric-card {
  display: grid;
  grid-template-columns: minmax(0, 1.04fr) minmax(0, .96fr);
  gap: 6px;
  min-height: 0;
  padding: 8px;
  overflow: hidden;
  border: 2px solid #13dfe6;
  border-radius: 0;
  background: linear-gradient(145deg, rgba(11, 93, 106, .92), rgba(5, 29, 49, .96) 62%);
  box-shadow: inset 0 0 22px rgba(21, 216, 225, .08);
}

.water-column .metric-card__current {
  min-width: 0;
  justify-content: center;
  padding-left: 3px;
}

.water-column .metric-card__current > .metric-card__label { order: -1; }

.water-column .metric-card__reading {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.water-column .metric-card__icon {
  width: 19px;
  height: 19px;
  flex: 0 0 19px;
  fill: none;
  stroke: #3ee6ed;
  stroke-width: 2.3;
}

.water-column .metric-card__icon text {
  fill: currentColor;
  stroke: none;
  font-size: 12px;
  font-weight: 700;
}

.water-column .metric-card__label {
  margin: 0 0 8px;
  color: var(--text-primary);
  font-weight: 650;
  white-space: normal;
}

.water-column .metric-card__value {
  min-width: 0;
  font-size: clamp(20px, 1.35vw, 24px);
  letter-spacing: -.035em;
  white-space: nowrap;
}
.water-column .metric-card__unit { display: none; }
.water-column .metric-card__grade { margin-top: 7px; font-size: 12px; font-weight: 700; }
.water-column .metric-card__predictions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 5px 8px;
  min-width: 0;
  padding: 8px 5px 6px 8px;
  background: linear-gradient(145deg, rgba(63, 123, 151, .45), rgba(37, 77, 108, .46));
}
.water-column .predict-row { display: flex; flex-direction: column; align-items: flex-start; justify-content: center; gap: 1px; min-width: 0; line-height: 1.25; }
.water-column .predict-label { color: #afc3d2; font-size: 9px; white-space: nowrap; }
.water-column .predict-label small { display: block; margin-top: 1px; font-size: 8px; }
.water-column .predict-value { color: #35ecf4; font-size: 13px; font-weight: 700; }

.grade--good { color: #30e8d2 !important; }
.grade--warn { color: #ffc33b !important; }
.grade--bad { color: #ff4d4f !important; }
.grade--muted { color: #8fa6bd !important; }

.quality-assessment__value {
  font-family: var(--font-display);
  font-size: 26px;
  line-height: 1;
}

.quality-assessment__hint {
  margin-top: 8px;
  color: #93afc2;
  font-size: 8px;
  line-height: 1.35;
}

.quality-assessment__predictions .predict-value { font-size: 14px; }

.biomass-column {
  height: 100%;
  padding: 10px !important;
}

.biomass-column .section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 5px;
  margin-bottom: 7px;
}

.biomass-column .biomass-title {
  min-width: 0;
  font-size: 13px;
  letter-spacing: -.03em;
  white-space: nowrap;
}

.biomass-column .biomass-title svg {
  width: 15px;
  height: 15px;
  flex: 0 0 15px;
}

.biomass-day-tabs {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  overflow: hidden;
  border: 1px solid rgba(54, 143, 191, .72);
  border-radius: 4px;
}

.biomass-day-tabs button {
  min-width: 0;
  height: 27px;
  padding: 0 2px;
  border: 0;
  border-right: 1px solid rgba(54, 143, 191, .5);
  background: rgba(7, 31, 59, .76);
  color: #9db5ca;
  font: 600 8px/1 var(--font-body);
  white-space: nowrap;
  cursor: pointer;
}

.biomass-day-tabs button:last-child { border-right: 0; }
.biomass-day-tabs button:hover { color: #eafcff; background: rgba(20, 92, 119, .72); }
.biomass-day-tabs button.active {
  background: #19cad8;
  color: #04283b;
  box-shadow: inset 0 0 10px rgba(118, 255, 248, .32);
}

.theme-light .biomass-day-tabs { border-color: rgba(35, 132, 168, .5); }
.theme-light .biomass-day-tabs button { background: #edf6fa; color: #557489; }
.theme-light .biomass-day-tabs button.active { background: #22b9c8; color: #fff; }
.biomass-column :deep(.biomass-charts) {
  grid-template-columns: 1fr;
  grid-template-rows: repeat(3, minmax(0, 1fr));
}

.feeding-strategy-row {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 34px;
  margin-bottom: 7px;
  padding: 0 9px;
  overflow: hidden;
  border: 1px solid rgba(37, 201, 218, .38);
  background: linear-gradient(90deg, rgba(11, 80, 98, .72), rgba(11, 37, 65, .7));
  color: #dceaf5;
  font-size: 11px;
  white-space: nowrap;
}

.feeding-strategy-row__label {
  padding-right: 7px;
  border-right: 1px solid rgba(62, 226, 234, .42);
  color: #46e3e9;
  font-weight: 700;
}

.feeding-strategy-row strong {
  overflow: hidden;
  color: #f3f8ff;
  text-overflow: ellipsis;
}

.feeding-strategy-row small {
  margin-left: auto;
  color: #8fb4c9;
  font-size: 9px;
}

.biomass-feeding-history {
  flex: 0 0 200px !important;
  height: 200px !important;
  margin-top: 7px;
  padding: 7px 8px !important;
  border-radius: 4px;
  background: rgba(8, 26, 50, .82);
}

.biomass-feeding-history :deep(.feeding-header) { margin-bottom: 5px; }
.biomass-feeding-history :deep(.section-title) { font-size: 13px; }
.biomass-feeding-history :deep(.el-button) { min-height: 26px; padding: 4px 8px; }

.theme-light .feeding-strategy-row {
  border-color: rgba(26, 150, 171, .36);
  background: linear-gradient(90deg, rgba(202, 238, 242, .9), rgba(235, 246, 250, .92));
  color: #36576b;
}

.theme-light .feeding-strategy-row strong { color: #173f55; }
.theme-light .feeding-strategy-row small { color: #67879a; }
.theme-light .biomass-feeding-history { background: rgba(242, 249, 251, .94); }

.center-hub {
  display: grid;
  grid-template-rows: minmax(230px, 40fr) minmax(330px, 60fr);
  gap: 8px;
  overflow: hidden;
}

.aerial-hub {
  position: relative;
  min-height: 0;
  overflow: hidden;
  background: #07172d;
}

.aerial-hub > img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 78%;
}

.aerial-hub__systems {
  position: absolute;
  z-index: 2;
  right: 8px;
  bottom: 8px;
  left: 8px;
  padding: 6px;
  border: 1px solid rgba(78, 194, 211, .38);
  border-radius: 5px;
  background: rgba(5, 25, 51, .72);
  backdrop-filter: blur(8px);
}

.aerial-hub__systems .system-entry__buttons {
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 5px;
}

.aerial-hub__systems .system-entry__button {
  height: 38px;
  padding: 4px;
  border-radius: 3px;
  white-space: normal;
  line-height: 1.25;
}

.center-hub__bottom {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 8px;
  min-height: 0;
  overflow: hidden;
}

.daily-report-module,
.hub-chat { min-height: 0; height: 100%; }
.daily-report-module :deep(.report-header > div:first-child) { display: block; }
.daily-report-module :deep(.report-header > div:first-child > span) { display: none; }
.daily-report-module :deep(.report-form--shared-pond) { grid-template-columns: 1fr; }
.daily-report-module :deep(.snapshot-grid) { grid-template-columns: repeat(3, minmax(0, 1fr)); grid-template-rows: repeat(2, auto); gap: 6px; }
.daily-report-module :deep(.quality-row) { gap: 6px; font-size: 11px; }
.daily-report-module :deep(.report-actions) { margin-top: 6px; }

.video-column {
  height: 100% !important;
  overflow: hidden;
}

.theme-light .water-column .metric-card {
  background: linear-gradient(135deg, rgba(225, 240, 247, .98), rgba(247, 251, 253, .96));
}

.theme-light .aerial-hub__systems {
  border-color: rgba(38, 132, 161, .36);
  background: rgba(238, 248, 251, .78);
}
</style>
