<template>
  <div ref="wrapRef" class="table-wrap" :class="{ 'table-wrap--compact': compact }">
    <el-table
      ref="tableRef"
      :data="records"
      :height="tableHeight"
      stripe
    >
      <el-table-column
        prop="feedDate"
        label="日期"
        :width="compact ? undefined : 120"
        :min-width="compact ? 92 : undefined"
      />
      <el-table-column
        prop="feedTotalKg"
        label="投喂量 (kg)"
        :width="compact ? undefined : 130"
        :min-width="compact ? 76 : undefined"
      />
      <el-table-column v-if="!compact" prop="remark" label="备注" min-width="200">
        <template #default="{ row }">
          {{ row.remark || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" :width="compact ? 78 : 140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$emit('edit', row)">编辑</el-button>
          <el-button link type="danger" @click="$emit('delete', row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import type { FeedingRecord } from '../api/feeding'

defineProps<{
  records: FeedingRecord[]
  compact?: boolean
}>()

const emit = defineEmits<{
  edit: [row: FeedingRecord]
  delete: [row: FeedingRecord]
  'load-more': []
}>()

const tableRef = ref()
const wrapRef = ref<HTMLElement>()
const tableHeight = ref(200)
let scrollEl: HTMLElement | null = null
let resizeObserver: ResizeObserver | null = null

function handleScroll() {
  if (!scrollEl) return
  const { scrollTop, scrollHeight, clientHeight } = scrollEl
  if (scrollHeight - scrollTop - clientHeight < 40) {
    emit('load-more')
  }
}

onMounted(() => {
  scrollEl = tableRef.value?.$el?.querySelector('.el-scrollbar__wrap') ?? null
  scrollEl?.addEventListener('scroll', handleScroll)

  if (wrapRef.value) {
    resizeObserver = new ResizeObserver(([entry]) => {
      const h = Math.floor(entry.contentRect.height)
      if (h > 0) tableHeight.value = h
    })
    resizeObserver.observe(wrapRef.value)
  }
})

onUnmounted(() => {
  scrollEl?.removeEventListener('scroll', handleScroll)
  resizeObserver?.disconnect()
})
</script>

<style scoped>
.table-wrap {
  height: 100%;
  min-height: 0;
}

.table-wrap--compact :deep(.el-table) { font-size: 11px; }
.table-wrap--compact :deep(.el-table .el-table__cell) { padding: 3px 0; }
.table-wrap--compact :deep(.el-table .cell) { padding: 0 5px; white-space: nowrap; }
.table-wrap--compact :deep(.el-button) { min-width: 0; padding: 0 1px; font-size: 10px; }
.table-wrap--compact :deep(.el-button + .el-button) { margin-left: 2px; }
.table-wrap--compact :deep(.el-table-fixed-column--right .cell) { overflow: visible; text-overflow: clip; }

@media (max-width: 1100px) {
  .table-wrap {
    min-height: 280px;
  }
}
</style>
