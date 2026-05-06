package com.antiaddiction.data;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.Map;

/**
 * 演示用本地身份数据库（NeoForge 版，逻辑与 Fabric 版完全相同）
 */
public class DemoDatabase {

    private static volatile Map<String, String> USERS = new HashMap<>();

    static {
        USERS.put("胡墨凡", "429004201712150350");
    }

    public static void updateUsers(Map<String, String> users) {
        USERS = new HashMap<>(users);
    }

    public static VerificationResult verify(String name, String idCard) {
        if (name == null || idCard == null) return VerificationResult.invalid();
        String normalized = idCard.trim().toUpperCase();
        String stored     = USERS.get(name.trim());
        if (stored == null || !stored.equalsIgnoreCase(normalized)) {
            return VerificationResult.invalid("姓名或身份证号不正确，请重新输入");
        }
        int age = extractAge(normalized);
        if (age < 0) return VerificationResult.invalid("身份证号格式不正确（出生日期解析失败）");
        return VerificationResult.valid(age, age < 18);
    }

    static int extractAge(String idCard) {
        try {
            int year  = Integer.parseInt(idCard.substring(6, 10));
            int month = Integer.parseInt(idCard.substring(10, 12));
            int day   = Integer.parseInt(idCard.substring(12, 14));
            return Period.between(LocalDate.of(year, month, day), LocalDate.now()).getYears();
        } catch (Exception e) {
            return -1;
        }
    }
}
