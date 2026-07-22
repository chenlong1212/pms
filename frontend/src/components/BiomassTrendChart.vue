<template>
  <div class="biomass-charts">
    <div ref="countChartRef" class="chart-panel panel"></div>
    <div ref="avgWeightChartRef" class="chart-panel panel"></div>
    <div ref="biomassChartRef" class="chart-panel panel"></div>
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
  1: '#1687c5',
  2: '#159b7b',
  3: '#d89212',
}

function chartColor() {
  if (props.color) return props.color
  if (props.data?.pondId) return POND_COLORS[props.data.pondId] ?? '#1687c5'
  return '#1687c5'
}

function buildLineOption(title: string, yName: string, seriesName: string, data: (number | string)[]) {
  const color = chartColor()
  return {
    backgroundColor: 'transparent',
    title: {
      text: title,
      left: 12,
      top: 8,
      textStyle: { color: '#183247', fontSize: 12, fontWeight: 600, fontFamily: 'Sora, Noto Sans SC, sans-serif' },
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      backgroundColor: '#ffffff',
      borderColor: '#cddde7',
      textStyle: { color: '#183247' },
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '18%',
      top: 28,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: props.data?.dates ?? [],
      axisLabel: { rotate: 45, fontSize: 9, color: '#8095a4' },
      axisLine: { lineStyle: { color: '#cddde7' } },
    },
    yAxis: {
      type: 'value',
      name: yName,
      nameTextStyle: { color: '#587286', fontSize: 11 },
      axisLabel: { color: '#8095a4', fontSize: 10 },
      splitLine: { lineStyle: { color: '#e5eef3' } },
    },
    series: [
      {
        name: seriesName,
        type: 'line',
        data,
        smooth: true,
        symbol: 'circle',
        symbolSize: 4,
        itemStyle: { color },
        lineStyle: { color, width: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: color + '35' },
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
    if (!countChart) countChart = echarts.init(countChartRef.value)
    countChart.setOption(
      buildLineOption(
        '数量图表',
        '尾',
        '数量 (尾)',
        props.data.count,
      ),
      true,
    )
  }

  if (avgWeightChartRef.value) {
    if (!avgWeightChart) avgWeightChart = echarts.init(avgWeightChartRef.value)
    avgWeightChart.setOption(
      buildLineOption(
        '平均重量图表',
        'kg/尾',
        '平均重量 (kg/尾)',
        props.data.avgWeight,
      ),
      true,
    )
  }

  if (biomassChartRef.value) {
    if (!biomassChart) biomassChart = echarts.init(biomassChartRef.value)
    biomassChart.setOption(
      buildLineOption(
        '生物量图表',
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
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  height: 100%;
  width: 100%;
  min-height: 0;
  overflow: hidden;
}

.chart-panel {
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

@media (max-width: 900px) {
  .biomass-charts {
    grid-template-columns: 1fr;
    grid-template-rows: none;
    height: auto;
    min-height: 640px;
    overflow: visible;
  }

  .chart-panel {
    height: 200px;
    min-height: 200px;
  }
}
</style>
