package com.bingbaihanji.quaternion;

import com.bingbaihanji.matrix.ArcBall;
import com.bingbaihanji.matrix.Vector3;

/**
 * 基于四元数的ArcBall旋转控制器,继承 {@link ArcBall} 复用球面映射和旋转轴/角度计算
 * <p>
 * 特性:
 * - 使用四元数进行旋转计算,避免万向节锁
 * - 继承父类的 mapToSphere / computeRotationAxis / computeRotationAngle
 * - 提供原地操作方法减少GC压力
 */
public class QuaternionArcBall extends ArcBall {

    /**
     * @param width  视口宽度
     * @param height 视口高度
     */
    public QuaternionArcBall(int width, int height) {
        super(width, height);
    }

    /**
     * 生成旋转四元数(返回新四元数)
     *
     * @param axis  旋转轴(需已归一化)
     * @param angle 旋转角度(弧度)
     * @return 对应的旋转四元数
     */
    public Quaternion generateRotationQuaternion(Vector3 axis, double angle) {
        return Quaternion.fromAxisAngle(axis.x, axis.y, axis.z, angle);
    }

    /**
     * 生成旋转四元数(原地操作,不分配新对象)
     *
     * @param axis   旋转轴(需已归一化)
     * @param angle  旋转角度(弧度)
     * @param result 存储结果的四元数
     */
    public void generateRotationQuaternion(Vector3 axis, double angle, Quaternion result) {
        double halfAngle = angle * 0.5;
        double sinHalf = Math.sin(halfAngle);
        double cosHalf = Math.cos(halfAngle);
        result.set(axis.x * sinHalf, axis.y * sinHalf, axis.z * sinHalf, cosHalf);
        result.normalize();
    }

    /**
     * 计算两点之间的旋转四元数
     * <p>
     * 继承父类的 mapToSphere / computeRotationAxis / computeRotationAngle,
     * 仅负责将结果封装为四元数
     * </p>
     *
     * @param x1     第一个点的x坐标
     * @param y1     第一个点的y坐标
     * @param x2     第二个点的x坐标
     * @param y2     第二个点的y坐标
     * @param result 存储计算结果的四元数对象
     */
    public void computeRotationQuaternion(double x1, double y1, double x2, double y2, Quaternion result) {
        // 映射到球面坐标(继承自父类)
        mapToSphere(x1, y1, tempVec1);
        mapToSphere(x2, y2, tempVec2);

        // 计算旋转轴和角度(继承自父类)
        computeRotationAxis(tempVec1, tempVec2, tempAxis);
        double angle = -computeRotationAngle(tempVec1, tempVec2);

        // 生成旋转四元数
        generateRotationQuaternion(tempAxis, angle, result);
    }

    /**
     * 从旋转四元数中提取欧拉角
     *
     * @param quat 旋转四元数
     * @return 欧拉角数组(角度制)
     */
    public double[] extractEulerAngles(Quaternion quat) {
        double[] angles = new double[3];
        quat.toAngles(angles);
        return angles;
    }
}
