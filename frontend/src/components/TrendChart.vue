<template>
  <div ref="chartRef" class="trend-chart"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { DeviceData } from '../api/device'

const props = defineProps<{
  data: DeviceData[]
}>()

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

function renderChart() {
  if (!chartRef.value || !props.data.length) return

  if (!chart) {
    chart = echarts.init(chartRef.value, 'dark')
  }

  const times = props.data.map(d => d.collectTimeStr)
  const doxData = props.data.map(d => d.dox)
  const phData = props.data.map(d => d.ph)
  const thwData = props.data.map(d => d.thw)

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
    },
    legend: {
      data: ['溶解氧 (mg/L)', 'pH', '水温 (℃)'],
      top: 0,
      textStyle: { color: '#8b949e' },
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '10%',
      top: 40,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: times,
      axisLabel: { rotate: 45, fontSize: 11, color: '#8b949e' },
      axisLine: { lineStyle: { color: '#30363d' } },
    },
    yAxis: [
      {
        type: 'value',
        name: '溶解氧 / 水温',
        nameTextStyle: { color: '#8b949e' },
        axisLabel: { color: '#8b949e' },
        splitLine: { lineStyle: { color: '#21262d' } },
      },
      {
        type: 'value',
        name: 'pH',
        position: 'right',
        nameTextStyle: { color: '#8b949e' },
        axisLabel: { color: '#8b949e' },
        splitLine: { show: false },
        min: (value: { min: number }) => Math.floor(value.min - 1),
        max: (value: { max: number }) => Math.ceil(value.max + 1),
      },
    ],
    series: [
      {
        name: '溶解氧 (mg/L)',
        type: 'line',
        data: doxData,
        smooth: true,
        yAxisIndex: 0,
        itemStyle: { color: '#58a6ff' },
      },
      {
        name: 'pH',
        type: 'line',
        data: phData,
        smooth: true,
        yAxisIndex: 1,
        itemStyle: { color: '#3fb950' },
      },
      {
        name: '水温 (℃)',
        type: 'line',
        data: thwData,
        smooth: true,
        yAxisIndex: 0,
        itemStyle: { color: '#d29922' },
      },
    ],
    dataZoom: [{ type: 'inside', start: 0, end: 100 }],
  }, true)
}

function handleResize() {
  chart?.resize()
}

watch(() => props.data, async (newData) => {
  if (newData?.length) {
    await nextTick()
    renderChart()
  }
}, { deep: true })

onMounted(() => {
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.trend-chart {
  width: 100%;
  height: 100%;
}
</style>
