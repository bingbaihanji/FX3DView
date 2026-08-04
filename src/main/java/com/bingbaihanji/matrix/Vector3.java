package com.bingbaihanji.matrix;

import java.util.Objects;

/**
 * 三维向量类,用于表示和操作三维空间中的向量
 *
 * <p>该类提供了向量运算的基本操作,包括:
 * <ul>
 *   <li>向量加法,减法</li>
 *   <li>向量缩放(数乘)</li>
 *   <li>向量点积(数量积)</li>
 *   <li>向量叉积(向量积)</li>
 *   <li>向量归一化(单位化)</li>
 *   <li>向量长度计算</li>
 * </ul>
 *
 * <p>该类支持两种操作模式:
 * <ul>
 *   <li>非原地操作:返回新的向量实例,保持原向量不变</li>
 *   <li>原地操作:修改当前向量实例,提高性能</li>
 * </ul>
 *
 * @author bingbaihanji
 * @version 1.0
 */
public final class Vector3 {

    /**
     * 零向量(所有分量为0)
     */
    public static final Vector3 ZERO = new Vector3(0.0, 0.0, 0.0);

    // X轴单位向量
    public static final Vector3 UNIT_X = new Vector3(1.0, 0.0, 0.0);

    // Y轴单位向量
    public static final Vector3 UNIT_Y = new Vector3(0.0, 1.0, 0.0);

    // Z轴单位向量
    public static final Vector3 UNIT_Z = new Vector3(0.0, 0.0, 1.0);

    /**
     * 归一化的最小长度阈值,避免除以零或生成NaN值
     */
    private static final double MIN_NORMALIZE_LENGTH = 1e-12;

    /**
     * 向量 X Y Z 分量
     */
    public double x, y, z;

    public Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3() {
        this(0, 0, 0);
    }

    /**
     * 拷贝构造函数,从已有的 Vector3 实例创建副本
     *
     * @param other 要拷贝的向量
     */
    public Vector3(Vector3 other) {
        Objects.requireNonNull(other, "源向量不能为空");
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    /**
     * 工厂方法,创建3D向量实例
     */
    public static Vector3 of(double x, double y, double z) {
        if (x == 0.0 && y == 0.0 && z == 0.0) {
            return ZERO;
        }
        return new Vector3(x, y, z);
    }

    /**
     * 向量加法:当前向量与另一个向量相加,返回新向量
     */
    public Vector3 add(Vector3 other) {
        Objects.requireNonNull(other, "待相加的向量other不能为空");
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    /**
     * 原地加法,不创建新对象
     */
    public Vector3 addLocal(Vector3 other) {
        Objects.requireNonNull(other, "待相加的向量other不能为空");
        this.x += other.x;
        this.y += other.y;
        this.z += other.z;
        return this;
    }

    /**
     * 向量减法:当前向量与另一个向量相减,返回新向量
     */
    public Vector3 sub(Vector3 other) {
        Objects.requireNonNull(other, "待相减的向量other不能为空");
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    /**
     * 原地减法,不创建新对象
     */
    public Vector3 subLocal(Vector3 other) {
        Objects.requireNonNull(other, "待相减的向量other不能为空");
        this.x -= other.x;
        this.y -= other.y;
        this.z -= other.z;
        return this;
    }

    /**
     * 向量缩放:当前向量的所有分量乘以标量,返回新向量
     */
    public Vector3 scale(double s) {
        return new Vector3(x * s, y * s, z * s);
    }

    /**
     * 原地缩放
     */
    public Vector3 scaleLocal(double s) {
        this.x *= s;
        this.y *= s;
        this.z *= s;
        return this;
    }

    /**
     * 向量点积(数量积):当前向量与另一个向量的点积计算
     */
    public double dot(Vector3 other) {
        Objects.requireNonNull(other, "点积计算的向量other不能为空");
        return x * other.x + y * other.y + z * other.z;
    }

    /**
     * 向量叉积(向量积):当前向量与另一个向量的叉积计算
     */
    public Vector3 cross(Vector3 other) {
        Objects.requireNonNull(other, "叉积计算的向量other不能为空");
        return new Vector3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    /**
     * 原地叉积计算
     */
    public Vector3 crossLocal(Vector3 other) {
        Objects.requireNonNull(other, "叉积计算的向量other不能为空");
        double newX = y * other.z - z * other.y;
        double newY = z * other.x - x * other.z;
        double newZ = x * other.y - y * other.x;
        this.x = newX;
        this.y = newY;
        this.z = newZ;
        return this;
    }

    /**
     * 叉积计算到目标向量
     */
    public void cross(Vector3 other, Vector3 result) {
        Objects.requireNonNull(other, "叉积计算的向量other不能为空");
        Objects.requireNonNull(result, "结果向量不能为空");
        result.x = this.y * other.z - this.z * other.y;
        result.y = this.z * other.x - this.x * other.z;
        result.z = this.x * other.y - this.y * other.x;
    }

    /**
     * 计算向量的长度平方(模的平方)
     */
    public double lengthSquared() {
        return x * x + y * y + z * z;
    }

    /**
     * 计算向量的长度(模)
     */
    public double length() {
        return Math.sqrt(lengthSquared());
    }

    /**
     * 向量归一化(单位化):将向量转换为长度为1的单位向量
     */
    public Vector3 normalize() {
        double len = length();
        if (len < MIN_NORMALIZE_LENGTH) {
            return ZERO;
        }
        return scale(1.0 / len);
    }

    /**
     * 原地归一化
     */
    public Vector3 normalizeLocal() {
        double len = length();
        if (len < MIN_NORMALIZE_LENGTH) {
            this.x = this.y = this.z = 0;
        } else {
            double invLen = 1.0 / len;
            this.x *= invLen;
            this.y *= invLen;
            this.z *= invLen;
        }
        return this;
    }

    /**
     * 设置向量值
     */
    public Vector3 set(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    /**
     * 复制向量值
     */
    public Vector3 set(Vector3 other) {
        Objects.requireNonNull(other, "源向量不能为空");
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
        return this;
    }

    /**
     * 判断向量是否为零向量
     */
    public boolean isZero() {
        return Math.abs(x) < MIN_NORMALIZE_LENGTH &&
                Math.abs(y) < MIN_NORMALIZE_LENGTH &&
                Math.abs(z) < MIN_NORMALIZE_LENGTH;
    }

    @Override
    public String toString() {
        return String.format("Vector3[%.6f, %.6f, %.6f]", x, y, z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Vector3 vector3 = (Vector3) o;
        return Double.doubleToLongBits(x) == Double.doubleToLongBits(vector3.x) &&
                Double.doubleToLongBits(y) == Double.doubleToLongBits(vector3.y) &&
                Double.doubleToLongBits(z) == Double.doubleToLongBits(vector3.z);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }
}