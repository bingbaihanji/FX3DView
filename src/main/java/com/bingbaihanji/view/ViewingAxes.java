package com.bingbaihanji.view;

import javafx.geometry.Point3D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.scene.transform.Transform;

/**
 * ViewingAxes 用于在 JavaFX 场景中绘制三维坐标轴的 2D 表示，
 * 并在轴的外侧绘制由三条椭圆虚线组成的"球体轮廓"（投影后的圆 -> 椭圆）。
 * 优化点：修复标签消失问题、实现颜色与坐标轴匹配、所有椭圆使用暗色虚线。
 * <p>
 * 设计要点：
 * - 在世界坐标系中以原点为中心建立单位圆（半径 = axisLength），分别位于三个与坐标轴垂直的平面上（XY、XZ、YZ 平面）。
 * - 将这些圆上的若干采样点用当前视图变换投影到屏幕，连线得到投影椭圆，虚线绘制。
 * - 椭圆与坐标轴"内切"（因为圆半径就是轴的一半长度），并随视角正确变形。
 */
public final class ViewingAxes {
    // ===================== 常量定义（统一管理，便于修改） =====================
    /**
     * 坐标轴边距（用于计算轴长度）
     */
    private static final double BORDER_SIZE = 5.0;
    /**
     * 字体大小
     */
    private static final double FONT_SIZE = 14.0;
    /**
     * 用于采样投影圆的分段数（越大越平滑）
     */
    private static final int CIRCLE_SEGMENTS = 128;
    /**
     * 标签偏移量（避免与轴线重叠）
     */
    private static final double LABEL_OFFSET = 12.0;
    /**
     * 球体晕圈放大系数
     */
    private static final double GLOW_SCALE_1 = 1.05;
    private static final double GLOW_SCALE_2 = 1.2;
    /**
     * 轴长度占半画布的缩放因子（留出边距）
     */
    private static final double AXIS_LENGTH_FACTOR = 0.9;

    // 坐标轴颜色常量（核心色调）
    private static final Color X_AXIS_COLOR = Color.ORANGERED;
    private static final Color Y_AXIS_COLOR = Color.LIME;
    private static final Color Z_AXIS_COLOR = Color.DODGERBLUE;

    // 椭圆统一使用暗色虚线样式
    private static final double ELLIPSE_ALPHA = 0.5;
    private static final double ELLIPSE_BRIGHTNESS = 0.7;
    private static final double[] DASH_PATTERN = {5, 5};

    // 球体晕圈透明度
    private static final double GLOW_ALPHA_1 = 0.06;
    private static final double GLOW_ALPHA_2 = 0.03;

    // ===================== 成员变量 =====================
    /**
     * 绘制用画布
     */
    private final Canvas canvas;
    /**
     * 当前视图变换（取反后用于绘制）
     */
    private final Affine currentViewTransform = new Affine();
    /**
     * 坐标轴文字字体
     */
    private final Font axisFont;
    /**
     * 预分配投影点数组（复用以避免每帧GC）
     */
    private final double[] circleXs = new double[CIRCLE_SEGMENTS + 1];
    private final double[] circleYs = new double[CIRCLE_SEGMENTS + 1];
    /**
     * 坐标轴端点（世界坐标）
     */
    private Point3D xNegative, xPositive;
    private Point3D yNegative, yPositive;
    private Point3D zNegative, zPositive;
    /**
     * 坐标轴端点（屏幕坐标）
     */
    private Point3D xNegativeScreen, xPositiveScreen;
    private Point3D yNegativeScreen, yPositiveScreen;
    private Point3D zNegativeScreen, zPositiveScreen;
    /**
     * 坐标轴中心点（屏幕坐标）
     */
    private Point3D centerScreen;
    /**
     * 轴长度（半轴，从中心到正端点的长度，世界坐标单位）
     */
    private double axisLength = 0.0;

    // ===================== 构造方法与基础方法 =====================

    /**
     * 创建 ViewingAxes
     *
     * @param size 坐标轴画布大小（正方形边长）
     */
    public ViewingAxes(double size) {
        this.axisFont = new Font("Arial", FONT_SIZE);
        this.canvas = new Canvas(size, size);
        setSize(size);
    }

    /**
     * 获取画布
     */
    public Canvas getCanvas() {
        return this.canvas;
    }

    /**
     * 设置画布尺寸并初始化坐标轴位置
     */
    public void setSize(double size) {
        // 略宽一点防止文字被裁切
        canvas.setWidth(size * 1.1);
        canvas.setHeight(size);
        centerScreen = new Point3D(size / 2.0, size / 2.0, 0.0);
        setupAxes(size);
        drawAxes();
    }

    /**
     * 根据新的 3D 变换更新坐标轴
     *
     * @param transform 当前场景的 3D 变换
     */
    public void updateAxes(Transform transform) {
        try {
            // 使用视图变换的逆矩阵，保证坐标轴方向与模型一致
            currentViewTransform.setToTransform(new Affine(transform).createInverse());
        } catch (NonInvertibleTransformException e) {
            e.printStackTrace();
            // 如果不可逆，保留旧的变换（尽量不要崩溃）
        }
        drawAxes();
    }

    /**
     * 重新绘制坐标轴（使用当前变换）
     */
    public void redrawAxes() {
        drawAxes();
    }

    // ===================== 初始化与核心绘制 =====================

    /**
     * 初始化坐标轴端点（世界坐标）
     */
    private void setupAxes(double size) {
        // axisLength 是从中心到正端点的长度（世界坐标）
        axisLength = (size / 2.0 - BORDER_SIZE) * AXIS_LENGTH_FACTOR;
        xNegative = new Point3D(-axisLength, 0, 0);
        xPositive = new Point3D(axisLength, 0, 0);
        yNegative = new Point3D(0, -axisLength, 0);
        yPositive = new Point3D(0, axisLength, 0);
        zNegative = new Point3D(0, 0, -axisLength);
        zPositive = new Point3D(0, 0, axisLength);
    }

    /**
     * 绘制三维坐标轴与包裹其外的"球体"椭圆轮廓（核心方法）
     */
    private void drawAxes() {
        // 设置变换的平移：将3D原点映射到画布中心
        currentViewTransform.setTx(centerScreen.getX());
        currentViewTransform.setTy(centerScreen.getY());
        currentViewTransform.setTz(0.0);

        // 将世界坐标轴端点转换为屏幕坐标（用于绘制轴线与标签）
        xNegativeScreen = currentViewTransform.transform(xNegative);
        xPositiveScreen = currentViewTransform.transform(xPositive);
        yNegativeScreen = currentViewTransform.transform(yNegative);
        yPositiveScreen = currentViewTransform.transform(yPositive);
        zNegativeScreen = currentViewTransform.transform(zNegative);
        zPositiveScreen = currentViewTransform.transform(zPositive);

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFont(axisFont);
        // 清空画布
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setLineCap(StrokeLineCap.ROUND);

        // 1) 先画球体晕圈（背景层）
        drawSphereGlow(gc);

        // 2) 再画投影的椭圆（球的等投影轮廓）- 使用暗色虚线
        drawProjectedCircle(gc, Axis.X, X_AXIS_COLOR);
        drawProjectedCircle(gc, Axis.Y, Y_AXIS_COLOR);
        drawProjectedCircle(gc, Axis.Z, Z_AXIS_COLOR);

        // 3) 最后画三条轴线与标签（保证轴在线条之上）
        gc.setLineWidth(2.0); // 坐标轴加粗
        drawAxis(gc, xNegativeScreen, xPositiveScreen, X_AXIS_COLOR, "X");
        drawAxis(gc, yNegativeScreen, yPositiveScreen, Y_AXIS_COLOR, "Y");
        drawAxis(gc, zNegativeScreen, zPositiveScreen, Z_AXIS_COLOR, "Z");
    }

    /**
     * 绘制单条轴线与标签（修复标签消失问题）
     *
     * @param gc    绘图上下文
     * @param start 负方向端点（屏幕坐标）
     * @param end   正方向端点（屏幕坐标）
     * @param color 轴颜色
     * @param label 坐标轴名称
     */
    private void drawAxis(GraphicsContext gc, Point3D start, Point3D end, Color color, String label) {
        // 绘制轴线
        gc.setStroke(color);
        gc.strokeLine(start.getX(), start.getY(), centerScreen.getX(), centerScreen.getY());
        gc.strokeLine(centerScreen.getX(), centerScreen.getY(), end.getX(), end.getY());

        // 绘制标签：使用方向向量计算偏移，确保标签在延长线上
        gc.setFill(color);

        // 负端标签位置
        double dxStart = start.getX() - centerScreen.getX();
        double dyStart = start.getY() - centerScreen.getY();
        double lenStart = Math.sqrt(dxStart * dxStart + dyStart * dyStart);
        if (lenStart > 0) {
            double dirXStart = dxStart / lenStart;
            double dirYStart = dyStart / lenStart;
            gc.fillText("- " + label,
                    start.getX() + dirXStart * LABEL_OFFSET,
                    start.getY() + dirYStart * LABEL_OFFSET);
        }

        // 正端标签位置
        double dxEnd = end.getX() - centerScreen.getX();
        double dyEnd = end.getY() - centerScreen.getY();
        double lenEnd = Math.sqrt(dxEnd * dxEnd + dyEnd * dyEnd);
        if (lenEnd > 0) {
            double dirXEnd = dxEnd / lenEnd;
            double dirYEnd = dyEnd / lenEnd;
            gc.fillText("+ " + label,
                    end.getX() + dirXEnd * LABEL_OFFSET,
                    end.getY() + dirYEnd * LABEL_OFFSET);
        }
    }

    /**
     * 绘制投影圆（椭圆）：手动计算仿射变换，避免Point3D分配
     * <p>
     * 所有椭圆均使用暗色虚线样式
     * </p>
     *
     * @param gc        绘图上下文
     * @param axis      对应坐标轴（决定椭圆平面与颜色）
     * @param axisColor 坐标轴主色调（用于匹配椭圆颜色）
     */
    private void drawProjectedCircle(GraphicsContext gc, Axis axis, Color axisColor) {
        final double twoPI = Math.PI * 2.0;

        // 缓存变换矩阵元素，避免每点重复getter调用
        double mxx = currentViewTransform.getMxx();
        double mxy = currentViewTransform.getMxy();
        double mxz = currentViewTransform.getMxz();
        double tx = currentViewTransform.getTx();
        double myx = currentViewTransform.getMyx();
        double myy = currentViewTransform.getMyy();
        double myz = currentViewTransform.getMyz();
        double ty = currentViewTransform.getTy();

        for (int i = 0; i <= CIRCLE_SEGMENTS; i++) {
            double t = (i / (double) CIRCLE_SEGMENTS) * twoPI;
            double cosT = Math.cos(t) * axisLength;
            double sinT = Math.sin(t) * axisLength;

            // 生成世界坐标（内联原generateCirclePoint，避免Point3D分配）
            double worldX, worldY, worldZ;
            switch (axis) {
                case X:
                    worldX = 0;
                    worldY = cosT;
                    worldZ = sinT;
                    break; // YZ平面
                case Y:
                    worldX = cosT;
                    worldY = 0;
                    worldZ = sinT;
                    break; // XZ平面
                case Z:
                    worldX = cosT;
                    worldY = sinT;
                    worldZ = 0;
                    break; // XY平面
                default:
                    worldX = 0;
                    worldY = 0;
                    worldZ = 0;
            }

            // 手动矩阵×向量变换，避免Affine.transform()的Point3D分配
            circleXs[i] = mxx * worldX + mxy * worldY + mxz * worldZ + tx;
            circleYs[i] = myx * worldX + myy * worldY + myz * worldZ + ty;
        }

        // 设置统一的暗色虚线样式
        gc.setLineDashes(DASH_PATTERN);
        gc.setLineWidth(1.5);
        gc.setStroke(axisColor.deriveColor(0, 1, ELLIPSE_BRIGHTNESS, ELLIPSE_ALPHA));
        gc.strokePolyline(circleXs, circleYs, CIRCLE_SEGMENTS + 1);

        // 恢复实线
        gc.setLineDashes(null);
    }

    /**
     * 绘制球体柔和晕圈（背景层）
     *
     * @param gc 绘图上下文
     */
    private void drawSphereGlow(GraphicsContext gc) {
        double cx = centerScreen.getX();
        double cy = centerScreen.getY();
        double r1 = axisLength * GLOW_SCALE_1;
        double r2 = axisLength * GLOW_SCALE_2;

        // 第一层晕圈：略大，淡色
        gc.setFill(Color.GRAY.deriveColor(0, 1, 1, GLOW_ALPHA_1));
        gc.fillOval(cx - r1, cy - r1, r1 * 2, r1 * 2);

        // 第二层晕圈：更大，更淡
        gc.setFill(Color.GRAY.deriveColor(0, 1, 1, GLOW_ALPHA_2));
        gc.fillOval(cx - r2, cy - r2, r2 * 2, r2 * 2);
    }

    // ===================== 内部枚举 =====================

    /**
     * 简单枚举：指定哪个轴的平面用来绘制圆
     */
    private enum Axis {
        X, Y, Z
    }
}