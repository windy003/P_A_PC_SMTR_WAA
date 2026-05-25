"""
Screenshots Auto Organizer
自动整理截图的系统托盘程序
"""

import sys
import os
import json
import threading
import urllib.request
import webbrowser
from pathlib import Path
from datetime import datetime, timedelta
from PyQt5.QtWidgets import (
    QApplication, QSystemTrayIcon, QMenu, QAction, QMessageBox,
    QDialog, QFormLayout, QLineEdit, QDialogButtonBox
)
from PyQt5.QtCore import QTimer, Qt, QObject, pyqtSignal
from PyQt5.QtGui import QIcon, QPixmap, QPainter, QFont, QColor
import shutil
import re
from dotenv import load_dotenv

# 加载 .env 文件
load_dotenv(Path(__file__).parent / '.env')


def create_count_icon(count, bg_color, text=None):
    """创建带有数字（或自定义文字）的托盘图标

    count: 显示的数字
    bg_color: QColor 背景颜色
    text: 若提供，则直接显示该文字（覆盖 count）
    """
    pixmap = QPixmap(64, 64)
    pixmap.fill(bg_color)

    painter = QPainter(pixmap)
    painter.setRenderHint(QPainter.Antialiasing)

    font = QFont("Arial", 32, QFont.Bold)
    painter.setFont(font)

    if text is None:
        text = str(count) if count < 1000 else "999+"

    metrics = painter.fontMetrics()
    text_width = metrics.horizontalAdvance(text)
    text_height = metrics.height()

    text_x = (pixmap.width() - text_width) // 2
    text_y = (pixmap.height() + text_height) // 2 - metrics.descent()

    painter.setPen(QColor(255, 255, 255))
    painter.drawText(text_x, text_y, text)

    painter.end()
    return QIcon(pixmap)


class ScreenshotOrganizer(QSystemTrayIcon):
    # 用户点击"添加新的服务器"时发出
    add_server_requested = pyqtSignal()

    def __init__(self, parent=None):
        super().__init__(parent)

        # 从环境变量获取截图目录路径
        screenshots_env = os.getenv('SCREENSHOTS_PATH', '~/OneDrive/图片/Screenshots')
        self.screenshots_path = Path(screenshots_env).expanduser()

        # 项目目录（程序所在目录）
        self.project_dir = Path(__file__).parent

        # 初始化托盘图标
        self.setup_tray()

        # 设置定时器，每1分钟检查一次
        self.timer = QTimer()
        self.timer.timeout.connect(self.check_time_and_run)
        self.timer.start(60000)  # 60000 毫秒 = 1 分钟

        # 程序启动时显示提示
        self.showMessage(
            "截图整理工具已启动",
            "将每1分钟自动整理截图",
            QSystemTrayIcon.Information,
            2000
        )

        # 启动时仅自动检测文件夹并刷新图标
        print("\n[启动检查] 自动检测文件夹状态并刷新图标...")
        self.force_update_icon_status()

    def setup_tray(self):
        """设置系统托盘"""
        # 创建托盘菜单
        menu = QMenu()

        # 添加菜单项
        organize_action = QAction("立即整理旧截图 (&Z)", menu)
        organize_action.triggered.connect(self.manual_execute)
        menu.addAction(organize_action)

        check_status_action = QAction("仅刷新图标状态 (&S)", menu)
        check_status_action.triggered.connect(self.force_update_icon_status)
        menu.addAction(check_status_action)

        menu.addSeparator()

        open_folder_action = QAction("打开Screenshots文件夹", menu)
        open_folder_action.triggered.connect(self.open_screenshots_folder)
        menu.addAction(open_folder_action)

        menu.addSeparator()

        add_server_action = QAction("添加新的服务器 (&A)", menu)
        add_server_action.triggered.connect(self.add_server_requested.emit)
        menu.addAction(add_server_action)

        menu.addSeparator()

        about_action = QAction("关于", menu)
        about_action.triggered.connect(self.show_about)
        menu.addAction(about_action)

        quit_action = QAction("退出 (&X)", menu)
        quit_action.triggered.connect(self.quit_app)
        menu.addAction(quit_action)

        self.setContextMenu(menu)

        # 设置初始图标
        self.update_icon(False)

        # 设置工具提示
        self.setToolTip("Screenshots 自动整理工具\n每1分钟自动执行")

        # 显示托盘图标
        self.show()

    def update_icon(self, has_new_folder):
        """更新托盘图标，动态显示条目数"""
        # 统计总条目数
        total_count = self.count_total_items()

        # 创建带数字的图标
        icon_with_count = self.create_icon_with_count(has_new_folder, total_count)
        self.setIcon(icon_with_count)

        # 更新工具提示
        if total_count > 0:
            self.setToolTip(f"Screenshots 自动整理工具\nPC有 {total_count} 个文件\n每1分钟自动执行")
        else:
            self.setToolTip("Screenshots 自动整理工具\n每1分钟自动执行")

    def count_total_items(self):
        """统计所有时间文件夹和"已到期"文件夹中的文件总数"""
        try:
            if not self.screenshots_path.exists():
                return 0

            time_folder_pattern = re.compile(r'^\d{2}-\d{2}$')
            total_count = 0

            for item in self.screenshots_path.iterdir():
                if item.is_dir():
                    # 统计旧的时间格式文件夹或新的"已到期"文件夹
                    if time_folder_pattern.match(item.name) or item.name == "已到期":
                        folder_file_count = sum(1 for f in item.iterdir() if f.is_file())
                        total_count += folder_file_count

            return total_count
        except Exception as e:
            print(f"统计文件数时出错: {e}")
            return 0

    def create_icon_with_count(self, has_new_folder, count):
        """创建带有数字的图标（本地截图：红色背景）"""
        return create_count_icon(count, QColor(255, 0, 0))

    def check_time_and_run(self):
        """每1分钟执行一次检查"""
        now = datetime.now()
        print(f"\n[定时执行] 当前时间: {now.strftime('%Y-%m-%d %H:%M:%S')}")
        self.check_and_organize()

    def manual_execute(self):
        """手动执行一次"""
        print("\n[手动执行] 用户手动触发检查")
        self.showMessage(
            "手动执行",
            "正在检查并整理截图...",
            QSystemTrayIcon.Information,
            1000
        )
        self.check_and_organize()

    def force_update_icon_status(self):
        """仅检查文件夹状态并更新图标"""
        print("\n[手动状态检测] 用户手动触发图标状态更新")
        self.showMessage(
            "状态检测",
            "正在检查文件夹状态...",
            QSystemTrayIcon.Information,
            1000
        )
        final_status = self._check_for_existing_time_folders()
        self.update_icon(final_status)
        print(f"图标状态已更新为: {'有' if final_status else '无'}")

    def check_and_organize(self):
        """检查并整理图片"""
        try:
            # 检查 Screenshots 目录是否存在
            if not self.screenshots_path.exists():
                print(f"Screenshots 目录不存在: {self.screenshots_path}")
                self.update_icon(False)
                return

            # 计算3天前的时间点
            now = datetime.now()
            three_days_ago = now - timedelta(days=3)

            print(f"\n当前时间: {now.strftime('%Y-%m-%d %H:%M:%S')}")
            print(f"检查3天前的图片 (早于: {three_days_ago.strftime('%Y-%m-%d %H:%M:%S')})")

            # 查找符合条件的图片，统一放到"已到期"文件夹
            # 使用列表存储: [(文件路径, 创建时间), ...]
            expired_files = []
            image_extensions = {'.png', '.jpg', '.jpeg', '.gif', '.bmp', '.webp'}
            folder_name = "已到期"

            for file_path in self.screenshots_path.iterdir():
                # 只处理文件，不处理文件夹
                if not file_path.is_file():
                    continue

                # 检查是否是图片文件
                if file_path.suffix.lower() not in image_extensions:
                    continue

                # 获取文件的创建时间（Windows 下是创建时间）
                creation_time = datetime.fromtimestamp(file_path.stat().st_ctime)

                # 检查是否是3天前或更早的文件
                if creation_time < three_days_ago:
                    # 将所有到期文件添加到列表中
                    expired_files.append((file_path, creation_time))
                    print(f"找到匹配文件: {file_path.name} (创建时间: {creation_time.strftime('%Y-%m-%d %H:%M:%S')}) -> {folder_name}")

            # 如果有符合条件的文件，创建文件夹并移动
            has_new_folder = False
            total_moved = 0

            if expired_files:
                print(f"\n开始整理，共找到 {len(expired_files)} 个到期文件")

                # 创建"已到期"文件夹
                target_folder = self.screenshots_path / folder_name
                target_folder.mkdir(exist_ok=True)
                print(f"\n创建/使用文件夹: {folder_name}")

                # 移动所有到期文件
                for file_path, creation_time in expired_files:
                    try:
                        dest_path = target_folder / file_path.name
                        shutil.move(str(file_path), str(dest_path))
                        print(f"  移动文件: {file_path.name}")
                        total_moved += 1
                    except Exception as e:
                        print(f"  移动文件失败 {file_path.name}: {e}")

                has_new_folder = True

                # 显示通知
                self.showMessage(
                    "截图已整理",
                    f"已将 {total_moved} 个文件移动到'{folder_name}'文件夹",
                    QSystemTrayIcon.Information,
                    3000
                )

                print(f"\n整理完成！共移动 {total_moved} 个文件到'{folder_name}'文件夹")
            else:
                print("未找到符合条件的文件")

            # 更新图标：改为检查是否存在任何时间文件夹来决定最终图标状态
            print("\n正在根据文件夹存在情况更新最终图标状态...")
            final_status = self._check_for_existing_time_folders()
            print(f"图标状态检查结果: {'有' if final_status else '无'}")
            self.update_icon(final_status)

        except Exception as e:
            print(f"检查过程出错: {e}")
            import traceback
            traceback.print_exc()
            self.update_icon(False)

    def _check_for_existing_time_folders(self):
        """检查是否存在任何时间格式的文件夹或"已到期"文件夹"""
        try:
            if not self.screenshots_path.exists():
                return False
            time_folder_pattern = re.compile(r'^\d{2}-\d{2}$')
            for item in self.screenshots_path.iterdir():
                if item.is_dir():
                    # 检查是否是旧的时间格式文件夹或新的"已到期"文件夹
                    if time_folder_pattern.match(item.name) or item.name == "已到期":
                        return True  # 找到一个就够了
            return False
        except Exception as e:
            print(f"检查时间文件夹时出错: {e}")
            return False

    def open_screenshots_folder(self):
        """打开 Screenshots 文件夹"""
        if self.screenshots_path.exists():
            os.startfile(str(self.screenshots_path))
        else:
            QMessageBox.warning(
                None,
                "文件夹不存在",
                f"Screenshots 文件夹不存在:\n{self.screenshots_path}"
            )

    def show_about(self):
        """显示关于对话框"""
        QMessageBox.information(
            None,
            "关于",
            "Screenshots 自动整理工具\n\n"
            "自动检测并整理 OneDrive Screenshots 文件夹中的图片\n"
            "每1分钟自动检查一次\n\n"
            "功能：将3天前的截图统一归档到'已到期'文件夹"
        )

    def quit_app(self):
        """退出应用"""
        self.timer.stop()
        QApplication.quit()


class ServerDialog(QDialog):
    """添加 / 编辑服务器的对话框，输入主机名、IP、端口"""

    def __init__(self, parent=None, hostname="", ip="", port="5001", title="添加新的服务器"):
        super().__init__(parent)
        self.setWindowTitle(title)
        self.setMinimumWidth(420)

        # 放大整个对话框的字体（标签、输入框、按钮都会继承）
        dialog_font = QFont()
        dialog_font.setPointSize(14)
        self.setFont(dialog_font)

        layout = QFormLayout(self)
        layout.setContentsMargins(20, 20, 20, 20)
        layout.setSpacing(14)

        self.hostname_edit = QLineEdit(hostname)
        self.hostname_edit.setPlaceholderText("例如：客厅电脑")
        self.ip_edit = QLineEdit(ip)
        self.ip_edit.setPlaceholderText("例如：192.168.1.100")
        self.port_edit = QLineEdit(str(port))
        self.port_edit.setPlaceholderText("例如：5001")

        # 输入框高度更舒适
        for edit in (self.hostname_edit, self.ip_edit, self.port_edit):
            edit.setMinimumHeight(32)

        layout.addRow("主机名:", self.hostname_edit)
        layout.addRow("IP 地址:", self.ip_edit)
        layout.addRow("端口:", self.port_edit)

        buttons = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)
        buttons.button(QDialogButtonBox.Ok).setText("确定")
        buttons.button(QDialogButtonBox.Cancel).setText("取消")
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        layout.addRow(buttons)

    def get_values(self):
        """返回 (主机名, IP, 端口)，均已去除首尾空白"""
        return (
            self.hostname_edit.text().strip(),
            self.ip_edit.text().strip(),
            self.port_edit.text().strip(),
        )


class ServerTrayIcon(QSystemTrayIcon):
    """代表一个远程服务器的托盘图标，定时轮询其 /api/status 接口"""

    # 后台线程轮询完成后发出: (是否在线, 文件数, 状态消息)
    status_signal = pyqtSignal(bool, int, str)

    def __init__(self, manager, hostname, ip, port, parent=None):
        super().__init__(parent)
        self.manager = manager
        self.hostname = hostname
        self.ip = ip
        self.port = str(port)

        self.online = False
        self.count = 0
        self.last_message = "尚未连接"

        # 跨线程更新界面：信号自动排队到主线程执行
        self.status_signal.connect(self.on_status)

        self.setup_menu()
        self.update_display()
        self.show()

        # 每 60 秒轮询一次
        self.timer = QTimer()
        self.timer.timeout.connect(self.poll)
        self.timer.start(60000)

        # 立即轮询一次
        self.poll()

    def setup_menu(self):
        """构建服务器图标的右键菜单"""
        menu = QMenu()

        refresh_action = QAction("刷新状态", menu)
        refresh_action.triggered.connect(self.poll)
        menu.addAction(refresh_action)

        open_web_action = QAction("打开网页", menu)
        open_web_action.triggered.connect(self.open_web)
        menu.addAction(open_web_action)

        menu.addSeparator()

        edit_action = QAction("编辑服务器", menu)
        edit_action.triggered.connect(lambda: self.manager.edit_server(self))
        menu.addAction(edit_action)

        remove_action = QAction("删除此服务器", menu)
        remove_action.triggered.connect(lambda: self.manager.remove_server(self))
        menu.addAction(remove_action)

        menu.addSeparator()

        quit_action = QAction("退出全部", menu)
        quit_action.triggered.connect(QApplication.quit)
        menu.addAction(quit_action)

        self.setContextMenu(menu)

    def poll(self):
        """在后台线程发起状态查询，避免阻塞界面"""
        thread = threading.Thread(target=self._poll_worker, daemon=True)
        thread.start()

    def _poll_worker(self):
        url = f"http://{self.ip}:{self.port}/api/status"
        try:
            # 绕过系统代理直连：开了代理时局域网 IP 走代理会连接失败
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            with opener.open(url, timeout=4) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            count = int(data.get("totalCount", 0))
            message = data.get("message", f"在线，共 {count} 个文件")
            self.status_signal.emit(True, count, message)
        except Exception as e:
            self.status_signal.emit(False, 0, str(e))

    def on_status(self, ok, count, message):
        """轮询结果回调（运行在主线程）"""
        self.online = ok
        self.count = count
        self.last_message = message if ok else f"连接失败 ({message})"
        self.update_display()

    def update_display(self):
        """根据当前状态刷新图标和悬浮提示"""
        if self.online:
            # 在线：蓝色背景 + 文件数
            icon = create_count_icon(self.count, QColor(0, 120, 215))
        else:
            # 离线：灰色背景 + 问号
            icon = create_count_icon(0, QColor(120, 120, 120), text="?")
        self.setIcon(icon)
        self.setToolTip(self.tooltip_text())

    def tooltip_text(self):
        """悬浮提示：主机名、IP、端口、状态"""
        return (
            f"主机名: {self.hostname}\n"
            f"IP: {self.ip}\n"
            f"端口: {self.port}\n"
            f"状态: {self.last_message}"
        )

    def open_web(self):
        """在浏览器中打开服务器主页"""
        webbrowser.open(f"http://{self.ip}:{self.port}/")


class AppManager(QObject):
    """统管本地截图托盘 + 所有远程服务器托盘，并负责持久化"""

    def __init__(self):
        super().__init__()
        self.project_dir = Path(__file__).parent
        self.servers_file = self.project_dir / "servers.json"
        self.server_icons = []

        # 本地截图整理托盘（原有功能）
        self.organizer = ScreenshotOrganizer()
        self.organizer.add_server_requested.connect(self.add_server)

        # 启动时恢复已保存的服务器
        self.load_servers()

    def load_servers(self):
        """从 servers.json 读取并重建服务器托盘图标"""
        if not self.servers_file.exists():
            return
        try:
            data = json.loads(self.servers_file.read_text(encoding="utf-8"))
            for s in data:
                self._create_icon(s["hostname"], s["ip"], s["port"])
            print(f"已恢复 {len(data)} 个服务器")
        except Exception as e:
            print(f"加载服务器列表出错: {e}")

    def save_servers(self):
        """将当前服务器列表写入 servers.json"""
        try:
            data = [
                {"hostname": i.hostname, "ip": i.ip, "port": i.port}
                for i in self.server_icons
            ]
            self.servers_file.write_text(
                json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
            )
        except Exception as e:
            print(f"保存服务器列表出错: {e}")

    def _create_icon(self, hostname, ip, port):
        icon = ServerTrayIcon(self, hostname, ip, port)
        self.server_icons.append(icon)
        return icon

    def add_server(self):
        """弹出对话框新增一个服务器"""
        dlg = ServerDialog(title="添加新的服务器")
        if dlg.exec_() != QDialog.Accepted:
            return

        hostname, ip, port = dlg.get_values()
        if not ip or not port:
            QMessageBox.warning(None, "输入错误", "IP 和端口不能为空")
            return
        if not hostname:
            hostname = ip

        self._create_icon(hostname, ip, port)
        self.save_servers()
        self.organizer.showMessage(
            "已添加服务器",
            f"{hostname} ({ip}:{port})",
            QSystemTrayIcon.Information,
            2000,
        )

    def edit_server(self, icon):
        """编辑已有服务器的信息"""
        dlg = ServerDialog(
            hostname=icon.hostname, ip=icon.ip, port=icon.port, title="编辑服务器"
        )
        if dlg.exec_() != QDialog.Accepted:
            return

        hostname, ip, port = dlg.get_values()
        if not ip or not port:
            QMessageBox.warning(None, "输入错误", "IP 和端口不能为空")
            return

        icon.hostname = hostname or ip
        icon.ip = ip
        icon.port = port
        icon.update_display()
        icon.poll()
        self.save_servers()

    def remove_server(self, icon):
        """删除一个服务器"""
        reply = QMessageBox.question(
            None,
            "删除服务器",
            f"确定删除服务器 {icon.hostname} ({icon.ip}:{icon.port}) 吗？",
            QMessageBox.Yes | QMessageBox.No,
        )
        if reply != QMessageBox.Yes:
            return

        icon.timer.stop()
        icon.hide()
        if icon in self.server_icons:
            self.server_icons.remove(icon)
        icon.deleteLater()
        self.save_servers()


def main():
    """主函数"""
    app = QApplication(sys.argv)
    app.setQuitOnLastWindowClosed(False)  # 关闭最后一个窗口时不退出

    # 创建应用管理器（包含本地截图托盘 + 远程服务器托盘）
    manager = AppManager()

    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
