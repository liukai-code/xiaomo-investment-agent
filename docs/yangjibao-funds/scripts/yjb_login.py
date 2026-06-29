#!/usr/bin/env python3
"""
养基宝 微信扫码登录
生成QR码 → 微信扫码 → 获取Token → 保存到 ~/.yjb_token.json

用法: python yjb_login.py
"""
import requests, hashlib, time, json, os, sys

SECRET = 'YxmKSrQR4uoJ5lOoWIhcbd7SlUEh9OOc'
BASE = 'http://browser-plug-api.yangjibao.com'
TOKEN_FILE = os.path.expanduser('~/.yjb_token.json')


def make_headers(path, token=''):
    sign_path = path.split('?')[0]
    t = int(time.time())
    s = hashlib.md5(('' + sign_path + (token or '') + str(t) + SECRET).encode()).hexdigest()
    return {'Request-Time': str(t), 'Request-Sign': s, 'Authorization': token}


# Step 1: 获取QR码
print('\n正在获取登录二维码...')
r = requests.get(f'{BASE}/qr_code', headers=make_headers('/qr_code'), timeout=10)
data = r.json()

if data['code'] != 200:
    print(f'获取QR码失败: {data}')
    sys.exit(1)

qr_id = data['data']['id']
qr_url = data['data']['url']

# Step 2: 展示QR码
print(f'\n在线二维码: https://api.qrserver.com/v1/create-qr-code/?size=300x300&data={qr_url}')
print(f'直接链接: {qr_url}\n')

try:
    import qrcode
    qr = qrcode.QRCode(border=2)
    qr.add_data(qr_url)
    qr.make(fit=True)
    qr.print_ascii(invert=True)
    print()
except ImportError:
    print('提示: pip install qrcode 可显示终端内二维码\n')

# Step 3: 轮询扫码状态
print('等待微信扫码... (3分钟有效)\n')

for i in range(60):
    time.sleep(3)
    try:
        r = requests.get(f'{BASE}/qr_code_state/{qr_id}',
                         headers=make_headers(f'/qr_code_state/{qr_id}'), timeout=10)
        sd = r.json()
        state = sd.get('data', {}).get('state', '0')
        token = sd.get('data', {}).get('token', '')

        if str(state) == '2' and token:
            nickname = sd.get('data', {}).get('nickname', '')
            avatar = sd.get('data', {}).get('avatar', '')

            with open(TOKEN_FILE, 'w') as f:
                json.dump({'token': token, 'timestamp': int(time.time())}, f)

            print('=' * 50)
            print(' 登录成功!')
            print(f' 昵称: {nickname}')
            print(f' Token 已保存到: {TOKEN_FILE}')
            print('=' * 50)
            print('\n现在可以运行 yjb_fetch.py 获取持仓数据')
            sys.exit(0)

        dots = '.' * ((i % 3) + 1)
        print(f'  [{i+1}/60] 等待扫码{dots}   ', end='\r')
    except Exception as e:
        pass

print('\n登录超时，请重新运行')
