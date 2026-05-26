# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。

## 构建与运行

```bash
# 编译
mvn compile

# 打包 fat JAR（shade 插件）
mvn package

# 运行（打包后）
java -jar target/FX3DView-1.0-SNAPSHOT.jar

# 直接通过 Maven 运行
mvn javafx:run
```

- Java 17，JavaFX 17.0.6，Maven 项目（pom.xml）
- 主类：`com.bingbaihanji.StartMain`（通过 shade 插件清单配置）
- 全项目使用 Lombok（@Slf4j、@Getter）；已在 maven-compiler-plugin 中配置注解处理器
- 日志：SLF4J + Logback（配置文件 `src/main/resources/logback.xml`，输出到 `logs/fx3dview.log`，7 天滚动保留）

## 架构

本应用是一个 3D 模型查看器，内置两种旋转后端（四元数和矩阵），启动时通过策略模式选择。

### 启动链

`StartMain.main()` → `MatrixViewerLauncher`（或 `QuaternionViewerLauncher`） → `Fx3DViewerApp` → `WindowsThemeJavaFXApp` →
`Application`

每个启动器向 `Fx3DViewerApp` 注入一个 `RotationStrategy`（四元数或矩阵）。启动器类之间的唯一区别在于注入哪种策略——所有共享逻辑均位于
`Fx3DViewerApp` 中。

### 核心组件装配（`Fx3DViewerApp.startWindowsThemeUI()` 内部）

`Scene3DManager` 管理 3D 场景图：`root` → `world` →（`axisGroup`、`moleculeGroup`）。导入的模型放置在 `moleculeGroup` 中。

`CameraSystem` 将 `PerspectiveCamera` 封装在三层 `GroupTransform` 层次结构中（旋转 → 平移 → 相机）。旋转层由注入的
`RotationStrategy` 驱动。

`MouseInteraction` 附加到 `SubScene`，处理 ArcBall 旋转（左键拖拽）、平移（右键拖拽）、缩放（滚轮）和拾取（点击）。
`KeyboardInteraction` 处理快捷键。

`MainLayout` 组装 UI：顶部为 `MenuBar`（来自 `MenuNode`）的 `BorderPane`，中央为 `SubScene` + `ViewingAxes` 叠加画布。

### RotationStrategy 接口

```java
void applyDragRotation(width, height, prevX, prevY, currX, currY, factor);

void applyAutoRotation(angleRad);

void reset(initXAngle, initYAngle);

Affine getRotationAffine();

String getStrategyName();

void applyRandomRotation();  // UI层功能，已在接口中统一

void updateFromAffine();     // 从Affine同步内部旋转状态
```

- **`QuaternionRotation`**：使用 `com.bingbaihanji.quaternion.Quaternion` + `QuaternionArcBallUtils`。采用 NLERP
  插值，无万向节锁问题。
- **`MatrixRotation`**：使用 `com.bingbaihanji.matrix.Matrix3` + `ArcBallUtils`。采用 3×3 矩阵 lerp（阻尼）——需要严格 SO(3)
  插值时，可使用 `Matrix3.slerp()` 进行真正的球面线性插值。
- 所有方法均在接口中声明，`KeyboardInteraction` 和 `MouseInteraction` 中不再有 `instanceof` 检查。
- 阻尼系数统一使用 `InteractionConfig.ROTATION_DAMPING`（0.95），不再在各策略中硬编码。

`matrix` 包包含独立的数学原语（`Matrix3`、`Vector3`、`ArcBall`、`ArcBallUtils`）。`quaternion` 包包含 `Quaternion`、`Vector3`、
`QuaternionArcBall`、`QuaternionArcBallUtils`。它们独立于旋转策略层——策略只是委托给它们的薄适配器。

### OBJ 加载管线

`ObjImporter`（实现 `Importer`）将 .obj 文件解析为 `TriangleMesh`（用于 `MeshView`）或 `PolygonMesh`（用于
`PolygonMeshView`）。`loading/` 中的关键类：`Model3D`、`MaterialData`、`MtlReader`、`SmoothingGroups`、`PolygonMesh`、
`PolygonMeshView`。内部类 `ObjModel` 使用关键字→解析器的分发映射处理 OBJ 行类型。`PolyObjModel` 扩展 `ObjModel`
，增加了多边形面的存储。

### Windows 暗色标题栏

`WindowsThemeJavaFXApp` 使用 JNA（`net.java.dev.jna`）调用 DWM API，实现原生标题栏的沉浸式暗色模式 + 圆角。所有应用类应继承此基类（重复的
`AbstractWindowsThemeApp` 和 `MyDarkApp` 已移除）。

### 遗留代码

`src/main/java/com/bingbaihanji/legacy/` 包含重构前的单体类，使用 `.exclude` 扩展名（已从 Maven
编译中排除）。仅作参考保留——所有新开发均在模块化包中进行。

### 配置类

`CameraConfig` 和 `InteractionConfig` 是常量持有类，包含私有构造函数（防止实例化）。调整缩放速度、旋转阻尼、自动旋转速度等参数应在这些文件中进行。

### GroupTransform

`com.bingbaihanji.world.GroupTransform` 继承 `Group`，在构造时预填充 transforms 列表，包含 `Translate`、`Rotate` × 3、
`Scale` 和 pivot 变换。通过 `RotateOrder` 枚举（XYZ、YZX 等）支持可配置的旋转顺序。在场景图和相机系统中广泛使用。

### 已知权衡

- **三套独立的 Vector3 类**：`matrix.Vector3`（double）、`quaternion.Vector3`（float）、`loading.Vec3f`
  （float）各自服务于不同的包。统一合并可减少重复，但需要跨包精度对齐——尚未完成。
- **`Matrix3.lerp()` 与 `Matrix3.slerp()`**：小增量阻尼（如 0.95 向单位矩阵混合——误差可忽略）使用 `lerp()`。当结果必须保持正确的
  SO(3) 旋转矩阵时使用 `slerp()`。
- **MenuEvent** 现已改为基于实例（每个窗口独立管理灯光、线框、点云模式等状态）。旧版静态状态版本会导致跨窗口状态共享问题。
- **着色模式**已提取到 `ShadingHandler` 中，负责贴图/纯色/线框/叠加/法线着色 5 种模式，`MenuEvent` 委托给该类处理。

### 点云渲染

`PointCloudBuilder` 使用单个 `TriangleMesh` 批量渲染所有采样点（每个点渲染为 3 个正交小方块形成的十字星标记）。替代了为每个采样点创建独立
`Sphere` 节点的旧方案，渲染节点数从 N 降至 1，消除了大模型点云模式下的卡顿问题。采样上限 100,000 点。

### 线程安全注意

- 模型加载通过 `DragDropHandler.loadModelFile` 在后台 `Task<Group>` 中执行，UI 回调在 `onSucceeded` 中自动回到 FX
  线程。菜单导入和拖放均走同一异步路径。
- `LightManager.openControlDialog()` 使用双重检查锁定防止重复创建控制窗口。
- `AutoRotationAnimation.lastNow` 声明为 `volatile`，确保跨线程可见性。
- 所有 UI 状态（`MenuEvent`、`ShadingHandler` 的实例字段）仅从 JavaFX Application Thread 访问。

### 修改规则

- 修改代码时，对于复杂的代码务必添加中文注释