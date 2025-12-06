"""
网络诊断脚本 - 帮助诊断 Android 设备连接问题
"""

from flask import Flask, jsonify, request
from datetime import datetime

app = Flask(__name__)

# 记录所有连接尝试
connection_logs = []

@app.route('/api/diagnostic', methods=['GET'])
def diagnostic():
    """
    诊断接口 - 记录所有连接设备的信息
    """
    client_ip = request.remote_addr
    user_agent = request.headers.get('User-Agent', 'Unknown')
    timestamp = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    log_entry = {
        'timestamp': timestamp,
        'client_ip': client_ip,
        'user_agent': user_agent,
        'headers': dict(request.headers)
    }

    connection_logs.append(log_entry)

    print(f"\n{'='*60}")
    print(f"收到连接请求:")
    print(f"  时间: {timestamp}")
    print(f"  客户端 IP: {client_ip}")
    print(f"  User-Agent: {user_agent}")
    print(f"  所有请求头: {dict(request.headers)}")
    print(f"{'='*60}\n")

    return jsonify({
        'status': 'success',
        'message': f'诊断成功！您的设备 IP 是 {client_ip}',
        'client_ip': client_ip,
        'timestamp': timestamp
    })

@app.route('/api/logs', methods=['GET'])
def get_logs():
    """获取所有连接日志"""
    return jsonify({
        'total_connections': len(connection_logs),
        'logs': connection_logs
    })

@app.route('/', methods=['GET'])
def index():
    """主页"""
    return f"""
    <html>
    <head>
        <title>网络诊断服务器</title>
        <meta charset="utf-8">
        <style>
            body {{ font-family: Arial, sans-serif; padding: 20px; }}
            .info {{ background: #e3f2fd; padding: 15px; margin: 10px 0; border-radius: 5px; }}
            .log {{ background: #f5f5f5; padding: 10px; margin: 5px 0; border-left: 3px solid #2196F3; }}
        </style>
    </head>
    <body>
        <h1>网络诊断服务器</h1>
        <div class="info">
            <h3>使用说明：</h3>
            <p>在 Android 手机浏览器中访问：<code>http://192.168.2.56:5002/api/diagnostic</code></p>
            <p>然后刷新此页面查看连接日志</p>
        </div>

        <h2>连接日志 (共 {len(connection_logs)} 条)</h2>
        {''.join([f'<div class="log"><strong>{log["timestamp"]}</strong> - IP: {log["client_ip"]} - {log["user_agent"]}</div>' for log in connection_logs])}

        <p><a href="/">刷新页面</a> | <a href="/api/logs">查看 JSON 日志</a></p>
    </body>
    </html>
    """

if __name__ == '__main__':
    print("="*60)
    print("网络诊断服务器启动")
    print("="*60)
    print(f"服务器地址: http://0.0.0.0:5002")
    print(f"PC 访问: http://192.168.2.56:5002")
    print(f"Android 测试 URL: http://192.168.2.56:5002/api/diagnostic")
    print("="*60)
    print("\n请在无法连接的 Android 手机浏览器中访问上述 URL")
    print("如果能访问诊断接口，说明网络是通的，问题在应用层")
    print("如果无法访问，说明是网络层问题（路由器隔离等）\n")

    app.run(host='0.0.0.0', port=5002, debug=False, threaded=True)
