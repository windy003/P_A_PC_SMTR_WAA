package com.screenshot.monitor.widget

import android.os.Build
import android.util.Log

/**
 * 设备配置类 - 根据不同设备型号定制小部件显示参数
 */
data class WidgetStyleConfig(
    val pcTextSize: Float,          // PC文字大小 (sp)
    val dateTextSize: Float,        // 日期文字大小 (sp)
    val hourTextSize: Float,        // 小时文字大小 (sp)
    val minuteTextSize: Float,      // 分钟文字大小 (sp)
    val updateTimeSize: Float,      // 更新时间文字大小 (sp)
    val widgetPadding: Int          // 小部件内边距 (dp)
)

object DeviceConfig {
    private const val TAG = "DeviceConfig"

    // 默认配置
    private val DEFAULT_CONFIG = WidgetStyleConfig(
        pcTextSize = 10f,
        dateTextSize = 9f,
        hourTextSize = 9f,
        minuteTextSize = 7f,
        updateTimeSize = 6f,
        widgetPadding = 4
    )

    // LG Wing 配置
    private val LG_WING_CONFIG = WidgetStyleConfig(
        pcTextSize = 15f,
        dateTextSize = 15f,
        hourTextSize = 15f,
        minuteTextSize = 15f,
        updateTimeSize = 15f,
        widgetPadding = 5
    )

    // 小米 12 Pro 配置
    private val XIAOMI_12_PRO_CONFIG = WidgetStyleConfig(
        pcTextSize = 10f,
        dateTextSize = 10f,
        hourTextSize = 10f,
        minuteTextSize = 10f,
        updateTimeSize = 10f,
        widgetPadding = 4
    )

    // 小米 Mix Fold 2 配置
    private val XIAOMI_MIX_FOLD_2_CONFIG = WidgetStyleConfig(
        pcTextSize = 12f,
        dateTextSize = 11f,
        hourTextSize = 11f,
        minuteTextSize = 9f,
        updateTimeSize = 7f,
        widgetPadding = 6
    )

    /**
     * 获取当前设备的配置
     */
    fun getDeviceConfig(): WidgetStyleConfig {
        val deviceModel = Build.MODEL
        val deviceManufacturer = Build.MANUFACTURER
        val deviceBrand = Build.BRAND
        val deviceProduct = Build.PRODUCT

        Log.d(TAG, "========================================")
        Log.d(TAG, "设备检测信息:")
        Log.d(TAG, "  制造商 (MANUFACTURER): $deviceManufacturer")
        Log.d(TAG, "  型号 (MODEL): $deviceModel")
        Log.d(TAG, "  品牌 (BRAND): $deviceBrand")
        Log.d(TAG, "  产品 (PRODUCT): $deviceProduct")
        Log.d(TAG, "========================================")

        val config = when {
            // LG Wing
            isLGWing(deviceManufacturer, deviceModel) -> {
                Log.d(TAG, "✓ 匹配到: LG Wing")
                Log.d(TAG, "  应用配置: pcText=${LG_WING_CONFIG.pcTextSize}sp, date=${LG_WING_CONFIG.dateTextSize}sp, hour=${LG_WING_CONFIG.hourTextSize}sp")
                LG_WING_CONFIG
            }
            // 小米 12 Pro
            isXiaomi12Pro(deviceManufacturer, deviceModel) -> {
                Log.d(TAG, "✓ 匹配到: 小米 12 Pro")
                Log.d(TAG, "  应用配置: pcText=${XIAOMI_12_PRO_CONFIG.pcTextSize}sp, date=${XIAOMI_12_PRO_CONFIG.dateTextSize}sp, hour=${XIAOMI_12_PRO_CONFIG.hourTextSize}sp")
                XIAOMI_12_PRO_CONFIG
            }
            // 小米 Mix Fold 2
            isXiaomiMixFold2(deviceManufacturer, deviceModel) -> {
                Log.d(TAG, "✓ 匹配到: 小米 Mix Fold 2")
                Log.d(TAG, "  应用配置: pcText=${XIAOMI_MIX_FOLD_2_CONFIG.pcTextSize}sp, date=${XIAOMI_MIX_FOLD_2_CONFIG.dateTextSize}sp, hour=${XIAOMI_MIX_FOLD_2_CONFIG.hourTextSize}sp")
                XIAOMI_MIX_FOLD_2_CONFIG
            }
            else -> {
                Log.w(TAG, "✗ 未匹配到已知设备，使用默认配置")
                Log.d(TAG, "  应用配置: pcText=${DEFAULT_CONFIG.pcTextSize}sp, date=${DEFAULT_CONFIG.dateTextSize}sp, hour=${DEFAULT_CONFIG.hourTextSize}sp")
                DEFAULT_CONFIG
            }
        }

        Log.d(TAG, "========================================")
        return config
    }

    /**
     * 检测是否为 LG Wing
     */
    private fun isLGWing(manufacturer: String, model: String): Boolean {
        return (manufacturer.equals("LG", ignoreCase = true) ||
                manufacturer.equals("LGE", ignoreCase = true)) &&
               (model.contains("Wing", ignoreCase = true) ||
                model.contains("LM-F100", ignoreCase = true))
    }

    /**
     * 检测是否为小米 12 Pro
     */
    private fun isXiaomi12Pro(manufacturer: String, model: String): Boolean {
        return manufacturer.equals("Xiaomi", ignoreCase = true) &&
               (model.contains("2201122C", ignoreCase = true) ||  // 国行版
                model.contains("2201122G", ignoreCase = true) ||  // 全球版
                model.contains("12 Pro", ignoreCase = true))
    }

    /**
     * 检测是否为小米 Mix Fold 2
     */
    private fun isXiaomiMixFold2(manufacturer: String, model: String): Boolean {
        return manufacturer.equals("Xiaomi", ignoreCase = true) &&
               (model.contains("22061218C", ignoreCase = true) ||  // 国行版
                model.contains("MIX Fold 2", ignoreCase = true) ||
                model.contains("Mix Fold 2", ignoreCase = true))
    }

    /**
     * 获取设备信息字符串（用于调试）
     */
    fun getDeviceInfo(): String {
        return "设备: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    }
}
