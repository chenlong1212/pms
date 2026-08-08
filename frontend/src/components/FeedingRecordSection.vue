<template>
  <section class="feeding-section panel" :class="{ 'feeding-section--compact': compact }">
    <div class="feeding-header">
      <h2 v-if="!hideTitle" class="section-title">投喂记录</h2>
      <div class="feeding-toolbar">
        <el-select
          v-if="!hidePondSelect"
          v-model="selectedPondId"
          placeholder="选择鱼塘"
          style="width: 180px"
          @change="onPondChange"
        >
          <el-option
            v-for="pond in ponds"
            :key="pond.id"
            :label="`${pond.name}（${pond.fishSpecies}）`"
            :value="pond.id"
          />
        </el-select>
        <el-button type="primary" size="small" @click="openCreateDialog">新增投喂</el-button>
        <el-button size="small" @click="openStrategyDialog">投喂策略</el-button>
      </div>
    </div>

    <div v-loading="loading" class="feeding-body">
      <FeedingRecordTable
        v-if="records.length"
        :records="records"
        :compact="compact"
        @edit="openEditDialog"
        @delete="handleDelete"
        @load-more="loadMore"
      />
      <div v-else-if="!loading" class="empty-state">暂无投喂记录</div>
    </div>

    <el-dialog
      v-model="formDialogVisible"
      :title="editingId == null ? '新增投喂' : '编辑投喂'"
      width="520px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form :model="form" label-width="90px">
        <el-form-item label="日期" required>
          <el-date-picker
            v-model="formDate"
            type="date"
            placeholder="选择日期"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            style="width: 100%"
            :disabled-date="disableFutureDate"
          />
        </el-form-item>
        <el-form-item label="投喂总量" required>
          <el-input-number
            v-model="formFeedTotal"
            :min="0.1"
            :precision="1"
            :step="0.5"
            style="width: 100%"
          />
          <span class="unit-hint">kg</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="256"
            show-word-limit
            placeholder="选填，可记录分批投喂详情"
          />
        </el-form-item>
        <el-form-item v-if="editingId == null" label="快捷填入">
          <div class="strategy-buttons">
            <el-button
              size="small"
              :loading="strategyApplying"
              @click="applyStrategy(1)"
            >
              直接投喂
            </el-button>
            <el-button
              size="small"
              :loading="strategyApplying"
              @click="applyStrategy(2)"
            >
              分两次投喂
            </el-button>
            <el-button
              size="small"
              :loading="strategyApplying"
              @click="applyStrategy(3)"
            >
              分三次投喂
            </el-button>
          </div>
          <p class="strategy-hint">根据今日生物量自动填入投喂总量与备注</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="strategyDialogVisible"
      title="投喂策略"
      width="520px"
      destroy-on-close
    >
      <div v-loading="strategyLoading" class="strategy-content">
        <p v-if="strategy" class="strategy-text">{{ strategy.summaryText }}</p>
      </div>
      <template #footer>
        <el-button @click="strategyDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import FeedingRecordTable from './FeedingRecordTable.vue'
import {
  getPonds,
  getFeedingRecords,
  createFeedingRecord,
  updateFeedingRecord,
  deleteFeedingRecord,
  getFeedingStrategy,
  buildPlanRemark,
  type Pond,
  type FeedingRecord,
  type FeedingStrategy,
} from '../api/feeding'

const props = defineProps<{
  compact?: boolean
  ponds?: Pond[]
  selectedPondId?: number | null
  hidePondSelect?: boolean
  hideTitle?: boolean
}>()

const PAGE_SIZE = 20

const ponds = ref<Pond[]>([])
const selectedPondId = ref<number | null>(null)
const records = ref<FeedingRecord[]>([])
const loading = ref(false)
const currentPage = ref(1)
const total = ref(0)
const loadingMore = ref(false)

const formDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const form = ref({ remark: '' })
const formDate = ref('')
const formFeedTotal = ref<number | null>(null)

const strategyDialogVisible = ref(false)
const strategyLoading = ref(false)
const strategy = ref<FeedingStrategy | null>(null)
const strategyApplying = ref(false)
const cachedStrategy = ref<FeedingStrategy | null>(null)

function pad(n: number) {
  return String(n).padStart(2, '0')
}

function nowDateStr() {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function disableFutureDate(date: Date) {
  const today = new Date()
  today.setHours(23, 59, 59, 999)
  return date.getTime() > today.getTime()
}

async function fetchPonds() {
  try {
    if (props.ponds?.length) {
      ponds.value = props.ponds
    } else {
      const res = await getPonds()
      ponds.value = res.data.data
    }
    if (props.selectedPondId != null) {
      selectedPondId.value = props.selectedPondId
      await fetchRecords(true)
    } else if (ponds.value.length && selectedPondId.value == null) {
      selectedPondId.value = ponds.value[0].id
      await fetchRecords(true)
    }
  } catch {
    ElMessage.error('获取鱼塘列表失败')
  }
}

watch(() => props.ponds, value => {
  if (value?.length) ponds.value = value
}, { deep: true })

watch(() => props.selectedPondId, async value => {
  if (value != null && value !== selectedPondId.value) {
    selectedPondId.value = value
    cachedStrategy.value = null
    await fetchRecords(true)
  }
})

async function fetchRecords(reset = false) {
  if (selectedPondId.value == null) return
  if (reset) {
    currentPage.value = 1
    records.value = []
  }
  loading.value = reset
  loadingMore.value = !reset
  try {
    const res = await getFeedingRecords(selectedPondId.value, currentPage.value, PAGE_SIZE)
    const data = res.data.data
    total.value = data.total
    if (reset) {
      records.value = data.records
    } else {
      const existingIds = new Set(records.value.map(r => r.id))
      records.value.push(...data.records.filter(r => !existingIds.has(r.id)))
    }
  } catch {
    ElMessage.error('获取投喂记录失败')
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function onPondChange() {
  cachedStrategy.value = null
  fetchRecords(true)
}

function loadMore() {
  if (loadingMore.value || loading.value) return
  if (records.value.length >= total.value) return
  currentPage.value += 1
  fetchRecords(false)
}

async function loadStrategyForForm(): Promise<FeedingStrategy | null> {
  if (selectedPondId.value == null) return null
  if (cachedStrategy.value) return cachedStrategy.value
  const res = await getFeedingStrategy(selectedPondId.value)
  cachedStrategy.value = res.data.data
  return cachedStrategy.value
}

function openCreateDialog() {
  if (selectedPondId.value == null) {
    ElMessage.warning('请先选择鱼塘')
    return
  }
  editingId.value = null
  cachedStrategy.value = null
  formDate.value = nowDateStr()
  formFeedTotal.value = null
  form.value.remark = ''
  formDialogVisible.value = true
}

function openEditDialog(row: FeedingRecord) {
  editingId.value = row.id
  formDate.value = row.feedDate
  formFeedTotal.value = Number(row.feedTotalKg)
  form.value.remark = row.remark ?? ''
  formDialogVisible.value = true
}

function resetForm() {
  editingId.value = null
  formDate.value = ''
  formFeedTotal.value = null
  form.value.remark = ''
  cachedStrategy.value = null
}

async function applyStrategy(mealsPerDay: 1 | 2 | 3) {
  if (selectedPondId.value == null) return
  strategyApplying.value = true
  try {
    const data = await loadStrategyForForm()
    if (!data?.available || data.dailyFeedKg == null) {
      ElMessage.warning('暂无当日生物量，无法自动填入投喂策略')
      return
    }
    const plan = data.plans.find(p => p.mealsPerDay === mealsPerDay)
    if (!plan) return
    formDate.value = nowDateStr()
    formFeedTotal.value = data.dailyFeedKg
    form.value.remark = buildPlanRemark(plan)
  } catch {
    ElMessage.error('获取投喂策略失败')
  } finally {
    strategyApplying.value = false
  }
}

async function submitForm() {
  if (selectedPondId.value == null) return
  if (!formDate.value) {
    ElMessage.warning('请填写投喂日期')
    return
  }
  if (formFeedTotal.value == null || formFeedTotal.value <= 0) {
    ElMessage.warning('请填写投喂总量')
    return
  }

  const today = nowDateStr()
  if (formDate.value > today) {
    ElMessage.warning('投喂日期不能晚于今天')
    return
  }

  const body = {
    pondId: selectedPondId.value,
    feedDate: formDate.value,
    feedTotalKg: formFeedTotal.value,
    remark: form.value.remark.trim() || null,
  }

  submitting.value = true
  try {
    if (editingId.value == null) {
      await createFeedingRecord(body)
      ElMessage.success('新增成功')
    } else {
      await updateFeedingRecord(editingId.value, body)
      ElMessage.success('保存成功')
    }
    formDialogVisible.value = false
    await fetchRecords(true)
  } catch {
    ElMessage.error(editingId.value == null ? '新增失败' : '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: FeedingRecord) {
  try {
    await ElMessageBox.confirm('确定删除这条投喂记录吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteFeedingRecord(row.id)
    ElMessage.success('删除成功')
    await fetchRecords(true)
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

async function openStrategyDialog() {
  if (selectedPondId.value == null) {
    ElMessage.warning('请先选择鱼塘')
    return
  }
  strategyDialogVisible.value = true
  strategy.value = null
  strategyLoading.value = true
  try {
    const res = await getFeedingStrategy(selectedPondId.value)
    strategy.value = res.data.data
  } catch {
    ElMessage.error('获取投喂策略失败')
    strategyDialogVisible.value = false
  } finally {
    strategyLoading.value = false
  }
}

onMounted(() => {
  fetchPonds()
})
</script>

<style scoped>
.feeding-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  padding: 12px 16px;
}

.feeding-section--compact {
  flex: none;
  height: 100%;
}

.feeding-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
  flex-shrink: 0;
}

.feeding-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.feeding-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.strategy-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.strategy-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--text-secondary);
}

.unit-hint {
  margin-left: 8px;
  color: var(--text-secondary);
  font-size: 13px;
}

.strategy-content {
  min-height: 120px;
}

.strategy-text {
  margin: 0;
  white-space: pre-line;
  line-height: 1.7;
  color: var(--text-primary);
  font-size: 14px;
}

@media (max-width: 1100px) {
  .feeding-section,
  .feeding-section--compact {
    height: auto;
    min-height: 360px;
    overflow: visible;
  }

  .feeding-body {
    min-height: 280px;
    overflow: auto;
  }
}
</style>
