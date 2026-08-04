package com.bingbaihanji.camera;

/**
 * 相机系统配置常量
 * <p>
 * 集中管理相机相关的配置参数,便于调整和维护
 * </p>
 *
 * @author bingbaihanji
 */
public final class CameraConfig {

    /**
     * 相机近裁剪面距离
     */
    public static final double NEAR_CLIP = 1.0;

    /**
     * 相机远裁剪面距离
     * 24位深度缓冲: near/far=1/500 → 约15位有效精度 → 远平面处步长约0.015单位
     * 足以消除绝大多数模型的Z-fighting闪烁
     */
    public static final double FAR_CLIP = 500.0;

    /**
     * 相机初始距离(Z轴位置)
     */
    public static final double INITIAL_DISTANCE = -80.0;

    /**
     * 相机初始X轴旋转角度(度)
     */
    public static final double INITIAL_X_ANGLE = 0.0;

    /**
     * 相机初始Y轴旋转角度(度)
     */
    public static final double INITIAL_Y_ANGLE = 0.0;

    // 模型离 camera 最近的距离
    public static final double MODEL_NEAR_CLIP = -8.0;

    /**
     * 私有构造函数,防止实例化
     */
    private CameraConfig() {
        throw new AssertionError("工具类不应该被实例化");
    }
}
