"""
测试服务器 - 运行在端口 8080
用于测试 LG Wing 能否访问不同端口
"""

from flask import Flask

app = Flask(__name__)

@app.route('/')
def index():
    return """
    <html>
    <head><title>测试服务器 8080</title></head>
    <body>
        <h1>成功！</h1>
        <p>如果你能看到这个页面，说明可以访问端口 8080</p>
    </body>
    </html>
    """

if __name__ == '__main__':
    print("="*60)
    print("测试服务器启动在端口 8080")
    print(f"请在 LG Wing 浏览器访问: http://192.168.2.56:8080")
    print("="*60)
    app.run(host='0.0.0.0', port=8080, debug=False)
