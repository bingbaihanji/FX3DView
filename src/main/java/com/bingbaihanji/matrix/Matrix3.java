package com.bingbaihanji.matrix;

import java.util.Objects;

/// # 高性能 3x3 三维旋转矩阵实现
///
/// 添加原地操作方法，减少GC压力
///
/// ## 矩阵与向量关系
///
/// `Matrix3` 表示 3D 空间中的线性变换(如旋转)。对于一个 3D 向量 `(x, y, z)`，当它与 `Matrix3` 矩阵相乘时，会得到一个新的向量 `(x', y', z')`：
///
/// ```
/// (x', y', z') = Matrix3 × (x, y, z)
/// ```
///
/// 具体计算方式：
///
/// - `x' = m00 * x + m01 * y + m02 * z`
/// - `y' = m10 * x + m11 * y + m12 * z`
/// - `z' = m20 * x + m21 * y + m22 * z`
///
/// 每个元素的具体作用取决于矩阵所表示的变换类型（如**旋转**、**缩放**等）。
///
/// ## 旋转矩阵示例
///
/// 绕 X 轴旋转矩阵（`rotationX`），θ 为旋转角度：
///
/// ```
/// [ 1      0       0    ]  ← 第0行（x' 只由原x决定，不受y、z影响）
/// [ 0    cosθ    -sinθ   ]  ← 第1行（y' 由原y的cosθ分量和原z的-sinθ分量组合）
/// [ 0    sinθ     cosθ   ]  ← 第2行（z' 由原y的sinθ分量和原z的cosθ分量组合）
/// ```
public final class Matrix3 {

    /**
     * 向量归一化的误差阈值，用于判断轴向量是否为单位向量
     */
    private static final double NORMALIZE_THRESHOLD = 1e-6;
    /**
     * 行列式的极小值阈值，用于判断矩阵是否可逆
     */
    private static final double DETERMINANT_THRESHOLD = 1e-12;
    /**
     * SLERP中复用的临时矩阵，避免每次创建新对象
     */
    private static final Matrix3 tempMatrix = new Matrix3();
    public double m00, m01, m02;  // 第0列(m00)、第1列(m01)、第2列(m02)的x分量
    // 其中：
// 第0列 (m00, m10, m20) 代表变换后新X轴的单位向量
// 第1列 (m01, m11, m21) 代表变换后新Y轴的单位向量
// 第2列 (m02, m12, m22) 代表变换后新Z轴的单位向量
    public double m10, m11, m12;  // 第0列(m10)、第1列(m11)、第2列(m12)的y分量
    public double m20, m21, m22;  // 第0列(m20)、第1列(m21)、第2列(m22)的z分量

    /**
     * 默认构造函数，初始化为单位矩阵
     */
    public Matrix3() {
        identity();
    }

    public Matrix3(
            double m00, double m01, double m02,
            double m10, double m11, double m12,
            double m20, double m21, double m22
    ) {
        this.m00 = m00;
        this.m01 = m01;
        this.m02 = m02;
        this.m10 = m10;
        this.m11 = m11;
        this.m12 = m12;
        this.m20 = m20;
        this.m21 = m21;
        this.m22 = m22;
    }

    /**
     * 静态方法：创建单位矩阵
     */
    public static Matrix3 identityMatrix() {
        return new Matrix3(
                1, 0, 0,
                0, 1, 0,
                0, 0, 1);
    }

    /**
     * 根据罗德里格斯旋转公式，生成绕指定轴旋转指定角度的3x3旋转矩阵
     * <p>
     * 该方法使用罗德里格斯旋转公式(Rodrigues' Rotation Formula)计算旋转矩阵：
     * <pre>
     * R = I + sin(θ)·K + (1 - cos(θ))·K²
     * </pre>
     * 其中：
     * <ul>
     *   <li>I：3×3单位矩阵</li>
     *   <li>θ：旋转角度（弧度）</li>
     *   <li>K：旋转轴向量k的反对称矩阵：
     *     <pre>
     *     K = [ 0   -kz   ky ]
     *         [ kz   0   -kx ]
     *         [ -ky  kx   0  ]
     *     </pre>
     *   </li>
     * </ul>
     * <p>
     * 如果输入的旋转轴向量未归一化，方法内部会自动进行归一化处理。
     *
     * @param axis  旋转轴向量，不能为null，支持非单位向量（内部自动归一化）
     * @param angle 旋转角度，以弧度为单位
     * @return 绕指定轴旋转指定角度的3x3旋转矩阵
     * @throws NullPointerException 如果axis参数为null
     */
    public static Matrix3 rotation(Vector3 axis, double angle) {
        Objects.requireNonNull(axis, "旋转轴向量axis不能为空");
        double x = axis.x, y = axis.y, z = axis.z;
        double lenSq = x * x + y * y + z * z;

        // 若轴向量未归一化，执行归一化操作
        if (Math.abs(lenSq - 1.0) > NORMALIZE_THRESHOLD) {
            double invLen = 1.0 / Math.sqrt(lenSq);
            x *= invLen;
            y *= invLen;
            z *= invLen;
        }

        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        double oneMinusCos = 1.0 - cos;

        // 按罗德里格斯公式构造旋转矩阵

        return new Matrix3(
                cos + x * x * oneMinusCos,
                x * y * oneMinusCos - z * sin,
                x * z * oneMinusCos + y * sin,
                y * x * oneMinusCos + z * sin,
                cos + y * y * oneMinusCos,
                y * z * oneMinusCos - x * sin,
                z * x * oneMinusCos - y * sin,
                z * y * oneMinusCos + x * sin,
                cos + z * z * oneMinusCos
        );
    }

    /**
     * 计算绕任意轴旋转向量的旋转矩阵
     *
     * @param axis   旋转轴向量，必须是非空的单位向量或可归一化的向量
     * @param angle  旋转角度，以弧度为单位
     * @param result 存储计算结果的3x3矩阵，不能为null
     */
    public static void rotation(Vector3 axis, double angle, Matrix3 result) {
        Objects.requireNonNull(axis, "旋转轴向量axis不能为空");
        Objects.requireNonNull(result, "结果矩阵不能为空");

        double x = axis.x, y = axis.y, z = axis.z;
        double lenSq = x * x + y * y + z * z;
        // 如果旋转轴向量不是单位向量，则进行归一化处理
        if (Math.abs(lenSq - 1.0) > NORMALIZE_THRESHOLD) {
            double invLen = 1.0 / Math.sqrt(lenSq);
            x *= invLen;
            y *= invLen;
            z *= invLen;
        }

        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        double oneMinusCos = 1.0 - cos;
        // 使用罗德里格斯公式(Rodrigues' rotation formula)计算旋转矩阵
        result.m00 = cos + x * x * oneMinusCos;
        result.m01 = x * y * oneMinusCos - z * sin;
        result.m02 = x * z * oneMinusCos + y * sin;

        result.m10 = y * x * oneMinusCos + z * sin;
        result.m11 = cos + y * y * oneMinusCos;
        result.m12 = y * z * oneMinusCos - x * sin;

        result.m20 = z * x * oneMinusCos - y * sin;
        result.m21 = z * y * oneMinusCos + x * sin;
        result.m22 = cos + z * z * oneMinusCos;
    }

    /**
     * 静态矩阵乘法，计算两个3x3矩阵的乘积（a * b）
     */
    public static Matrix3 multiply(Matrix3 a, Matrix3 b) {
        Objects.requireNonNull(a, "左乘矩阵a不能为空");
        Objects.requireNonNull(b, "右乘矩阵b不能为空");
        return new Matrix3(
                a.m00 * b.m00 + a.m01 * b.m10 + a.m02 * b.m20,
                a.m00 * b.m01 + a.m01 * b.m11 + a.m02 * b.m21,
                a.m00 * b.m02 + a.m01 * b.m12 + a.m02 * b.m22,
                a.m10 * b.m00 + a.m11 * b.m10 + a.m12 * b.m20,
                a.m10 * b.m01 + a.m11 * b.m11 + a.m12 * b.m21,
                a.m10 * b.m02 + a.m11 * b.m12 + a.m12 * b.m22,
                a.m20 * b.m00 + a.m21 * b.m10 + a.m22 * b.m20,
                a.m20 * b.m01 + a.m21 * b.m11 + a.m22 * b.m21,
                a.m20 * b.m02 + a.m21 * b.m12 + a.m22 * b.m22
        );
    }

    /**
     * @Description: 创建一个绕X轴旋转的3x3旋转矩阵
     * @param: angle - 旋转角度（弧度制）
     * @return: 绕X轴旋转的3x3旋转矩阵
     * <p>
     * 矩阵构成：
     * <pre>
     * [ 1   0     0  ]
     * [ 0  cos  -sin ]
     * [ 0  sin   cos ]
     */
    public static Matrix3 rotationX(double angle) {
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        return new Matrix3(
                1, 0, 0,
                0, cos, -sin,
                0, sin, cos
        );
    }

    /**
     * @Description: 创建一个绕Y轴旋转的3x3旋转矩阵
     * @param: angle - 旋转角度（弧度制）
     * @return: 绕Y轴旋转的3x3旋转矩阵
     * <p>
     * 矩阵构成：
     * <pre>
     * [ cos  0  sin ]
     * [ 0    1   0  ]
     * [ -sin 0  cos ]
     */
    public static Matrix3 rotationY(double angle) {
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        return new Matrix3(
                cos, 0, sin,
                0, 1, 0,
                -sin, 0, cos);
    }

    /**
     * @Description: 创建一个绕Z轴旋转的3x3旋转矩阵
     * @param: angle - 旋转角度（弧度制）
     * @return: 绕Z轴旋转的3x3旋转矩阵
     * <p>
     * 矩阵构成：
     * <pre>
     * [ cos -sin 0 ]
     * [ sin  cos 0 ]
     * [ 0    0   1 ]
     */
    public static Matrix3 rotationZ(double angle) {
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        return new Matrix3(
                cos, -sin, 0,
                sin, cos, 0,
                0, 0, 1
        );
    }

    /**
     * 从欧拉角创建3x3旋转矩阵，旋转顺序为Y-X-Z
     *
     * @param xAngle 绕X轴的旋转角度（弧度）
     * @param yAngle 绕Y轴的旋转角度（弧度）
     * @param zAngle 绕Z轴的旋转角度（弧度）
     * @return 表示组合旋转的3x3矩阵
     */
    public static Matrix3 fromEulerAngles(double xAngle, double yAngle, double zAngle) {
        // 创建绕各轴的旋转矩阵
        Matrix3 rx = rotationX(xAngle);
        Matrix3 ry = rotationY(yAngle);
        Matrix3 rz = rotationZ(zAngle);

        // 按Y-X-Z顺序组合旋转矩阵
        return multiply(multiply(ry, rx), rz);
    }

    /**
     * 矩阵元素的线性插值（LERP，非球面插值）
     * <p>
     * 注意：线性插值不保持正交性，多次插值后矩阵会退化。
     * 仅适用于小幅度的阻尼/平滑操作，此时误差可忽略。
     * 需要保持旋转矩阵正交性时请使用 {@link #slerp(Matrix3, Matrix3, double)}。
     *
     * @param a 插值起始矩阵
     * @param b 插值结束矩阵
     * @param t 插值参数[0,1]
     * @return 线性插值结果矩阵
     */
    public static Matrix3 lerp(Matrix3 a, Matrix3 b, double t) {
        Objects.requireNonNull(a, "插值起始矩阵a不能为空");
        Objects.requireNonNull(b, "插值结束矩阵b不能为空");
        if (t <= 0.0) {
            return a;
        }
        if (t >= 1.0) {
            return b;
        }

        return new Matrix3(
                a.m00 + (b.m00 - a.m00) * t,
                a.m01 + (b.m01 - a.m01) * t,
                a.m02 + (b.m02 - a.m02) * t,
                a.m10 + (b.m10 - a.m10) * t,
                a.m11 + (b.m11 - a.m11) * t,
                a.m12 + (b.m12 - a.m12) * t,
                a.m20 + (b.m20 - a.m20) * t,
                a.m21 + (b.m21 - a.m21) * t,
                a.m22 + (b.m22 - a.m22) * t);
    }

    /**
     * 原地矩阵元素线性插值
     *
     * @param a      插值起始矩阵
     * @param b      插值结束矩阵
     * @param t      插值参数[0,1]
     * @param result 存储结果的矩阵
     */
    public static void lerp(Matrix3 a, Matrix3 b, double t, Matrix3 result) {
        Objects.requireNonNull(a, "插值起始矩阵a不能为空");
        Objects.requireNonNull(b, "插值结束矩阵b不能为空");
        Objects.requireNonNull(result, "结果矩阵不能为空");

        if (t <= 0.0) {
            result.set(a);
            return;
        }
        if (t >= 1.0) {
            result.set(b);
            return;
        }

        result.m00 = a.m00 + (b.m00 - a.m00) * t;
        result.m01 = a.m01 + (b.m01 - a.m01) * t;
        result.m02 = a.m02 + (b.m02 - a.m02) * t;
        result.m10 = a.m10 + (b.m10 - a.m10) * t;
        result.m11 = a.m11 + (b.m11 - a.m11) * t;
        result.m12 = a.m12 + (b.m12 - a.m12) * t;
        result.m20 = a.m20 + (b.m20 - a.m20) * t;
        result.m21 = a.m21 + (b.m21 - a.m21) * t;
        result.m22 = a.m22 + (b.m22 - a.m22) * t;
    }

    /**
     * 球面线性插值（SLERP）——在SO(3)流形上对两个旋转矩阵进行插值
     * <p>
     * 算法：计算相对旋转 R_rel = A^T * B，提取其轴-角表示，
     * 对角度进行线性插值（t * θ），再由插值后的轴角构造旋转矩阵，
     * 最终结果为 A * R_interpolated。
     * 这保证了插值结果始终是合法的旋转矩阵（正交且行列式为1）。
     *
     * @param a 起始旋转矩阵
     * @param b 结束旋转矩阵
     * @param t 插值参数[0,1]
     * @return SO(3)球面线性插值结果
     */
    public static Matrix3 slerp(Matrix3 a, Matrix3 b, double t) {
        Objects.requireNonNull(a, "插值起始矩阵a不能为空");
        Objects.requireNonNull(b, "插值结束矩阵b不能为空");

        if (t <= 0.0) {
            return new Matrix3().set(a);
        }
        if (t >= 1.0) {
            return new Matrix3().set(b);
        }

        // 计算相对旋转：R_rel = A^T * B
        Matrix3 aInv = a.transpose(); // 旋转矩阵的逆 = 转置
        Matrix3 rRel = multiply(aInv, b);

        // 从相对旋转中提取轴角
        Vector3 axis = new Vector3();
        double angle = rRel.toAxisAngle(axis);

        // 对角度进行线性插值
        double interpAngle = t * angle;

        // 由插值后的轴角构造旋转矩阵
        Matrix3 rInterp = rotation(axis, interpAngle);

        // 最终结果：A * R_interpolated
        return multiply(a, rInterp);
    }

    /**
     * 原地球面线性插值
     *
     * @param a      起始旋转矩阵
     * @param b      结束旋转矩阵
     * @param t      插值参数[0,1]
     * @param result 存储结果的矩阵
     */
    public static void slerp(Matrix3 a, Matrix3 b, double t, Matrix3 result) {
        Objects.requireNonNull(a, "插值起始矩阵a不能为空");
        Objects.requireNonNull(b, "插值结束矩阵b不能为空");
        Objects.requireNonNull(result, "结果矩阵不能为空");

        if (t <= 0.0) {
            result.set(a);
            return;
        }
        if (t >= 1.0) {
            result.set(b);
            return;
        }

        Matrix3 aInv = a.transpose();
        Matrix3 rRel = multiply(aInv, b);
        Vector3 axis = new Vector3();
        double angle = rRel.toAxisAngle(axis);
        double interpAngle = t * angle;

        // 原地构造插值旋转矩阵
        rotation(axis, interpAngle, tempMatrix);
        Matrix3 finalResult = multiply(a, tempMatrix);
        result.set(finalResult);
    }

    /**
     * 从旋转矩阵中提取轴-角表示（用于SLERP等需要轴角插值的场景）
     * <p>
     * 旋转角度 θ = acos((trace(R) - 1) / 2)
     * 旋转轴由 R - R^T 的反对称部分提取
     *
     * @param outAxis 输出的旋转轴向量（单位向量），不能为null
     * @return 旋转角度（弧度），若矩阵接近单位矩阵则返回0
     */
    public double toAxisAngle(Vector3 outAxis) {
        Objects.requireNonNull(outAxis, "输出轴向量不能为空");

        double cos = (m00 + m11 + m22 - 1.0) / 2.0;
        cos = Math.max(-1.0, Math.min(1.0, cos)); // 防止浮点误差越界
        double angle = Math.acos(cos);

        // 角度接近0时，旋转轴可任意取（默认为Y轴）
        if (Math.abs(angle) < 1e-10) {
            outAxis.set(0, 1, 0);
            return 0.0;
        }

        double sin = Math.sin(angle);
        if (Math.abs(sin) < 1e-10) {
            // 角度接近180°，从对角线元素推导轴分量
            double x = Math.sqrt(Math.max(0, (m00 - cos) / (1 - cos)));
            double y = Math.sqrt(Math.max(0, (m11 - cos) / (1 - cos)));
            double z = Math.sqrt(Math.max(0, (m22 - cos) / (1 - cos)));
            // 通过反对称部分的符号确定方向
            if (m21 - m12 < 0) {
                x = -x;
            }
            if (m02 - m20 < 0) {
                y = -y;
            }
            if (m10 - m01 < 0) {
                z = -z;
            }
            double len = Math.sqrt(x * x + y * y + z * z);
            outAxis.set(x / len, y / len, z / len);
        } else {
            double inv2sin = 1.0 / (2.0 * sin);
            outAxis.set(
                    (m21 - m12) * inv2sin,
                    (m02 - m20) * inv2sin,
                    (m10 - m01) * inv2sin
            );
        }
        return angle;
    }

    /**
     * 设置当前矩阵为单位矩阵
     */
    public Matrix3 identity() {
        this.m00 = 1;
        this.m01 = 0;
        this.m02 = 0;
        this.m10 = 0;
        this.m11 = 1;
        this.m12 = 0;
        this.m20 = 0;
        this.m21 = 0;
        this.m22 = 1;
        return this;
    }

    /**
     * 设置矩阵为单位矩阵
     */
    public Matrix3 setIdentity() {
        this.m00 = 1;
        this.m01 = 0;
        this.m02 = 0;
        this.m10 = 0;
        this.m11 = 1;
        this.m12 = 0;
        this.m20 = 0;
        this.m21 = 0;
        this.m22 = 1;
        return this;
    }

    /**
     * 复制矩阵值
     */
    public Matrix3 set(Matrix3 other) {
        Objects.requireNonNull(other, "源矩阵不能为空");
        this.m00 = other.m00;
        this.m01 = other.m01;
        this.m02 = other.m02;
        this.m10 = other.m10;
        this.m11 = other.m11;
        this.m12 = other.m12;
        this.m20 = other.m20;
        this.m21 = other.m21;
        this.m22 = other.m22;
        return this;
    }

    public Matrix3 multiply(Matrix3 other) {
        return multiply(this, other);
    }

    /**
     * 就地矩阵乘法，直接修改当前矩阵为this * other的结果
     */
    public Matrix3 mulSelf(Matrix3 other) {
        Objects.requireNonNull(other, "右乘矩阵other不能为空");
        double nm00 = m00 * other.m00 + m01 * other.m10 + m02 * other.m20;
        double nm01 = m00 * other.m01 + m01 * other.m11 + m02 * other.m21;
        double nm02 = m00 * other.m02 + m01 * other.m12 + m02 * other.m22;
        double nm10 = m10 * other.m00 + m11 * other.m10 + m12 * other.m20;
        double nm11 = m10 * other.m01 + m11 * other.m11 + m12 * other.m21;
        double nm12 = m10 * other.m02 + m11 * other.m12 + m12 * other.m22;
        double nm20 = m20 * other.m00 + m21 * other.m10 + m22 * other.m20;
        double nm21 = m20 * other.m01 + m21 * other.m11 + m22 * other.m21;
        double nm22 = m20 * other.m02 + m21 * other.m12 + m22 * other.m22;
        m00 = nm00;
        m01 = nm01;
        m02 = nm02;
        m10 = nm10;
        m11 = nm11;
        m12 = nm12;
        m20 = nm20;
        m21 = nm21;
        m22 = nm22;
        return this;
    }

    public Matrix3 transpose() {
        return new Matrix3(m00, m10, m20, m01, m11, m21, m02, m12, m22);
    }

    public Matrix3 transposeSelf() {
        double t01 = m01, t02 = m02, t12 = m12;
        m01 = m10;
        m02 = m20;
        m10 = t01;
        m12 = m21;
        m20 = t02;
        m21 = t12;
        return this;
    }

    /**
     * 计算当前矩阵的逆矩阵
     */
    public Matrix3 inverse() {
        double det = determinant();
        if (Math.abs(det) < DETERMINANT_THRESHOLD) {
            return null;
        }
        double invDet = 1.0 / det;
        double c00 = (m11 * m22 - m12 * m21) * invDet;
        double c01 = -(m01 * m22 - m02 * m21) * invDet;
        double c02 = (m01 * m12 - m02 * m11) * invDet;
        double c10 = -(m10 * m22 - m12 * m20) * invDet;
        double c11 = (m00 * m22 - m02 * m20) * invDet;
        double c12 = -(m00 * m12 - m02 * m10) * invDet;
        double c20 = (m10 * m21 - m11 * m20) * invDet;
        double c21 = -(m00 * m21 - m01 * m20) * invDet;
        double c22 = (m00 * m11 - m01 * m10) * invDet;
        return new Matrix3(c00, c01, c02, c10, c11, c12, c20, c21, c22);
    }

    /**
     * 计算3x3矩阵的行列式值
     */
    public double determinant() {
        return m00 * (m11 * m22 - m12 * m21)
                - m01 * (m10 * m22 - m12 * m20)
                + m02 * (m10 * m21 - m11 * m20);
    }

    /**
     * 将当前矩阵作为线性变换应用到3D向量上
     */
    public Vector3 applyTo(Vector3 v) {
        Objects.requireNonNull(v, "待变换的向量v不能为空");
        return new Vector3(
                m00 * v.x + m01 * v.y + m02 * v.z,   // 新x坐标 = 矩阵第一行与v的点积
                m10 * v.x + m11 * v.y + m12 * v.z,      // 新y坐标 = 矩阵第二行与v的点积
                m20 * v.x + m21 * v.y + m22 * v.z);     // 新z坐标 = 矩阵第三行与v的点积
    }

    /**
     * 原地应用变换到向量
     */
    public void applyToLocal(Vector3 v, Vector3 result) {
        Objects.requireNonNull(v, "待变换的向量v不能为空");
        Objects.requireNonNull(result, "结果向量不能为空");
        result.x = m00 * v.x + m01 * v.y + m02 * v.z;
        result.y = m10 * v.x + m11 * v.y + m12 * v.z;
        result.z = m20 * v.x + m21 * v.y + m22 * v.z;
    }

    @Override
    public String toString() {
        return String.format(
                "Matrix3[\n %.6f, %.6f, %.6f\n %.6f, %.6f, %.6f\n %.6f, %.6f, %.6f\n]",
                m00, m01, m02, m10, m11, m12, m20, m21, m22);
    }
}