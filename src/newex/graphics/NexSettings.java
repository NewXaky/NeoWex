package newex.graphics;

import arc.Core;

/**
 * Cài đặt đồ họa (thay cho hệ thống cài đặt của WarHammer). Lưu trong Core.settings nên có thể chỉnh bằng
 * Core.settings.put("nex-effect-enabled", false) hoặc thêm checkbox vào menu mod sau.
 */
public final class NexSettings {
    private NexSettings() {}

    public static final String effectEnabledKey = "nex-effect-enabled";
    public static final String distortionEnabledKey = "nex-distortion-enabled";
    public static final String distortionStrengthKey = "nex-distortion-strength";

    /** Bật/tắt các hiệu ứng sét, xúc tu, vệt đuôi. */
    public static boolean effectEnabled() {
        return Core.settings.getBool(effectEnabledKey, true);
    }

    /** Bật/tắt méo không gian (lỗ đen, thấu kính lồi). Lõi đen vẫn vẽ dù tắt méo. */
    public static boolean distortionEnabled() {
        return Core.settings.getBool(distortionEnabledKey, true);
    }

    /** Hệ số nhân độ mạnh méo, 0..2 (mặc định 1). */
    public static float distortionStrengthScale() {
        return Math.max(0f, Core.settings.getInt(distortionStrengthKey, 100) / 100f);
    }
}
