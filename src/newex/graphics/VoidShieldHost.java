package newex.graphics;

import arc.graphics.Color;

/** Đơn vị nào muốn có khiên "void" (shader voidShield2) thì implements interface này rồi gọi NexVoidShieldRenderer.add(this). */
public interface VoidShieldHost {
    float shieldLongAxis();
    float shieldMinorAxis();
    float shieldRotation();
    float shieldRenderState();
    float shieldStateProgress();
    float shieldFraction();
    float voidShieldAlpha();
    Color voidShieldColor();
}
