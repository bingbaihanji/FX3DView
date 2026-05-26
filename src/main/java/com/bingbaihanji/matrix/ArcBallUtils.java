package com.bingbaihanji.matrix;

import javafx.scene.transform.Affine;

/**
 * ArcBall工具类（实例模式，避免静态可变缓存导致的线程安全问题）
 * <p>
 * 每个 RotationStrategy 实例持有自己的 ArcBallUtils，
 * 消除了静态 arcBall 缓存的竞态条件。
 * </p>
 */
public class ArcBallUtils {

    /**
     * 缓存的ArcBall实例（实例级，非static，线程安全）
     */
    private ArcBall arcBall;

    /**
     * @param width  初始视口宽度
     * @param height 初始视口高度
     */
    public ArcBallUtils(int width, int height) {
        this.arcBall = new ArcBall(width, height);
    }

    // ==================== 静态工具方法（无状态，仅做数据转换） ====================

    /**
     * 从仿射变换矩阵中提取旋转矩阵部分
     *
     * @param affine 仿射变换矩阵，包含旋转、缩放、平移等变换信息
     * @return Matrix3 3x3旋转矩阵，仅包含旋转变换部分
     */
    public static Matrix3 getRotationFromAffine(Affine affine) {
        return new Matrix3(
                affine.getMxx(), affine.getMxy(), affine.getMxz(),
                affine.getMyx(), affine.getMyy(), affine.getMyz(),
                affine.getMzx(), affine.getMzy(), affine.getMzz()
        );
    }

    /**
     * 将3x3旋转矩阵的值设置到仿射变换对象中
     *
     * @param affine         仿射变换对象，用于接收旋转矩阵的值
     * @param rotationMatrix 3x3旋转矩阵，包含旋转变换的数据
     */
    public static void setRotationToAffine(Affine affine, Matrix3 rotationMatrix) {
        affine.setMxx(rotationMatrix.m00);
        affine.setMxy(rotationMatrix.m01);
        affine.setMxz(rotationMatrix.m02);
        affine.setMyx(rotationMatrix.m10);
        affine.setMyy(rotationMatrix.m11);
        affine.setMyz(rotationMatrix.m12);
        affine.setMzx(rotationMatrix.m20);
        affine.setMzy(rotationMatrix.m21);
        affine.setMzz(rotationMatrix.m22);
    }

    // ==================== 实例方法（持有 ArcBall 状态） ====================

    /**
     * 计算弧球旋转矩阵（返回新矩阵）
     *
     * @param width  视口宽度
     * @param height 视口高度
     * @param x1     起始点x坐标
     * @param y1     起始点y坐标
     * @param x2     终点x坐标
     * @param y2     终点y坐标
     * @return 表示旋转变换的3x3矩阵
     */
    public Matrix3 getArcBallRotationMatrix(int width, int height,
                                            double x1, double y1,
                                            double x2, double y2) {
        ensureArcBallSize(width, height);
        Vector3 v1 = arcBall.mapToSphere(x1, y1);
        Vector3 v2 = arcBall.mapToSphere(x2, y2);

        Vector3 axis = arcBall.computeRotationAxis(v1, v2);
        double angle = -arcBall.computeRotationAngle(v1, v2);

        return arcBall.generateRotationMatrix(axis, angle);
    }

    /**
     * 计算ArcBall旋转矩阵（原地操作，结果填充到result）
     *
     * @param width  窗口宽度
     * @param height 窗口高度
     * @param x1     起始点x坐标
     * @param y1     起始点y坐标
     * @param x2     终止点x坐标
     * @param y2     终止点y坐标
     * @param result 存储计算结果的3x3矩阵
     */
    public void getArcBallRotationMatrix(int width, int height,
                                         double x1, double y1,
                                         double x2, double y2,
                                         Matrix3 result) {
        ensureArcBallSize(width, height);
        arcBall.computeRotationMatrix(x1, y1, x2, y2, result);
    }

    /**
     * 当窗口尺寸变化时重建ArcBall
     */
    private void ensureArcBallSize(int width, int height) {
        if (arcBall.width != width || arcBall.height != height) {
            arcBall = new ArcBall(width, height);
        }
    }
}
