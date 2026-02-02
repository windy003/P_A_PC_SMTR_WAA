"""
Screenshots Auto Organizer
自动整理截图的系统托盘程序
"""

import sys
import os
from pathlib import Path
from datetime import datetime, timedelta
from PyQt5.QtWidgets import QApplication, QSystemTrayIcon, QMenu, QAction, QMessageBox
from PyQt5.QtCore import QTimer, Qt
from PyQt5.QtGui import QIcon, QPixmap, QPainter, QFont, QColor
import shutil
import re
from dotenv import load_dotenv

# 加载 .env 文件
load_dotenv(Path(__file__).parent / '.env')


class ScreenshotOrganizer(QSystemTrayIcon):
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
        """创建带有数字的图标"""
        # 创建一个64x64的图标
        pixmap = QPixmap(64, 64)

        # 背景改为全红色
        pixmap.fill(QColor(255, 0, 0))

        # 在图标上绘制数字（包括0）
        painter = QPainter(pixmap)
        painter.setRenderHint(QPainter.Antialiasing)

        # 设置字体
        font = QFont("Arial", 32, QFont.Bold)
        painter.setFont(font)

        # 绘制数字
        text = str(count) if count < 1000 else "999+"

        # 计算文字大小，使其居中
        metrics = painter.fontMetrics()
        text_width = metrics.horizontalAdvance(text)
        text_height = metrics.height()

        # 计算居中位置
        text_x = (pixmap.width() - text_width) // 2
        text_y = (pixmap.height() + text_height) // 2 - metrics.descent()

        # 绘制白色数字
        painter.setPen(QColor(255, 255, 255))
        painter.drawText(text_x, text_y, text)

        painter.end()

        return QIcon(pixmap)

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


def main():
    """主函数"""
    app = QApplication(sys.argv)
    app.setQuitOnLastWindowClosed(False)  # 关闭最后一个窗口时不退出

    # 创建托盘应用
    organizer = ScreenshotOrganizer()

    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
