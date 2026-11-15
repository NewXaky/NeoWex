package newex.content;

/**
 * Đơn vị tùy chỉnh có cấp độ (thay cho thuộc tính JS isGalileoJS + level trên unit của leolyr / elorix / suv-27 / reykor).
 * FixSave dùng interface này để nhận biết unit nào đã là lớp tùy chỉnh (sau khi tải save, game chỉ tạo lại lớp gốc).
 */
public interface LeveledUnit {
    int getLevel();

    void setLevel(int level);
}
