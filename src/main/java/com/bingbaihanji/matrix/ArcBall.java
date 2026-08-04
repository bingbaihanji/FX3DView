package com.bingbaihanji.matrix;

/**
 * ArcBall算法
 */
public class ArcBall {

    /**
     * 定义极小值,防止浮点数精度问题导致的除零异常
     */
    protected static final double EPS = 1e-8;

    /**
     * 窗口宽度
     */
    public final int width;

    /**
     * 窗口高度
     */
    public final int height;

    /**
     * 复用临时对象减少GC(子类 QuaternionArcBall 可复用)
     */
    protected final Vector3 tempVec1 = new Vector3(0, 0, 0);

    protected final Vector3 tempVec2 = new Vector3(0, 0, 0);

    protected final Vector3 tempAxis = new Vector3(0, 0, 0);

    /**
     * 构造函数,初始化窗口尺寸.
     */
    public ArcBall(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width 和 height 必须为正数.");
        }
        this.width = width;
        this.height = height;
    }

    /**
     * 将屏幕坐标映射到单位球面
     *
     * @param x 屏幕x坐标
     * @param y 屏幕y坐标
     * @return 映射到单位球面上的三维向量
     */
    public Vector3 mapToSphere(double x, double y) {
        // 将屏幕坐标归一化到[-1, 1]范围
        double xNorm = (2.0 * x - width) / width;
        double yNorm = -((2.0 * y - height) / height);

        // 计算归一化坐标的平方长度
        double lengthSquared = xNorm * xNorm + yNorm * yNorm;

        // 根据平方长度判断是否在单位圆内
        if (lengthSquared > 1.0 - EPS) {
            // 超出单位圆时,将向量投影到单位球面上
            double length = Math.sqrt(lengthSquared);
            return new Vector3(xNorm / length, yNorm / length, EPS);
        } else {
            // 在单位圆内时,计算z坐标使向量位于单位球面上
            double z = Math.sqrt(Math.max(EPS, 1.0 - lengthSquared));
            return new Vector3(xNorm, yNorm, z);
        }
    }


    /**
     * 将二维平面坐标映射到三维单位球面上(复用向量对象)
     *
     * @param x      平面x坐标
     * @param y      平面y坐标
     * @param result 存储映射结果的三维向量
     */
    public void mapToSphere(double x, double y, Vector3 result) {
        // 将平面坐标归一化到[-1, 1]范围
        double xNorm = (2.0 * x - width) / width;
        double yNorm = -((2.0 * y - height) / height);

        // 计算归一化坐标的平方长度并计算Z坐标(确保在单位球面上)
        double lengthSquared = xNorm * xNorm + yNorm * yNorm;

        // 如果点在单位圆外,则进行球面投影
        if (lengthSquared > 1.0 - EPS) {
            double length = Math.sqrt(lengthSquared);
            result.x = xNorm / length;
            result.y = yNorm / length;
            result.z = EPS;
        } else {
            // 如果点在单位圆内,则计算对应的z坐标
            result.x = xNorm;
            result.y = yNorm;
            result.z = Math.sqrt(Math.max(EPS, 1.0 - lengthSquared));
        }
    }


    /**
     * 计算旋转轴(利用Vector3叉积+归一化)
     *
     * @param v1 第一个向量
     * @param v2 第二个向量
     * @return 归一化后的旋转轴向量
     */
    public Vector3 computeRotationAxis(Vector3 v1, Vector3 v2) {
        // 计算两个向量的叉积作为旋转轴
        Vector3 cross = v1.cross(v2);
        if (cross.lengthSquared() < EPS) {
            cross = new Vector3(EPS, 0, 0);
        }
        return cross.normalize();
    }

    /**
     * 计算旋转轴(复用向量对象)
     */
    public void computeRotationAxis(Vector3 v1, Vector3 v2, Vector3 result) {
        v1.cross(v2, result);

        if (result.lengthSquared() < EPS) {
            result.x = EPS;
            result.y = 0;
            result.z = 0;
        }
        result.normalizeLocal();
    }


    /**
     * 点积计算两个三维向量之间的夹角
     *
     * @param v1 第一个向量
     * @param v2 第二个向量
     * @return 两个向量之间的夹角(弧度)
     */
    public double computeRotationAngle(Vector3 v1, Vector3 v2) {
        // 计算两个向量的点积
        double dotProduct = v1.dot(v2);
        // 将点积限制在[-1, 1]范围内,避免由于浮点数精度问题导致Math.acos函数异常
        dotProduct = Math.max(-1.0, Math.min(1.0, dotProduct));
        // 使用反余弦函数计算夹角
        return Math.acos(dotProduct);
    }


    /**
     * 生成旋转矩阵(返回自定义Matrix3)
     */
    public Matrix3 generateRotationMatrix(Vector3 axis, double angle) {
        return Matrix3.rotation(axis, angle);
    }

    /**
     * 生成旋转矩阵(直接填充到现有矩阵)
     */
    public void generateRotationMatrix(Vector3 axis, double angle, Matrix3 result) {
        Matrix3.rotation(axis, angle, result);
    }

    /**
     * 从旋转矩阵中提取欧拉角
     *
     * @param rotationMatrix 3x3旋转矩阵
     * @return 包含三个欧拉角的数组,顺序为[xAngle, yAngle, zAngle],单位为度
     */
    public double[] extractEulerAngles(Matrix3 rotationMatrix) {
        double xAngle, yAngle, zAngle;

        // 计算绕Y轴的旋转角度
        yAngle = Math.asin(-rotationMatrix.m20);

        // 根据万向节锁条件分别计算绕X轴和Z轴的旋转角度
        if (Math.abs(Math.cos(yAngle)) > 1e-6) {
            xAngle = Math.atan2(rotationMatrix.m21, rotationMatrix.m22);
            zAngle = Math.atan2(rotationMatrix.m10, rotationMatrix.m00);
        } else {
            // 处理万向节锁特殊情况
            xAngle = 0;
            zAngle = Math.atan2(-rotationMatrix.m01, rotationMatrix.m11);
        }
        // 将弧度转换为角度并返回
        return new double[]{
                Math.toDegrees(xAngle),
                Math.toDegrees(yAngle),
                Math.toDegrees(zAngle)
        };
    }


    /**
     * 计算两点之间的旋转矩阵
     *
     * @param x1     第一个点的x坐标
     * @param y1     第一个点的y坐标
     * @param x2     第二个点的x坐标
     * @param y2     第二个点的y坐标
     * @param result 存储计算结果的3x3矩阵
     */
    public void computeRotationMatrix(double x1, double y1, double x2, double y2, Matrix3 result) {
        // 将二维坐标映射到球面上获取三维向量
        mapToSphere(x1, y1, tempVec1);
        mapToSphere(x2, y2, tempVec2);

        // 计算两个向量之间的旋转轴和旋转角度
        computeRotationAxis(tempVec1, tempVec2, tempAxis);
        double angle = -computeRotationAngle(tempVec1, tempVec2);

        // 根据旋转轴和角度生成最终的旋转矩阵
        generateRotationMatrix(tempAxis, angle, result);
    }

}