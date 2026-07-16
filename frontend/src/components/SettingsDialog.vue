<!-- frontend/src/components/SettingsDialog.vue -->
<template>
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
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue';
import {
  getConfig, saveConfig, testConfig, TestConnectionResult, UserConfig,
  getUsageStats, resetUsageStats, UsageStats,
  getDailyUsage, DailyUsage,
  getChannels, createChannel, updateChannel, deleteChannel, activateChannel,
  ApiChannel, ApiChannelList,
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
  CheckCircle2, XCircle, Info, BarChart3, Plus, Trash2, Check, RadioTower, RotateCcw,
} from 'lucide-vue-next';

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

// 多渠道相关状态
const channels = ref<ApiChannel[]>([]);
const selectedChannelId = ref<number | null>(null);
const isCreating = ref(false);
const channelLoading = ref(false);

const menus = [
  { key: 'api', label: 'API配置', icon: Key },
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
  }
});

watch(activeMenu, async (newVal) => {
  if (newVal === 'stats') {
    await loadStats();
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

.about-info {
  color: #666;
  font-size: 14px;
}

.about-info p {
  margin: 8px 0;
}

.stats-charts-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.stats-chart-half {
  margin-bottom: 0;
}
</style>
