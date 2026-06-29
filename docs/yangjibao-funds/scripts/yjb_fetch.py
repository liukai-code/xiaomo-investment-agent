#!/usr/bin/env python3
"""
养基宝 持仓数据抓取 + 分析汇总
使用已保存的 Token 调用 browser-plug-api 获取个人持仓、账户汇总、指数行情。

用法:
  python yjb_fetch.py          # 获取全部数据
  python yjb_fetch.py --json   # 另存为 JSON 文件
  python yjb_fetch.py --csv    # 导出持仓为 CSV
"""
import requests, hashlib, time, json, os, sys, csv, argparse
from datetime import datetime

SECRET = 'YxmKSrQR4uoJ5lOoWIhcbd7SlUEh9OOc'
BASE = 'http://browser-plug-api.yangjibao.com'
TOKEN_FILE = os.path.expanduser('~/.yjb_token.json')
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))


def load_token():
    if not os.path.exists(TOKEN_FILE):
        print('未找到 Token，请先运行 yjb_login.py 扫码登录')
        sys.exit(1)
    with open(TOKEN_FILE) as f:
        return json.load(f).get('token')


def make_headers(path, token):
    sign_path = path.split('?')[0]
    t = int(time.time())
    s = hashlib.md5(('' + sign_path + token + str(t) + SECRET).encode()).hexdigest()
    return {'Request-Time': str(t), 'Request-Sign': s, 'Authorization': token}


def api_get(path, token):
    r = requests.get(BASE + path, headers=make_headers(path, token), timeout=15)
    if r.status_code != 200:
        raise Exception(f'HTTP {r.status_code}')
    d = r.json()
    if d.get('code') != 200:
        raise Exception(d.get('message', 'API error'))
    return d.get('data', {})


def arrow(val):
    v = float(val) if val else 0
    if v > 0: return ''
    if v < 0: return ''
    return ''


def main():
    parser = argparse.ArgumentParser(description='养基宝持仓数据抓取')
    parser.add_argument('--json', action='store_true', help='导出 JSON')
    parser.add_argument('--csv', action='store_true', help='导出 CSV')
    args = parser.parse_args()

    token = load_token()
    sep = '=' * 60

    print(sep)
    print('  养基宝 个人持仓分析')
    print(f'  时间: {datetime.now().strftime("%Y-%m-%d %H:%M")}')
    print(sep)

    all_data = {}

    # === 1. 账户汇总 ===
    try:
        collect = api_get('/account_collect', token)
        acc_list = collect.get('account_data', [])
        all_data['account_collect'] = collect
        print('\n 账户汇总')
        for a in acc_list:
            ttl = a.get('title', '?')
            cost = float(a.get('hold_cost', 0))
            income = float(a.get('today_income', 0))
            rate = float(a.get('today_income_rate', 0))
            arow = arrow(income)
            print(f'  {ttl}: 持仓成本 {cost:.2f}  {arow}今日 {income:+.2f} ({rate}%)')
    except Exception as e:
        print(f'  [跳过] 账户汇总: {e}')

    # === 2. 指数行情 ===
    try:
        print('\n 主要指数')
        idx = api_get('/index_data', token)
        items = idx if isinstance(idx, list) else idx.get('list', [])
        all_data['index_data'] = items
        for item in items[:12]:
            name = item.get('name') or item.get('show_name', '?')
            price = item.get('v') or item.get('price', '-')
            try:
                change = float(item.get('dir') or item.get('change', 0))
            except:
                change = 0
            arow = arrow(change)
            print(f'  {arow} {name}: {price}  {change:+.2f}%')
    except Exception as e:
        print(f'  [跳过] 指数行情: {e}')

    # === 3. 持仓明细 ===
    try:
        print('\n 持仓明细')
        accounts = api_get('/user_account', token).get('list', [])
        all_data['accounts'] = accounts

        grand_total = 0
        grand_earn = 0
        all_holdings = []

        for acc in accounts:
            aid = acc['id']
            atitle = acc['title']

            data = api_get(f'/fund_hold?account_id={aid}', token)
            items = data if isinstance(data, list) else data.get('list', [])
            all_holdings.append({'_account': atitle, 'items': items})

            if not items:
                print(f'\n  [{atitle}] (空)')
                continue

            acc_money = 0
            acc_earn = 0
            print(f'\n  [{atitle}] ({len(items)})')

            items_sorted = sorted(items, key=lambda x: float(x.get('money') or x.get('market_value') or 0), reverse=True)

            for item in items_sorted:
                name = item.get('name') or item.get('short_name') or item.get('fund_name') or '?'
                code = item.get('code', '?')
                money = float(item.get('money') or item.get('market_value') or 0)
                earn = float(item.get('earn') or item.get('hold_earn') or item.get('total_earn') or 0)
                share = item.get('share') or item.get('hold_share') or '-'
                cost = item.get('cost') or item.get('hold_cost') or '-'
                earn_pct = (earn / (money - earn) * 100) if (money - earn) > 0 else 0
                acc_money += money
                acc_earn += earn
                arow = arrow(earn)
                print(f'    {arow} {name}({code})')
                print(f'        {money:.2f}  {earn:+.2f} ({earn_pct:+.1f}%)  份额:{share}  成本:{cost}')

            print(f'    {"-" * 40}')
            print(f'    小计 {acc_money:.2f}  盈亏 {acc_earn:+.2f}')
            grand_total += acc_money
            grand_earn += acc_earn

        all_data['all_holdings'] = all_holdings

        print(f'\n{sep}')
        print(f'  总持仓 {grand_total:.2f}  总盈亏 {grand_earn:+.2f}   ({grand_earn/(grand_total-grand_earn)*100:+.1f}%)' if (grand_total - grand_earn) > 0 else f'  总持仓 {grand_total:.2f}')
        print(sep)

    except Exception as e:
        print(f'\n持仓获取失败: {e}')
        import traceback; traceback.print_exc()

    # === 导出 ===
    if args.json:
        ts = datetime.now().strftime('%Y%m%d_%H%M%S')
        path = os.path.join(SCRIPT_DIR, f'yjb_data_{ts}.json')
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(all_data, f, ensure_ascii=False, indent=2, default=str)
        print(f'\nJSON 已保存: {path}')

    if args.csv:
        ts = datetime.now().strftime('%Y%m%d_%H%M%S')
        path = os.path.join(SCRIPT_DIR, f'yjb_holdings_{ts}.csv')
        with open(path, 'w', encoding='utf-8-sig', newline='') as f:
            w = csv.writer(f)
            w.writerow(['账户', '基金名称', '代码', '市值', '盈亏', '盈亏率%', '份额', '成本单价'])
            for acc_h in all_holdings:
                at = acc_h['_account']
                for item in acc_h['items']:
                    money = float(item.get('money') or item.get('market_value') or 0)
                    earn = float(item.get('earn') or item.get('hold_earn') or 0)
                    pct = (earn / (money - earn) * 100) if (money - earn) > 0 else 0
                    w.writerow([
                        at,
                        item.get('name') or item.get('short_name') or '',
                        item.get('code', ''),
                        money, earn, f'{pct:.1f}',
                        item.get('share') or item.get('hold_share') or '',
                        item.get('cost') or item.get('hold_cost') or '',
                    ])
        print(f'CSV 已保存: {path}')


if __name__ == '__main__':
    main()
