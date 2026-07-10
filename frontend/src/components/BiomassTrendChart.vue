<template>
  <div class="biomass-charts">
    <div ref="countChartRef" class="chart-panel"></div>
    <div ref="avgWeightChartRef" class="chart-panel"></div>
    <div ref="biomassChartRef" class="chart-panel"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import type { BiomassTrend } from '../api/biomass'

const props = defineProps<{
  data: BiomassTrend | null
  color?: string
}>()

const countChartRef = ref<HTMLDivElement>()
const avgWeightChartRef = ref<HTMLDivElement>()
const biomassChartRef = ref<HTMLDivElement>()
let countChart: echarts.ECharts | null = null
let avgWeightChart: echarts.ECharts | null = null
let biomassChart: echarts.ECharts | null = null

const POND_COLORS: Record<number, string> = {
  1: '#58a6ff',
  2: '#3fb950',
  3: '#d29922',
}

function getColor() {
  if (props.color) return props.color
  if (props.data?.pondId) return POND_COLORS[props.data.pondId] ?? '#58a6ff'
  return '#58a6ff'
}

function buildLineOption(title: string, yName: string, seriesName: string, data: (number | string)[]) {
  const color = getColor()
  return {
    backgroundColor: 'transparent',
    title: {
      text: title,
      left: 0,
      top: 0,
      textStyle: { color: '#e6edf3', fontSize: 14, fontWeight: 600 },
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '12%',
      top: 36,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: props.data?.dates ?? [],
      axisLabel: { rotate: 45, fontSize: 11, color: '#8b949e' },
      axisLine: { lineStyle: { color: '#30363d' } },
    },
    yAxis: {
      type: 'value',
      name: yName,
      nameTextStyle: { color: '#8b949e' },
      axisLabel: { color: '#8b949e' },
      splitLine: { lineStyle: { color: '#21262d' } },
    },
    series: [
      {
        name: seriesName,
        type: 'line',
        data,
        smooth: true,
        itemStyle: { color },
        lineStyle: { color },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: color + '40' },
            { offset: 1, color: color + '05' },
          ]),
        },
      },
    ],
    dataZoom: [{ type: 'inside', start: 0, end: 100 }],
  }
}

function renderCharts() {
  if (!props.data?.dates.length) return

  if (countChartRef.value) {
    if (!countChart) countChart = echarts.init(countChartRef.value, 'dark')
    countChart.setOption(
      buildLineOption(
        `${props.data.fishSpecies} 数量`,
        '尾',
        '数量 (尾)',
        props.data.count,
      ),
      true,
    )
  }

  if (avgWeightChartRef.value) {
    if (!avgWeightChart) avgWeightChart = echarts.init(avgWeightChartRef.value, 'dark')
    avgWeightChart.setOption(
      buildLineOption(
        `${props.data.fishSpecies} 平均重量`,
        'kg/尾',
        '平均重量 (kg/尾)',
        props.data.avgWeight,
      ),
      true,
    )
  }

  if (biomassChartRef.value) {
    if (!biomassChart) biomassChart = echarts.init(biomassChartRef.value, 'dark')
    biomassChart.setOption(
      buildLineOption(
        `${props.data.fishSpecies} 生物量`,
        'kg',
        '生物量 (kg)',
        props.data.biomass,
      ),
      true,
    )
  }
}

function handleResize() {
  countChart?.resize()
  avgWeightChart?.resize()
  biomassChart?.resize()
}

watch(() => props.data, async (newData) => {
  if (newData?.dates.length) {
    await nextTick()
    renderCharts()
  }
}, { deep: true })

onMounted(() => {
  window.addEventListener('resize', handleResize)
  if (props.data?.dates.length) renderCharts()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  countChart?.dispose()
  avgWeightChart?.dispose()
  biomassChart?.dispose()
  countChart = null
  avgWeightChart = null
  biomassChart = null
})
</script>

<style scoped>
.biomass-charts {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  min-height: 720px;
}

.chart-panel {
  flex: 1;
  min-height: 200px;
}
</style>
