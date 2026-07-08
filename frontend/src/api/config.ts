// frontend/src/api/config.ts

export interface UserConfig {
  apiKey: string;
  baseUrl: string;
  modelName: string;
}

export interface ApiChannel {
  id: number;
  channelName: string;
  apiKey: string;
  baseUrl: string;
  modelName: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ApiChannelList {
  channels: ApiChannel[];
  activeChannelId: number | null;
}

export async function getConfig(): Promise<UserConfig | null> {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('用户未登录');
  }

  const response = await fetch('/api/user/config', {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error('获取配置失败');
  }

  const result = await response.json();
  if (result.code === 1) {
    return result.data;
  }
  return null;
}

export async function saveConfig(config: UserConfig): Promise<void> {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('用户未登录');
  }

  const response = await fetch('/api/user/config', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(config),
  });

  if (!response.ok) {
    throw new Error('保存配置失败');
  }

  const result = await response.json();
  if (result.code !== 1) {
    throw new Error(result.msg || '保存配置失败');
  }
}

export async function deleteConfig(): Promise<void> {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('用户未登录');
  }

  const response = await fetch('/api/user/config', {
    method: 'DELETE',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error('删除配置失败');
  }
}

export interface TestConnectionResult {
  success: boolean;
  latencyMs?: number;
  model?: string;
}

export async function testConfig(config: UserConfig): Promise<TestConnectionResult> {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('用户未登录');
  }

  const response = await fetch('/api/user/config/test', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(config),
  });

  if (!response.ok) {
    throw new Error('测试连接请求失败');
  }

  const result = await response.json();
  if (result.code === 1) {
    return result.data;
  }
  throw new Error(result.msg || '测试连接失败');
}

export interface UsageStats {
  totalRequests: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalToolCalls: number;
  totalConversations: number;
  totalMessages: number;
}

export async function getUsageStats(): Promise<UsageStats> {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('用户未登录');
  }

  const response = await fetch('/api/usage/stats', {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error('获取统计数据失败');
  }

  const result = await response.json();
  if (result.code === 1) {
    return result.data;
  }
  throw new Error(result.msg || '获取统计数据失败');
}

// ==================== 多渠道管理 ====================

export async function getChannels(): Promise<ApiChannelList> {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('用户未登录');
  }

  const response = await fetch('/api/user/config/channels', {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error('获取渠道列表失败');
  }

  const result = await response.json();
  if (result.code === 1) {
    return result.data;
  }
  throw new Error(result.msg || '获取渠道列表失败');
}

export async function createChannel(channel: ApiChannel): Promise<ApiChannel> {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('用户未登录');
  }

  const response = await fetch('/api/user/config/channels', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(channel),
  });

  if (!response.ok) {
    throw new Error('创建渠道失败');
  }

  const result = await response.json();
  if (result.code === 1) {
    return result.data;
  }
  throw new Error(result.msg || '创建渠道失败');
}

export async function updateChannel(id: number, channel: ApiChannel): Promise<ApiChannel> {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('用户未登录');
  }

  const response = await fetch(`/api/user/config/channels/${id}`, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(channel),
  });

  if (!response.ok) {
    throw new Error('更新渠道失败');
  }

  const result = await response.json();
  if (result.code === 1) {
    return result.data;
  }
  throw new Error(result.msg || '更新渠道失败');
}

export async function deleteChannel(id: number): Promise<void> {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('用户未登录');
  }

  const response = await fetch(`/api/user/config/channels/${id}`, {
    method: 'DELETE',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error('删除渠道失败');
  }

  const result = await response.json();
  if (result.code !== 1) {
    throw new Error(result.msg || '删除渠道失败');
  }
}

export async function activateChannel(id: number): Promise<void> {
  const token = localStorage.getItem('token');
  if (!token) {
    throw new Error('用户未登录');
  }

  const response = await fetch(`/api/user/config/channels/${id}/activate`, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    throw new Error('激活渠道失败');
  }

  const result = await response.json();
  if (result.code !== 1) {
    throw new Error(result.msg || '激活渠道失败');
  }
}
