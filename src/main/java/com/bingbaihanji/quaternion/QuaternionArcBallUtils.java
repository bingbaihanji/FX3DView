package com.bingbaihanji.quaternion;

import javafx.scene.transform.Affine;

/**
 * 四元数ArcBall工具类(实例模式,避免静态可变缓存导致的线程安全问题)
 * <p>
 * 提供与JavaFX Affine变换的集成,支持高性能的3D旋转操作.
 * 每个 RotationStrategy 实例持有自己的 QuaternionArcBallUtils,
 * 消除了静态 arcBall 缓存的竞态条件.
 * </p>
 */
public class QuaternionArcBallUtils {

    /**
     * 缓存的ArcBall实例(实例级,非static,线程安全)
     */
    private QuaternionArcBall arcBall;

    /**
     * @param width  初始视口宽度
     * @param height 初始视口高度
     */
    public QuaternionArcBallUtils(int width, int height) {
        this.arcBall = new QuaternionArcBall(width, height);
    }

    // ==================== 静态工具方法(无状态,仅做数据转换) ====================

    /**
     * 从仿射变换中提取旋转四元数
     */
    public static Quaternion getRotationFromAffine(Affine affine) {
        if (affine == null) {
            return Quaternion.identity();
        }

        double[][] matrix = new double[][]{
                {affine.getMxx(), affine.getMxy(), affine.getMxz()},
                {affine.getMyx(), affine.getMyy(), affine.getMyz()},
                {affine.getMzx(), affine.getMzy(), affine.getMzz()}
        };

        return Quaternion.fromMatrix3x3(matrix);
    }

    /**
     * 将四元数旋转设置到仿射变换中
     */
    public static void setRotationToAffine(Affine affine, Quaternion rotation) {
        if (affine == null || rotation == null) {
            return;
        }

        double[][] matrix = rotation.getMatrix3x3();
        affine.setMxx(matrix[0][0]);
        affine.setMxy(matrix[0][1]);
        affine.setMxz(matrix[0][2]);

        affine.setMyx(matrix[1][0]);
        affine.setMyy(matrix[1][1]);
        affine.setMyz(matrix[1][2]);

        affine.setMzx(matrix[2][0]);
        affine.setMzy(matrix[2][1]);
        affine.setMzz(matrix[2][2]);
    }

    // ==================== 实例方法(持有 ArcBall 状态) ====================

    /**
     * 计算ArcBall旋转四元数(返回新四元数)
     */
    public Quaternion getArcBallRotationQuaternion(int width, int height,
                                                   double x1, double y1,
                                                   double x2, double y2) {
        ensureArcBallSize(width, height);

        Quaternion result = new Quaternion();
        arcBall.computeRotationQuaternion(x1, y1, x2, y2, result);
        return result;
    }

    /**
     * 计算ArcBall旋转四元数(原地操作,避免分配新对象)
     */
    public void getArcBallRotationQuaternion(int width, int height,
                                             double x1, double y1,
                                             double x2, double y2,
                                             Quaternion result) {
        ensureArcBallSize(width, height);
        arcBall.computeRotationQuaternion(x1, y1, x2, y2, result);
    }

    /**
     * 当窗口尺寸变化时重建ArcBall
     */
    private void ensureArcBallSize(int width, int height) {
        if (arcBall.width != width || arcBall.height != height) {
            arcBall = new QuaternionArcBall(width, height);
        }
    }
}
