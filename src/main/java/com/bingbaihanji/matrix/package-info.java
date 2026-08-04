/// # ArcBall算法实现3D旋转(基于旋转矩阵) 核心笔记
///
/// ArcBall算法是将2D鼠标拖动映射为3D旋转变换的经典算法,核心通过**球面映射-向量运算-矩阵生成-变换应用**的流程,实现自然的3D交互.本笔记提炼算法核心流程,代码实现与工程化技巧,适配JavaFX 3D开发场景.
///
///         ## 一,算法核心六步流程
///
/// ### 第一步:坐标映射 - 2D屏幕坐标 → 3D单位球面
///
/// **目的**:建立2D鼠标操作与3D球面的关联,将像素坐标转为球面点.
///
///         **核心代码**:
///
///         ```java
/// public Vector3 mapToSphere(double screenX, double screenY){
///     double xNorm = (2.0 * screenX - width) / width;
///     double yNorm = -((2.0 * screenY - height) / height); // Y轴翻转(屏幕与3D坐标系方向相反)
///     double lengthSquared = xNorm * xNorm + yNorm * yNorm;
///
///     // 单位球面方程:x²+y²+z²=1,超出单位圆则投影到边缘
///     if (lengthSquared > 1.0){
///         double length = Math.sqrt(lengthSquared);
///         return new Vector3(xNorm/length, yNorm/length, 0);
/// } else {
///         return new Vector3(xNorm, yNorm, Math.sqrt(1.0 - lengthSquared));
/// }
/// }
/// ```
///
///         **关键原理**:
///
///         - 屏幕坐标系:`(0,0)` 到 `(width,height)`
///         - 归一化坐标系:`(-1,-1)` 到 `(1,1)`
///         - Y轴翻转:屏幕Y轴向下,3D Y轴向上
/// - 单位球面:确保所有向量长度≈1
///
///         ### 第二步:向量计算 - 获取球面起始/终点向量
///
/// **目的**:记录鼠标按下(起始)和拖动(终点)对应的球面3D向量,作为旋转计算的基础.
///
///         **核心代码**:
///
///         ```java
/// // 鼠标按下时记录起始向量
/// Vector3 startVector = arcBall.mapToSphere(mouseDownX, mouseDownY);
/// // 鼠标拖动时计算当前向量
/// Vector3 currentVector = arcBall.mapToSphere(currentX, currentY);
/// ```
///
///         **物理意义**:两个向量代表用户在虚拟球面上"抓起点"和"拖动终点".
///
///         ### 第三步:旋转轴 - 叉积确定旋转方向
///
/// **目的**:计算垂直于两个球面向量的旋转轴(3D旋转的核心要素).
///
///         **核心代码**:
///
///         ```java
/// public Vector3 computeRotationAxis(Vector3 startVec, Vector3 endVec){
///     return startVec.cross(endVec).normalize(); // 叉积+归一化得到单位旋转轴
/// }
/// ```
///
///         **关键原理**:
///
///         - 叉积方向:右手法则,垂直于两个向量所在平面
/// - 叉积大小:`|a×b| = |a||b|sinθ`,与旋转角度相关
/// - 归一化:确保旋转轴是单位向量
///
/// ### 第四步:旋转角度 - 点积计算旋转幅度
///
/// **目的**:计算两个球面向量的夹角,确定旋转的角度大小.
///
///         **核心代码**:
///
///         ```java
/// public double computeRotationAngle(Vector3 startVec, Vector3 endVec){
///     double dot = Math.max(-1.0, Math.min(1.0, startVec.dot(endVec))); // 钳制范围避免acos异常
///     return Math.acos(dot); // 单位向量点积=cosθ,反余弦得弧度角度
/// }
/// ```
///
///         **关键原理**:单位向量点积的几何意义为两向量夹角的余弦值.
///
///         ### 第五步:矩阵生成 - 罗德里格斯公式生成旋转矩阵
///
/// **目的**:将旋转轴和角度转换为3x3旋转矩阵(计算机图形学标准旋转变换形式).
///
///         **核心代码**:
///
///         ```java
/// public static Matrix3 rotation(Vector3 axis, double angle){
///     double x = axis.x, y = axis.y, z = axis.z;
///     double sin = Math.sin(angle), cos = Math.cos(angle), omc = 1.0 - cos;
///
///     // 罗德里格斯旋转公式:R = I + sinθ·K + (1-cosθ)·K²(K为轴的反对称矩阵)
///     return new Matrix3(
///             cos + x*x*omc,      x*y*omc - z*sin, x*z*omc + y*sin,
///             y*x*omc + z*sin,    cos + y*y*omc,   y*z*omc - x*sin,
///             z*x*omc - y*sin,    z*y*omc + x*sin, cos + z*z*omc
/// );
/// }
/// ```
///
///         **数学推导**:
///
///         ```
/// 设旋转轴单位向量: u = (x, y, z)
/// 旋转角度: θ
///
/// 罗德里格斯公式:
/// R = I + sinθ·[u]× + (1-cosθ)·[u]×²
///
/// 其中[u]×是u的反对称矩阵:
/// [ 0  -z   y ]
/// [u]× = [ z   0  -x ]
/// [ -y  x   0 ]
/// ```
///
///         ### 第六步:矩阵应用 - 绑定3D对象/相机
///
/// **目的**:将旋转矩阵转换为JavaFX可识别的`Affine`变换,应用到3D节点(模型/相机).
///
///         **核心代码**:
///
///         ```java
/// private Matrix3 lastRotation = Matrix3.identityMatrix(); // 初始无旋转
/// public void applyRotation(Matrix3 newRotation){
///     lastRotation = lastRotation.multiply(newRotation); // 累积旋转(保持连续性)
///     ArcBallUtils.setRotationToAffine(cameraTransform, lastRotation); // 矩阵转Affine
/// }
/// ```
///
///         **关键原理**:
///
///         - 矩阵相乘实现旋转累积,避免拖动时旋转跳跃
/// - `Affine`是JavaFX 3D节点的核心变换容器
/// - 保持旋转状态,实现连续自然的交互体验
///
/// ## 二,完整集成示例:ArcBall控制器
///
/// 将六步流程封装为控制器,对接JavaFX鼠标事件:
///
///         ```java
/// public class ArcBallController {
///     private ArcBall arcBall;
///     private Matrix3 lastRotation = Matrix3.identityMatrix();
///     private Affine cameraTransform; // 3D相机/模型的变换对象
///     private Vector3 startSphereVector;
///
///     public ArcBallController(int width, int height, Affine cameraTransform){
///         this.arcBall = new ArcBall(width, height);
///         this.cameraTransform = cameraTransform;
/// }
///
///     // 鼠标按下事件
///     public void onMousePressed(double x, double y){
///         startSphereVector = arcBall.mapToSphere(x, y);
/// }
///
///     // 鼠标拖动事件(核心流程串联)
///     public void onMouseDragged(double currentX, double currentY){
///         // 步骤1-2:坐标映射+向量计算
///         Vector3 currentVector = arcBall.mapToSphere(currentX, currentY);
///         // 步骤3:计算旋转轴
///         Vector3 axis = arcBall.computeRotationAxis(startSphereVector, currentVector);
///         // 步骤4:计算旋转角度
///         double angle = arcBall.computeRotationAngle(startSphereVector, currentVector);
///         // 步骤5:生成旋转矩阵
///         Matrix3 rotationMatrix = Matrix3.rotation(axis, angle);
///         // 步骤6:应用旋转
///         applyRotation(rotationMatrix);
///         // 更新起始向量,实现连续旋转
///         startSphereVector = currentVector;
/// }
///
///     private void applyRotation(Matrix3 rotation){
///         lastRotation = lastRotation.multiply(rotation);
///         ArcBallUtils.setRotationToAffine(cameraTransform, lastRotation);
/// }
///
///     // 重置旋转
///     public void resetRotation(){
///         lastRotation = Matrix3.identityMatrix();
///         ArcBallUtils.setRotationToAffine(cameraTransform, lastRotation);
/// }
/// }
/// ```
///
///         ## 三,性能优化技巧
///
/// ### 1. 对象复用
///
/// 避免频繁创建`Vector3`/`Matrix3`对象,复用临时对象减少GC:
///
///         ```java
/// public class OptimizedArcBallController {
///     // 复用对象减少GC
///     private final Vector3 tempStartVec = new Vector3();
///     private final Vector3 tempCurrentVec = new Vector3();
///     private final Vector3 tempAxis = new Vector3();
///     private final Matrix3 tempMatrix = new Matrix3();
///
///     public void onMouseDraggedOptimized(double startX, double startY,
///                                         double currentX, double currentY){
///         // 使用重载方法,直接修改对象值
///         arcBall.mapToSphere(startX, startY, tempStartVec);
///         arcBall.mapToSphere(currentX, currentY, tempCurrentVec);
///         arcBall.computeRotationAxis(tempStartVec, tempCurrentVec, tempAxis);
///
///         double angle = arcBall.computeRotationAngle(tempStartVec, tempCurrentVec);
///         arcBall.generateRotationMatrix(tempAxis, angle, tempMatrix);
///
///         // 原地乘法优化
///         lastRotation.mulSelf(tempMatrix);
///         ArcBallUtils.setRotationToAffine(cameraTransform, lastRotation);
/// }
/// }
/// ```
///
///         ### 2. 原地运算
///
/// 使用矩阵原地乘法(`mulSelf`)替代新对象创建:
///
///         ```java
/// // 优化前:创建新对象
///         lastRotation = lastRotation.multiply(rotationMatrix);
///
/// // 优化后:原地计算
/// lastRotation.mulSelf(rotationMatrix); // 减少内存开销
/// ```
///
///         ### 3. 小角度优化
///
/// 对角度小于阈值的旋转直接跳过,提升交互流畅度:
///
///         ```java
/// public void onMouseDraggedWithThreshold(double currentX, double currentY){
///     Vector3 currentVector = arcBall.mapToSphere(currentX, currentY);
///     double angle = arcBall.computeRotationAngle(startSphereVector, currentVector);
///
///     // 角度阈值优化:避免微小抖动
///     if (angle > 0.001){ // 约0.057度
///         Vector3 axis = arcBall.computeRotationAxis(startSphereVector, currentVector);
///         Matrix3 rotationMatrix = Matrix3.rotation(axis, angle);
///         applyRotation(rotationMatrix);
/// }
///
///     startSphereVector = currentVector;
/// }
/// ```
///
///         ## 四,算法核心特性
///
/// ### 1. 自然交互体验
///
/// - **大范围旋转**:鼠标在球面边缘拖动时产生大角度旋转
/// - **精细控制**:鼠标在球面中心附近时旋转角度较小
/// - **连续性**:通过累积矩阵保持旋转的连续性
/// - **方向一致**:鼠标拖动方向与3D旋转方向直观对应
///
/// ### 2. 数学一致性
///
/// - **单位球面**:确保所有向量都是单位长度
/// - **正交矩阵**:旋转矩阵保持正交性,不会引入缩放
/// - **右手法则**:符合标准的3D数学约定
/// - **无奇点**:避免万向节锁问题
///
/// ### 3. 轻量高效
///
/// - **基础运算**:仅涉及向量点积,叉积和矩阵乘法
/// - **计算成本低**:适合实时交互应用
/// - **内存友好**:通过对象复用优化GC
///
/// ## 五,实际应用场景
///
/// ### 1. 3D模型查看器
///
/// 对接JavaFX的`MeshView`/`Group`节点,实现模型旋转查看:
///
///         ```java
/// // 初始化控制器
/// ArcBallController controller = new ArcBallController(800, 600, cameraAffine);
///
/// // 绑定鼠标事件
/// scene.setOnMousePressed(e -> controller.onMousePressed(e.getX(), e.getY()));
///         scene.setOnMouseDragged(e -> controller.onMouseDragged(e.getX(), e.getY()));
///
/// // 重置功能
///         scene.setOnKeyPressed(e -> {
///         if (e.getCode() == KeyCode.R) controller.resetRotation();
/// });
/// ```
///
///         ### 2. 科学可视化
///
/// 分子结构,地质数据,天文模型的3D交互旋转:
///
///         ```java
/// public class ScientificVisualizer {
///     public void rotateDataset(double startX, double startY, double endX, double endY){
///         // 使用ArcBall算法旋转整个数据集
///         Matrix3 rotation = ArcBallUtils.getArcBallRotationMatrix(
///                 width, height, startX, startY, endX, endY);
///
///         applyToAllDataPoints(rotation);
/// }
/// }
/// ```
///
///         ### 3. 工业设计预览
///
/// 产品3D模型的交互式旋转展示,支持多角度查看设计细节.
///
///         ## 六,常见问题解决
///
/// ### 1. 旋转方向相反
///
/// 如果旋转方向与鼠标移动方向相反,可以在计算角度时取负值:
///
///         ```java
/// double rotationAngle = -arcBall.computeRotationAngle(startVec, endVec);
/// ```
///
///         ### 2. 旋转不流畅
///
/// 添加阻尼系数来平滑旋转:
///
///         ```java
/// Matrix3 deltaRot = ArcBallUtils.getArcBallRotationMatrix(width, height, prevX, prevY, currentX, currentY);
/// deltaRot = Matrix3.slerp(Matrix3.identityMatrix(), deltaRot, 0.95); // 阻尼系数
/// ```
///
///         ### 3. 处理窗口大小变化
///
/// 当窗口大小变化时,需要更新ArcBall实例:
///
///         ```java
/// private void updateArcBall(int width, int height){
///     if (arcBall == null || arcBall.width != width || arcBall.height != height){
///         arcBall = new ArcBall(width, height);
/// }
/// }
/// ```
package com.bingbaihanji.matrix;
