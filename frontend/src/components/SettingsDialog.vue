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
          <div v-if="activeMenu === 'api'" class="section">
            <h4>API配置</h4>
            <p class="section-desc">配置大模型API连接信息</p>

            <div class="form-group">
              <label><Key :size="14" /> API Key</label>
              <div class="input-group">
                <input
                  :type="showApiKey ? 'text' : 'password'"
                  v-model="form.apiKey"
                  placeholder="输入API Key"
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
                v-model="form.baseUrl"
                placeholder="https://api.example.com"
              />
            </div>

            <div class="form-group">
              <label><Cpu :size="14" /> 模型选择</label>
              <div class="model-select">
                <select v-model="form.modelName" @change="onModelChange">
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
              <button class="test-btn" @click="handleTest" :disabled="testing || loading">
                <Loader2 v-if="testing" :size="14" class="spin" />
                <Plug v-else :size="14" />
                {{ testing ? '测试中...' : '测试连接' }}
              </button>
              <button class="save-btn" @click="handleSave" :disabled="loading || testing">
                <Loader2 v-if="loading" :size="14" class="spin" />
                <Save v-else :size="14" />
                {{ loading ? '保存中...' : '保存' }}
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

              <div class="section-actions">
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
import { ref, reactive, watch } from 'vue';
import { getConfig, saveConfig, testConfig, TestConnectionResult, UserConfig, getUsageStats, UsageStats } from '../api/config';
import { Settings, X, Key, Globe, Cpu, Eye, EyeOff, Plug, Save, Loader2, CheckCircle2, XCircle, Info, BarChart3 } from 'lucide-vue-next';

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
const statsLoading = ref(false);
const statsError = ref('');

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

const form = reactive<UserConfig>({
  apiKey: '',
  baseUrl: '',
  modelName: '',
});

watch(() => props.visible, async (newVal) => {
  if (newVal) {
    await loadConfig();
  }
});

watch(activeMenu, async (newVal) => {
  if (newVal === 'stats') {
    await loadStats();
  }
});

async function loadConfig() {
  try {
    const config = await getConfig();
    if (config) {
      form.apiKey = config.apiKey || '';
      form.baseUrl = config.baseUrl || '';
      form.modelName = config.modelName || '';

      if (config.modelName && !presetModels.includes(config.modelName)) {
        isCustomModel.value = true;
        customModelName.value = config.modelName;
      }
    }
  } catch (error) {
    console.error('加载配置失败:', error);
  }
}

async function loadStats() {
  statsLoading.value = true;
  statsError.value = '';
  try {
    usageStats.value = await getUsageStats();
  } catch (error) {
    console.error('加载统计数据失败:', error);
    statsError.value = (error as Error).message || '加载失败';
  } finally {
    statsLoading.value = false;
  }
}

function onModelChange() {
  if (form.modelName === 'custom') {
    isCustomModel.value = true;
    customModelName.value = '';
  } else {
    isCustomModel.value = false;
  }
}

function onCustomModelBlur() {
  if (customModelName.value) {
    form.modelName = customModelName.value;
  }
}

async function handleSave() {
  if (!form.apiKey) {
    alert('请输入API Key');
    return;
  }

  loading.value = true;
  try {
    await saveConfig({
      apiKey: form.apiKey,
      baseUrl: form.baseUrl,
      modelName: form.modelName,
    });
    alert('配置保存成功');
    emit('saved');
    close();
  } catch (error) {
    alert('保存失败: ' + (error as Error).message);
  } finally {
    loading.value = false;
  }
}

async function handleTest() {
  if (!form.apiKey) {
    testError.value = '请先输入 API Key';
    return;
  }

  testing.value = true;
  testResult.value = null;
  testError.value = '';

  try {
    const result = await testConfig({
      apiKey: form.apiKey,
      baseUrl: form.baseUrl,
      modelName: form.modelName,
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
  width: 850px;
  height: 550px;
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
</style>
