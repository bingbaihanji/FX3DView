package com.bingbaihanji.rotation;

import javafx.scene.transform.Affine;

/**
 * 旋转策略接口
 * <p>
 * 定义3D旋转的核心操作,支持多种旋转算法实现(四元数,矩阵等)
 * 通过策略模式实现旋转算法的可插拔替换
 * </p>
 *
 * @author bingbaihanji
 */
public interface RotationStrategy {

    /**
     * 根据鼠标拖拽应用增量旋转(ArcBall旋转)
     *
     * @param width  视口宽度
     * @param height 视口高度
     * @param prevX  上一次鼠标X坐标
     * @param prevY  上一次鼠标Y坐标
     * @param currX  当前鼠标X坐标
     * @param currY  当前鼠标Y坐标
     * @param factor 速度因子(用于控制旋转速度,支持Ctrl/Shift修饰键)
     */
    void applyDragRotation(int width, int height,
                           double prevX, double prevY,
                           double currX, double currY,
                           double factor);

    /**
     * 应用自动旋转(绕固定轴旋转)
     *
     * @param angleRad 旋转角度(弧度)
     */
    void applyAutoRotation(double angleRad);

    /**
     * 重置到初始旋转状态
     *
     * @param initXAngle 初始X轴旋转角度(度)
     * @param initYAngle 初始Y轴旋转角度(度)
     */
    void reset(double initXAngle, double initYAngle);

    /**
     * 获取当前旋转的Affine变换
     * <p>
     * 用于应用到JavaFX场景图的变换链中
     * </p>
     *
     * @return 旋转变换的Affine对象
     */
    Affine getRotationAffine();

    /**
     * 获取策略名称
     * <p>
     * 用于UI显示和日志记录
     * </p>
     *
     * @return 策略名称(如"四元数","旋转矩阵")
     */
    String getStrategyName();

    /**
     * 应用随机旋转(UI层功能)
     * <p>
     * 生成随机旋转轴和角度进行演示,通常通过快捷键触发
     * </p>
     */
    void applyRandomRotation();

    /**
     * 从当前Affine变换中同步内部旋转状态
     * <p>
     * 用于鼠标按下时保存当前旋转状态,确保后续增量旋转在正确的基准上进行
     * </p>
     */
    void updateFromAffine();
}
