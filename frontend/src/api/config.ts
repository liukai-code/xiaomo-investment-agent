// frontend/src/api/config.ts

export interface UserConfig {
  apiKey: string;
  baseUrl: string;
  modelName: string;
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
