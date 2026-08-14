<template>
  <div class="biomass-charts" :class="{ 'biomass-charts--light': lightMode }">
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
  lightMode?: boolean
}>()

const countChartRef = ref<HTMLDivElement>()
const avgWeightChartRef = ref<HTMLDivElement>()
const biomassChartRef = ref<HTMLDivElement>()
let countChart: echarts.ECharts | null = null
let avgWeightChart: echarts.ECharts | null = null
let biomassChart: echarts.ECharts | null = null

function buildLineOption(title: string, seriesName: string, data: (number | string)[], color: string) {
  const titleColor = props.lightMode ? '#173b55' : '#f4f8ff'
  const axisColor = props.lightMode ? '#607d90' : '#7895b1'
  const axisLineColor = props.lightMode ? '#7fa8bd' : '#35658d'
  const gridColor = props.lightMode ? 'rgba(70,118,149,.16)' : 'rgba(100,155,204,.18)'
  const numericData = data.map(Number).filter(Number.isFinite)
  const dataMin = numericData.length ? Math.min(...numericData) : 0
  const dataMax = numericData.length ? Math.max(...numericData) : 1
  const span = Math.max(dataMax - dataMin, Math.abs(dataMax) * 0.01, 0.01)
  const axisPadding = span * 0.22
  const axisMin = Math.max(0, dataMin - axisPadding)
  const axisMax = dataMax + axisPadding
  const labelInterval = Math.max(0, Math.ceil((props.data?.dates.length ?? 0) / 7) - 1)
  return {
    backgroundColor: 'transparent',
    title: {
      text: title,
      left: 14,
      top: 5,
      textStyle: { color: titleColor, fontSize: 11, fontWeight: 700, fontFamily: 'Sora, Noto Sans SC, sans-serif' },
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      axisPointer: { type: 'cross' },
      backgroundColor: props.lightMode ? '#f8fcff' : '#0d2545',
      borderColor: '#2877a2',
      textStyle: { color: props.lightMode ? '#24465d' : '#e8f5ff' },
    },
    grid: {
      left: 8,
      right: 12,
      bottom: 26,
      top: 28,
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: props.data?.dates ?? [],
      axisLabel: { rotate: 28, interval: labelInterval, fontSize: 8, color: axisColor, hideOverlap: true },
      axisLine: { lineStyle: { color: axisLineColor } },
    },
    yAxis: {
      type: 'value',
      scale: true,
      min: axisMin,
      max: axisMax,
      splitNumber: 2,
      axisLabel: {
        color: axisColor,
        fontSize: 9,
        formatter: (value: number) => {
          if (Math.abs(value) >= 1000) return Math.round(value).toLocaleString()
          if (Math.abs(value) >= 10) return value.toFixed(0)
          return value.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
        },
      },
      splitLine: { lineStyle: { color: gridColor } },
    },
    series: [
      {
        name: seriesName,
        type: 'line',
        data,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        itemStyle: { color },
        lineStyle: { color, width: 3 },
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
        '鱼群数量趋势（尾）',
        '数量 (尾)',
        props.data.count,
        '#ff4057',
      ),
      true,
    )
  }

  if (avgWeightChartRef.value) {
    if (!avgWeightChart) avgWeightChart = echarts.init(avgWeightChartRef.value)
    avgWeightChart.setOption(
      buildLineOption(
        '平均重量趋势（kg/尾）',
        '平均重量 (kg/尾)',
        props.data.avgWeight,
        '#38d3b2',
      ),
      true,
    )
  }

  if (biomassChartRef.value) {
    if (!biomassChart) biomassChart = echarts.init(biomassChartRef.value)
    biomassChart.setOption(
      buildLineOption(
        '生物量趋势（kg）',
        '生物量 (kg)',
        props.data.biomass,
        '#8f5cff',
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

watch([() => props.data, () => props.lightMode], async ([newData]) => {
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
  grid-template-rows: 1fr;
  gap: 4px;
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
  background: rgba(10, 24, 47, 0.92);
  border-color: rgba(76, 116, 160, 0.28);
  border-radius: 5px;
  box-shadow: none;
}

.biomass-charts--light .chart-panel {
  background: rgba(247, 251, 253, 0.96);
  border-color: rgba(62, 128, 163, 0.25);
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
