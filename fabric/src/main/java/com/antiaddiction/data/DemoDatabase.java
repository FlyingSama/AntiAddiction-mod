package com.antiaddiction.data;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

/**
 * 演示用本地身份数据库（真实场景应替换为后端 API 验证）
 *
 * 中国居民身份证号规则（18位）：
 *   前6位  = 地区代码
 *   7-10位 = 出生年（YYYY）
 *   11-12位= 出生月（MM）
 *   13-14位= 出生日（DD）
 *   15-17位= 顺序码
 *   第18位 = 校验码（0-9 或 X）
 */
public class DemoDatabase {

    // key = 姓名, value = 身份证号（大写）
    private static volatile Map<String, String> USERS = new HashMap<>();

    static {
        USERS.put("胡墨凡", "429004201712150350");
    }

    /** 由后端推送更新用户列表（替换全部） */
    public static void updateUsers(Map<String, String> users) {
        USERS = new HashMap<>(users);
    }

    /**
     * 验证姓名+身份证是否匹配，并返回年龄信息
     */
    public static VerificationResult verify(String name, String idCard) {
        if (name == null || idCard == null) return VerificationResult.invalid();

        String normalized = idCard.trim().toUpperCase();
        String stored = USERS.get(name.trim());

        if (stored == null || !stored.equalsIgnoreCase(normalized)) {
            return VerificationResult.invalid("姓名或身份证号不正确，请重新输入");
        }

        int age = extractAge(normalized);
        if (age < 0) {
            return VerificationResult.invalid("身份证号格式不正确（出生日期解析失败）");
        }

        return VerificationResult.valid(age, age < 18);
    }

    /**
     * 从身份证号中提取年龄
     */
    static int extractAge(String idCard) {
        try {
            int year  = Integer.parseInt(idCard.substring(6, 10));
            int month = Integer.parseInt(idCard.substring(10, 12));
            int day   = Integer.parseInt(idCard.substring(12, 14));
            LocalDate birth = LocalDate.of(year, month, day);
            return Period.between(birth, LocalDate.now()).getYears();
        } catch (Exception e) {
            return -1;
        }
    }
}
