/** 基于最近数据点的简单线性回归外推预测 */
export function linearPredict(
  points: { time: number; value: number }[],
  minutesAhead: number,
): number | null {
  if (points.length === 0) return null
  if (points.length === 1) return points[0].value

  const xs = points.map((p) => (p.time - points[0].time) / 60_000)
  const ys = points.map((p) => p.value)
  const n = xs.length

  const sumX = xs.reduce((a, b) => a + b, 0)
  const sumY = ys.reduce((a, b) => a + b, 0)
  const sumXY = xs.reduce((a, x, i) => a + x * ys[i], 0)
  const sumXX = xs.reduce((a, x) => a + x * x, 0)

  const denom = n * sumXX - sumX * sumX
  if (Math.abs(denom) < 1e-10) return ys[n - 1]

  const slope = (n * sumXY - sumX * sumY) / denom
  const intercept = (sumY - slope * sumX) / n

  const lastX = xs[n - 1]
  return intercept + slope * (lastX + minutesAhead)
}

export function parseCollectTime(collectTimeStr: string): number {
  return new Date(collectTimeStr.replace(' ', 'T')).getTime()
}

export function formatPredictValue(value: number | null, decimals = 2): string {
  if (value == null || Number.isNaN(value)) return '--'
  return value.toFixed(decimals)
}
