<!-- frontend/src/components/SettingsDialog.vue -->
<template>
  <div class="settings-overlay" v-if="visible" @click.self="close">
    <div class="settings-dialog">
      <div class="settings-header">
        <h3>API配置</h3>
        <button class="close-btn" @click="close">×</button>
      </div>

      <div class="settings-body">
        <div class="form-group">
          <label>API Key</label>
          <div class="input-group">
            <input
              :type="showApiKey ? 'text' : 'password'"
              v-model="form.apiKey"
              placeholder="输入API Key"
            />
            <button class="toggle-btn" @click="showApiKey = !showApiKey">
              {{ showApiKey ? '隐藏' : '显示' }}
            </button>
          </div>
        </div>

        <div class="form-group">
          <label>Base URL</label>
          <input
            v-model="form.baseUrl"
            placeholder="https://api.example.com"
          />
        </div>

        <div class="form-group">
          <label>模型选择</label>
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
      </div>

      <div class="settings-footer">
        <button class="delete-btn" @click="handleDelete" :disabled="loading">
          删除配置
        </button>
        <div class="footer-right">
          <button class="cancel-btn" @click="close">取消</button>
          <button class="save-btn" @click="handleSave" :disabled="loading">
            {{ loading ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue';
import { getConfig, saveConfig, deleteConfig, UserConfig } from '../api/config';

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'saved'): void;
}>();

const loading = ref(false);
const showApiKey = ref(false);
const isCustomModel = ref(false);
const customModelName = ref('');

const presetModels = [
  'mimo-v2.5-pro',
  'claude-3-opus',
  'claude-3-sonnet',
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

async function loadConfig() {
  try {
    const config = await getConfig();
    if (config) {
      form.apiKey = config.apiKey || '';
      form.baseUrl = config.baseUrl || '';
      form.modelName = config.modelName || '';

      // 检查是否为自定义模型
      if (config.modelName && !presetModels.includes(config.modelName)) {
        isCustomModel.value = true;
        customModelName.value = config.modelName;
      }
    }
  } catch (error) {
    console.error('加载配置失败:', error);
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

async function handleDelete() {
  if (!confirm('确定要删除配置吗？')) {
    return;
  }

  loading.value = true;
  try {
    await deleteConfig();
    form.apiKey = '';
    form.baseUrl = '';
    form.modelName = '';
    alert('配置已删除');
    emit('saved');
    close();
  } catch (error) {
    alert('删除失败: ' + (error as Error).message);
  } finally {
    loading.value = false;
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
  width: 500px;
  max-width: 90vw;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
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
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #666;
}

.settings-body {
  padding: 20px;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
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
}

input, select {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

.model-select {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.settings-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-top: 1px solid #eee;
}

.footer-right {
  display: flex;
  gap: 8px;
}

button {
  padding: 10px 20px;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.delete-btn {
  background: #ff4d4f;
  color: white;
  border-color: #ff4d4f;
}

.cancel-btn {
  background: #f0f0f0;
}

.save-btn {
  background: #1890ff;
  color: white;
  border-color: #1890ff;
}
</style>
