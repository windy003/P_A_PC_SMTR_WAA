"""
Screenshots 文件还原工具
将 Screenshots 目录下所有子文件夹中的文件移动到根目录，并删除所有子文件夹
"""

import os
import shutil
from pathlib import Path


def restore_screenshots():
    """将所有子文件夹中的文件移回根目录"""
    # 获取 Screenshots 目录
    home_dir = Path.home()
    screenshots_path = home_dir / "OneDrive" / "图片" / "Screenshots"

    # 检查目录是否存在
    if not screenshots_path.exists():
        print(f"错误: Screenshots 目录不存在: {screenshots_path}")
        return

    print(f"Screenshots 目录: {screenshots_path}\n")

    # 统计信息
    moved_files = 0
    deleted_folders = 0
    errors = 0

    # 遍历 Screenshots 目录下的所有项目
    for item in screenshots_path.iterdir():
        # 只处理文件夹
        if item.is_dir():
            folder_name = item.name
            print(f"处理文件夹: {folder_name}")

            # 遍历文件夹中的所有文件
            files_in_folder = list(item.iterdir())

            for file_path in files_in_folder:
                if file_path.is_file():
                    try:
                        # 目标路径
                        dest_path = screenshots_path / file_path.name

                        # 如果目标文件已存在，添加数字后缀
                        counter = 1
                        original_dest = dest_path
                        while dest_path.exists():
                            stem = original_dest.stem
                            suffix = original_dest.suffix
                            dest_path = screenshots_path / f"{stem}_{counter}{suffix}"
                            counter += 1

                        # 移动文件
                        shutil.move(str(file_path), str(dest_path))
                        print(f"  ✓ 移动: {file_path.name}")
                        moved_files += 1
                    except Exception as e:
                        print(f"  ✗ 移动失败 {file_path.name}: {e}")
                        errors += 1

            # 删除空文件夹
            try:
                # 检查文件夹是否为空
                remaining_items = list(item.iterdir())
                if not remaining_items:
                    item.rmdir()
                    print(f"  ✓ 删除文件夹: {folder_name}")
                    deleted_folders += 1
                else:
                    print(f"  ⚠ 文件夹不为空，跳过删除: {folder_name}")
            except Exception as e:
                print(f"  ✗ 删除文件夹失败 {folder_name}: {e}")
                errors += 1

            print()

    # 显示统计信息
    print("=" * 50)
    print("完成！")
    print(f"移动文件数: {moved_files}")
    print(f"删除文件夹数: {deleted_folders}")
    if errors > 0:
        print(f"错误数: {errors}")
    print("=" * 50)


if __name__ == "__main__":
    print("=" * 50)
    print("Screenshots 文件还原工具")
    print("=" * 50)
    print()

    # 确认操作
    confirm = input("此操作将把所有子文件夹中的文件移回根目录并删除文件夹。\n确认继续？(输入 yes 继续): ")

    if confirm.lower() in ['yes', 'y']:
        print("\n开始处理...\n")
        restore_screenshots()
    else:
        print("操作已取消")
