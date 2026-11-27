package com.example.orienteeringassistant.util;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;

import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * NFC 工具类：读取 NFC 标签上写入的文本
 * 约定：标签里写入和二维码一样的内容，如 ORI|CP|A01
 * （可用「NFC Tools」等 App 把检查点代码写进标签）
 */
public class NfcUtils {

    /** 读取标签文本，失败返回 null */
    public static String readTagText(Tag tag) {
        if (tag == null) {
            return null;
        }
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef == null) {
                return null;
            }
            ndef.connect();
            NdefMessage message = ndef.getNdefMessage();
            ndef.close();
            if (message == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (NdefRecord record : message.getRecords()) {
                String text = parseRecord(record);
                if (text != null && !text.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(text);
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String parseRecord(NdefRecord record) {
        try {
            if (record.getTnf() == NdefRecord.TNF_WELL_KNOWN
                    && Arrays.equals(record.getType(), NdefRecord.RTD_TEXT)) {
                byte[] payload = record.getPayload();
                if (payload.length < 2) {
                    return null;
                }
                // 第一个字节：bit7 = UTF-16 标志，后 6 位 = 语言码长度
                int status = payload[0] & 0xFF;
                int languageLength = status & 0x3F;
                boolean utf16 = (status & 0x80) != 0;
                Charset charset = utf16 ? Charset.forName("UTF-16") : Charset.forName("UTF-8");
                return new String(payload, 1 + languageLength,
                        payload.length - 1 - languageLength, charset).trim();
            }
            if (record.getTnf() == NdefRecord.TNF_WELL_KNOWN
                    && Arrays.equals(record.getType(), NdefRecord.RTD_URI)) {
                byte[] payload = record.getPayload();
                if (payload.length < 2) {
                    return null;
                }
                return new String(payload, 1, payload.length - 1,
                        Charset.forName("UTF-8")).trim();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 当前设备是否支持 NFC */
    public static boolean isNfcAvailable(NfcAdapter adapter) {
        return adapter != null;
    }
}
