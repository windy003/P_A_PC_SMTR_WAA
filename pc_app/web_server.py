"""
Screenshot Status Web Server
提供截图状态查询的 Web API 服务
"""

from flask import Flask, jsonify, send_file
from flask_cors import CORS
from pathlib import Path
import os
from datetime import datetime
import re

app = Flask(__name__)
CORS(app)  # 允许跨域访问

# 配置路径
screenshots_path = Path("~/Pictures/Screenshots").expanduser()


def check_has_folders():
    """
    检查 Screenshots 目录中是否存在由 screenshot_organizer.py 创建的文件夹
    返回: (has_folders, total_count)
        has_folders: True (有文件夹) 或 False (无文件夹)
        total_count: 所有文件夹中的文件总数
    """
    try:
        if not screenshots_path.exists():
            return False, 0

        # 查找符合时间格式的文件夹（如 "05-06", "14-15" 等）或 "已到期" 文件夹
        import re
        time_folder_pattern = re.compile(r'^\d{2}-\d{2}$')  # 匹配 "HH-HH" 格式

        has_folders = False
        total_count = 0

        for item in screenshots_path.iterdir():
            # 检查是否是文件夹
            if item.is_dir():
                # 检查文件夹名是否符合时间格式或是 "已到期" 文件夹
                if time_folder_pattern.match(item.name) or item.name == "已到期":
                    print(f"发现文件夹: {item.name}")
                    has_folders = True

                    # 统计该文件夹中的文件数量
                    folder_file_count = sum(1 for f in item.iterdir() if f.is_file())
                    total_count += folder_file_count
                    print(f"  文件夹 {item.name} 包含 {folder_file_count} 个文件")

        print(f"总共发现 {total_count} 个文件")
        return has_folders, total_count

    except Exception as e:
        print(f"检查状态时出错: {e}")
        return False, 0


@app.route('/api/status', methods=['GET'])
def get_status():
    """
    获取截图状态 API
    返回: {"status": "has"/"none", "totalCount": 数量}
    """
    print("=== 收到 /api/status 请求 ===")
    has_files, total_count = check_has_folders()
    print(f"检测结果: has_files = {has_files}, total_count = {total_count}")
    status = "has" if has_files else "none"

    response = {
        "status": status,
        "timestamp": datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
        "message": f"有已整理的截图，共 {total_count} 个文件" if has_files else "无已整理的截图",
        "totalCount": total_count
    }

    print(f"返回响应: {response}")
    return jsonify(response)


@app.route('/api/health', methods=['GET'])
def health_check():
    """健康检查接口"""
    return jsonify({
        "status": "ok",
        "server": "Screenshot Status Server",
        "timestamp": datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    })


@app.route('/favicon.ico')
def favicon():
    """网站图标"""
    icon_path = Path(__file__).parent / '16-9.png'
    return send_file(icon_path, mimetype='image/png')


@app.route('/', methods=['GET'])
def index():
    """主页"""
    return """
    <html>
    <head>
        <title>Screenshot Status Server</title>
        <meta charset="utf-8">
        <link rel="icon" type="image/png" href="/favicon.ico">
    </head>
    <body style="font-family: Arial, sans-serif; padding: 20px;">
        <h1>截图状态服务器</h1>
        <p>API 端点：</p>
        <ul>
            <li><a href="/api/status">/api/status</a> - 获取截图状态</li>
            <li><a href="/api/health">/api/health</a> - 健康检查</li>
        </ul>
    </body>
    </html>
    """


if __name__ == '__main__':
    print("启动截图状态 Web 服务器...")
    print(f"监控目录: {screenshots_path}")
    print(f"服务器运行在: http://0.0.0.0:5001")
    print("API 端点: http://0.0.0.0:5001/api/status")

    # 运行服务器（监听所有网络接口，以便局域网访问）
    # threaded=True: 启用多线程，支持多个客户端同时连接
    app.run(host='0.0.0.0', port=5001, debug=False, threaded=True)
