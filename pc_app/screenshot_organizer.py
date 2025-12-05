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


class ScreenshotOrganizer(QSystemTrayIcon):
    def __init__(self, parent=None):
        super().__init__(parent)

        # 获取用户家目录
        self.home_dir = Path.home()
        self.screenshots_path = self.home_dir / "OneDrive" / "图片" / "Screenshots"

        # 项目目录（程序所在目录）
        self.project_dir = Path(__file__).parent
        self.icon_has = self.project_dir / "有.png"
        self.icon_none = self.project_dir / "无.png"

        # 初始化托盘图标
        self.setup_tray()

        # 设置定时器，每分钟检查一次
        self.timer = QTimer()
        self.timer.timeout.connect(self.check_time_and_run)
        self.timer.start(60000)  # 60000 毫秒 = 1 分钟

        # 程序启动时显示提示
        self.showMessage(
            "截图整理工具已启动",
            "将在每个整点01分自动整理截图",
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
        self.setToolTip("Screenshots 自动整理工具\n每个整点01分自动执行")

        # 显示托盘图标
        self.show()

    def update_icon(self, has_new_folder):
        """更新托盘图标，动态显示条目数"""
        # 统计总条目数
        total_count = self.count_total_items()

        # 根据状态选择基础图标
        if has_new_folder and self.icon_has.exists():
            base_icon_path = self.icon_has
        elif self.icon_none.exists():
            base_icon_path = self.icon_none
        else:
            # 如果图标文件不存在，使用默认图标
            self.setIcon(QApplication.style().standardIcon(
                QApplication.style().SP_FileDialogInfoView
            ))
            return

        # 创建带数字的图标
        icon_with_count = self.create_icon_with_count(base_icon_path, total_count)
        self.setIcon(icon_with_count)

        # 更新工具提示
        if total_count > 0:
            self.setToolTip(f"Screenshots 自动整理工具\nPC有 {total_count} 个文件\n每个整点01分自动执行")
        else:
            self.setToolTip("Screenshots 自动整理工具\n每个整点01分自动执行")

    def count_total_items(self):
        """统计所有时间文件夹中的文件总数"""
        try:
            if not self.screenshots_path.exists():
                return 0

            time_folder_pattern = re.compile(r'^\d{2}-\d{2}$')
            total_count = 0

            for item in self.screenshots_path.iterdir():
                if item.is_dir() and time_folder_pattern.match(item.name):
                    folder_file_count = sum(1 for f in item.iterdir() if f.is_file())
                    total_count += folder_file_count

            return total_count
        except Exception as e:
            print(f"统计文件数时出错: {e}")
            return 0

    def create_icon_with_count(self, base_icon_path, count):
        """创建带有数字的图标"""
        # 加载基础图标
        pixmap = QPixmap(str(base_icon_path))
        if pixmap.isNull():
            # 如果加载失败，创建一个默认图标
            pixmap = QPixmap(64, 64)
            pixmap.fill(QColor(200, 200, 200))

        # 确保图标大小合适（系统托盘通常是 16x16 或 32x32）
        pixmap = pixmap.scaled(64, 64, Qt.KeepAspectRatio, Qt.SmoothTransformation)

        # 如果有条目，在图标上绘制数字
        if count > 0:
            painter = QPainter(pixmap)
            painter.setRenderHint(QPainter.Antialiasing)

            # 设置字体
            font = QFont("Arial", 32, QFont.Bold)
            painter.setFont(font)

            # 绘制数字背景（圆形或矩形）
            text = str(count) if count < 1000 else "999+"

            # 计算文字大小
            metrics = painter.fontMetrics()
            text_width = metrics.horizontalAdvance(text)
            text_height = metrics.height()

            # 绘制半透明背景
            bg_x = pixmap.width() - text_width - 8
            bg_y = pixmap.height() - text_height - 4
            bg_width = text_width + 8
            bg_height = text_height + 4

            painter.setBrush(QColor(255, 0, 0, 200))
            painter.setPen(Qt.NoPen)
            painter.drawRoundedRect(bg_x, bg_y, bg_width, bg_height, 4, 4)

            # 绘制白色数字
            painter.setPen(QColor(255, 255, 255))
            painter.drawText(
                bg_x + 4,
                bg_y + text_height - 4,
                text
            )

            painter.end()

        return QIcon(pixmap)

    def check_time_and_run(self):
        """检查时间，如果是整点01分则执行"""
        now = datetime.now()
        # 只在每个整点的01分执行
        if now.minute == 1:
            print(f"\n[定时执行] 当前时间: {now.strftime('%Y-%m-%d %H:%M:%S')}")
            self.check_and_organize()
        else:
            # 其他时间不执行，只打印日志
            print(f"[等待中] 当前时间: {now.strftime('%H:%M')}, 下次执行时间: {now.hour + 1:02d}:01" if now.minute > 1 else f"{now.hour:02d}:01")

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

            # 查找符合条件的图片，按时间段分组
            # 使用字典存储: {文件夹名: [(文件路径, 创建时间), ...]}
            files_by_folder = {}
            image_extensions = {'.png', '.jpg', '.jpeg', '.gif', '.bmp', '.webp'}

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
                    # 计算该图片应该归档到哪个时间段
                    # 获取图片创建时间的整点小时
                    hour_start = creation_time.replace(minute=0, second=0, microsecond=0)
                    hour_end = hour_start + timedelta(hours=1)

                    # 创建文件夹名称（只保留时间段，Windows 不允许冒号）
                    folder_name = f"{hour_start.strftime('%H')}-{hour_end.strftime('%H')}"

                    # 将文件添加到对应的分组中
                    if folder_name not in files_by_folder:
                        files_by_folder[folder_name] = []

                    files_by_folder[folder_name].append((file_path, creation_time))
                    print(f"找到匹配文件: {file_path.name} (创建时间: {creation_time.strftime('%Y-%m-%d %H:%M:%S')}) -> {folder_name}")

            # 如果有符合条件的文件，创建文件夹并移动
            has_new_folder = False
            total_moved = 0
            folder_count = len(files_by_folder)

            if files_by_folder:
                print(f"\n开始整理，共需创建 {folder_count} 个文件夹")

                # 遍历每个时间段分组
                for folder_name, file_list in files_by_folder.items():
                    target_folder = self.screenshots_path / folder_name

                    # 创建文件夹
                    target_folder.mkdir(exist_ok=True)
                    print(f"\n创建文件夹: {folder_name}")

                    # 移动该时间段的所有文件
                    for file_path, creation_time in file_list:
                        try:
                            dest_path = target_folder / file_path.name
                            shutil.move(str(file_path), str(dest_path))
                            print(f"  移动文件: {file_path.name}")
                            total_moved += 1
                        except Exception as e:
                            print(f"  移动文件失败 {file_path.name}: {e}")

                has_new_folder = True

                # 显示通知
                if folder_count == 1:
                    self.showMessage(
                        "截图已整理",
                        f"已将 {total_moved} 个文件移动到文件夹:\n{list(files_by_folder.keys())[0]}",
                        QSystemTrayIcon.Information,
                        3000
                    )
                else:
                    self.showMessage(
                        "截图已整理",
                        f"已整理 {total_moved} 个文件到 {folder_count} 个文件夹",
                        QSystemTrayIcon.Information,
                        3000
                    )

                print(f"\n整理完成！共移动 {total_moved} 个文件到 {folder_count} 个文件夹")
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
        """检查是否存在任何时间格式的文件夹"""
        try:
            if not self.screenshots_path.exists():
                return False
            time_folder_pattern = re.compile(r'^\d{2}-\d{2}$')
            for item in self.screenshots_path.iterdir():
                if item.is_dir() and time_folder_pattern.match(item.name):
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
            "每个整点01分自动检查一次\n\n"
            "功能：将3天前同一时段的截图归档到对应文件夹"
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
