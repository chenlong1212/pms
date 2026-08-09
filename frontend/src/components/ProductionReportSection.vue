<template>
  <section class="report-section panel" :class="{ 'report-section--compact': compact }">
    <div class="report-header">
      <div><h2 class="section-title">{{ headerTypeTabs ? '生产报告' : dailyOnly ? '生产日报' : '智能生产报告' }}</h2><span>数据校验后生成，可追溯历史快照</span></div>
      <div class="report-header__actions">
        <div v-if="headerTypeTabs" class="report-type-tabs" role="group" aria-label="报告类型">
          <button type="button" :class="{ active: form.reportType === 'DAILY' }" @click="selectReportType('DAILY')">日报</button>
          <button type="button" :class="{ active: form.reportType === 'WEEKLY' }" @click="selectReportType('WEEKLY')">周报</button>
        </div>
        <el-button class="history-data-button" size="small" @click="historyVisible = true; loadHistory()">历史数据</el-button>
      </div>
    </div>
    <div class="report-form" :class="{ 'report-form--shared-pond': hidePondSelect }">
      <el-select v-if="!hidePondSelect" v-model="form.pondId" placeholder="选择池塘">
        <el-option v-for="pond in ponds" :key="pond.id" :label="pond.name" :value="pond.id" />
      </el-select>
      <el-radio-group v-if="!dailyOnly && !headerTypeTabs" v-model="form.reportType" size="small">
        <el-radio-button value="DAILY">日报</el-radio-button>
        <el-radio-button value="WEEKLY">周报</el-radio-button>
      </el-radio-group>
      <el-date-picker v-model="form.reportDate" type="date" value-format="YYYY-MM-DD"
        :disabled-date="disableFuture" placeholder="报告日期" />
    </div>
    <div v-if="preview" class="report-preview">
      <div class="quality-row">
        <el-tag :type="preview.dataQuality.ready ? 'success' : 'danger'">
          {{ preview.dataQuality.ready ? '核心数据可用' : '核心数据不完整' }}
        </el-tag>
        <span>水质 {{ preview.waterQuality.sampleCount }} 个采样点</span>
      </div>
      <div class="snapshot-grid">
        <span><small>溶解氧均值</small><strong>{{ metric(preview.waterQuality.dox, 'mg/L') }}</strong></span>
        <span><small>pH 均值</small><strong>{{ metric(preview.waterQuality.ph) }}</strong></span>
        <span><small>水温均值</small><strong>{{ metric(preview.waterQuality.temperature, '℃') }}</strong></span>
        <span><small>当前生物量</small><strong>{{ preview.biomass?.biomassKg ?? '--' }} kg</strong></span>
        <span><small>实际投喂</small><strong>{{ preview.feeding.actualKg ?? '--' }} kg</strong></span>
        <span><small>建议投喂</small><strong>{{ preview.feeding.recommendedKg ?? '--' }} kg</strong></span>
      </div>
      <el-alert v-if="qualityMessages.length" :title="qualityMessages.join('；')"
        type="warning" :closable="false" show-icon />
    </div>
    <div class="report-actions">
      <span v-if="previewLoading" class="preview-status">正在加载当日数据…</span>
      <el-button type="primary" :loading="generating" :disabled="previewLoading || !preview?.dataQuality.ready" @click="generate">
        生成报告
      </el-button>
    </div>

    <el-dialog v-model="detailVisible" :title="activeReport?.title" width="760px">
      <div v-if="activeReport" class="report-detail">
        <div class="report-meta">{{ activeReport.startDate }} 至 {{ activeReport.endDate }} ·
          {{ activeReport.modelName }} · {{ formatTime(activeReport.createdAt) }}</div>
        <pre>{{ activeReport.content }}</pre>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="downloadPdf(activeReport!)">下载 PDF</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="historyVisible" title="历史生产报告" width="850px">
      <el-table v-loading="historyLoading" :data="history">
        <el-table-column prop="title" label="报告" min-width="160" />
        <el-table-column label="周期" min-width="200">
          <template #default="{ row }">{{ row.startDate }} 至 {{ row.endDate }}</template>
        </el-table-column>
        <el-table-column label="生成时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="primary" @click="downloadPdf(row)">PDF</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="historyPage" layout="prev, pager, next, total"
        :total="historyTotal" :page-size="10" @current-change="loadHistory" />
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import type { Pond } from '../api/biomass'
import { createReport, listReports, previewReport, reportPdfUrl,
  type MetricSummary, type ProductionReport, type ReportPreview, type ReportType } from '../api/report'

const props = defineProps<{ ponds: Pond[]; selectedPondId: number | null; hidePondSelect?: boolean; compact?: boolean; dailyOnly?: boolean; headerTypeTabs?: boolean }>()
const now = new Date()
const todayText = [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'),
  String(now.getDate()).padStart(2, '0')].join('-')
const form = reactive({ pondId: props.selectedPondId as number | null,
  reportType: 'DAILY' as ReportType, reportDate: todayText })
const preview = ref<ReportPreview | null>(null)
const previewLoading = ref(false), generating = ref(false), detailVisible = ref(false)
const historyVisible = ref(false), historyLoading = ref(false)
const activeReport = ref<ProductionReport | null>(null), history = ref<ProductionReport[]>([])
const historyPage = ref(1), historyTotal = ref(0)
watch(() => props.selectedPondId, value => { if (value != null) form.pondId = value })
watch(form, () => {
  preview.value = null
  if (form.pondId != null && form.reportDate) loadPreview()
}, { immediate: true })
const qualityMessages = computed(() => preview.value ? [
  ...preview.value.dataQuality.missingFields.map(v => `缺少${v}`),
  ...preview.value.dataQuality.warnings,
] : [])
function requestData() {
  if (form.pondId == null) throw new Error('请选择池塘')
  return { pondId: form.pondId, reportType: form.reportType, reportDate: form.reportDate }
}
function selectReportType(type: ReportType) {
  const unchanged = form.reportType === type && form.reportDate === todayText
  form.reportType = type
  form.reportDate = todayText
  if (unchanged) loadPreview()
}
async function loadPreview() {
  try { previewLoading.value = true; preview.value = (await previewReport(requestData())).data.data }
  catch (e) { ElMessage.error(e instanceof Error ? e.message : '预览报告数据失败') }
  finally { previewLoading.value = false }
}
async function generate() {
  try {
    generating.value = true
    activeReport.value = (await createReport(requestData())).data.data
    detailVisible.value = true
    ElMessage.success('报告生成成功')
  } catch (error) {
    const message = axios.isAxiosError(error)
      ? error.response?.data?.message
      : error instanceof Error ? error.message : null
    ElMessage.error(message || '报告生成失败，请检查数据和大模型配置')
  }
  finally { generating.value = false }
}
async function loadHistory() {
  try {
    historyLoading.value = true
    const data = (await listReports(undefined, historyPage.value, 10)).data.data
    history.value = data.records; historyTotal.value = data.total
  } catch { ElMessage.error('获取历史报告失败') }
  finally { historyLoading.value = false }
}
function openDetail(report: ProductionReport) { activeReport.value = report; detailVisible.value = true }
function downloadPdf(report: ProductionReport) { window.open(reportPdfUrl(report.id), '_blank') }
function metric(value: MetricSummary | null, unit = '') { return value ? `${value.avg}${unit ? ` ${unit}` : ''}` : '--' }
function formatTime(value: string) { return value?.replace('T', ' ').slice(0, 19) || '--' }
function disableFuture(date: Date) { return date.getTime() > Date.now() }
</script>

<style scoped>
.report-section{padding:18px;min-height:310px}.report-header{display:flex;justify-content:space-between;align-items:flex-start}
.report-header>div:first-child{display:flex;align-items:baseline;gap:10px}.report-header span{color:var(--text-muted);font-size:12px}
.report-header__actions{display:flex;align-items:center;gap:6px;flex:0 0 auto}
.report-type-tabs{display:flex;overflow:hidden;border:1px solid rgba(45,139,183,.7);border-radius:4px}
.report-type-tabs button{height:24px;padding:0 9px;border:0;border-right:1px solid rgba(45,139,183,.55);background:rgba(8,34,62,.72);color:var(--text-secondary);font-size:11px;cursor:pointer}
.report-type-tabs button:last-child{border-right:0}.report-type-tabs button.active{background:#19cad8;color:#052b3d;font-weight:700}
.history-data-button{flex:0 0 auto;min-width:72px;white-space:nowrap}
.report-form{display:grid;grid-template-columns:1fr auto 1fr;gap:10px;margin:18px 0}.report-preview{display:grid;gap:12px}
.report-form--shared-pond{grid-template-columns:auto 1fr}
.quality-row{display:flex;align-items:center;gap:12px;color:var(--text-secondary);font-size:13px}.snapshot-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:8px 16px;color:var(--text-primary);font-size:13px}
.snapshot-grid>span{position:relative;display:flex;flex-direction:column;justify-content:center;gap:6px;min-width:0;min-height:62px;padding:9px 10px 8px;border:1px solid rgba(65,137,178,.24);border-radius:3px;background:linear-gradient(145deg,rgba(15,48,78,.7),rgba(8,29,54,.45));box-shadow:inset 0 1px 0 rgba(132,213,235,.05)}
.snapshot-grid>span::before{content:"";position:absolute;top:-1px;left:9px;width:24px;height:2px;background:#27d6df;box-shadow:0 0 7px rgba(39,214,223,.42)}
.snapshot-grid>span:nth-child(4)::before,.snapshot-grid>span:nth-child(6)::before{background:#8f72ff;box-shadow:0 0 7px rgba(143,114,255,.4)}
.snapshot-grid>span:nth-child(5)::before{background:#f0a93a;box-shadow:0 0 7px rgba(240,169,58,.35)}
.snapshot-grid small{overflow:hidden;color:#7f9db4;font-size:9px;line-height:1.2;white-space:nowrap;text-overflow:ellipsis}.snapshot-grid strong{overflow:hidden;color:#eef7ff;font-family:var(--font-mono);font-size:13px;font-weight:650;line-height:1.2;white-space:nowrap;text-overflow:ellipsis}
.report-actions{display:flex;align-items:center;justify-content:flex-end;gap:8px;margin-top:16px}.preview-status{margin-right:auto;color:var(--text-muted);font-size:11px}.report-meta{color:var(--text-muted);font-size:13px;margin-bottom:16px}
.report-detail pre{white-space:pre-wrap;font:inherit;line-height:1.8;color:var(--text-primary);max-height:60vh;overflow:auto}.el-pagination{justify-content:center;margin-top:18px}
.report-section--compact{display:flex;flex-direction:column;height:100%;min-height:0;padding:9px 12px;overflow:hidden}.report-section--compact .report-form{margin:8px 0}.report-section--compact .report-preview{flex:1;min-height:0;grid-template-rows:auto auto auto;align-content:space-between;gap:8px}.report-section--compact .report-actions{flex:0 0 auto;margin-top:8px}
.report-section--compact .snapshot-grid{min-height:0}.report-section--compact .snapshot-grid>span{min-height:58px;padding:7px 8px}
.report-section--compact :deep(.el-alert){padding:6px 9px}.report-section--compact :deep(.el-alert__title){font-size:10px;line-height:1.35}
:global(.theme-light) .snapshot-grid>span{border-color:rgba(43,126,160,.2);background:linear-gradient(145deg,rgba(226,241,247,.92),rgba(244,250,252,.82))}
:global(.theme-light) .snapshot-grid small{color:#66879a}:global(.theme-light) .snapshot-grid strong{color:#173f55}
:global(.theme-light) .report-type-tabs button{background:#edf6fa;color:#58758a}
:global(.theme-light) .report-type-tabs button.active{background:#22b9c8;color:#fff}
@media(max-width:900px){.report-form,.snapshot-grid{grid-template-columns:1fr}}
</style>
