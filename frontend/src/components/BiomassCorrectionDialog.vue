<template>
  <el-dialog
    v-model="visible"
    title="鱼群数据校正"
    width="min(520px, 92vw)"
    append-to-body
    destroy-on-close
  >
    <el-form label-width="104px" v-loading="loading">
      <el-form-item label="池塘">
        <el-select v-model="form.pondId" style="width: 100%" @change="loadRecord">
          <el-option v-for="pond in ponds" :key="pond.id" :label="`${pond.name}（${pond.fishSpecies}）`" :value="pond.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="记录日期">
        <el-date-picker
          v-model="form.recordDate"
          type="date"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          :disabled-date="disableFutureDate"
          style="width: 100%"
          @change="loadRecord"
        />
      </el-form-item>
      <el-form-item label="鱼群数量">
        <el-input-number v-model="form.fishCount" :min="1" :max="100000000" :step="100" :precision="0" style="width: 100%" />
        <span class="field-hint">单位：尾</span>
      </el-form-item>
      <el-form-item label="平均重量">
        <el-input-number v-model="form.avgWeightKg" :min="0.0001" :max="100" :step="0.01" :precision="4" style="width: 100%" />
        <span class="field-hint">单位：kg/尾</span>
      </el-form-item>
      <el-form-item label="校正后生物量">
        <strong class="biomass-preview">{{ previewBiomass }} kg</strong>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存校正</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Pond } from '../api/biomass'
import { correctBiomassRecord, getBiomassRecord, type BiomassCorrection } from '../api/biomass'

const props = defineProps<{ ponds: Pond[]; selectedPondId: number | null }>()
const emit = defineEmits<{ saved: [record: BiomassCorrection] }>()

const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const form = ref({ pondId: 0, recordDate: today(), fishCount: null as number | null, avgWeightKg: null as number | null })

const previewBiomass = computed(() => {
  if (form.value.fishCount == null || form.value.avgWeightKg == null) return '--'
  return (form.value.fishCount * form.value.avgWeightKg).toFixed(2)
})

function today() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function disableFutureDate(date: Date) {
  const end = new Date()
  end.setHours(23, 59, 59, 999)
  return date.getTime() > end.getTime()
}

async function open() {
  form.value.pondId = props.selectedPondId ?? props.ponds[0]?.id ?? 0
  form.value.recordDate = today()
  visible.value = true
  await loadRecord()
}

async function loadRecord() {
  if (!form.value.pondId || !form.value.recordDate) return
  loading.value = true
  try {
    const response = await getBiomassRecord(form.value.pondId, form.value.recordDate)
    const record = response.data.data
    form.value.fishCount = record?.fishCount ?? null
    form.value.avgWeightKg = record?.avgWeightKg ?? null
    if (!record) ElMessage.info('该日期暂无记录，可填写数据后新增')
  } catch {
    ElMessage.error('获取鱼群数据失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.value.pondId || !form.value.recordDate) {
    ElMessage.warning('请选择池塘和记录日期')
    return
  }
  if (form.value.fishCount == null || form.value.fishCount <= 0) {
    ElMessage.warning('鱼群数量必须大于 0')
    return
  }
  if (form.value.avgWeightKg == null || form.value.avgWeightKg <= 0) {
    ElMessage.warning('平均重量必须大于 0')
    return
  }
  saving.value = true
  try {
    const response = await correctBiomassRecord({
      pondId: form.value.pondId,
      recordDate: form.value.recordDate,
      fishCount: form.value.fishCount,
      avgWeightKg: form.value.avgWeightKg,
    })
    emit('saved', response.data.data)
    visible.value = false
    ElMessage.success('鱼群数据校正成功')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '保存校正数据失败')
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.field-hint { display: block; width: 100%; margin-top: 3px; color: var(--text-secondary); font-size: 11px; line-height: 1.4; }
.biomass-preview { color: var(--color-dox); font-family: var(--font-mono); font-size: 18px; }
</style>
