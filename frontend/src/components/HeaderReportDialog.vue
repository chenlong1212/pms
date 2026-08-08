<template>
  <el-dialog v-model="generatorVisible" :title="form.reportType === 'DAILY' ? '生成生产日报' : '生成生产周报'" width="720px">
    <div class="header-report-form">
      <div class="header-report-field">
        <span>当前池塘</span>
        <strong>{{ pondName }}</strong>
      </div>
      <div class="header-report-field">
        <span>报告日期</span>
        <el-date-picker v-model="form.reportDate" type="date" value-format="YYYY-MM-DD"
          :disabled-date="disableFuture" placeholder="选择报告日期" @change="preview = null" />
      </div>
      <el-button :loading="previewLoading" @click="loadPreview">查看数据</el-button>
    </div>

    <div v-if="preview" class="header-report-preview">
      <div class="preview-status">
        <el-tag :type="preview.dataQuality.ready ? 'success' : 'danger'">
          {{ preview.dataQuality.ready ? '核心数据可用' : '核心数据不完整' }}
        </el-tag>
        <span>水质采样点 {{ preview.waterQuality.sampleCount }} 个</span>
      </div>
      <div class="preview-grid">
        <div><span>溶解氧均值</span><strong>{{ metric(preview.waterQuality.dox, 'mg/L') }}</strong></div>
        <div><span>pH 均值</span><strong>{{ metric(preview.waterQuality.ph) }}</strong></div>
        <div><span>水温均值</span><strong>{{ metric(preview.waterQuality.temperature, '℃') }}</strong></div>
        <div><span>生物量</span><strong>{{ preview.biomass?.biomassKg ?? '--' }} kg</strong></div>
        <div><span>实际投喂</span><strong>{{ preview.feeding.actualKg ?? '--' }} kg</strong></div>
        <div><span>建议投喂</span><strong>{{ preview.feeding.recommendedKg ?? '--' }} kg</strong></div>
      </div>
      <el-alert v-if="qualityMessages.length" :title="qualityMessages.join('；')" type="warning" :closable="false" show-icon />
    </div>

    <template #footer>
      <el-button @click="generatorVisible = false">关闭</el-button>
      <el-button type="primary" :loading="generating" :disabled="!preview?.dataQuality.ready" @click="generate">
        生成{{ form.reportType === 'DAILY' ? '日报' : '周报' }}
      </el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="detailVisible" :title="activeReport?.title" width="760px">
    <div v-if="activeReport" class="report-detail">
      <div class="report-meta">{{ activeReport.startDate }} 至 {{ activeReport.endDate }} · {{ activeReport.modelName }} · {{ formatTime(activeReport.createdAt) }}</div>
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
      <el-table-column label="周期" min-width="200"><template #default="{ row }">{{ row.startDate }} 至 {{ row.endDate }}</template></el-table-column>
      <el-table-column label="生成时间" width="170"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
      <el-table-column label="操作" width="150"><template #default="{ row }">
        <el-button link type="primary" @click="openDetail(row)">查看</el-button>
        <el-button link type="primary" @click="downloadPdf(row)">PDF</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="historyPage" layout="prev, pager, next, total" :total="historyTotal" :page-size="10" @current-change="loadHistory" />
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import type { Pond } from '../api/biomass'
import { createReport, listReports, previewReport, reportPdfUrl,
  type MetricSummary, type ProductionReport, type ReportPreview, type ReportType } from '../api/report'

const props = defineProps<{ ponds: Pond[]; selectedPondId: number | null }>()
const today = new Date()
const todayText = [today.getFullYear(), String(today.getMonth() + 1).padStart(2, '0'), String(today.getDate()).padStart(2, '0')].join('-')
const generatorVisible = ref(false), detailVisible = ref(false), historyVisible = ref(false)
const previewLoading = ref(false), generating = ref(false), historyLoading = ref(false)
const preview = ref<ReportPreview | null>(null), activeReport = ref<ProductionReport | null>(null)
const history = ref<ProductionReport[]>([]), historyPage = ref(1), historyTotal = ref(0)
const form = reactive({ reportType: 'DAILY' as ReportType, reportDate: todayText })
const pondName = computed(() => props.ponds.find(item => item.id === props.selectedPondId)?.name ?? '未选择池塘')
const qualityMessages = computed(() => preview.value ? [...preview.value.dataQuality.missingFields.map(v => `缺少${v}`), ...preview.value.dataQuality.warnings] : [])

function openReport(type: ReportType) { form.reportType = type; form.reportDate = todayText; preview.value = null; generatorVisible.value = true }
function openHistory() { historyVisible.value = true; void loadHistory() }
function requestData() { if (props.selectedPondId == null) throw new Error('请先选择池塘'); return { pondId: props.selectedPondId, reportType: form.reportType, reportDate: form.reportDate } }
async function loadPreview() { try { previewLoading.value = true; preview.value = (await previewReport(requestData())).data.data } catch (e) { ElMessage.error(e instanceof Error ? e.message : '预览报告数据失败') } finally { previewLoading.value = false } }
async function generate() {
  try { generating.value = true; activeReport.value = (await createReport(requestData())).data.data; generatorVisible.value = false; detailVisible.value = true; ElMessage.success('报告生成成功') }
  catch (error) { const message = axios.isAxiosError(error) ? error.response?.data?.message : error instanceof Error ? error.message : null; ElMessage.error(message || '报告生成失败，请检查数据和大模型配置') }
  finally { generating.value = false }
}
async function loadHistory() { try { historyLoading.value = true; const data = (await listReports(undefined, historyPage.value, 10)).data.data; history.value = data.records; historyTotal.value = data.total } catch { ElMessage.error('获取历史报告失败') } finally { historyLoading.value = false } }
function openDetail(report: ProductionReport) { activeReport.value = report; detailVisible.value = true }
function downloadPdf(report: ProductionReport) { window.open(reportPdfUrl(report.id), '_blank') }
function metric(value: MetricSummary | null, unit = '') { return value ? `${value.avg}${unit ? ` ${unit}` : ''}` : '--' }
function formatTime(value: string) { return value?.replace('T', ' ').slice(0, 19) || '--' }
function disableFuture(date: Date) { return date.getTime() > Date.now() }

defineExpose({ openDaily: () => openReport('DAILY'), openWeekly: () => openReport('WEEKLY'), openHistory })
</script>

<style scoped>
.header-report-form{display:grid;grid-template-columns:1fr 1.4fr auto;gap:14px;align-items:end}.header-report-field{display:grid;gap:6px}.header-report-field>span{color:var(--text-muted);font-size:11px}.header-report-field strong{height:32px;display:flex;align-items:center;color:var(--text-primary)}.header-report-preview{display:grid;gap:14px;margin-top:18px;padding:16px;border:1px solid var(--border-color);border-radius:8px;background:rgba(8,27,53,.76)}.preview-status{display:flex;align-items:center;gap:12px;color:var(--text-secondary)}.preview-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.preview-grid>div{display:grid;gap:3px;padding:10px;border:1px solid var(--border-subtle);background:rgba(27,57,91,.55)}.preview-grid span{color:var(--text-muted);font-size:11px}.preview-grid strong{color:#e8f7ff;font-family:var(--font-mono)}.report-meta{color:var(--text-muted);font-size:13px;margin-bottom:16px}.report-detail pre{max-height:60vh;overflow:auto;color:var(--text-primary);font:inherit;line-height:1.8;white-space:pre-wrap}.el-pagination{justify-content:center;margin-top:18px}
</style>
