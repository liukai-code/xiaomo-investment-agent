<!-- frontend/src/components/SettingsDialog.vue -->
<template>
  <Transition name="settings-fade">
    <div class="settings-overlay" v-if="visible" @click.self="close">
    <div class="settings-dialog">
      <div class="settings-header">
        <h3><Settings :size="18" /> 设置</h3>
        <button class="close-btn" @click="close"><X :size="20" /></button>
      </div>

      <div class="settings-content">
        <div class="settings-sidebar">
          <div
            v-for="menu in menus"
            :key="menu.key"
            class="menu-item"
            :class="{ active: activeMenu === menu.key }"
            @click="activeMenu = menu.key"
          >
            <component :is="menu.icon" :size="16" />
            <span>{{ menu.label }}</span>
          </div>
        </div>

        <div class="settings-body">
          <!-- API配置 -->
          <div v-if="activeMenu === 'api'" class="section api-section">
            <div class="channel-list-panel">
              <div class="channel-list-header">
                <h4>渠道列表</h4>
                <button class="add-channel-btn" @click="handleAddChannel" title="添加渠道">
                  <Plus :size="16" />
                </button>
              </div>
              <div class="channel-list">
                <div
                  v-for="channel in channels"
                  :key="channel.id"
                  class="channel-item"
                  :class="{ active: selectedChannelId === channel.id }"
                  @click="selectChannel(channel)"
                >
                  <div class="channel-item-info">
                    <div class="channel-item-name">
                      <RadioTower :size="14" />
                      <span>{{ channel.channelName }}</span>
                    </div>
                    <div class="channel-item-model">{{ channel.modelName || '未配置模型' }}</div>
                  </div>
                  <div class="channel-item-actions">
                    <span v-if="channel.active" class="active-badge" title="当前使用">使用中</span>
                    <button
                      v-if="!channel.active"
                      class="icon-btn activate-btn"
                      @click.stop="handleActivateChannel(channel)"
                      title="设为当前渠道"
                    >
                      <Check :size="14" />
                    </button>
                    <button
                      class="icon-btn delete-btn"
                      @click.stop="handleDeleteChannel(channel)"
                      title="删除渠道"
                    >
                      <Trash2 :size="14" />
                    </button>
                  </div>
                </div>
                <div v-if="channels.length === 0" class="channel-empty">
                  暂无渠道，点击上方 + 添加
                </div>
              </div>
            </div>

            <div class="channel-form-panel">
              <div v-if="selectedChannelId !== null || isCreating">
                <h4>{{ isCreating ? '新建渠道' : '编辑渠道' }}</h4>
                <p class="section-desc">{{ isCreating ? '配置新的API连接信息' : '修改当前渠道配置' }}</p>

                <div class="form-group">
                  <label><RadioTower :size="14" /> 渠道名称</label>
                  <input
                    v-model="channelForm.channelName"
                    placeholder="输入渠道名称，如：官方API、代理渠道"
                  />
                </div>

                <div class="form-group">
                  <label><Key :size="14" /> API Key</label>
                  <div class="input-group">
                    <input
                      :type="showApiKey ? 'text' : 'password'"
                      v-model="channelForm.apiKey"
                      :placeholder="isCreating ? '输入API Key' : '留空则不更新'"
                    />
                    <button class="toggle-btn" @click="showApiKey = !showApiKey">
                      <EyeOff v-if="showApiKey" :size="14" />
                      <Eye v-else :size="14" />
                    </button>
                  </div>
                </div>

                <div class="form-group">
                  <label><Globe :size="14" /> Base URL</label>
                  <input
                    v-model="channelForm.baseUrl"
                    placeholder="https://api.example.com"
                  />
                </div>

                <div class="form-group">
                  <label><Cpu :size="14" /> 模型选择</label>
                  <div class="model-select">
                    <select v-model="channelForm.modelName" @change="onModelChange">
                      <option value="">选择模型</option>
                      <option v-for="model in presetModels" :key="model" :value="model">
                        {{ model }}
                      </option>
                      <option value="custom">自定义模型</option>
                    </select>
                    <input
                      v-if="isCustomModel"
                      v-model="customModelName"
                      placeholder="输入模型名称"
                      @blur="onCustomModelBlur"
                    />
                  </div>
                </div>

                <div class="section-actions">
                  <button class="test-btn" @click="handleTest" :disabled="testing || channelLoading">
                    <Loader2 v-if="testing" :size="14" class="spin" />
                    <Plug v-else :size="14" />
                    {{ testing ? '测试中...' : '测试连接' }}
                  </button>
                  <button class="save-btn" @click="handleSaveChannel" :disabled="channelLoading || testing">
                    <Loader2 v-if="channelLoading" :size="14" class="spin" />
                    <Save v-else :size="14" />
                    {{ channelLoading ? '保存中...' : '保存' }}
                  </button>
                </div>
                <div v-if="testResult" class="test-result success">
                  <CheckCircle2 :size="14" />
                  连接成功！延迟: {{ testResult.latencyMs }}ms
                  <span v-if="testResult.model"> | 模型: {{ testResult.model }}</span>
                </div>
                <div v-if="testError" class="test-result error">
                  <XCircle :size="14" />
                  {{ testError }}
                </div>
              </div>
              <div v-else class="channel-form-empty">
                <RadioTower :size="48" :stroke-width="1" />
                <p>选择左侧渠道进行编辑，或点击 + 添加新渠道</p>
              </div>
            </div>
          </div>

          <!-- 账户信息 -->
          <div v-if="activeMenu === 'account'" class="section">
            <h4><User :size="16" /> 账户信息</h4>
            <p class="section-desc">查看您的账户详情和修改密码</p>

            <div class="account-info-card">
              <div class="info-row">
                <span class="info-label"><Shield :size="14" /> 账号</span>
                <span class="info-value mono">{{ authStore.accountId || '—' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label"><Globe :size="14" /> 邮箱</span>
                <span class="info-value">{{ authStore.email || '—' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label"><BarChart3 :size="14" /> 注册时间</span>
                <span class="info-value">{{ formatDate(authStore.createdAt) }}</span>
              </div>
            </div>

            <div v-if="authStore.freeTokenQuota > 0" class="account-quota-card">
              <h5><Gift :size="14" /> 免费体验额度</h5>
              <div class="quota-info-row">
                <div class="quota-info-item">
                  <span class="quota-num">{{ remainingFreeTokens.toLocaleString() }}</span>
                  <span class="quota-label">剩余 Token</span>
                </div>
                <div class="quota-info-item">
                  <span class="quota-num">{{ authStore.freeTokenUsed.toLocaleString() }}</span>
                  <span class="quota-label">已使用</span>
                </div>
                <div class="quota-info-item">
                  <span class="quota-num">{{ authStore.freeTokenQuota.toLocaleString() }}</span>
                  <span class="quota-label">总额度</span>
                </div>
              </div>
              <div class="quota-progress">
                <div class="quota-bar" :style="{ width: freeQuotaPercent + '%' }"></div>
              </div>
            </div>

            <div class="password-section">
              <h5><Key :size="14" /> 修改密码</h5>
              <div class="form-group">
                <label>旧密码</label>
                <div class="input-group">
                  <input
                    :type="showOldPwd ? 'text' : 'password'"
                    v-model="pwdForm.oldPassword"
                    placeholder="输入当前密码"
                  />
                  <button class="toggle-btn" @click="showOldPwd = !showOldPwd">
                    <EyeOff v-if="showOldPwd" :size="14" />
                    <Eye v-else :size="14" />
                  </button>
                </div>
              </div>
              <div class="form-group">
                <label>新密码</label>
                <div class="input-group">
                  <input
                    :type="showNewPwd ? 'text' : 'password'"
                    v-model="pwdForm.newPassword"
                    placeholder="至少6位"
                  />
                  <button class="toggle-btn" @click="showNewPwd = !showNewPwd">
                    <EyeOff v-if="showNewPwd" :size="14" />
                    <Eye v-else :size="14" />
                  </button>
                </div>
              </div>
              <div class="form-group">
                <label>确认新密码</label>
                <input
                  type="password"
                  v-model="pwdForm.confirmPassword"
                  placeholder="再次输入新密码"
                />
              </div>
              <div class="section-actions">
                <button class="save-btn" @click="handleChangePassword" :disabled="pwdLoading">
                  <Loader2 v-if="pwdLoading" :size="14" class="spin" />
                  <Key v-else :size="14" />
                  {{ pwdLoading ? '提交中...' : '修改密码' }}
                </button>
              </div>
              <div v-if="pwdSuccess" class="test-result success">
                <CheckCircle2 :size="14" />
                密码修改成功，下次登录请使用新密码
              </div>
              <div v-if="pwdError" class="test-result error">
                <XCircle :size="14" />
                {{ pwdError }}
              </div>
            </div>
          </div>

          <!-- 对话偏好 -->
          <div v-if="activeMenu === 'preferences'" class="section">
            <h4><Sliders :size="16" /> 对话偏好</h4>
            <p class="section-desc">调整 AI 对话的参数，影响回复风格和上下文长度</p>

            <div class="pref-group">
              <div class="pref-header">
                <label>Temperature（创造性）</label>
                <span class="pref-value">{{ (prefs.temperature ?? 0.7).toFixed(1) }}</span>
              </div>
              <input
                type="range"
                v-model.number="prefs.temperature"
                min="0" max="1" step="0.1"
                class="pref-slider"
              />
              <div class="pref-range-labels">
                <span>精确确定</span>
                <span>平衡</span>
                <span>创意发散</span>
              </div>
              <p class="pref-hint">越低越确定、越保守；越高越发散、越有创意。金融分析建议 0.3 以下。</p>
            </div>

            <div class="pref-group">
              <div class="pref-header">
                <label>最大回复长度 (maxTokens)</label>
                <span class="pref-value">{{ (prefs.maxTokens ?? 4096).toLocaleString() }}</span>
              </div>
              <select v-model.number="prefs.maxTokens" class="pref-select">
                <option :value="1024">1,024 — 简短回答</option>
                <option :value="2048">2,048 — 适中</option>
                <option :value="4096">4,096 — 默认</option>
                <option :value="8192">8,192 — 详细分析</option>
                <option :value="16384">16,384 — 最大长度</option>
              </select>
              <p class="pref-hint">控制单次回复的最大 Token 数量，较长回复消耗更多额度。</p>
            </div>

            <div class="pref-group">
              <div class="pref-header">
                <label>上下文窗口</label>
                <span class="pref-value">{{ prefs.contextWindow ?? 50 }} 条</span>
              </div>
              <input
                type="range"
                v-model.number="prefs.contextWindow"
                min="5" max="100" step="5"
                class="pref-slider"
              />
              <div class="pref-range-labels">
                <span>5 条</span>
                <span>50 条</span>
                <span>100 条</span>
              </div>
              <p class="pref-hint">保留的历史消息条数。越大上下文越完整，但消耗 Token 越多。</p>
            </div>

            <div class="pref-group">
              <div class="pref-header">
                <label>记忆功能</label>
                <label class="switch">
                  <input type="checkbox" v-model="prefs.memoryEnabled" />
                  <span class="switch-slider"></span>
                </label>
              </div>
              <p class="pref-hint">开启后 AI 会记住您的偏好和画像，越用越懂你。关闭后不再注入记忆到对话上下文，也不再自动提取画像。</p>
            </div>

            <div class="pref-group" :class="{ 'pref-disabled': !prefs.memoryEnabled }">
              <div class="pref-header">
                <label>对话摘要压缩</label>
                <label class="switch">
                  <input type="checkbox" v-model="prefs.compressionEnabled" :disabled="!prefs.memoryEnabled" />
                  <span class="switch-slider"></span>
                </label>
              </div>
              <p class="pref-hint">开启后长对话会自动压缩历史消息为摘要，节省 Token 消耗。关闭后保留完整对话历史。</p>
            </div>

            <div class="section-actions">
              <button class="reset-btn" @click="resetPreferences">
                <RotateCcw :size="14" />
                恢复默认
              </button>
              <button class="save-btn" @click="savePreferences" :disabled="prefsLoading">
                <Loader2 v-if="prefsLoading" :size="14" class="spin" />
                <Save v-else :size="14" />
                {{ prefsLoading ? '保存中...' : '保存' }}
              </button>
            </div>
            <div v-if="prefsSuccess" class="test-result success">
              <CheckCircle2 :size="14" />
              偏好已保存，下次对话生效
            </div>
            <div v-if="prefsError" class="test-result error">
              <XCircle :size="14" />
              {{ prefsError }}
            </div>
          </div>

          <!-- 用量统计 -->
          <div v-if="activeMenu === 'stats'" class="section">
            <h4>用量统计</h4>
            <p class="section-desc">查看您的AI使用情况</p>

            <div v-if="statsLoading" class="stats-loading">
              <Loader2 :size="20" class="spin" />
              <span>加载中...</span>
            </div>

            <div v-else-if="statsError" class="test-result error">
              <XCircle :size="14" />
              {{ statsError }}
            </div>

            <div v-else-if="usageStats" class="stats-container">
              <!-- 免费额度卡片 -->
              <div v-if="authStore.freeTokenQuota > 0" class="stats-group">
                <h5><Gift :size="14" /> 免费体验额度</h5>
                <div class="stats-grid">
                  <div class="stat-card free-quota-card">
                    <div class="stat-value">{{ remainingFreeTokens.toLocaleString() }}</div>
                    <div class="stat-label">剩余 Token</div>
                  </div>
                  <div class="stat-card free-quota-card">
                    <div class="stat-value">{{ authStore.freeTokenUsed.toLocaleString() }}</div>
                    <div class="stat-label">已使用</div>
                  </div>
                </div>
                <div class="quota-progress">
                  <div class="quota-bar" :style="{ width: freeQuotaPercent + '%' }"></div>
                </div>
                <p class="section-desc" style="margin-top: 8px;">额度用完后请在「API 配置」中添加自己的 API Key</p>
              </div>

              <div class="stats-group">
                <h5>Token 用量</h5>
                <div class="stats-grid">
                  <div class="stat-card">
                    <div class="stat-value">{{ usageStats.totalRequests.toLocaleString() }}</div>
                    <div class="stat-label">请求次数</div>
                  </div>
                  <div class="stat-card">
                    <div class="stat-value">{{ usageStats.totalInputTokens.toLocaleString() }}</div>
                    <div class="stat-label">输入 Tokens</div>
                  </div>
                  <div class="stat-card">
                    <div class="stat-value">{{ usageStats.totalOutputTokens.toLocaleString() }}</div>
                    <div class="stat-label">输出 Tokens</div>
                  </div>
                  <div class="stat-card">
                    <div class="stat-value">{{ (usageStats.totalInputTokens + usageStats.totalOutputTokens).toLocaleString() }}</div>
                    <div class="stat-label">总 Tokens</div>
                  </div>
                </div>
              </div>

              <div class="stats-group">
                <h5>对话统计</h5>
                <div class="stats-grid">
                  <div class="stat-card">
                    <div class="stat-value">{{ usageStats.totalConversations.toLocaleString() }}</div>
                    <div class="stat-label">对话数</div>
                  </div>
                  <div class="stat-card">
                    <div class="stat-value">{{ usageStats.totalMessages.toLocaleString() }}</div>
                    <div class="stat-label">消息数</div>
                  </div>
                  <div class="stat-card">
                    <div class="stat-value">{{ usageStats.totalToolCalls.toLocaleString() }}</div>
                    <div class="stat-label">工具调用次数</div>
                  </div>
                </div>
              </div>

              <!-- 每日 Token 趋势 -->
              <div v-if="tokenTrendOption" class="stats-group">
                <h5>每日 Token 消耗趋势</h5>
                <v-chart :option="tokenTrendOption" autoresize style="height: 220px" />
              </div>

              <!-- 底部双图表 -->
              <div v-if="requestBarOption || tokenPieOption" class="stats-charts-row">
                <div v-if="requestBarOption" class="stats-group stats-chart-half">
                  <h5>每日请求数</h5>
                  <v-chart :option="requestBarOption" autoresize style="height: 200px" />
                </div>
                <div v-if="tokenPieOption" class="stats-group stats-chart-half">
                  <h5>Token 构成</h5>
                  <v-chart :option="tokenPieOption" autoresize style="height: 240px" />
                </div>
              </div>

              <div class="section-actions">
                <button class="reset-btn" @click="handleResetStats" :disabled="statsLoading">
                  <RotateCcw :size="14" />
                  重置统计
                </button>
                <button class="save-btn" @click="loadStats" :disabled="statsLoading">
                  <BarChart3 :size="14" />
                  刷新数据
                </button>
              </div>
            </div>

            <div v-else class="stats-loading">
              <span>暂无数据</span>
            </div>
          </div>

          <!-- 关于 -->
          <div v-if="activeMenu === 'about'" class="section">
            <h4>关于</h4>
            <p class="section-desc">小墨金融投资导学助手</p>
            <div class="about-info">
              <p>版本：1.0.0</p>
              <p>技术栈：Spring Boot 3.5 + Vue 3</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue';
import {
  getConfig, saveConfig, testConfig, TestConnectionResult, UserConfig,
  getUsageStats, resetUsageStats, UsageStats,
  getDailyUsage, DailyUsage,
  getChannels, createChannel, updateChannel, deleteChannel, activateChannel,
  ApiChannel, ApiChannelList,
  getPreferences, updatePreferences, UserPreferences,
} from '../api/config';
import { use } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { LineChart, BarChart, PieChart } from 'echarts/charts';
import {
  GridComponent, TooltipComponent, LegendComponent, DataZoomComponent
} from 'echarts/components';
import VChart from 'vue-echarts';

use([
  CanvasRenderer,
  LineChart, BarChart, PieChart,
  GridComponent, TooltipComponent, LegendComponent, DataZoomComponent
]);
import {
  Settings, X, Key, Globe, Cpu, Eye, EyeOff, Plug, Save, Loader2,
  CheckCircle2, XCircle, Info, BarChart3, Plus, Trash2, Check, RadioTower, RotateCcw, Gift,
  User, Shield, Sliders,
} from 'lucide-vue-next';
import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();

const remainingFreeTokens = computed(() => Math.max(0, authStore.freeTokenQuota - authStore.freeTokenUsed));
const freeQuotaPercent = computed(() => authStore.freeTokenQuota > 0 ? Math.min(100, (authStore.freeTokenUsed / authStore.freeTokenQuota) * 100) : 0);

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'saved'): void;
}>();

const activeMenu = ref('api');
const loading = ref(false);
const showApiKey = ref(false);
const isCustomModel = ref(false);
const customModelName = ref('');
const testing = ref(false);
const testResult = ref<TestConnectionResult | null>(null);
const testError = ref('');
const usageStats = ref<UsageStats | null>(null);
const dailyData = ref<DailyUsage[]>([]);
const statsLoading = ref(false);
const statsError = ref('');

// 修改密码相关
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });
const showOldPwd = ref(false);
const showNewPwd = ref(false);
const pwdLoading = ref(false);
const pwdError = ref('');
const pwdSuccess = ref(false);

// 对话偏好相关
const prefs = reactive<UserPreferences>({ temperature: 0.7, maxTokens: 4096, contextWindow: 50, memoryEnabled: true, compressionEnabled: true });
const prefsLoading = ref(false);
const prefsError = ref('');
const prefsSuccess = ref(false);

// 多渠道相关状态
const channels = ref<ApiChannel[]>([]);
const selectedChannelId = ref<number | null>(null);
const isCreating = ref(false);
const channelLoading = ref(false);

const menus = [
  { key: 'api', label: 'API配置', icon: Key },
  { key: 'account', label: '账户信息', icon: User },
  { key: 'preferences', label: '对话偏好', icon: Sliders },
  { key: 'stats', label: '用量统计', icon: BarChart3 },
  { key: 'about', label: '关于', icon: Info },
];

const presetModels = [
  'mimo-v2.5-pro',
  'claude-opus-4-7',
  'claude-sonnet-4-6',
  'gpt-4o',
  'gpt-4-turbo',
];

const channelForm = reactive({
  channelName: '',
  apiKey: '',
  baseUrl: '',
  modelName: '',
});

watch(() => props.visible, async (newVal) => {
  if (newVal) {
    await loadChannels();
    // 如果当前在用量统计 tab，自动刷新数据
    if (activeMenu.value === 'stats') {
      await loadStats();
    }
  }
});

watch(activeMenu, async (newVal) => {
  if (newVal === 'stats') {
    await loadStats();
  }
  if (newVal === 'preferences') {
    await loadPreferences();
  }
});

async function loadChannels() {
  try {
    const data = await getChannels();
    channels.value = data.channels;
    // 自动选中激活的渠道
    if (data.activeChannelId) {
      const activeChannel = data.channels.find(c => c.id === data.activeChannelId);
      if (activeChannel) {
        selectChannel(activeChannel);
      }
    } else if (data.channels.length > 0) {
      selectChannel(data.channels[0]);
    } else {
      selectedChannelId.value = null;
      isCreating.value = false;
    }
  } catch (error) {
    console.error('加载渠道列表失败:', error);
  }
}

function selectChannel(channel: ApiChannel) {
  selectedChannelId.value = channel.id;
  isCreating.value = false;
  channelForm.channelName = channel.channelName;
  channelForm.apiKey = ''; // 不回显已脱敏的key
  channelForm.baseUrl = channel.baseUrl || '';
  channelForm.modelName = channel.modelName || '';

  if (channel.modelName && !presetModels.includes(channel.modelName)) {
    isCustomModel.value = true;
    customModelName.value = channel.modelName;
  } else {
    isCustomModel.value = false;
    customModelName.value = '';
  }

  // 清除测试结果
  testResult.value = null;
  testError.value = '';
}

function handleAddChannel() {
  selectedChannelId.value = null;
  isCreating.value = true;
  channelForm.channelName = '';
  channelForm.apiKey = '';
  channelForm.baseUrl = '';
  channelForm.modelName = '';
  isCustomModel.value = false;
  customModelName.value = '';
  testResult.value = null;
  testError.value = '';
}

async function loadStats() {
  statsLoading.value = true;
  statsError.value = '';
  try {
    const [s, d] = await Promise.all([getUsageStats(), getDailyUsage()]);
    usageStats.value = s;
    dailyData.value = d;
  } catch (error) {
    console.error('加载统计数据失败:', error);
    statsError.value = (error as Error).message || '加载失败';
  } finally {
    statsLoading.value = false;
  }
}

// ECharts 图表配置
const tokenTrendOption = computed(() => {
  if (!dailyData.value.length) return null;
  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any[]) => {
        const date = params[0]?.name || '';
        let html = `<div style="font-weight:600;margin-bottom:4px">${date}</div>`;
        for (const p of params) {
          html += `<div>${p.marker} ${p.seriesName}: ${(p.value as number).toLocaleString()}</div>`;
        }
        return html;
      }
    },
    legend: { data: ['输入 Token', '输出 Token'], top: 4 },
    grid: { left: 60, right: 20, top: 40, bottom: 60 },
    dataZoom: [{ type: 'inside' }, { type: 'slider', height: 20, bottom: 8 }],
    xAxis: {
      type: 'category',
      data: dailyData.value.map(d => d.date),
      axisLabel: { fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        fontSize: 11,
        formatter: (v: number) => v >= 1000 ? (v / 1000).toFixed(0) + 'k' : String(v)
      }
    },
    series: [
      {
        name: '输入 Token',
        type: 'line',
        stack: 'total',
        areaStyle: { opacity: 0.3 },
        data: dailyData.value.map(d => d.inputTokens),
        itemStyle: { color: '#1890ff' }
      },
      {
        name: '输出 Token',
        type: 'line',
        stack: 'total',
        areaStyle: { opacity: 0.3 },
        data: dailyData.value.map(d => d.outputTokens),
        itemStyle: { color: '#52c41a' }
      }
    ]
  };
});

const requestBarOption = computed(() => {
  if (!dailyData.value.length) return null;
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 20, bottom: 40 },
    xAxis: {
      type: 'category',
      data: dailyData.value.map(d => d.date),
      axisLabel: { fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      axisLabel: { fontSize: 11 }
    },
    series: [{
      type: 'bar',
      data: dailyData.value.map(d => d.requestCount),
      itemStyle: { color: '#1890ff', borderRadius: [4, 4, 0, 0] },
      barMaxWidth: 24
    }]
  };
});

const tokenPieOption = computed(() => {
  const totalInput = usageStats.value?.totalInputTokens || 0;
  const totalOutput = usageStats.value?.totalOutputTokens || 0;
  if (totalInput === 0 && totalOutput === 0) return null;
  return {
    tooltip: {
      formatter: (p: any) => `${p.name}: ${p.value.toLocaleString()} (${p.percent}%)`
    },
    legend: { bottom: 0, textStyle: { fontSize: 12 } },
    series: [{
      type: 'pie',
      radius: ['35%', '58%'],
      center: ['50%', '45%'],
      data: [
        { value: totalInput, name: '输入 Token', itemStyle: { color: '#1890ff' } },
        { value: totalOutput, name: '输出 Token', itemStyle: { color: '#52c41a' } }
      ],
      label: { formatter: '{b}\n{d}%', fontSize: 12 }
    }]
  };
});

async function handleResetStats() {
  if (!confirm('确定要重置用量统计吗？对话记录不会被删除。')) {
    return;
  }
  statsLoading.value = true;
  statsError.value = '';
  try {
    await resetUsageStats();
    usageStats.value = null;
    await loadStats();
  } catch (error) {
    console.error('重置统计数据失败:', error);
    statsError.value = (error as Error).message || '重置失败';
  } finally {
    statsLoading.value = false;
  }
}

function onModelChange() {
  if (channelForm.modelName === 'custom') {
    isCustomModel.value = true;
    customModelName.value = '';
  } else {
    isCustomModel.value = false;
  }
}

function onCustomModelBlur() {
  if (customModelName.value) {
    channelForm.modelName = customModelName.value;
  }
}

async function handleSaveChannel() {
  if (!channelForm.channelName.trim()) {
    alert('请输入渠道名称');
    return;
  }
  if (isCreating.value && !channelForm.apiKey) {
    alert('请输入API Key');
    return;
  }

  channelLoading.value = true;
  try {
    const payload: ApiChannel = {
      id: 0,
      channelName: channelForm.channelName,
      apiKey: channelForm.apiKey,
      baseUrl: channelForm.baseUrl,
      modelName: channelForm.modelName,
      active: false,
      createdAt: '',
      updatedAt: '',
    };

    if (isCreating.value) {
      await createChannel(payload);
    } else {
      await updateChannel(selectedChannelId.value!, payload);
    }

    await loadChannels();
    emit('saved');
    alert(isCreating.value ? '渠道创建成功' : '渠道更新成功');
  } catch (error) {
    alert('保存失败: ' + (error as Error).message);
  } finally {
    channelLoading.value = false;
  }
}

async function handleActivateChannel(channel: ApiChannel) {
  try {
    await activateChannel(channel.id);
    await loadChannels();
    emit('saved');
  } catch (error) {
    alert('激活失败: ' + (error as Error).message);
  }
}

async function handleDeleteChannel(channel: ApiChannel) {
  const confirmMsg = channel.active
    ? `确定删除当前使用的渠道"${channel.channelName}"吗？删除后将自动切换到其他渠道。`
    : `确定删除渠道"${channel.channelName}"吗？`;
  if (!confirm(confirmMsg)) {
    return;
  }

  try {
    await deleteChannel(channel.id);
    await loadChannels();
    emit('saved');
  } catch (error) {
    alert('删除失败: ' + (error as Error).message);
  }
}

function formatDate(dateStr: string) {
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return dateStr;
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' });
}

async function handleChangePassword() {
  pwdError.value = '';
  pwdSuccess.value = false;

  if (!pwdForm.oldPassword) {
    pwdError.value = '请输入旧密码';
    return;
  }
  if (!pwdForm.newPassword || pwdForm.newPassword.length < 6) {
    pwdError.value = '新密码长度不能少于6位';
    return;
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    pwdError.value = '两次输入的新密码不一致';
    return;
  }

  pwdLoading.value = true;
  try {
    await authStore.changePassword(pwdForm.oldPassword, pwdForm.newPassword);
    pwdSuccess.value = true;
    pwdForm.oldPassword = '';
    pwdForm.newPassword = '';
    pwdForm.confirmPassword = '';
    setTimeout(() => { pwdSuccess.value = false; }, 5000);
  } catch (error) {
    pwdError.value = (error as Error).message || '修改失败';
    setTimeout(() => { pwdError.value = ''; }, 5000);
  } finally {
    pwdLoading.value = false;
  }
}

async function loadPreferences() {
  try {
    const data = await getPreferences();
    prefs.temperature = data.temperature ?? 0.7;
    prefs.maxTokens = data.maxTokens ?? 4096;
    prefs.contextWindow = data.contextWindow ?? 50;
    prefs.memoryEnabled = data.memoryEnabled ?? true;
    prefs.compressionEnabled = data.compressionEnabled ?? true;
  } catch (error) {
    console.error('加载偏好失败:', error);
  }
}

async function savePreferences() {
  prefsLoading.value = true;
  prefsError.value = '';
  prefsSuccess.value = false;
  try {
    await updatePreferences({
      temperature: prefs.temperature,
      maxTokens: prefs.maxTokens,
      contextWindow: prefs.contextWindow,
      memoryEnabled: prefs.memoryEnabled,
      compressionEnabled: prefs.compressionEnabled,
    });
    prefsSuccess.value = true;
    setTimeout(() => { prefsSuccess.value = false; }, 3000);
  } catch (error) {
    prefsError.value = (error as Error).message || '保存失败';
    setTimeout(() => { prefsError.value = ''; }, 5000);
  } finally {
    prefsLoading.value = false;
  }
}

function resetPreferences() {
  prefs.temperature = 0.7;
  prefs.maxTokens = 4096;
  prefs.contextWindow = 50;
  prefs.memoryEnabled = true;
  prefs.compressionEnabled = true;
}

async function handleTest() {
  if (!channelForm.apiKey) {
    testError.value = '请先输入 API Key';
    return;
  }

  testing.value = true;
  testResult.value = null;
  testError.value = '';

  try {
    const result = await testConfig({
      apiKey: channelForm.apiKey,
      baseUrl: channelForm.baseUrl,
      modelName: channelForm.modelName,
    });
    testResult.value = result;
    setTimeout(() => { testResult.value = null; }, 3000);
  } catch (error) {
    testError.value = (error as Error).message;
    setTimeout(() => { testError.value = ''; }, 5000);
  } finally {
    testing.value = false;
  }
}

function close() {
  emit('close');
}
</script>

<style scoped>
.settings-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.settings-dialog {
  background: white;
  border-radius: 8px;
  width: 900px;
  height: 580px;
  max-width: 90vw;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
  display: flex;
  flex-direction: column;
}

.settings-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #eee;
}

.settings-header h3 {
  margin: 0;
  font-size: 18px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #666;
}

.settings-content {
  display: flex;
  min-height: 400px;
}

.settings-sidebar {
  width: 150px;
  border-right: 1px solid #eee;
  padding: 12px 0;
}

.menu-item {
  padding: 10px 20px;
  cursor: pointer;
  font-size: 14px;
  color: #666;
  display: flex;
  align-items: center;
  gap: 8px;
}

.menu-item:hover {
  background: #f5f5f5;
}

.menu-item.active {
  color: #1890ff;
  background: #e6f7ff;
  border-right: 2px solid #1890ff;
}

.settings-body {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.section h4 {
  margin: 0 0 8px 0;
  font-size: 16px;
}

.section-desc {
  color: #999;
  font-size: 13px;
  margin: 0 0 20px 0;
}

/* API配置双栏布局 */
.api-section {
  display: flex;
  gap: 16px;
  height: 100%;
}

.channel-list-panel {
  width: 240px;
  min-width: 240px;
  border-right: 1px solid #eee;
  display: flex;
  flex-direction: column;
  padding-right: 16px;
}

.channel-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.channel-list-header h4 {
  margin: 0;
  font-size: 14px;
}

.add-channel-btn {
  padding: 4px 8px;
  background: #1890ff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 13px;
}

.add-channel-btn:hover {
  background: #40a9ff;
}

.channel-list {
  flex: 1;
  overflow-y: auto;
}

.channel-item {
  padding: 10px 12px;
  border: 1px solid #eee;
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.channel-item:hover {
  border-color: #1890ff;
  background: #f0f8ff;
}

.channel-item.active {
  border-color: #1890ff;
  background: #e6f7ff;
}

.channel-item-info {
  margin-bottom: 4px;
}

.channel-item-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
}

.channel-item-model {
  font-size: 12px;
  color: #999;
  margin-top: 2px;
  padding-left: 20px;
}

.channel-item-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  margin-top: 4px;
}

.active-badge {
  font-size: 11px;
  color: #52c41a;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 4px;
  padding: 1px 6px;
  margin-right: auto;
}

.icon-btn {
  padding: 4px;
  background: none;
  border: 1px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  color: #999;
  display: flex;
  align-items: center;
}

.icon-btn:hover {
  background: #f0f0f0;
  border-color: #ddd;
}

.activate-btn:hover {
  color: #52c41a;
  background: #f6ffed;
  border-color: #b7eb8f;
}

.delete-btn:hover {
  color: #ff4d4f;
  background: #fff2f0;
  border-color: #ffccc7;
}

.channel-empty {
  text-align: center;
  color: #999;
  font-size: 13px;
  padding: 20px 0;
}

.channel-form-panel {
  flex: 1;
  overflow-y: auto;
}

.channel-form-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #ccc;
  gap: 12px;
}

.channel-form-empty p {
  font-size: 14px;
  color: #999;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 8px;
  font-weight: 500;
  font-size: 14px;
}

.input-group {
  display: flex;
  gap: 8px;
}

.input-group input {
  flex: 1;
}

.toggle-btn {
  padding: 8px 12px;
  background: #f0f0f0;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}

input, select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

.model-select {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #eee;
}

button {
  padding: 8px 16px;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.test-btn {
  background: #52c41a;
  color: white;
  border-color: #52c41a;
}

.test-btn:hover {
  background: #73d13d;
}

.save-btn {
  background: #1890ff;
  color: white;
  border-color: #1890ff;
}

.reset-btn {
  background: #52c41a;
  color: white;
  border-color: #52c41a;
}

.reset-btn:hover {
  background: #73d13d;
}

.test-result {
  margin-top: 12px;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.test-result.success {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  color: #52c41a;
}

.test-result.error {
  background: #fff2f0;
  border: 1px solid #ffccc7;
  color: #ff4d4f;
}

.stats-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px 0;
  color: #999;
}

.stats-group {
  margin-bottom: 24px;
}

.stats-group h5 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #333;
  font-weight: 600;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

.stat-card {
  background: #f8f9fa;
  border: 1px solid #e8e8e8;
  border-radius: 6px;
  padding: 16px;
  text-align: center;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1890ff;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 12px;
  color: #999;
}

.free-quota-card {
  background: linear-gradient(135deg, #f6ffed 0%, #e6f7ff 100%);
  border-color: #b7eb8f;
}

.free-quota-card .stat-value {
  color: #52c41a;
}

.quota-progress {
  height: 8px;
  background: #f0f0f0;
  border-radius: 4px;
  overflow: hidden;
  margin-top: 12px;
}

.quota-bar {
  height: 100%;
  background: linear-gradient(90deg, #52c41a 0%, #faad14 70%, #ff4d4f 100%);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.about-info {
  color: #666;
  font-size: 14px;
}

.about-info p {
  margin: 8px 0;
}

.account-info-card {
  background: #f8f9fa;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 20px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #eee;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #666;
}

.info-value {
  font-size: 14px;
  color: #333;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 6px;
}

.info-value.mono {
  font-family: 'Consolas', 'Monaco', monospace;
  letter-spacing: 0.5px;
}

.copy-btn {
  padding: 2px 4px;
  background: none;
  border: none;
  cursor: pointer;
  color: #999;
  display: flex;
  align-items: center;
  border-radius: 3px;
}

.copy-btn:hover {
  color: #1890ff;
  background: #e6f7ff;
}

.account-quota-card {
  background: linear-gradient(135deg, #f6ffed 0%, #e6f7ff 100%);
  border: 1px solid #b7eb8f;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 20px;
}

.account-quota-card h5 {
  margin: 0 0 12px 0;
  font-size: 14px;
  color: #333;
  display: flex;
  align-items: center;
  gap: 6px;
}

.quota-info-row {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.quota-info-item {
  flex: 1;
  text-align: center;
}

.quota-num {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: #52c41a;
}

.quota-label {
  display: block;
  font-size: 12px;
  color: #999;
  margin-top: 2px;
}

.password-section {
  border-top: 1px solid #eee;
  padding-top: 20px;
}

.password-section h5 {
  margin: 0 0 16px 0;
  font-size: 14px;
  color: #333;
  display: flex;
  align-items: center;
  gap: 6px;
}

.pref-group {
  background: #f8f9fa;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
}

.pref-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.pref-header label {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.pref-value {
  font-size: 14px;
  font-weight: 700;
  color: #1890ff;
  font-family: 'Consolas', 'Monaco', monospace;
}

.pref-slider {
  width: 100%;
  height: 6px;
  -webkit-appearance: none;
  appearance: none;
  background: #e0e0e0;
  border-radius: 3px;
  outline: none;
  margin: 8px 0;
}

.pref-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #1890ff;
  cursor: pointer;
  border: 2px solid #fff;
  box-shadow: 0 1px 3px rgba(0,0,0,0.2);
}

.pref-range-labels {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #999;
  margin-bottom: 6px;
}

.pref-hint {
  font-size: 12px;
  color: #999;
  margin: 6px 0 0 0;
}

.pref-select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  background: white;
  cursor: pointer;
}

.pref-disabled {
  opacity: 0.5;
  pointer-events: none;
}

.switch {
  position: relative;
  display: inline-block;
  width: 44px;
  height: 24px;
  flex-shrink: 0;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.switch-slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: #ccc;
  border-radius: 24px;
  transition: 0.3s;
}

.switch-slider::before {
  content: "";
  position: absolute;
  height: 18px;
  width: 18px;
  left: 3px;
  bottom: 3px;
  background-color: white;
  border-radius: 50%;
  transition: 0.3s;
}

.switch input:checked + .switch-slider {
  background-color: #1890ff;
}

.switch input:checked + .switch-slider::before {
  transform: translateX(20px);
}

.stats-charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.stats-chart-half {
  margin-bottom: 0;
}

/* 淡入淡出动画 */
.settings-fade-enter-active {
  transition: opacity 0.2s ease;
}
.settings-fade-enter-active .settings-dialog {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.settings-fade-leave-active {
  transition: opacity 0.15s ease;
}
.settings-fade-leave-active .settings-dialog {
  transition: transform 0.15s ease, opacity 0.15s ease;
}
.settings-fade-enter-from {
  opacity: 0;
}
.settings-fade-enter-from .settings-dialog {
  opacity: 0;
  transform: scale(0.95);
}
.settings-fade-leave-to {
  opacity: 0;
}
.settings-fade-leave-to .settings-dialog {
  opacity: 0;
  transform: scale(0.95);
}
</style>
