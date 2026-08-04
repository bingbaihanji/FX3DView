package com.bingbaihanji.rotation;

import com.bingbaihanji.interaction.InteractionConfig;
import com.bingbaihanji.quaternion.Quaternion;
import com.bingbaihanji.quaternion.QuaternionArcBallUtils;
import javafx.scene.transform.Affine;
import lombok.extern.slf4j.Slf4j;

/**
 * 基于四元数的旋转策略实现
 * <p>
 * 使用四元数表示和计算旋转,具有以下优势:
 * - 避免万向节锁问题
 * - 平滑的旋转插值(NLERP)
 * - 更好的数值稳定性
 * - 高效的旋转累积
 * - 统一使用 double 精度计算
 * </p>
 *
 * @author bingbaihanji
 */
@Slf4j
public class QuaternionRotation implements RotationStrategy {

    /**
     * 旋转变换的Affine对象
     */
    private final Affine rotationAffine = new Affine();

    /**
     * ArcBall工具实例(实例模式,线程安全)
     */
    private final QuaternionArcBallUtils arcBallUtils = new QuaternionArcBallUtils(1, 1);

    /**
     * 临时四元数(复用减少GC,非final以支持引用交换)
     */
    private Quaternion tempQuat1 = new Quaternion();

    private Quaternion tempQuat2 = new Quaternion();

    private Quaternion tempQuat3 = new Quaternion();

    /**
     * 当前累积的旋转四元数
     */
    private Quaternion lastRotationQuaternion = Quaternion.identity();

    @Override
    public void applyDragRotation(int width, int height,
                                  double prevX, double prevY,
                                  double currX, double currY,
                                  double factor) {
        // 应用速度因子调整
        double adjX = prevX + (currX - prevX) * factor;
        double adjY = prevY - (currY - prevY) * factor; // Y轴翻转

        // 使用原地重载计算增量旋转(避免分配新Quaternion)
        arcBallUtils.getArcBallRotationQuaternion(
                width, height, prevX, prevY, adjX, adjY, tempQuat3);

        // 应用阻尼效果(使用NLERP进行平滑)
        double damping = InteractionConfig.ROTATION_DAMPING;
        tempQuat1.set(0, 0, 0, 1); // 单位四元数
        Quaternion.interpolate(tempQuat2, tempQuat1, tempQuat3, damping);

        // 累积旋转:新旋转 = 上次旋转 × 阻尼后的增量旋转
        Quaternion newRot = Quaternion.multiply(lastRotationQuaternion, tempQuat2);
        newRot.normalize();
        lastRotationQuaternion = newRot;

        // 应用到Affine变换
        QuaternionArcBallUtils.setRotationToAffine(rotationAffine, lastRotationQuaternion);
    }

    @Override
    public void applyAutoRotation(double angleRad) {
        // 绕Y轴旋转(屏幕竖直方向)
        Quaternion rotation = Quaternion.fromAxisAngle(0, 1, 0, angleRad);

        // 原地累积旋转(避免multiply分配新Quaternion)
        Quaternion.multiply(lastRotationQuaternion, rotation, tempQuat1);
        tempQuat1.normalize();
        // 交换引用:tempQuat1成为新累积旋转,旧lastRotationQuaternion变成可复用temp
        Quaternion swap = lastRotationQuaternion;
        lastRotationQuaternion = tempQuat1;
        tempQuat1 = swap;

        // 应用到Affine
        QuaternionArcBallUtils.setRotationToAffine(rotationAffine, lastRotationQuaternion);
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
        Quaternion randomRot = Quaternion.fromAxisAngle(axisX, axisY, axisZ, angle);

        lastRotationQuaternion = Quaternion.multiply(lastRotationQuaternion, randomRot);
        lastRotationQuaternion.normalize();
        QuaternionArcBallUtils.setRotationToAffine(rotationAffine, lastRotationQuaternion);

        log.info("应用随机旋转: 轴=[{},{},{}], 角度={}°",
                String.format("%.2f", axisX), String.format("%.2f", axisY),
                String.format("%.2f", axisZ), String.format("%.1f", Math.toDegrees(angle)));
    }

    @Override
    public void reset(double initXAngle, double initYAngle) {
        // 重置旋转(使用四元数)
        double initXRad = Math.toRadians(initXAngle);
        double initYRad = Math.toRadians(initYAngle);
        Quaternion qx = Quaternion.fromEuler(initXRad, 0, 0);
        Quaternion qy = Quaternion.fromEuler(0, initYRad, 0);
        Quaternion initRot = Quaternion.multiply(qy, qx);

        QuaternionArcBallUtils.setRotationToAffine(rotationAffine, initRot);
        lastRotationQuaternion = initRot;
    }

    @Override
    public Affine getRotationAffine() {
        return rotationAffine;
    }

    @Override
    public String getStrategyName() {
        return "四元数";
    }

    /**
     * 更新当前旋转状态(用于鼠标按下时保存状态)
     * <p>
     * 从Affine变换中提取当前旋转四元数
     * </p>
     */
    @Override
    public void updateFromAffine() {
        lastRotationQuaternion = QuaternionArcBallUtils.getRotationFromAffine(rotationAffine);
    }
}
