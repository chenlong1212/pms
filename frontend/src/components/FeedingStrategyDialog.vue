<template>
  <el-dialog
    v-model="visible"
    title="池塘投喂策略"
    width="min(980px, 94vw)"
    append-to-body
    destroy-on-close
  >
    <div class="strategy-rule">
      <strong>投喂规则</strong>
      <span>每日投喂总量按当前生物量的 2%–3% 计算，每日投喂 1–3 次，系统按次数均分并给出每次投喂量。</span>
    </div>

    <el-table v-loading="loading" :data="strategies" stripe max-height="420">
      <el-table-column prop="pondName" label="池塘" min-width="110" fixed />
      <el-table-column prop="fishSpecies" label="养殖品种" min-width="100" />
      <el-table-column label="投喂比例" width="100">
        <template #default="{ row }">{{ formatRate(row.dailyRate) }}</template>
      </el-table-column>
      <el-table-column prop="mealsPerDay" label="每日次数" width="90">
        <template #default="{ row }">{{ row.mealsPerDay }} 次</template>
      </el-table-column>
      <el-table-column label="投喂时间" min-width="170">
        <template #default="{ row }">{{ row.feedTimes.join('、') || '--' }}</template>
      </el-table-column>
      <el-table-column label="日投喂量" width="110">
        <template #default="{ row }">{{ row.dailyFeedKg == null ? '--' : `${row.dailyFeedKg} kg` }}</template>
      </el-table-column>
      <el-table-column label="每次投喂量" min-width="180">
        <template #default="{ row }">
          <span v-if="row.mealAmountsKg.length">
            {{ row.mealAmountsKg.map((amount: number, index: number) => `${row.feedTimes[index]} ${amount}kg`).join('；') }}
          </span>
          <span v-else>暂无当日生物量</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="startEdit(row)">修改</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-alert
      v-if="!loading && !strategies.length"
      title="暂无可配置的池塘"
      type="info"
      :closable="false"
      show-icon
    />

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="editVisible"
    :title="`修改投喂策略 · ${editing?.pondName ?? ''}`"
    width="min(500px, 92vw)"
    append-to-body
  >
    <el-form label-width="104px">
      <el-form-item label="投喂比例">
        <el-input-number v-model="form.ratePercent" :min="2" :max="3" :step="0.1" :precision="1" />
        <span class="form-unit">%（生物量占比）</span>
      </el-form-item>
      <el-form-item label="每日次数">
        <el-radio-group v-model="form.mealsPerDay" @change="syncFeedTimes">
          <el-radio-button :value="1">1 次</el-radio-button>
          <el-radio-button :value="2">2 次</el-radio-button>
          <el-radio-button :value="3">3 次</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="投喂时间">
        <div class="time-list">
          <el-time-picker
            v-for="(_, index) in form.feedTimes"
            :key="index"
            v-model="form.feedTimes[index]"
            format="HH:mm"
            value-format="HH:mm"
            :placeholder="`第 ${index + 1} 次`"
            :clearable="false"
          />
        </div>
      </el-form-item>
      <el-form-item label="计算说明">
        <p class="calculation-hint">
          日投喂量 = 当日生物量 × {{ form.ratePercent.toFixed(1) }}%，再按 {{ form.mealsPerDay }} 次均分。
        </p>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存策略</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Pond } from '../api/biomass'
import {
  getFeedingStrategy,
  updateFeedingStrategy,
  type FeedingStrategy,
} from '../api/feeding'

const props = defineProps<{ ponds: Pond[] }>()
const emit = defineEmits<{ saved: [strategy: FeedingStrategy] }>()

const visible = ref(false)
const loading = ref(false)
const strategies = ref<FeedingStrategy[]>([])
const editVisible = ref(false)
const saving = ref(false)
const editing = ref<FeedingStrategy | null>(null)
const form = ref({ ratePercent: 2.5, mealsPerDay: 2, feedTimes: ['08:00', '17:00'] })

const defaultTimes: Record<number, string[]> = {
  1: ['09:00'],
  2: ['08:00', '17:00'],
  3: ['08:00', '12:00', '17:00'],
}

function formatRate(rate: number | null) {
  return rate == null ? '--' : `${(Number(rate) * 100).toFixed(1)}%`
}

async function load() {
  loading.value = true
  try {
    const responses = await Promise.all(props.ponds.map(pond => getFeedingStrategy(pond.id)))
    strategies.value = responses.map(response => response.data.data)
  } catch {
    ElMessage.error('获取投喂策略失败')
  } finally {
    loading.value = false
  }
}

function open() {
  visible.value = true
  load()
}

function startEdit(row: FeedingStrategy) {
  editing.value = row
  form.value = {
    ratePercent: Number(row.dailyRate) * 100,
    mealsPerDay: row.mealsPerDay,
    feedTimes: [...row.feedTimes],
  }
  syncFeedTimes()
  editVisible.value = true
}

function syncFeedTimes() {
  const count = form.value.mealsPerDay
  const defaults = defaultTimes[count]
  form.value.feedTimes = Array.from({ length: count }, (_, index) => form.value.feedTimes[index] || defaults[index])
}

async function save() {
  if (!editing.value) return
  if (form.value.feedTimes.some(time => !time)) {
    ElMessage.warning('请填写全部投喂时间')
    return
  }
  if (new Set(form.value.feedTimes).size !== form.value.feedTimes.length) {
    ElMessage.warning('投喂时间不能重复')
    return
  }
  saving.value = true
  try {
    const response = await updateFeedingStrategy(editing.value.pondId, {
      dailyRate: form.value.ratePercent / 100,
      mealsPerDay: form.value.mealsPerDay,
      feedTimes: form.value.feedTimes,
    })
    const updated = response.data.data
    const index = strategies.value.findIndex(item => item.pondId === updated.pondId)
    if (index >= 0) strategies.value[index] = updated
    editVisible.value = false
    emit('saved', updated)
    ElMessage.success('投喂策略已保存')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '保存投喂策略失败')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.strategy-rule {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 14px;
  border: 1px solid rgba(53, 220, 230, .28);
  border-radius: 8px;
  background: rgba(37, 157, 166, .08);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.strategy-rule strong { flex: 0 0 auto; color: var(--color-dox); }
.form-unit { margin-left: 8px; color: var(--text-secondary); font-size: 12px; }
.time-list { display: grid; gap: 10px; width: 100%; }
.time-list :deep(.el-date-editor) { width: 100%; }
.calculation-hint { margin: 0; color: var(--text-secondary); font-size: 12px; line-height: 1.6; }
</style>
