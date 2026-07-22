<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sendChat, type ChatMessage } from '../api/chat'

const messages = ref<ChatMessage[]>([
  {
    role: 'assistant',
    content: '你好，我是池塘运营助手。可以问我水质、生物量、投喂记录或投喂策略。',
  },
])
const input = ref('')
const loading = ref(false)
const listRef = ref<HTMLElement | null>(null)

const suggestions = [
  '现在溶氧多少？',
  '有哪些池塘？',
  '一号塘今天建议喂多少？',
]

const MAX_HISTORY_MESSAGES = 10

async function scrollToBottom() {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}

async function ask(text: string) {
  const content = text.trim()
  if (!content || loading.value) return

  messages.value.push({ role: 'user', content })
  input.value = ''
  loading.value = true
  await scrollToBottom()

  try {
    const history = messages.value
      .filter((m) => m.role === 'user' || m.role === 'assistant')
      .slice(-MAX_HISTORY_MESSAGES)
    const { data } = await sendChat(history)
    if (data.code !== 200 || !data.data?.content) {
      throw new Error(data.message || '问答失败')
    }
    messages.value.push({
      role: 'assistant',
      content: data.data.content,
    })
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '问答请求失败'
    ElMessage.error(msg)
    messages.value.push({
      role: 'assistant',
      content: `抱歉，暂时无法回答：${msg}`,
    })
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

function onSubmit() {
  void ask(input.value)
}
</script>

<template>
  <section class="chat-panel panel">
    <header class="chat-panel__header">
      <h2 class="section-title">运营问答助手</h2>
      <span class="chat-panel__hint">可查询水质 / 生物量 / 投喂</span>
    </header>

    <div ref="listRef" class="chat-panel__messages" v-loading="loading">
      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="chat-bubble"
        :class="`chat-bubble--${msg.role}`"
      >
        <div class="chat-bubble__role">{{ msg.role === 'user' ? '我' : '助手' }}</div>
        <div class="chat-bubble__content">{{ msg.content }}</div>
      </div>
    </div>

    <div class="chat-panel__suggestions">
      <button
        v-for="item in suggestions"
        :key="item"
        type="button"
        class="chat-suggestion"
        :disabled="loading"
        @click="ask(item)"
      >
        {{ item }}
      </button>
    </div>

    <form class="chat-panel__input" @submit.prevent="onSubmit">
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        :maxlength="500"
        show-word-limit
        resize="none"
        placeholder="例如：一号塘最近 7 天生物量怎么样？"
        :disabled="loading"
        @keydown.enter.exact.prevent="onSubmit"
      />
      <el-button type="primary" :loading="loading" :disabled="!input.trim()" @click="onSubmit">
        发送
      </el-button>
    </form>
  </section>
</template>

<style scoped>
.chat-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  padding: 14px;
  gap: 10px;
}

.chat-panel__header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  flex-shrink: 0;
}

.chat-panel__hint {
  color: var(--text-muted);
  font-size: 12px;
  white-space: nowrap;
}

.chat-panel__messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 2px;
}

.chat-bubble {
  max-width: 92%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.chat-bubble--user {
  align-self: flex-end;
}

.chat-bubble--assistant {
  align-self: flex-start;
}

.chat-bubble__role {
  font-size: 11px;
  color: var(--text-muted);
  padding: 0 4px;
}

.chat-bubble--user .chat-bubble__role {
  text-align: right;
}

.chat-bubble__content {
  padding: 10px 12px;
  border-radius: 10px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
}

.chat-bubble--assistant .chat-bubble__content {
  background: var(--panel-elevated);
  border: 1px solid var(--border-subtle);
  color: var(--text-primary);
}

.chat-bubble--user .chat-bubble__content {
  background: rgba(86, 180, 233, 0.16);
  border: 1px solid rgba(86, 180, 233, 0.28);
  color: var(--text-primary);
}

.chat-panel__suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex-shrink: 0;
}

.chat-suggestion {
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
}

.chat-suggestion:hover:not(:disabled) {
  border-color: var(--color-dox);
  color: var(--color-dox);
}

.chat-suggestion:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.chat-panel__input {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  align-items: end;
  flex-shrink: 0;
}

.chat-panel__input :deep(.el-textarea__inner) {
  background: var(--panel-elevated);
  box-shadow: none;
}
</style>
