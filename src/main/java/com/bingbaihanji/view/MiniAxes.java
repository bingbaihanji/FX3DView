package com.bingbaihanji.view;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;

/**
 * 迷你坐标轴指示器，用于多视口右下角显示世界坐标轴方向。
 * <p>
 * 在小型 Canvas 上绘制 RGB 三色短线：
 * X 轴红色、Y 轴绿色、Z 轴蓝色，从中心向外延伸。
 * 通过相机变换的逆矩阵将世界坐标轴方向映射到屏幕。
 * </p>
 *
 * @author bingbaihanji
 */
public class MiniAxes {

    private static final double AXIS_LEN = 16.0;
    private static final double SIZE = 40.0;
    private static final double CX = SIZE / 2.0;
    private static final double CY = SIZE / 2.0;

    private final Canvas canvas;
    /**
     * 当前视图变换（取反后用于绘制），中心已平移到画布中心
     */
    private final Affine viewTransform = new Affine();

    public MiniAxes() {
        canvas = new Canvas(SIZE, SIZE);
        canvas.setMouseTransparent(true);
        drawAxes();
    }

    public Canvas getCanvas() {
        return canvas;
    }

    /**
     * 根据相机旋转 Affine 更新迷你轴方向（视口 0 用）。
     * <p>
     * 传入的 affine 是旋转策略的旋转矩阵（世界→模型旋转）。
     * 对其取逆得到视图变换，将世界坐标轴映射到屏幕空间。
     * </p>
     */
    public void updateFromAffine(Affine affine) {
        try {
            viewTransform.setToTransform(affine.createInverse());
        } catch (NonInvertibleTransformException e) {
            return;
        }
        // 去掉平移部分，仅保留旋转（迷你轴只需方向）
        viewTransform.setTx(CX);
        viewTransform.setTy(CY);
        viewTransform.setTz(0);
        drawAxes();
    }

    /**
     * 设置固定旋转角度（视口 1-3 用）。
     *
     * @param xAngleDeg 绕 X 轴旋转角度（度）
     * @param yAngleDeg 绕 Y 轴旋转角度（度）
     */
    public void setFixedAngles(double xAngleDeg, double yAngleDeg) {
        viewTransform.setToIdentity();
        // 相机变换顺序：方向旋转 → Z轴180度（补偿JavaFX的Y轴向下坐标系）
        viewTransform.appendRotation(yAngleDeg, 0, 0, 0, 0, 1, 0);
        viewTransform.appendRotation(xAngleDeg, 0, 0, 0, 1, 0, 0);
        viewTransform.appendRotation(180, 0, 0, 0, 0, 0, 1);
        viewTransform.setTx(CX);
        viewTransform.setTy(CY);
        drawAxes();
    }

    /**
     * 在 Canvas 上绘制 RGB 三色迷你坐标轴
     */
    private void drawAxes() {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, SIZE, SIZE);

        // 绘制半透明背景圆点
        gc.setFill(Color.rgb(255, 255, 255, 0.3));
        gc.fillOval(CX - 3, CY - 3, 6, 6);

        // 投影三个世界坐标轴方向到屏幕并绘制
        drawAxisLine(gc, AXIS_LEN, 0, 0, Color.ORANGERED);   // X 轴 → 红
        drawAxisLine(gc, 0, AXIS_LEN, 0, Color.LIME);         // Y 轴 → 绿
        drawAxisLine(gc, 0, 0, AXIS_LEN, Color.DODGERBLUE);   // Z 轴 → 蓝
    }

    /**
     * 将世界坐标方向向量通过视图变换投影到屏幕，绘制从中心出发的线段。
     *
     * @param gc    绘图上下文
     * @param wx    世界坐标 X 分量
     * @param wy    世界坐标 Y 分量
     * @param wz    世界坐标 Z 分量
     * @param color 线条颜色
     */
    private void drawAxisLine(GraphicsContext gc, double wx, double wy, double wz, Color color) {
        // 手动矩阵乘法，避免 Point3D 分配
        double sx = viewTransform.getMxx() * wx + viewTransform.getMxy() * wy
                + viewTransform.getMxz() * wz + viewTransform.getTx();
        double sy = viewTransform.getMyx() * wx + viewTransform.getMyy() * wy
                + viewTransform.getMyz() * wz + viewTransform.getTy();

        // 计算从中心到投影点的方向，限制长度
        double dx = sx - CX;
        double dy = sy - CY;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5) return; // 近乎垂直于屏幕，不可见

        double scale = AXIS_LEN / len;
        double ex = CX + dx * scale;
        double ey = CY + dy * scale;

        gc.setStroke(color);
        gc.setLineWidth(2.0);
        gc.strokeLine(CX, CY, ex, ey);
    }
}
