package com.bingbaihanji.quaternion;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * 四元数类,用于表示3D空间中的旋转:  q = w + xi + yj + zk
 * 四元数的数学原理较为复杂(涉及复数),因此不做过多深入讲解.
 * <p>
 * 功能特性:
 * - 支持欧拉角转换(XYZ顺序:横滚X -> 俯仰Y -> 偏航Z)
 * - 列主序矩阵格式(兼容 OpenGL / JavaFX Affine)
 * - 完整的四元数运算(乘法,插值,归一化等)
 * - 优化的缓存机制和数值稳定性处理
 * <p>
 * 设计原则:
 * - 保持API向后兼容
 * - 自动归一化确保数值稳定性
 * - 懒加载缓存提升性能
 * - 完善的错误处理和边界条件检查
 * <p>
 * 应用场景:需要旋转插值时使用四元数,需要将旋转应用到对象时转换为矩阵.
 * <p>
 * 四元数快速入门视频:https://www.youtube.com/watch?v=SCbpxiCN0U0
 * 更详细的讲解视频:https://www.youtube.com/watch?v=fKIss4EV6ME&t=0s
 *
 * @author Karl, bingbaihanji
 */
@Slf4j
@Getter
public class Quaternion {

    /**
     * 矩阵维度:4x4
     */
    private static final int MATRIX_4x4 = 4;

    /**
     * 矩阵维度:3x3
     */
    private static final int MATRIX_3x3 = 3;

    /**
     * 归一化检查的容差阈值
     */
    private static final double NORMALIZE_CHECKSUM_EPSILON = 1e-5;

    /**
     * 向量归一化的容差阈值
     */
    private static final double VECTOR_NORMALIZE_EPSILON = 1e-6;

    /**
     * 通用浮点数比较容差
     */
    private static final double EPS = 1e-6;

    /**
     * SLERP插值的点积阈值
     */
    private static final double SLERP_DOT_THRESHOLD = 0.9995;

    // ============================
    // 成员变量 q = w + xi + yj + zk
    // ============================

    /**
     * 四元数虚部 x 分量
     */
    private double x;

    /**
     * 四元数虚部 y 分量
     */
    private double y;

    /**
     * 四元数虚部 z 分量
     */
    private double z;

    /**
     * 四元数实部 w 分量
     */
    private double w;

    /**
     * 旋转矩阵缓存(4x4列主序),null表示缓存失效
     */
    private double[][] matrixCache4x4 = null;

    /**
     * 旋转矩阵缓存(3x3列主序),null表示缓存失效
     */
    private double[][] matrixCache3x3 = null;

    /**
     * 标记当前四元数是否已归一化
     */
    private boolean normalized = false;

    // ============================
    // 构造方法和工厂方法
    // ============================

    /**
     * 默认构造方法,创建单位四元数
     */
    public Quaternion() {
        setIdentity();
    }

    /**
     * 通过指定分量创建四元数
     *
     * @param x 虚部x分量
     * @param y 虚部y分量
     * @param z 虚部z分量
     * @param w 实部w分量
     */
    public Quaternion(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        this.normalized = false;
        invalidateCache();
    }

    /**
     * 通过4x4旋转矩阵创建四元数(二维数组,列主序)
     *
     * @param matrix 4x4列主序旋转矩阵(二维数组)
     */
    public Quaternion(double[][] matrix) {
        if (!validateMatrix(matrix, MATRIX_4x4)) {
            log.warn("输入矩阵为null或维度不足,使用单位四元数");
            setIdentity();
        } else {
            setFromMatrix4x4(matrix);
        }
    }

    /**
     * 通过3x3旋转矩阵创建四元数(二维数组,列主序)
     *
     * @param matrix 3x3列主序旋转矩阵(二维数组)
     * @param is3x3  是否为3x3矩阵
     */
    public Quaternion(double[][] matrix, boolean is3x3) {
        if (is3x3) {
            if (!validateMatrix(matrix, MATRIX_3x3)) {
                log.warn("输入矩阵为null或维度不足,使用单位四元数");
                setIdentity();
            } else {
                setFromMatrix3x3(matrix);
            }
        } else {
            if (!validateMatrix(matrix, MATRIX_4x4)) {
                log.warn("输入矩阵为null或维度不足,使用单位四元数");
                setIdentity();
            } else {
                setFromMatrix4x4(matrix);
            }
        }
    }

    /**
     * 创建单位四元数
     */
    public static Quaternion identity() {
        return new Quaternion(0.0, 0.0, 0.0, 1.0);
    }

    /**
     * 通过欧拉角创建四元数(XYZ顺序)
     *
     * @param roll  横滚角(绕X轴,弧度)
     * @param pitch 俯仰角(绕Y轴,弧度)
     * @param yaw   偏航角(绕Z轴,弧度)
     * @return 对应的四元数
     */
    public static Quaternion fromEuler(double roll, double pitch, double yaw) {
        double hr = roll * 0.5;
        double hp = pitch * 0.5;
        double hy = yaw * 0.5;

        double cr = Math.cos(hr), sr = Math.sin(hr);
        double cp = Math.cos(hp), sp = Math.sin(hp);
        double cy = Math.cos(hy), sy = Math.sin(hy);

        // 计算四元数分量(XYZ顺序)
        double w = cr * cp * cy + sr * sp * sy;
        double x = sr * cp * cy - cr * sp * sy;
        double y = cr * sp * cy + sr * cp * sy;
        double z = cr * cp * sy - sr * sp * cy;

        Quaternion q = new Quaternion(x, y, z, w);
        q.normalize();
        return q;
    }

    /**
     * 通过轴角创建四元数
     *
     * @param axisX    旋转轴X分量(需要已归一化)
     * @param axisY    旋转轴Y分量(需要已归一化)
     * @param axisZ    旋转轴Z分量(需要已归一化)
     * @param angleRad 旋转角度(弧度)
     * @return 对应的四元数
     */
    public static Quaternion fromAxisAngle(double axisX, double axisY, double axisZ, double angleRad) {
        double halfAngle = angleRad * 0.5;
        double sinHalf = Math.sin(halfAngle);
        double cosHalf = Math.cos(halfAngle);

        double x = axisX * sinHalf;
        double y = axisY * sinHalf;
        double z = axisZ * sinHalf;
        double w = cosHalf;

        Quaternion q = new Quaternion(x, y, z, w);
        q.normalize();
        return q;
    }

    /**
     * 从4x4列主序矩阵创建四元数(二维数组)
     *
     * @param matrix 4x4列主序旋转矩阵(二维数组)
     * @return 对应的四元数,失败返回null
     */
    public static Quaternion fromMatrix(double[][] matrix) {
        if (!validateMatrix(matrix, MATRIX_4x4)) {
            log.warn("输入矩阵为null或维度不足");
            return null;
        }
        return new Quaternion(matrix);
    }

    /**
     * 从3x3列主序矩阵创建四元数(二维数组)
     *
     * @param matrix 3x3列主序旋转矩阵(二维数组)
     * @return 对应的四元数,失败返回null
     */
    public static Quaternion fromMatrix3x3(double[][] matrix) {
        if (!validateMatrix(matrix, MATRIX_3x3)) {
            log.warn("输入矩阵为null或维度不足");
            return null;
        }
        return new Quaternion(matrix, true);
    }

    // ============================
    // 工具方法
    // ============================

    /**
     * 验证矩阵维度是否有效
     */
    private static boolean validateMatrix(double[][] matrix, int expectedSize) {
        if (matrix == null || matrix.length < expectedSize) {
            return false;
        }
        for (int i = 0; i < expectedSize; i++) {
            if (matrix[i] == null || matrix[i].length < expectedSize) {
                return false;
            }
        }
        return true;
    }

    /**
     * 浮点数近似比较
     */
    private static boolean approxEqual(double a, double b) {
        return Math.abs(a - b) < NORMALIZE_CHECKSUM_EPSILON;
    }

    /**
     * 四元数乘法:q = q1 × q2
     * 注意:在列主序和列向量约定下,表示先应用q2旋转,再应用q1旋转
     */
    public static Quaternion multiply(Quaternion q1, Quaternion q2) {
        if (q1 == null || q2 == null) {
            log.warn("四元数乘法参数为null,返回单位四元数");
            return new Quaternion();
        }

        double a = q1.w, b = q1.x, c = q1.y, d = q1.z;
        double e = q2.w, f = q2.x, g = q2.y, h = q2.z;

        // 哈密尔顿积计算
        double w = a * e - b * f - c * g - d * h;
        double x = a * f + b * e + c * h - d * g;
        double y = a * g - b * h + c * e + d * f;
        double z = a * h + b * g - c * f + d * e;

        return new Quaternion(x, y, z, w);
    }

    /**
     * 四元数乘法(原地结果,避免分配新对象):result = q1 × q2
     */
    public static void multiply(Quaternion q1, Quaternion q2, Quaternion result) {
        if (q1 == null || q2 == null || result == null) {
            log.warn("四元数乘法参数为null");
            return;
        }

        double a = q1.w, b = q1.x, c = q1.y, d = q1.z;
        double e = q2.w, f = q2.x, g = q2.y, h = q2.z;

        result.w = a * e - b * f - c * g - d * h;
        result.x = a * f + b * e + c * h - d * g;
        result.y = a * g - b * h + c * e + d * f;
        result.z = a * h + b * g - c * f + d * e;
        result.normalized = false;
        result.invalidateCache();
    }

    /**
     * 计算两个四元数的点积
     */
    public static double dot(Quaternion a, Quaternion b) {
        if (a == null || b == null) {
            return 0.0;
        }
        return a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
    }

    /**
     * NLERP归一化线性插值(原地操作)
     *
     * @param result 存储结果的四元数
     * @param a      起始四元数
     * @param b      结束四元数
     * @param blend  插值因子 [0, 1]
     */
    public static void interpolate(Quaternion result, Quaternion a, Quaternion b, double blend) {
        if (result == null || a == null || b == null) {
            log.warn("四元数插值参数为null");
            return;
        }

        double dotProduct = dot(a, b);
        double blendInv = 1.0 - blend;

        // 选择最短路径
        if (dotProduct < 0.0) {
            result.w = blendInv * a.w + blend * -b.w;
            result.x = blendInv * a.x + blend * -b.x;
            result.y = blendInv * a.y + blend * -b.y;
            result.z = blendInv * a.z + blend * -b.z;
        } else {
            result.w = blendInv * a.w + blend * b.w;
            result.x = blendInv * a.x + blend * b.x;
            result.y = blendInv * a.y + blend * b.y;
            result.z = blendInv * a.z + blend * b.z;
        }

        result.normalize();
    }

    /**
     * SLERP球面线性插值
     *
     * @param a 起始四元数
     * @param b 结束四元数
     * @param t 插值因子 [0, 1]
     * @return 插值结果四元数
     */
    public static Quaternion slerp(Quaternion a, Quaternion b, double t) {
        if (a == null || b == null) {
            log.warn("SLERP参数为null,返回单位四元数");
            return new Quaternion();
        }

        double dotProduct = dot(a, b);
        Quaternion bAdjusted = b;

        // 选择最短路径
        if (dotProduct < 0.0) {
            bAdjusted = new Quaternion(-b.x, -b.y, -b.z, -b.w);
            dotProduct = -dotProduct;
        }

        // 如果角度很小,使用NLERP(数值稳定性)
        if (dotProduct > SLERP_DOT_THRESHOLD) {
            return nlerp(a, bAdjusted, t);
        }

        // 标准SLERP计算
        double theta = Math.acos(dotProduct);
        double sinTheta = Math.sin(theta);
        double weightA = Math.sin((1 - t) * theta) / sinTheta;
        double weightB = Math.sin(t * theta) / sinTheta;

        Quaternion result = new Quaternion(
                a.x * weightA + bAdjusted.x * weightB,
                a.y * weightA + bAdjusted.y * weightB,
                a.z * weightA + bAdjusted.z * weightB,
                a.w * weightA + bAdjusted.w * weightB
        );

        result.normalize();
        return result;
    }

    /**
     * NLERP归一化线性插值
     */
    private static Quaternion nlerp(Quaternion a, Quaternion b, double t) {
        Quaternion result = new Quaternion(
                a.x + t * (b.x - a.x),
                a.y + t * (b.y - a.y),
                a.z + t * (b.z - a.z),
                a.w + t * (b.w - a.w)
        );
        result.normalize();
        return result;
    }

    // ============================
    // 矩阵操作相关方法
    // ============================

    /**
     * 创建4x4单位矩阵(列主序)
     */
    public static double[][] createIdentityMatrix4x4() {
        double[][] matrix = new double[4][4];
        setIdentityMatrix4x4(matrix);
        return matrix;
    }

    /**
     * 创建3x3单位矩阵(列主序)
     */
    public static double[][] createIdentityMatrix3x3() {
        double[][] matrix = new double[3][3];
        setIdentityMatrix3x3(matrix);
        return matrix;
    }

    /**
     * 设置4x4单位矩阵(列主序)
     */
    public static void setIdentityMatrix4x4(double[][] m) {
        if (!validateMatrix(m, MATRIX_4x4)) {
            throw new IllegalArgumentException("矩阵维度不足4x4");
        }
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                m[i][j] = 0.0;
            }
        }
        m[0][0] = 1.0;
        m[1][1] = 1.0;
        m[2][2] = 1.0;
        m[3][3] = 1.0;
    }

    /**
     * 设置3x3单位矩阵(列主序)
     */
    public static void setIdentityMatrix3x3(double[][] m) {
        if (!validateMatrix(m, MATRIX_3x3)) {
            throw new IllegalArgumentException("矩阵维度不足3x3");
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                m[i][j] = 0.0;
            }
        }
        m[0][0] = 1.0;
        m[1][1] = 1.0;
        m[2][2] = 1.0;
    }

    /**
     * 归一化3D向量(原地操作)
     */
    public static void normalizeVector(double[] vector) {
        if (vector == null || vector.length < 3) {
            log.warn("向量参数无效");
            return;
        }
        double length = vectorLength(vector);
        if (length <= EPS) {
            log.warn("向量长度为零,无法归一化");
            return;
        }
        vector[0] /= length;
        vector[1] /= length;
        vector[2] /= length;
    }

    /**
     * 计算3D向量长度
     */
    public static double vectorLength(double[] v) {
        if (v == null || v.length < 3) {
            return 0.0;
        }
        return vectorLength(v[0], v[1], v[2]);
    }

    /**
     * 计算3D向量长度
     */
    public static double vectorLength(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    // ============================
    // 矩阵到四元数转换(数值稳定版本)
    // ============================

    /**
     * 从4x4列主序矩阵创建四元数(稳定算法,内部方法)
     */
    private static Quaternion fromMatrix4x4Internal(double[][] matrix) {
        if (!validateMatrix(matrix, MATRIX_4x4)) {
            log.warn("输入矩阵为null或维度不足");
            return null;
        }

        // 提取3x3旋转部分(列主序)
        double m00 = matrix[0][0], m01 = matrix[0][1], m02 = matrix[0][2];
        double m10 = matrix[1][0], m11 = matrix[1][1], m12 = matrix[1][2];
        double m20 = matrix[2][0], m21 = matrix[2][1], m22 = matrix[2][2];

        // 使用稳定的Shoemake算法
        double trace = m00 + m11 + m22;
        Quaternion q = new Quaternion();

        if (trace > 0.0) {
            double s = Math.sqrt(trace + 1.0) * 2.0; // s = 4 * w
            q.w = 0.25 * s;
            q.x = (m21 - m12) / s;
            q.y = (m02 - m20) / s;
            q.z = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2.0; // s = 4 * x
            q.w = (m21 - m12) / s;
            q.x = 0.25 * s;
            q.y = (m01 + m10) / s;
            q.z = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2.0; // s = 4 * y
            q.w = (m02 - m20) / s;
            q.x = (m01 + m10) / s;
            q.y = 0.25 * s;
            q.z = (m12 + m21) / s;
        } else {
            double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2.0; // s = 4 * z
            q.w = (m10 - m01) / s;
            q.x = (m02 + m20) / s;
            q.y = (m12 + m21) / s;
            q.z = 0.25 * s;
        }

        // 归一化并确保符号稳定(w >= 0)
        q.normalize();
        if (q.w < 0.0) {
            q.x = -q.x;
            q.y = -q.y;
            q.z = -q.z;
            q.w = -q.w;
        }

        // 缓存输入矩阵
        q.matrixCache4x4 = copyMatrix(matrix);
        q.matrixCache3x3 = extract3x3Matrix(matrix);

        return q;
    }

    /**
     * 从3x3列主序矩阵创建四元数(内部方法)
     */
    private static Quaternion fromMatrix3x3Internal(double[][] matrix) {
        if (!validateMatrix(matrix, MATRIX_3x3)) {
            log.warn("输入矩阵为null或维度不足");
            return null;
        }

        // 将3x3矩阵扩展为4x4矩阵
        double[][] matrix4x4 = new double[4][4];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                matrix4x4[i][j] = matrix[i][j];
            }
        }
        matrix4x4[0][3] = 0.0;
        matrix4x4[1][3] = 0.0;
        matrix4x4[2][3] = 0.0;
        matrix4x4[3][0] = 0.0;
        matrix4x4[3][1] = 0.0;
        matrix4x4[3][2] = 0.0;
        matrix4x4[3][3] = 1.0;

        return fromMatrix4x4Internal(matrix4x4);
    }

    /**
     * 复制矩阵
     */
    private static double[][] copyMatrix(double[][] source) {
        if (source == null) {
            return null;
        }
        int rows = source.length;
        double[][] dest = new double[rows][];
        for (int i = 0; i < rows; i++) {
            dest[i] = source[i].clone();
        }
        return dest;
    }

    /**
     * 从4x4矩阵中提取3x3部分
     */
    private static double[][] extract3x3Matrix(double[][] matrix4x4) {
        if (!validateMatrix(matrix4x4, MATRIX_4x4)) {
            return null;
        }
        double[][] matrix3x3 = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                matrix3x3[i][j] = matrix4x4[i][j];
            }
        }
        return matrix3x3;
    }

    /**
     * 比较两个四元数是否近似相等
     */
    private static boolean approxEquals(Quaternion q1, Quaternion q2, double epsilon) {
        if (q1 == q2) {
            return true;
        }
        if (q1 == null || q2 == null) {
            return false;
        }
        return Math.abs(q1.x - q2.x) <= epsilon &&
                Math.abs(q1.y - q2.y) <= epsilon &&
                Math.abs(q1.z - q2.z) <= epsilon &&
                Math.abs(q1.w - q2.w) <= epsilon;
    }

    // ============================
    // Setter 方法
    // ============================

    public void setX(double x) {
        this.x = x;
        this.normalized = false;
        invalidateCache();
    }

    public void setY(double y) {
        this.y = y;
        this.normalized = false;
        invalidateCache();
    }

    public void setZ(double z) {
        this.z = z;
        this.normalized = false;
        invalidateCache();
    }

    public void setW(double w) {
        this.w = w;
        this.normalized = false;
        invalidateCache();
    }

    /**
     * 设置四元数的所有分量
     */
    public void set(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
        this.normalized = false;
        invalidateCache();
    }

    /**
     * 使缓存失效(当四元数改变时调用)
     */
    private void invalidateCache() {
        this.matrixCache4x4 = null;
        this.matrixCache3x3 = null;
    }

    // ============================
    // 归一化操作
    // ============================

    /**
     * 设置为单位四元数
     */
    public void setIdentity() {
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
        this.w = 1.0;
        this.normalized = true;
        invalidateCache();
    }

    /**
     * 检查是否为近似单位四元数
     */
    public boolean isIdentity() {
        return approxEqual(x, 0.0) && approxEqual(y, 0.0) &&
                approxEqual(z, 0.0) && approxEqual(w, 1.0);
    }

    /**
     * 归一化当前四元数(原地操作)
     *
     * @return this 用于链式调用
     */
    public Quaternion normalize() {
        if (normalized) {
            return this;
        }

        double normSquared = x * x + y * y + z * z + w * w;

        // 检查是否已经是单位四元数(在容差范围内)
        if (Math.abs(normSquared - 1.0) < NORMALIZE_CHECKSUM_EPSILON) {
            normalized = true;
            return this;
        }

        double norm = Math.sqrt(normSquared);
        if (norm <= EPS) {
            // 模长为零,设置为单位四元数
            log.warn("四元数模长为零,设置为单位四元数");
            setIdentity();
            return this;
        }

        // 归一化计算
        double invNorm = 1.0 / norm;
        x *= invNorm;
        y *= invNorm;
        z *= invNorm;
        w *= invNorm;

        normalized = true;
        invalidateCache(); // 归一化后矩阵缓存失效

        return this;
    }

    /**
     * 返回归一化后的副本(不修改原对象)
     */
    public Quaternion normalizedCopy() {
        Quaternion copy = new Quaternion(x, y, z, w);
        copy.normalize();
        return copy;
    }

    // ============================
    // 共轭和逆运算
    // ============================

    /**
     * 获取共轭四元数
     */
    public Quaternion getConjugate() {
        return new Quaternion(-x, -y, -z, w);
    }

    /**
     * 获取逆四元数
     */
    public Quaternion getInverse() {
        double normSquared = x * x + y * y + z * z + w * w;

        if (Math.abs(normSquared) < EPS) {
            log.warn("四元数模长为零,无法求逆,返回单位四元数");
            return new Quaternion();
        }

        // 如果是单位四元数,逆等于共轭
        if (Math.abs(normSquared - 1.0) < NORMALIZE_CHECKSUM_EPSILON) {
            return getConjugate();
        } else {
            double invNormSquared = 1.0 / normSquared;
            return new Quaternion(-x * invNormSquared, -y * invNormSquared,
                    -z * invNormSquared, w * invNormSquared);
        }
    }

    // ============================
    // 四元数运算
    // ============================

    /**
     * 左乘:this = q × this
     */
    public void leftMultiply(Quaternion q) {
        Quaternion result = multiply(q, this);
        set(result.x, result.y, result.z, result.w);
        normalize();
    }

    /**
     * 右乘:this = this × q
     */
    public void rightMultiply(Quaternion q) {
        Quaternion result = multiply(this, q);
        set(result.x, result.y, result.z, result.w);
        normalize();
    }

    /**
     * 获取列主序4x4旋转矩阵(懒加载缓存)
     */
    public double[][] getMatrix4x4() {
        if (matrixCache4x4 == null) {
            matrixCache4x4 = toRotationMatrix4x4(null);
        }
        return matrixCache4x4;
    }

    /**
     * 获取列主序3x3旋转矩阵(懒加载缓存)
     */
    public double[][] getMatrix3x3() {
        if (matrixCache3x3 == null) {
            matrixCache3x3 = toRotationMatrix3x3(null);
        }
        return matrixCache3x3;
    }

    /**
     * 强制生成或更新4x4旋转矩阵
     */
    public double[][] toRotationMatrix4x4() {
        matrixCache4x4 = toRotationMatrix4x4(matrixCache4x4);
        return matrixCache4x4;
    }

    /**
     * 强制生成或更新3x3旋转矩阵
     */
    public double[][] toRotationMatrix3x3() {
        matrixCache3x3 = toRotationMatrix3x3(matrixCache3x3);
        return matrixCache3x3;
    }

    /**
     * 将四元数转换为列主序4x4旋转矩阵
     *
     * @param m 目标数组(如为null则新建)
     * @return 4x4列主序旋转矩阵
     */
    public double[][] toRotationMatrix4x4(double[][] m) {
        double[][] result = (m == null || !validateMatrix(m, MATRIX_4x4)) ?
                new double[4][4] : m;

        // 如果是单位四元数,直接返回单位矩阵
        if (isIdentity()) {
            setIdentityMatrix4x4(result);
            matrixCache4x4 = result;
            return result;
        }

        // 确保归一化以获得稳定的矩阵
        normalize();

        // 预计算分量乘积
        double xx = x * x, yy = y * y, zz = z * z;
        double xy = x * y, xz = x * z, yz = y * z;
        double wx = w * x, wy = w * y, wz = w * z;

        // 列主序填充(OpenGL/JFX格式)
        result[0][0] = 1.0 - 2.0 * (yy + zz); // m00
        result[1][0] = 2.0 * (xy + wz);        // m10
        result[2][0] = 2.0 * (xz - wy);        // m20
        result[3][0] = 0.0;                    // m30

        result[0][1] = 2.0 * (xy - wz);        // m01
        result[1][1] = 1.0 - 2.0 * (xx + zz);  // m11
        result[2][1] = 2.0 * (yz + wx);        // m21
        result[3][1] = 0.0;                    // m31

        result[0][2] = 2.0 * (xz + wy);        // m02
        result[1][2] = 2.0 * (yz - wx);        // m12
        result[2][2] = 1.0 - 2.0 * (xx + yy);  // m22
        result[3][2] = 0.0;                    // m32

        // 第四列(平移部分,保持为单位矩阵)
        result[0][3] = 0.0;
        result[1][3] = 0.0;
        result[2][3] = 0.0;
        result[3][3] = 1.0;

        matrixCache4x4 = result;
        update3x3CacheFrom4x4(result);

        return result;
    }

    /**
     * 将四元数转换为列主序3x3旋转矩阵
     *
     * @param m 目标数组(如为null则新建)
     * @return 3x3列主序旋转矩阵
     */
    public double[][] toRotationMatrix3x3(double[][] m) {
        double[][] result = (m == null || !validateMatrix(m, MATRIX_3x3)) ?
                new double[3][3] : m;

        if (isIdentity()) {
            setIdentityMatrix3x3(result);
            matrixCache3x3 = result;
            return result;
        }

        normalize();

        double xx = x * x, yy = y * y, zz = z * z;
        double xy = x * y, xz = x * z, yz = y * z;
        double wx = w * x, wy = w * y, wz = w * z;

        result[0][0] = 1.0 - 2.0 * (yy + zz); // m00
        result[1][0] = 2.0 * (xy + wz);        // m10
        result[2][0] = 2.0 * (xz - wy);        // m20

        result[0][1] = 2.0 * (xy - wz);        // m01
        result[1][1] = 1.0 - 2.0 * (xx + zz);  // m11
        result[2][1] = 2.0 * (yz + wx);        // m21

        result[0][2] = 2.0 * (xz + wy);        // m02
        result[1][2] = 2.0 * (yz - wx);        // m12
        result[2][2] = 1.0 - 2.0 * (xx + yy);  // m22

        matrixCache3x3 = result;
        return result;
    }

    /**
     * 从4x4缓存更新3x3缓存
     */
    private void update3x3CacheFrom4x4(double[][] matrix4x4) {
        if (matrix4x4 == null || !validateMatrix(matrix4x4, MATRIX_4x4)) {
            return;
        }
        if (matrixCache3x3 == null) {
            matrixCache3x3 = new double[3][3];
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                matrixCache3x3[i][j] = matrix4x4[i][j];
            }
        }
    }

    /**
     * 从4x4矩阵设置四元数(内部方法)
     */
    private void setFromMatrix4x4(double[][] matrix) {
        Quaternion q = fromMatrix4x4Internal(matrix);
        if (q != null) {
            this.x = q.x;
            this.y = q.y;
            this.z = q.z;
            this.w = q.w;
            this.normalized = q.normalized;
            this.matrixCache4x4 = q.matrixCache4x4;
            this.matrixCache3x3 = q.matrixCache3x3;
        } else {
            log.warn("矩阵转换四元数失败,使用单位四元数");
            setIdentity();
        }
    }

    /**
     * 从3x3矩阵设置四元数(内部方法)
     */
    private void setFromMatrix3x3(double[][] matrix) {
        Quaternion q = fromMatrix3x3Internal(matrix);
        if (q != null) {
            this.x = q.x;
            this.y = q.y;
            this.z = q.z;
            this.w = q.w;
            this.normalized = q.normalized;
            this.matrixCache4x4 = q.matrixCache4x4;
            this.matrixCache3x3 = q.matrixCache3x3;
        } else {
            log.warn("矩阵转换四元数失败,使用单位四元数");
            setIdentity();
        }
    }

    // ============================
    // 欧拉角转换方法
    // ============================

    /**
     * 转换为欧拉角(XYZ顺序,返回角度制)
     *
     * @param dest 目标数组(如为null则新建)
     * @return 欧拉角数组 [横滚, 俯仰, 偏航](角度)
     */
    public double[] toAngles(double[] dest) {
        if (dest == null) {
            dest = new double[3];
        }

        if (isIdentity()) {
            dest[0] = dest[1] = dest[2] = 0.0;
            return dest;
        }

        normalize();

        // 计算横滚角(绕X轴)
        double sinRoll = 2.0 * (w * x + y * z);
        double cosRoll = 1.0 - 2.0 * (x * x + y * y);
        double roll = Math.atan2(sinRoll, cosRoll);

        // 计算俯仰角(绕Y轴),处理万向节锁奇异性
        double sinPitch = 2.0 * (w * y - z * x);
        double pitch;
        if (Math.abs(sinPitch) >= 1.0) {
            pitch = Math.copySign(Math.PI / 2.0, sinPitch);
        } else {
            pitch = Math.asin(sinPitch);
        }

        // 计算偏航角(绕Z轴)
        double sinYaw = 2.0 * (w * z + x * y);
        double cosYaw = 1.0 - 2.0 * (y * y + z * z);
        double yaw = Math.atan2(sinYaw, cosYaw);

        dest[0] = Math.toDegrees(roll);
        dest[1] = Math.toDegrees(pitch);
        dest[2] = Math.toDegrees(yaw);

        return dest;
    }

    /**
     * 转换为轴角表示
     *
     * @return 轴角数组 [axisX, axisY, axisZ, angleDegrees]
     */
    public double[] toAxisAngle() {
        normalize();
        double[] result = new double[4];

        double angleRad = 2.0 * Math.acos(w);
        double sinHalfAngle = Math.sqrt(1 - w * w);

        if (sinHalfAngle < VECTOR_NORMALIZE_EPSILON) {
            result[0] = 1.0;
            result[1] = 0.0;
            result[2] = 0.0;
        } else {
            result[0] = x / sinHalfAngle;
            result[1] = y / sinHalfAngle;
            result[2] = z / sinHalfAngle;
        }

        result[3] = Math.toDegrees(angleRad);
        return result;
    }

    // ============================
    // 相等性比较和哈希码
    // ============================

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Quaternion other = (Quaternion) obj;

        // 检查是否是相同的四元数(考虑到四元数 q 和 -q 表示相同的旋转)
        boolean directMatch = approxEquals(this, other, 0.001);
        boolean inverseMatch = approxEquals(this, new Quaternion(-other.x, -other.y, -other.z, -other.w), 0.001);

        return directMatch || inverseMatch;
    }

    @Override
    public int hashCode() {
        // 使用四舍五入到精度误差范围内的值来计算hashCode
        // 这样近似相等的四元数会有相同的hashCode
        double precision = 0.001;
        int xHash = (int) Math.round(x / precision);
        int yHash = (int) Math.round(y / precision);
        int zHash = (int) Math.round(z / precision);
        int wHash = (int) Math.round(w / precision);
        // 也要考虑负四元数的情况
        int negXHash = (int) Math.round(-x / precision);
        int negYHash = (int) Math.round(-y / precision);
        int negZHash = (int) Math.round(-z / precision);
        int negWHash = (int) Math.round(-w / precision);
        // 返回较小hashCode的那一组,确保q和-q有相同的hashCode
        if (xHash + yHash + zHash + wHash <= negXHash + negYHash + negZHash + negWHash) {
            return Objects.hash(xHash, yHash, zHash, wHash);
        } else {
            return Objects.hash(negXHash, negYHash, negZHash, negWHash);
        }
    }

    /**
     * 检查两个四元数是否表示相同的旋转(考虑精度误差)
     */
    public boolean isSameRotation(Quaternion other, double epsilon) {
        if (other == null) {
            return false;
        }
        return approxEquals(this, other, epsilon) ||
                approxEquals(this, new Quaternion(-other.x, -other.y, -other.z, -other.w), epsilon);
    }

    /**
     * 检查是否近似等于另一个四元数(使用默认精度0.001)
     */
    public boolean approxEquals(Quaternion other) {
        return isSameRotation(other, 0.001);
    }

    @Override
    public String toString() {
        return String.format("Quaternion{x=%.4f, y=%.4f, z=%.4f, w=%.4f}", x, y, z, w);
    }
}
