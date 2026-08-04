package com.bingbaihanji.interaction;

/**
 * 交互配置常量
 * <p>
 * 集中管理鼠标,键盘等交互相关的配置参数
 * </p>
 *
 * @author bingbaihanji
 */
public final class InteractionConfig {

    /**
     * 鼠标移动速度系数
     */
    public static final double MOUSE_SPEED = 0.1;

    /**
     * 平移速度系数
     */
    public static final double TRACK_SPEED = 0.3;

    /**
     * 控制键(Ctrl)修饰系数(减速)
     */
    public static final double CONTROL_MULTIPLIER = 0.1;

    /**
     * Shift键修饰系数(加速)
     */
    public static final double SHIFT_MULTIPLIER = 10.0;

    /**
     * 旋转阻尼系数(用于平滑旋转)
     */
    public static final double ROTATION_DAMPING = 0.95;

    /**
     * 自动旋转速度(度/秒)
     */
    public static final double AUTO_ROTATION_SPEED = 7.5;

    /**
     * FOV调节速度(Ctrl+滚轮时的视角变化速率)
     */
    public static final double FOV_SPEED = 0.15;

    /**
     * FOV最小值
     */
    public static final double FOV_MIN = 1.0;

    /**
     * FOV最大值
     */
    public static final double FOV_MAX = 120.0;

    /**
     * 私有构造函数,防止实例化
     */
    private InteractionConfig() {
        throw new AssertionError("工具类不应该被实例化");
    }
}
