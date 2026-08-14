<template>
  <div class="editor-shell">
    <header class="topbar">
      <div><h1>PMS 模块排版编辑器</h1><p>仅编辑水质、视频、生物量、3D 模型和智能体 · 按住模块直接拖动</p></div>
      <div class="topbar__actions">
        <span>{{ saveState }}</span>
        <button @click="reloadPage">刷新页面</button>
        <button @click="togglePreview">{{ previewMode ? '返回编辑' : '预览交互' }}</button>
        <button class="primary" @click="saveChanges">保存设计</button>
      </div>
    </header>

    <main class="workspace">
      <section class="stage-scroll">
        <div class="viewport-tools">
          <label>画布</label><button v-for="value in [0.6,0.75,0.9,1]" :key="value" :class="{active:scale===value}" @click="scale=value">{{ value*100 }}%</button>
        </div>
        <div class="canvas-wrap" :style="canvasWrapStyle">
          <div ref="canvasRef" class="canvas" :style="canvasStyle">
            <div ref="liveRef" class="live-page" :class="{'is-dragging':dragging}" @pointerdown.capture="canvasPointerDown" @click.capture="selectEvent" @mouseover.capture="hoverEvent" @mouseout.capture="outEvent">
              <LiveApp :key="liveKey" />
            </div>
            <div v-if="selection && !previewMode" class="selection" :class="{'is-dragging':dragging}" :style="selectionStyle">
              <span class="selection__tag" @pointerdown="beginMove"><b>⠿</b> {{ selection.tag }} · 按住元素可拖动</span>
              <i v-for="dir in directions" :key="dir" :class="`handle handle--${dir}`" @pointerdown.stop="beginResize($event,dir)" />
            </div>
          </div>
        </div>
      </section>

      <aside class="inspector">
        <template v-if="selection">
          <div class="inspector__head"><div><small>当前模块</small><strong>{{ selection.tag }}</strong></div><button @click="resetSelected">重置</button></div>
          <code>{{ selection.selector }}</code>
          <section><h2>位置与尺寸</h2><div class="fields fields--2">
            <label>X 位移<input v-model.number="form.x" type="number" @input="applyForm" /></label>
            <label>Y 位移<input v-model.number="form.y" type="number" @input="applyForm" /></label>
            <label>宽度<input v-model.number="form.width" type="number" @input="applyForm" /></label>
            <label>高度<input v-model.number="form.height" type="number" @input="applyForm" /></label>
          </div></section>
          <section><h2>快速操作</h2><div class="quick-actions"><button @click="nudge(-1,0)">←</button><button @click="nudge(0,-1)">↑</button><button @click="nudge(0,1)">↓</button><button @click="nudge(1,0)">→</button><button @click="duplicateStyle">复制样式</button></div></section>
        </template>
        <div v-else class="empty"><strong>选择一个主要模块</strong><p>可编辑：水质指标、视频、生物量计数、3D 模型、智能体。模块内部内容不会被误选。</p></div>
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import LiveApp from '../frontend/src/App.vue'

type Change = { selector:string;x:number;y:number;width:number|null;height:number|null;text?:string;styles:Record<string,string> }
type Selection = { el:HTMLElement;selector:string;tag:string;rect:DOMRect }
const canvasRef=ref<HTMLElement>();const liveRef=ref<HTMLElement>();const liveKey=ref(0);const selection=ref<Selection|null>(null); const previewMode=ref(false); const dragging=ref(false); const scale=ref(.75); const saveState=ref('尚未保存')
const changes=reactive<Record<string,Change>>({}); const directions=['n','e','s','w','ne','nw','se','sw'];let layerOrder=100
const moduleSelectors=['.metric-cards','.module-video','.biomass-section','.aerial-hub','.module-chat','.daily-report-module','.system-entry']
const moduleNames:Record<string,string>={'.metric-cards':'水质指标（pH / 水温 / 溶解氧）','.module-video':'视频监控','.biomass-section':'生物量估计与计数','.aerial-hub':'池塘航拍实景','.module-chat':'智能体','.daily-report-module':'生产日报','.system-entry':'外部系统入口（6项）'}
const form=reactive({x:0,y:0,width:0,height:0,text:'',fontSize:14,fontWeight:'400',color:'#000000',lineHeight:1.5,background:'#ffffff',radius:0,padding:0,margin:0,zIndex:0,opacity:1})
const canvasWrapStyle=computed(()=>({width:`${1440*scale.value}px`,height:`${900*scale.value}px`})); const canvasStyle=computed(()=>({transform:`scale(${scale.value})`}))
const selectionStyle=computed(()=>selection.value?({left:`${selection.value.rect.left}px`,top:`${selection.value.rect.top}px`,width:`${selection.value.rect.width}px`,height:`${selection.value.rect.height}px`}):{})

function cssPath(el:Element){const parts:string[]=[];let node:Element|null=el;const portalRoot=el.closest('.el-overlay,.el-popper');while(node&&node!==liveRef.value){let part=node.tagName.toLowerCase();if(node.id){part+=`#${CSS.escape(node.id)}`;parts.unshift(part);break}const cls=[...node.classList].filter(v=>!v.startsWith('is-')&&!v.startsWith('el-loading')).slice(0,2);if(cls.length)part+=cls.map(v=>`.${CSS.escape(v)}`).join('');const parent=node.parentElement;if(parent){const peers=[...parent.children].filter(v=>v.tagName===node!.tagName);if(peers.length>1)part+=`:nth-of-type(${peers.indexOf(node)+1})`}parts.unshift(part);if(node===portalRoot)break;node=parent}return parts.join(' > ')}
function rgbHex(value:string){const nums=value.match(/[\d.]+/g)?.slice(0,3).map(Number);return nums?.length===3?'#'+nums.map(v=>Math.round(v).toString(16).padStart(2,'0')).join(''):'#ffffff'}
function normalizedRect(el:HTMLElement){const rect=el.getBoundingClientRect(),base=canvasRef.value!.getBoundingClientRect();return new DOMRect((rect.left-base.left)/scale.value,(rect.top-base.top)/scale.value,rect.width/scale.value,rect.height/scale.value)}
function editableModule(target:HTMLElement){return moduleSelectors.map(selector=>target.closest<HTMLElement>(selector)).find(Boolean)??null}
function moduleName(el:HTMLElement){const key=moduleSelectors.find(selector=>el.matches(selector));return key?moduleNames[key]:el.className}
function readSelection(el:HTMLElement){const rect=normalizedRect(el);const style=getComputedStyle(el);const selector=cssPath(el);selection.value={el,selector,tag:moduleName(el),rect};const c=changes[selector];layerOrder+=1;Object.assign(form,{x:c?.x??0,y:c?.y??0,width:Math.round(rect.width),height:Math.round(rect.height),text:'',fontSize:parseFloat(style.fontSize)||14,fontWeight:style.fontWeight,color:rgbHex(style.color),lineHeight:parseFloat(style.lineHeight)/(parseFloat(style.fontSize)||14)||1.5,background:rgbHex(style.backgroundColor),radius:parseFloat(style.borderRadius)||0,padding:parseFloat(style.padding)||0,margin:parseFloat(style.margin)||0,zIndex:layerOrder,opacity:Number(style.opacity)||1});el.style.position='relative';el.style.zIndex=String(layerOrder);el.closest<HTMLElement>('.water-column,.biomass-column,.center-hub,.video-column')?.style.setProperty('z-index',String(layerOrder))}
function selectEvent(event:MouseEvent){if(previewMode.value)return;const module=editableModule(event.target as HTMLElement);if(!module)return;event.preventDefault();event.stopPropagation();readSelection(module)}
function canvasPointerDown(event:PointerEvent){if(previewMode.value||event.button!==0)return;const module=editableModule(event.target as HTMLElement);if(!module)return;readSelection(module);startPointer(event,'move')}
function hoverEvent(event:MouseEvent){if(previewMode.value)return;const module=editableModule(event.target as HTMLElement);if(module)module.dataset.editorHover='true'}
function outEvent(event:MouseEvent){const module=editableModule(event.target as HTMLElement);if(!module)return;if(event.relatedTarget instanceof Node&&module.contains(event.relatedTarget))return;delete module.dataset.editorHover}
function updateRect(){if(selection.value)selection.value={...selection.value,rect:normalizedRect(selection.value.el)}}
function ensureChange(){if(!selection.value)return null;return changes[selection.value.selector]??(changes[selection.value.selector]={selector:selection.value.selector,x:0,y:0,width:null,height:null,styles:{}})}
function applyForm(){if(!selection.value)return;const el=selection.value.el,c=ensureChange()!;c.x=form.x;c.y=form.y;c.width=form.width;c.height=form.height;c.styles={position:'relative',fontSize:`${form.fontSize}px`,fontWeight:form.fontWeight,color:form.color,lineHeight:String(form.lineHeight),backgroundColor:form.background,borderRadius:`${form.radius}px`,padding:`${form.padding}px`,margin:`${form.margin}px`,zIndex:String(form.zIndex),opacity:String(form.opacity),overflow:'visible'};el.style.translate=`${form.x}px ${form.y}px`;el.style.width=`${form.width}px`;el.style.height=`${form.height}px`;Object.assign(el.style,c.styles);saveState.value='有未保存修改';requestAnimationFrame(updateRect)}
function applyText(){if(!selection.value||selection.value.el.children.length)return;selection.value.el.textContent=form.text;ensureChange()!.text=form.text;saveState.value='有未保存修改';updateRect()}
function beginMove(event:PointerEvent){if((event.target as HTMLElement).classList.contains('handle'))return;startPointer(event,'move')}
function beginResize(event:PointerEvent,dir:string){startPointer(event,dir)}
function startPointer(event:PointerEvent,action:string){if(!selection.value)return;event.preventDefault();event.stopPropagation();const start={px:event.clientX,py:event.clientY,x:form.x,y:form.y,w:form.width,h:form.height};let moved=false;const move=(e:PointerEvent)=>{const rawX=e.clientX-start.px,rawY=e.clientY-start.py;if(!moved&&Math.hypot(rawX,rawY)<2)return;moved=true;dragging.value=true;const dx=rawX/scale.value,dy=rawY/scale.value;if(action==='move'){form.x=Math.round(start.x+dx);form.y=Math.round(start.y+dy)}else{if(action.includes('e'))form.width=Math.max(8,Math.round(start.w+dx));if(action.includes('s'))form.height=Math.max(8,Math.round(start.h+dy));if(action.includes('w')){form.width=Math.max(8,Math.round(start.w-dx));form.x=Math.round(start.x+dx)}if(action.includes('n')){form.height=Math.max(8,Math.round(start.h-dy));form.y=Math.round(start.y+dy)}}applyForm()};const end=()=>{dragging.value=false;window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',end);window.removeEventListener('pointercancel',end)};window.addEventListener('pointermove',move);window.addEventListener('pointerup',end);window.addEventListener('pointercancel',end)}
function applySaved(){if(!liveRef.value)return;Object.values(changes).forEach(c=>{const el=liveRef.value!.querySelector<HTMLElement>(c.selector);if(!el||!moduleSelectors.some(selector=>el.matches(selector)))return;el.style.translate=`${c.x}px ${c.y}px`;if(c.width)el.style.width=`${c.width}px`;if(c.height)el.style.height=`${c.height}px`;Object.assign(el.style,c.styles)})}
function togglePreview(){previewMode.value=!previewMode.value;selection.value=null}
function reloadPage(){selection.value=null;liveKey.value+=1;setTimeout(applySaved,500)}
function nudge(x:number,y:number){form.x+=x;form.y+=y;applyForm()}
function resetSelected(){if(!selection.value)return;delete changes[selection.value.selector];const el=selection.value.el;el.removeAttribute('style');saveState.value='有未保存修改';readSelection(el)}
function duplicateStyle(){if(!selection.value)return;navigator.clipboard.writeText(JSON.stringify(ensureChange()?.styles??{}));ElMessage.success('样式已复制为 JSON')}
async function saveChanges(){const payload={version:2,viewport:{width:1440,height:900},changes};const res=await fetch('/layout-save',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});if(!res.ok){ElMessage.error('保存失败');return}localStorage.setItem('pms-visual-editor',JSON.stringify(payload));saveState.value='已保存到 layout.json';ElMessage.success('设计已保存')}
onMounted(async()=>{try{const res=await fetch(`/layout.json?t=${Date.now()}`);if(res.ok){const data=await res.json();if(data.changes)Object.assign(changes,data.changes)}}catch{}setTimeout(applySaved,500)})
</script>

<style scoped>
.editor-shell{height:100vh;overflow:hidden;background:#dce7ec;color:#19384b;font-family:'Noto Sans SC',sans-serif}.topbar{height:62px;display:flex;align-items:center;justify-content:space-between;padding:0 18px;background:#fff;border-bottom:1px solid #afc5d0}.topbar h1{margin:0;font-size:16px}.topbar p{margin:2px 0 0;color:#6c8796;font-size:11px}.topbar__actions{display:flex;align-items:center;gap:7px}.topbar__actions span{margin-right:6px;color:#67808e;font-size:11px}button{border:1px solid #9cb8c5;border-radius:5px;background:#f7fbfc;color:#315d73;cursor:pointer}.topbar button{height:32px;padding:0 12px}.topbar .primary{background:#1689a4;border-color:#1689a4;color:#fff}.workspace{height:calc(100vh - 62px);display:grid;grid-template-columns:minmax(0,1fr) 292px}.stage-scroll{position:relative;overflow:auto;padding:44px 30px 30px;background-image:linear-gradient(rgba(48,104,128,.08) 1px,transparent 1px),linear-gradient(90deg,rgba(48,104,128,.08) 1px,transparent 1px);background-size:20px 20px}.viewport-tools{position:fixed;z-index:20;top:70px;left:12px;display:flex;gap:4px;padding:5px;background:#fff;border:1px solid #b6cbd4;border-radius:6px}.viewport-tools label{padding:3px 6px;font-size:11px}.viewport-tools button{padding:3px 7px;font-size:10px}.viewport-tools button.active{background:#1689a4;color:#fff}.canvas-wrap{margin:auto}.canvas{position:relative;width:1440px;height:900px;transform-origin:top left;box-shadow:0 12px 35px rgba(30,68,84,.22)}.live-page{width:1440px;height:900px;overflow:hidden;background:#03122d;cursor:grab;user-select:none}.live-page.is-dragging{cursor:grabbing}.live-page :deep(.app-body),.live-page :deep(.desktop-column),.live-page :deep(.system-entry),.live-page :deep(.system-entry__buttons){overflow:visible!important}.live-page [data-editor-hover="true"]{outline:1px dashed #22b8cf!important;outline-offset:-1px}.selection{position:absolute;z-index:99999;border:2px solid #14a9dc;box-shadow:0 0 0 1px #fff,0 0 16px rgba(20,169,220,.5);pointer-events:none}.selection.is-dragging{border-color:#ffb21a;box-shadow:0 0 0 1px #fff,0 0 20px rgba(255,178,26,.65)}.selection__tag{position:absolute;left:-2px;bottom:100%;min-height:22px;padding:4px 8px;background:#14a9dc;color:#fff;font:11px monospace;white-space:nowrap;cursor:grab;pointer-events:auto}.selection__tag:active{cursor:grabbing}.selection__tag b{font-size:15px;vertical-align:-1px}.handle{position:absolute;width:14px;height:14px;border:2px solid #fff;border-radius:3px;background:#14a9dc;box-shadow:0 1px 4px rgba(0,0,0,.35);pointer-events:auto}.handle--n{top:-8px;left:calc(50% - 7px);cursor:ns-resize}.handle--s{bottom:-8px;left:calc(50% - 7px);cursor:ns-resize}.handle--e{right:-8px;top:calc(50% - 7px);cursor:ew-resize}.handle--w{left:-8px;top:calc(50% - 7px);cursor:ew-resize}.handle--ne{right:-8px;top:-8px;cursor:nesw-resize}.handle--nw{left:-8px;top:-8px;cursor:nwse-resize}.handle--se{right:-8px;bottom:-8px;cursor:nwse-resize}.handle--sw{left:-8px;bottom:-8px;cursor:nesw-resize}.inspector{overflow:auto;padding:14px;background:#f7fafb;border-left:1px solid #b7cbd4}.inspector__head{display:flex;justify-content:space-between;align-items:center}.inspector__head small{display:block;color:#78909d}.inspector__head strong{font-size:14px}.inspector__head button{padding:5px 8px}.inspector code{display:block;margin:10px 0;padding:7px;overflow-wrap:anywhere;background:#e8f1f5;color:#527080;font-size:9px}.inspector section{padding:11px 0;border-top:1px solid #d2e0e6}.inspector h2{margin:0 0 8px;font-size:12px}.fields{display:grid;gap:7px}.fields--2{grid-template-columns:1fr 1fr}.inspector label{display:grid;gap:3px;color:#607d8c;font-size:10px}.inspector input,.inspector select,.inspector textarea{width:100%;min-width:0;padding:6px;border:1px solid #b8ccd5;border-radius:4px;background:#fff;color:#294e61;font:11px inherit}.inspector input[type=color]{height:30px;padding:2px}.quick-actions{display:flex;gap:5px;flex-wrap:wrap}.quick-actions button{padding:6px 9px}.empty{margin-top:35vh;text-align:center;color:#6f8996}.empty strong{font-size:13px}.empty p{font-size:11px;line-height:1.7}
</style>
