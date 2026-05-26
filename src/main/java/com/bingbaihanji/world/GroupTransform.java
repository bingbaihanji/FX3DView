package com.bingbaihanji.world;

import javafx.scene.Group;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

/**
 * GroupTransform 封装了 JavaFX 节点变换操作（平移、旋转、缩放及旋转中心点）
 * <p>
 * 继承自 {@link Group}，在构造时按指定旋转顺序预填充 transforms 列表，
 * 提供链式的 translate / rotate / scale / pivot 设置与重置方法。
 * </p>
 */
public class GroupTransform extends Group {

    // 平移变换
    public Translate translate = new Translate(); // 平移变换
    public Translate pivot = new Translate(); // 旋转的中心点
    public Translate inversePivot = new Translate(); // 旋转中心点的逆变换
    // 旋转变换
    public Rotate rx = new Rotate(); // 绕 X 轴旋转
    public Rotate ry = new Rotate(); // 绕 Y 轴旋转
    public Rotate rz = new Rotate(); // 绕 Z 轴旋转
    // 缩放变换
    public Scale s = new Scale();

    {
        rx.setAxis(Rotate.X_AXIS);
        ry.setAxis(Rotate.Y_AXIS);
        rz.setAxis(Rotate.Z_AXIS);
    }

    /**
     * 默认构造函数，按固定的顺序添加所有变换。
     */
    public GroupTransform() {
        super();
        getTransforms().addAll(translate, rz, ry, rx, s);
    }

    /**
     * 带旋转顺序的构造函数，根据指定的旋转顺序添加变换。
     *
     * @param rotateOrder 旋转的顺序
     */
    public GroupTransform(RotateOrder rotateOrder) {
        super();
        switch (rotateOrder) {
            case XYZ:
                getTransforms().addAll(translate, pivot, rz, ry, rx, s, inversePivot);
                break;
            case YXZ:
                getTransforms().addAll(translate, pivot, rz, rx, ry, s, inversePivot);
                break;
            case YZX:
                getTransforms().addAll(translate, pivot, rx, rz, ry, s, inversePivot); // 用于相机的变换
                break;
            case ZXY:
                getTransforms().addAll(translate, pivot, ry, rx, rz, s, inversePivot);
                break;
            case ZYX:
                getTransforms().addAll(translate, pivot, rx, ry, rz, s, inversePivot);
                break;
        }
    }

    /**
     * 设置平移的 x、y、z 值。
     *
     * @param x 平移的 x 坐标
     * @param y 平移的 y 坐标
     * @param z 平移的 z 坐标
     */
    public void setTranslate(double x, double y, double z) {
        translate.setX(x);
        translate.setY(y);
        translate.setZ(z);
    }

    /**
     * 设置平移的 x 和 y 值。
     *
     * @param x 平移的 x 坐标
     * @param y 平移的 y 坐标
     */
    public void setTranslate(double x, double y) {
        translate.setX(x);
        translate.setY(y);
    }

    /**
     * 设置平移的 x 值。
     *
     * @param x 平移的 x 坐标
     */
    public void setTx(double x) {
        translate.setX(x);
    }

    /**
     * 设置平移的 y 值。
     *
     * @param y 平移的 y 坐标
     */
    public void setTy(double y) {
        translate.setY(y);
    }

    /**
     * 设置平移的 z 值。
     *
     * @param z 平移的 z 坐标
     */
    public void setTz(double z) {
        translate.setZ(z);
    }

    /**
     * 设置旋转的 x、y、z 值。
     *
     * @param x 绕 X 轴的旋转角度
     * @param y 绕 Y 轴的旋转角度
     * @param z 绕 Z 轴的旋转角度
     */
    public void setRotate(double x, double y, double z) {
        rx.setAngle(x);
        ry.setAngle(y);
        rz.setAngle(z);
    }

    public void setRotateX(double x) {
        rx.setAngle(x);
    }

    public void setRotateY(double y) {
        ry.setAngle(y);
    }

    public void setRotateZ(double z) {
        rz.setAngle(z);
    }

    public void setRy(double y) {
        ry.setAngle(y);
    }

    public void setRz(double z) {
        rz.setAngle(z);
    }

    /**
     * 设置缩放因子。
     *
     * @param scaleFactor 缩放因子
     */
    public void setScale(double scaleFactor) {
        s.setX(scaleFactor);
        s.setY(scaleFactor);
        s.setZ(scaleFactor);
    }

    public void setSx(double x) {
        s.setX(x);
    }

    public void setSy(double y) {
        s.setY(y);
    }

    public void setSz(double z) {
        s.setZ(z);
    }

    /**
     * 设置旋转的中心点。
     *
     * @param x 中心点的 x 坐标
     * @param y 中心点的 y 坐标
     * @param z 中心点的 z 坐标
     */
    public void setPivot(double x, double y, double z) {
        pivot.setX(x);
        pivot.setY(y);
        pivot.setZ(z);
        inversePivot.setX(-x);
        inversePivot.setY(-y);
        inversePivot.setZ(-z);
    }

    /**
     * 重置所有变换参数为默认值。
     */
    public void reset() {
        translate.setX(0.0);
        translate.setY(0.0);
        translate.setZ(0.0);
        rx.setAngle(0.0);
        ry.setAngle(0.0);
        rz.setAngle(0.0);
        s.setX(1.0);
        s.setY(1.0);
        s.setZ(1.0);
        pivot.setX(0.0);
        pivot.setY(0.0);
        pivot.setZ(0.0);
        inversePivot.setX(0.0);
        inversePivot.setY(0.0);
        inversePivot.setZ(0.0);
    }

    /**
     * 重置平移、缩放和旋转中心点。
     */
    public void resetTSP() {
        translate.setX(0.0);
        translate.setY(0.0);
        translate.setZ(0.0);
        s.setX(1.0);
        s.setY(1.0);
        s.setZ(1.0);
        pivot.setX(0.0);
        pivot.setY(0.0);
        pivot.setZ(0.0);
        inversePivot.setX(0.0);
        inversePivot.setY(0.0);
        inversePivot.setZ(0.0);
    }

    /**
     * 打印当前变换参数的调试信息。
     */
    public String toString() {
        return "t = (" +
                translate.getX() + ", " +
                translate.getY() + ", " +
                translate.getZ() + ")  " +
                "r = (" +
                rx.getAngle() + ", " +
                ry.getAngle() + ", " +
                rz.getAngle() + ")  " +
                "s = (" +
                s.getX() + ", " +
                s.getY() + ", " +
                s.getZ() + ")  " +
                "p = (" +
                pivot.getX() + ", " +
                pivot.getY() + ", " +
                pivot.getZ() + ")  " +
                "ip = (" +
                inversePivot.getX() + ", " +
                inversePivot.getY() + ", " +
                inversePivot.getZ() + ")";
    }

    /**
     * 枚举类型 RotateOrder 表示旋转的顺序。
     */
    public enum RotateOrder {
        XYZ, XZY, YXZ, YZX, ZXY, ZYX
    }
}
