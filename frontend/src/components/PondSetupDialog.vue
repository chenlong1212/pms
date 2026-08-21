<template>
  <el-dialog
    v-model="visible"
    title="放养参数设置"
    width="min(520px, 92vw)"
    append-to-body
    destroy-on-close
    @closed="resetForm"
  >
    <el-form :model="form" label-width="110px" v-loading="loading">
      <el-form-item label="池塘" required>
        <el-select v-model="form.pondId" style="width: 100%" @change="loadSetup">
          <el-option v-for="p in ponds" :key="p.id" :label="`${p.name}（${p.fishSpecies}）`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="放养日期" required>
        <el-date-picker
          v-model="form.stockDate"
          type="date"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          placeholder="选择日期"
          :disabled-date="disableFuture"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="初始鱼群数量" required>
        <el-input-number
          v-model="form.initialFishCount"
          :min="1"
          :max="100000000"
          :step="100"
          :precision="0"
          style="width: 100%"
        />
        <span class="field-hint">单位：尾</span>
      </el-form-item>
      <el-form-item label="初始平均重量" required>
        <el-input-number
          v-model="form.initialWeightKg"
          :min="0.0001"
          :max="100"
          :step="0.01"
          :precision="4"
          style="width: 100%"
        />
        <span class="field-hint">单位：kg/尾</span>
      </el-form-item>
      <el-divider content-position="left">收获参数（可选，留空则系统按线性推测）</el-divider>
      <el-form-item label="收获日期">
        <el-date-picker
          v-model="form.harvestDate"
          type="date"
          value-format="YYYY-MM-DD"
          format="YYYY-MM-DD"
          placeholder="选填"
          :disabled-date="(d: Date) => d < (form.stockDate ? new Date(form.stockDate) : new Date())"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="最终鱼群数量">
        <el-input-number
          v-model="form.finalFishCount"
          :min="0"
          :max="100000000"
          :step="100"
          :precision="0"
          style="width: 100%"
          placeholder="留空不限制"
        />
        <span class="field-hint">单位：尾</span>
      </el-form-item>
      <el-form-item label="最终平均重量">
        <el-input-number
          v-model="form.finalWeightKg"
          :min="0.0001"
          :max="100"
          :step="0.01"
          :precision="4"
          style="width: 100%"
          placeholder="留空不限制"
        />
        <span class="field-hint">单位：kg/尾</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存参数</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { Pond, PondSetup, PondSetupRequest } from '../api/biomass'
import { getPondSetup, savePondSetup } from '../api/biomass'

const props = defineProps<{ ponds: Pond[]; selectedPondId: number | null }>()
const emit = defineEmits<{ setupSaved: [] }>()

const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const form = ref<PondSetupRequest>({
  pondId: 0,
  stockDate: today(),
  initialFishCount: undefined,
  initialWeightKg: undefined,
  harvestDate: undefined,
  finalFishCount: undefined,
  finalWeightKg: undefined,
})

function today() {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function disableFuture(d: Date) {
  const todayEnd = new Date()
  todayEnd.setHours(23, 59, 59, 999)
  return d.getTime() > todayEnd.getTime()
}

async function open() {
  form.value.pondId = props.selectedPondId ?? props.ponds[0]?.id ?? 0
  form.value.stockDate = today()
  form.value.initialFishCount = undefined
  form.value.initialWeightKg = undefined
  form.value.harvestDate = undefined
  form.value.finalFishCount = undefined
  form.value.finalWeightKg = undefined
  visible.value = true
  await loadSetup()
}

async function loadSetup() {
  if (!form.value.pondId) return
  loading.value = true
  try {
    const res = await getPondSetup(form.value.pondId)
    const s: PondSetup = res.data.data ?? {}
    form.value.stockDate = s.stockDate ?? today()
    form.value.initialFishCount = s.initialFishCount ?? undefined
    form.value.initialWeightKg = s.initialWeightKg ?? undefined
    form.value.harvestDate = s.harvestDate ?? undefined
    form.value.finalFishCount = s.finalFishCount ?? undefined
    form.value.finalWeightKg = s.finalWeightKg ?? undefined
  } catch {
    ElMessage.info('该池塘暂无放养参数')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.value.pondId) {
    ElMessage.warning('请选择池塘')
    return
  }
  if (!form.value.stockDate) {
    ElMessage.warning('请填写放养日期')
    return
  }
  if (form.value.initialFishCount == null || form.value.initialFishCount <= 0) {
    ElMessage.warning('初始鱼群数量必须大于 0')
    return
  }
  if (form.value.initialWeightKg == null || form.value.initialWeightKg <= 0) {
    ElMessage.warning('初始平均重量必须大于 0')
    return
  }
  if (form.value.harvestDate && form.value.stockDate && form.value.harvestDate <= form.value.stockDate) {
    ElMessage.warning('收获日期必须晚于放养日期')
    return
  }
  if (form.value.finalFishCount != null && form.value.initialFishCount != null
      && form.value.finalFishCount > form.value.initialFishCount) {
    ElMessage.warning('最终鱼群数量不能超过初始数量')
    return
  }

  saving.value = true
  try {
    await savePondSetup(form.value)
    emit('setupSaved')
    visible.value = false
    ElMessage.success('放养参数保存成功')
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function resetForm() {
  form.value.pondId = 0
  form.value.stockDate = ''
  form.value.initialFishCount = undefined
  form.value.initialWeightKg = undefined
  form.value.harvestDate = undefined
  form.value.finalFishCount = undefined
  form.value.finalWeightKg = undefined
}

defineExpose({ open })
</script>

<style scoped>
.field-hint { display: block; width: 100%; margin-top: 3px; color: var(--text-secondary); font-size: 11px; line-height: 1.4; }
:deep(.el-divider__text) { font-size: 12px; color: var(--text-secondary); }
</style>
