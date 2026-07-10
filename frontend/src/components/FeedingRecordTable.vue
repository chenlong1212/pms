<template>
  <el-table
    ref="tableRef"
    :data="records"
    height="360"
    stripe
  >
    <el-table-column prop="feedDate" label="日期" width="120" />
    <el-table-column prop="feedTotalKg" label="投喂总量 (kg)" width="130" />
    <el-table-column prop="remark" label="备注" min-width="200">
      <template #default="{ row }">
        {{ row.remark || '—' }}
      </template>
    </el-table-column>
    <el-table-column label="操作" width="140" fixed="right">
      <template #default="{ row }">
        <el-button link type="primary" @click="$emit('edit', row)">编辑</el-button>
        <el-button link type="danger" @click="$emit('delete', row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import type { FeedingRecord } from '../api/feeding'

defineProps<{
  records: FeedingRecord[]
}>()

const emit = defineEmits<{
  edit: [row: FeedingRecord]
  delete: [row: FeedingRecord]
  'load-more': []
}>()

const tableRef = ref()
let scrollEl: HTMLElement | null = null

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
})

onUnmounted(() => {
  scrollEl?.removeEventListener('scroll', handleScroll)
})
</script>
