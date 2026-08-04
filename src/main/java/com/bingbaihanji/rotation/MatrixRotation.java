package com.bingbaihanji.rotation;

import com.bingbaihanji.interaction.InteractionConfig;
import com.bingbaihanji.matrix.ArcBallUtils;
import com.bingbaihanji.matrix.Matrix3;
import com.bingbaihanji.matrix.Vector3;
import javafx.scene.transform.Affine;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于旋转矩阵的旋转策略实现
 * <p>
 * 使用3x3旋转矩阵表示和计算旋转,具有以下特点:
 * - 直观的数学表示
 * - 传统的旋转计算方法
 * - 适合教学演示
 * </p>
 *
 * @author bingbaihanji
 */
@Slf4j
public class MatrixRotation implements RotationStrategy {

    /**
     * 预分配Y轴向量,避免每帧new Vector3
     */
    private static final Vector3 Y_AXIS = new Vector3(0, 1, 0);

    /**
     * 旋转变换的Affine对象
     */
    private final Affine rotationAffine = new Affine();

    /**
     * ArcBall工具实例(实例模式,线程安全)
     */
    private final ArcBallUtils arcBallUtils = new ArcBallUtils(1, 1);

    /**
     * 临时矩阵(复用,减少每帧GC)
     */
    private final Matrix3 tempM1 = new Matrix3();

    private final Matrix3 tempM2 = new Matrix3();

    /**
     * 当前累积的旋转矩阵
     */
    private Matrix3 lastRotationMatrix = Matrix3.identityMatrix();

    @Override
    public void applyDragRotation(int width, int height,
                                  double prevX, double prevY,
                                  double currX, double currY,
                                  double factor) {
        // 应用速度因子调整
        double adjX = prevX + (currX - prevX) * factor;
        double adjY = prevY - (currY - prevY) * factor; // Y轴翻转

        // 原地计算增量旋转矩阵(避免分配新Matrix3)
        arcBallUtils.getArcBallRotationMatrix(
                width, height, prevX, prevY, adjX, adjY, tempM1);

        // 阻尼插值:tempM2 = lerp(identity, tempM1, damping)
        double damping = InteractionConfig.ROTATION_DAMPING;
        Matrix3.lerp(Matrix3.identityMatrix(), tempM1, damping, tempM2);

        // 累积旋转
        Matrix3 newRot = Matrix3.multiply(lastRotationMatrix, tempM2);
        ArcBallUtils.setRotationToAffine(rotationAffine, newRot);
        lastRotationMatrix = newRot;
    }

    @Override
    public void applyAutoRotation(double angleRad) {
        // 绕Y轴旋转(原地计算,避免分配新Matrix3)
        Matrix3.rotation(Y_AXIS, angleRad, tempM1);

        // 累积旋转(原地右乘)
        lastRotationMatrix.mulSelf(tempM1);

        // 应用到Affine
        ArcBallUtils.setRotationToAffine(rotationAffine, lastRotationMatrix);
    }

    @Override
    public void applyRandomRotation() {
        double axisX = Math.random() * 2 - 1;
        double axisY = Math.random() * 2 - 1;
        double axisZ = Math.random() * 2 - 1;

        double length = Math.sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);
        if (length > 0) {
            axisX /= length;
            axisY /= length;
            axisZ /= length;
        } else {
            axisX = 0;
            axisY = 1;
            axisZ = 0;
        }

        double angle = Math.toRadians(90 + Math.random() * 180);
        Vector3 axis = new Vector3(axisX, axisY, axisZ);
        Matrix3 randomRot = Matrix3.rotation(axis, angle);

        lastRotationMatrix = Matrix3.multiply(lastRotationMatrix, randomRot);
        ArcBallUtils.setRotationToAffine(rotationAffine, lastRotationMatrix);

        log.info("应用随机旋转: 轴=[{},{},{}], 角度={}°",
                String.format("%.2f", axisX), String.format("%.2f", axisY),
                String.format("%.2f", axisZ), String.format("%.1f", Math.toDegrees(angle)));
    }

    @Override
    public void reset(double initXAngle, double initYAngle) {
        // 重置旋转(使用矩阵)
        double initXRad = Math.toRadians(initXAngle);
        double initYRad = Math.toRadians(initYAngle);
        Matrix3 rxInit = Matrix3.rotationX(initXRad); // X轴旋转矩阵
        Matrix3 ryInit = Matrix3.rotationY(initYRad); // Y轴旋转矩阵
        Matrix3 initRot = Matrix3.multiply(ryInit, rxInit); // 矩阵相乘

        ArcBallUtils.setRotationToAffine(rotationAffine, initRot);
        lastRotationMatrix = initRot;
    }

    @Override
    public Affine getRotationAffine() {
        return rotationAffine;
    }

    @Override
    public String getStrategyName() {
        return "旋转矩阵";
    }

    /**
     * 更新当前旋转状态(用于鼠标按下时保存状态)
     * <p>
     * 从Affine变换中提取当前旋转矩阵
     * </p>
     */
    @Override
    public void updateFromAffine() {
        lastRotationMatrix = ArcBallUtils.getRotationFromAffine(rotationAffine);
    }
}
