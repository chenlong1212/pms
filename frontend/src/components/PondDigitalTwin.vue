<template>
  <section class="twin-stage panel" :class="{ 'twin-stage--light': lightMode }">
    <div ref="canvasHost" class="twin-stage__canvas" />
    <div
      v-if="hoveredIndex != null"
      class="twin-stage__tooltip"
      :style="{ left: `${tooltipPosition.x}px`, top: `${tooltipPosition.y}px` }"
    >
      <strong>{{ activeStats?.name }}</strong>
      <template v-if="activeStats?.available">
        <div class="twin-stage__data-grid">
          <span>溶解氧</span><b>{{ format(activeStats.dox, 2) }} mg/L</b>
          <span>pH</span><b>{{ format(activeStats.ph, 2) }}</b>
          <span>水温</span><b>{{ format(activeStats.temperature, 1) }} ℃</b>
          <span>生物量</span><b>{{ format(activeStats.biomass, 1) }} kg</b>
          <span>鱼群数量</span><b>{{ format(activeStats.count, 0) }} 尾</b>
          <span>平均质量</span><b>{{ format(activeStats.avgWeight, 3) }} kg/尾</b>
        </div>
      </template>
      <p v-else>暂无数据</p>
    </div>
    <button class="twin-stage__reset" type="button" @click="resetView" aria-label="复位三维视角">
      复位视角
    </button>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'

type PondStat = {
  name: string
  available: boolean
  dox: number | null
  ph: number | null
  temperature: number | null
  biomass: number | null
  count: number | null
  avgWeight: number | null
}

const props = defineProps<{ lightMode?: boolean; pondStats?: PondStat[] }>()
const canvasHost = ref<HTMLDivElement>()
const hoveredIndex = ref<number | null>(null)
const tooltipPosition = reactive({ x: 0, y: 0 })
const activeStats = computed(() => hoveredIndex.value == null ? null : props.pondStats?.[hoveredIndex.value] ?? null)

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let resizeObserver: ResizeObserver | null = null
let animationFrame = 0
let grid: THREE.GridHelper | null = null
let ambient: THREE.HemisphereLight | null = null
const raycaster = new THREE.Raycaster()
const pointer = new THREE.Vector2()
const pondSurfaces: THREE.Mesh[] = []
let highlightedSurface: THREE.Mesh | null = null

const cameraPosition = new THREE.Vector3(0, 13.8, 15.5)
const cameraTarget = new THREE.Vector3(0, 0, 0)

function material(color: number, roughness = 0.62, metalness = 0.12) {
  return new THREE.MeshStandardMaterial({ color, roughness, metalness })
}

function addBox(size: [number, number, number], position: [number, number, number], color: number) {
  if (!scene) return null
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(...size), material(color))
  mesh.position.set(...position)
  mesh.castShadow = true
  mesh.receiveShadow = true
  scene.add(mesh)
  return mesh
}

function addPond(x: number, z: number, index: number) {
  if (!scene) return
  const pondRadius = 1.72
  const wall = new THREE.Mesh(
    new THREE.CylinderGeometry(pondRadius, pondRadius, 0.62, 64, 1, true),
    new THREE.MeshStandardMaterial({ color: 0x2456ad, roughness: 0.38, metalness: 0.28, side: THREE.DoubleSide }),
  )
  wall.position.set(x, 0.57, z)
  wall.castShadow = true
  scene.add(wall)

  const rim = new THREE.Mesh(new THREE.TorusGeometry(pondRadius, 0.075, 12, 64), material(0x79a9ff, 0.25, 0.48))
  rim.rotation.x = Math.PI / 2
  rim.position.set(x, 0.9, z)
  scene.add(rim)

  const water = new THREE.Mesh(
    new THREE.CircleGeometry(pondRadius - 0.09, 64),
    new THREE.MeshPhysicalMaterial({ color: 0x276f80, roughness: 0.2, metalness: 0.08, transmission: 0.12, transparent: true, opacity: 0.92 }),
  )
  water.rotation.x = -Math.PI / 2
  water.position.set(x, 0.86, z)
  water.receiveShadow = true
  water.userData.pondIndex = index
  pondSurfaces.push(water)
  scene.add(water)

  const aerator = new THREE.Group()
  const hub = new THREE.Mesh(new THREE.CylinderGeometry(0.12, 0.12, 0.22, 16), material(0xe9f2f5, 0.32, 0.5))
  aerator.add(hub)
  for (let i = 0; i < 4; i += 1) {
    const arm = new THREE.Mesh(new THREE.BoxGeometry(0.62, 0.08, 0.12), material(i % 2 ? 0x36a8d1 : 0xe9f2f5))
    arm.position.x = 0.31
    arm.rotation.y = (Math.PI / 2) * i
    aerator.add(arm)
  }
  aerator.position.set(x, 1.02, z)
  scene.add(aerator)
}

function buildScene() {
  if (!scene) return
  addBox([15.8, 0.7, 11.6], [0, 0, 0], 0xc7cbd0)
  addBox([15.2, 0.12, 11], [0, 0.42, 0], 0x68717b)

  const ponds: Array<[number, number]> = [
    [4.55, 2.15], [0, 2.15], [-4.55, 2.15],
    [-4.55, -1.85], [0, -1.85], [4.55, -1.85],
  ]
  ponds.forEach(([x, z], index) => addPond(x, z, index))

  addBox([3.5, 1.35, 1.35], [4.55, 1.12, -4.55], 0xe4e7e8)
  addBox([2.1, 0.75, 1.1], [1.6, 0.82, -4.65], 0xd7dcdf)
  addBox([0.75, 0.55, 0.7], [6.45, 0.7, -4.45], 0x2d72b8)

  const walkway = material(0xe7bd23, 0.58, 0.18)
  ;[
    [0, 0.54, -5.05, 14.7, 0.07, 0.13],
    [7.15, 0.54, 0, 0.13, 0.07, 10],
    [-7.15, 0.54, 0, 0.13, 0.07, 10],
  ].forEach(([x, y, z, sx, sy, sz]) => {
    const lane = new THREE.Mesh(new THREE.BoxGeometry(sx, sy, sz), walkway)
    lane.position.set(x, y, z)
    scene!.add(lane)
  })
}

function format(value: number | null | undefined, precision: number) {
  return value == null ? '--' : Number(value).toFixed(precision)
}

function clearHighlight() {
  if (highlightedSurface) {
    const pondMaterial = highlightedSurface.material as THREE.MeshPhysicalMaterial
    pondMaterial.emissive.set(0x000000)
    pondMaterial.emissiveIntensity = 0
  }
  highlightedSurface = null
}

function handlePointerMove(event: PointerEvent) {
  if (!canvasHost.value || !camera) return
  const rect = canvasHost.value.getBoundingClientRect()
  pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
  pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1
  raycaster.setFromCamera(pointer, camera)
  const hit = raycaster.intersectObjects(pondSurfaces, false)[0]
  clearHighlight()
  if (!hit) {
    hoveredIndex.value = null
    canvasHost.value.style.cursor = 'grab'
    return
  }
  highlightedSurface = hit.object as THREE.Mesh
  const pondMaterial = highlightedSurface.material as THREE.MeshPhysicalMaterial
  pondMaterial.emissive.set(props.lightMode ? 0x58b8cc : 0x16d8e6)
  pondMaterial.emissiveIntensity = props.lightMode ? 0.18 : 0.35
  hoveredIndex.value = Number(hit.object.userData.pondIndex)
  tooltipPosition.x = Math.min(event.clientX - rect.left + 14, rect.width - 226)
  tooltipPosition.y = Math.max(10, Math.min(event.clientY - rect.top + 12, rect.height - 220))
  canvasHost.value.style.cursor = 'pointer'
}

function handlePointerLeave() {
  clearHighlight()
  hoveredIndex.value = null
}

function updateTheme() {
  if (!scene || !renderer) return
  const isLight = Boolean(props.lightMode)
  const background = isLight ? 0xeaf4f8 : 0x020d24
  scene.background = new THREE.Color(background)
  scene.fog = new THREE.Fog(background, 25, 52)
  renderer.setClearColor(background, 1)
  if (grid) {
    const mats = Array.isArray(grid.material) ? grid.material : [grid.material]
    mats.forEach(mat => { mat.opacity = isLight ? 0.18 : 0.28; mat.transparent = true })
  }
  if (ambient) {
    ambient.color.set(isLight ? 0xffffff : 0xb9dcff)
    ambient.groundColor.set(isLight ? 0x9eb6c4 : 0x071326)
    ambient.intensity = isLight ? 2.3 : 1.7
  }
}

function resetView() {
  camera?.position.copy(cameraPosition)
  controls?.target.copy(cameraTarget)
  controls?.update()
}

function resize() {
  if (!canvasHost.value || !renderer || !camera) return
  const { clientWidth, clientHeight } = canvasHost.value
  renderer.setSize(clientWidth, clientHeight, false)
  camera.aspect = clientWidth / Math.max(clientHeight, 1)
  camera.updateProjectionMatrix()
}

function animate() {
  controls?.update()
  if (renderer && scene && camera) renderer.render(scene, camera)
  animationFrame = requestAnimationFrame(animate)
}

watch(() => props.lightMode, updateTheme)

onMounted(() => {
  if (!canvasHost.value) return
  scene = new THREE.Scene()
  camera = new THREE.PerspectiveCamera(34, 1, 0.1, 100)
  renderer = new THREE.WebGLRenderer({ antialias: true, powerPreference: 'high-performance' })
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
  renderer.shadowMap.enabled = true
  renderer.shadowMap.type = THREE.PCFSoftShadowMap
  renderer.outputColorSpace = THREE.SRGBColorSpace
  canvasHost.value.appendChild(renderer.domElement)
  renderer.domElement.addEventListener('pointermove', handlePointerMove)
  renderer.domElement.addEventListener('pointerleave', handlePointerLeave)

  controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.dampingFactor = 0.08
  controls.minDistance = 10
  controls.maxDistance = 38
  controls.maxPolarAngle = Math.PI * 0.47
  controls.target.copy(cameraTarget)
  resetView()

  ambient = new THREE.HemisphereLight(0xb9dcff, 0x071326, 1.7)
  scene.add(ambient)
  const keyLight = new THREE.DirectionalLight(0xffffff, 3.2)
  keyLight.position.set(10, 18, 12)
  keyLight.castShadow = true
  keyLight.shadow.mapSize.set(2048, 2048)
  scene.add(keyLight)
  const cyanLight = new THREE.PointLight(0x21d9e8, 24, 28)
  cyanLight.position.set(-5, 8, -6)
  scene.add(cyanLight)

  grid = new THREE.GridHelper(50, 50, 0x1886b4, 0x0d4265)
  grid.position.y = -0.38
  scene.add(grid)
  buildScene()
  updateTheme()
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(canvasHost.value)
  resize()
  animate()
})

onUnmounted(() => {
  cancelAnimationFrame(animationFrame)
  resizeObserver?.disconnect()
  controls?.dispose()
  if (scene) {
    scene.traverse(object => {
      if (!(object instanceof THREE.Mesh)) return
      object.geometry.dispose()
      const materials = Array.isArray(object.material) ? object.material : [object.material]
      materials.forEach(item => item.dispose())
    })
  }
  renderer?.dispose()
  renderer?.domElement.removeEventListener('pointermove', handlePointerMove)
  renderer?.domElement.removeEventListener('pointerleave', handlePointerLeave)
  renderer?.domElement.remove()
  renderer = null
  scene = null
  camera = null
  controls = null
})
</script>

<style scoped>
.twin-stage { position: relative; min-height: 0; overflow: hidden; background: #020d24; }
.twin-stage--light { background: #eaf4f8; }
.twin-stage__canvas { width: 100%; height: 100%; cursor: grab; }
.twin-stage__canvas:active { cursor: grabbing; }
.twin-stage__canvas :deep(canvas) { display: block; width: 100%; height: 100%; }
.twin-stage__reset { position: absolute; top: 10px; right: 10px; padding: 5px 9px; border: 1px solid rgba(69,191,210,.45); border-radius: 4px; background: rgba(5,29,57,.78); color: #d7f7fb; font: 11px var(--font-body); cursor: pointer; backdrop-filter: blur(5px); }
.twin-stage__reset:hover { border-color: #31d8e3; color: #fff; }
.twin-stage--light .twin-stage__reset { background: rgba(246,252,254,.82); border-color: rgba(36,131,163,.38); color: #315f76; }
.twin-stage__tooltip { position: absolute; z-index: 3; width: 212px; padding: 11px 12px; border: 1px solid rgba(49,212,225,.48); border-radius: 7px; background: rgba(4,23,49,.92); color: #e8f8ff; pointer-events: none; box-shadow: 0 8px 24px rgba(0,5,20,.34); backdrop-filter: blur(8px); }
.twin-stage__tooltip strong { display: block; margin-bottom: 8px; color: #73f2ee; font-size: 13px; }
.twin-stage__tooltip p { margin: 2px 0 0; color: #8fa8ba; font-size: 12px; }
.twin-stage__data-grid { display: grid; grid-template-columns: 1fr auto; gap: 5px 10px; font-size: 11px; }
.twin-stage__data-grid span { color: #91a9bb; }
.twin-stage__data-grid b { color: #f0f8ff; font-family: var(--font-mono); font-weight: 500; }
.twin-stage--light .twin-stage__tooltip { border-color: rgba(34,137,166,.42); background: rgba(248,252,254,.95); color: #244d64; box-shadow: 0 8px 24px rgba(42,83,105,.18); }
.twin-stage--light .twin-stage__tooltip strong { color: #08788a; }
.twin-stage--light .twin-stage__tooltip p, .twin-stage--light .twin-stage__data-grid span { color: #607f91; }
.twin-stage--light .twin-stage__data-grid b { color: #1d485f; }
</style>
