<script setup lang="ts">
import { ref } from 'vue'
import { ChevronDown } from 'lucide-vue-next'

interface FaqItem {
  question: string
  answer: string
}

const faqItems: FaqItem[] = [
  {
    question: '小墨是什么？',
    answer: '小墨是一个基于大语言模型的金融投资 AI 助手。它能够理解自然语言提问，结合实时金融数据和专业工具，为投资者提供智能问答、深度分析、行情监控等一站式服务。支持 30+ 种金融工具调用，覆盖 A 股、港股、美股等多市场数据。',
  },
  {
    question: '支持哪些数据源？',
    answer: '小墨集成了 44 个数据端点，数据源包括：腾讯行情（实时报价）、东方财富（研报、龙虎榜、融资融券、行业轮动等）、同花顺（热点概念、EPS 预测）、巨潮资讯（上市公司公告）、新浪财经（财报数据）、问财 iwencai（自然语言选股）等。所有东财接口均内置限流保护，确保稳定访问。',
  },
  {
    question: '账号安全如何保障？',
    answer: '我们采用多层次安全措施保护您的账户：密码传输使用客户端 SHA-256 哈希 + 服务端 BCrypt 加密存储；登录 Token 采用 Redis 管理，72 小时自动过期且支持单设备登录；所有 API 请求均需携带有效 Token 鉴权；数据库和 Redis 均部署在安全的内网环境中。',
  },
]

const openIndex = ref<number | null>(null)

function toggle(index: number) {
  openIndex.value = openIndex.value === index ? null : index
}
</script>

<template>
  <section id="faq" class="portal-section">
    <div class="portal-container-narrow">
      <!-- Section header -->
      <div v-animate class="portal-section-header">
        <h2 class="portal-serif">常见问题</h2>
        <div class="portal-deco-bar" />
      </div>

      <!-- Accordion -->
      <div v-animate="200" class="portal-faq-list">
        <div
          v-for="(item, index) in faqItems"
          :key="index"
          class="portal-accordion-item"
        >
          <button
            class="portal-accordion-trigger"
            :class="{ open: openIndex === index }"
            @click="toggle(index)"
          >
            <span>{{ item.question }}</span>
            <ChevronDown :size="20" />
          </button>
          <div
            class="portal-accordion-content"
            :class="{ open: openIndex === index }"
          >
            <div>
              <div class="portal-accordion-body">
                {{ item.answer }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.portal-section-header {
  text-align: center;
  margin-bottom: 64px;
}

.portal-section-header h2 {
  font-size: 36px;
  font-weight: 600;
  letter-spacing: -0.5px;
  color: var(--portal-text);
}

@media (min-width: 768px) {
  .portal-section-header h2 {
    font-size: 48px;
  }
}

.portal-faq-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
