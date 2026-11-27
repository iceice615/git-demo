package com.example.orienteeringassistant.util;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

/**
 * 二维码工具类
 * 二维码内容统一格式：ORI|CP|检查点代码
 * 例如 ORI|CP|A01 —— 扫码后只识别这个前缀，其他二维码一律视为无效
 */
public class QrUtils {

    public static final String PREFIX = "ORI|CP|";

    /** 由检查点代码生成二维码内容 */
    public static String contentFor(String code) {
        return PREFIX + code.trim().toUpperCase();
    }

    /** 从扫码结果中解析检查点代码；不是本系统二维码时返回 null */
    public static String parseCode(String content) {
        if (content == null) {
            return null;
        }
        content = content.trim();
        if (!content.startsWith(PREFIX)) {
            return null;
        }
        String code = content.substring(PREFIX.length()).trim();
        return code.isEmpty() ? null : code;
    }

    /** 生成二维码图片（用于在 APP 内预览/打印） */
    public static Bitmap makeQrBitmap(String content, int size) {
        try {
            return new BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, size, size);
        } catch (Exception e) {
            return null;
        }
    }
}
