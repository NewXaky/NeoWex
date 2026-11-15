package newex.content;

/** Máy khoan có thể được Copes Block tăng tốc (trước đây gọi bằng duck-typing building.applyBoost(1.5, 35) trong JS). */
public interface DrillBoostable {
    void applyBoost(float multiplier, float duration);
}
