package com.bingbaihanji.ui;

import com.bingbaihanji.camera.CameraConfig;
import com.bingbaihanji.camera.CameraSystem;
import com.bingbaihanji.interaction.InteractionConfig;
import com.bingbaihanji.interaction.MouseInteraction;
import com.bingbaihanji.interaction.PickingController;
import com.bingbaihanji.view.MiniAxes;
import com.bingbaihanji.view.ViewingAxes;
import javafx.geometry.Pos;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;

/**
 * 多视口布局:2×2 GridPane,4个独立视口
 * <p>
 * TL: 透视主视角(共享主相机和旋转策略)
 * TR: 正面(独立相机,无旋转)
 * BL: 右面(独立相机,Y轴-90度)
 * BR: 底部(独立相机,X轴-90度)
 * </p>
 * <p>
 * 交互方式:
 * - 视口0:复用主 MouseInteraction(旋转/平移/缩放与主视图同步)
 * - 视口1-3:内联简单交互(左键拖拽旋转视角,右键拖拽平移模型,滚轮缩放)
 * - 单击激活(CORNFLOWERBLUE 边框高亮),双击最大化/恢复
 * </p>
 *
 * @author bingbaihanji
 */
public class MultiViewportLayout extends GridPane {

    private static final int NUM_VIEWPORTS = 4;

    /**
     * 每个视口在 GridPane 中的列位置
     */
    private static final int[] VIEWPORT_COLS = {0, 1, 0, 1};

    /**
     * 每个视口在 GridPane 中的行位置
     */
    private static final int[] VIEWPORT_ROWS = {0, 0, 1, 1};

    /**
     * 视口名称标签
     */
    private static final String[] VIEWPORT_NAMES = {"透视", "正面", "右面", "底部"};

    // ==================== 4 个视口的容器与子场景 ====================

    private final StackPane[] viewPortStacks = new StackPane[NUM_VIEWPORTS];

    private final SubScene[] subScenes = new SubScene[NUM_VIEWPORTS];

    private final Group[] roots = new Group[NUM_VIEWPORTS];

    private final Group[] modelContainers = new Group[NUM_VIEWPORTS];

    // 视口 1-3 的独立相机(视口0复用 CameraSystem 的主相机)
    private final PerspectiveCamera[] subCameras = new PerspectiveCamera[NUM_VIEWPORTS - 1];

    private final Group[] cameraGroups = new Group[NUM_VIEWPORTS - 1];

    // ==================== 交互状态(视口 1-3 的内联交互) ====================

    private final double[] mousePosX = new double[NUM_VIEWPORTS];

    private final double[] mousePosY = new double[NUM_VIEWPORTS];

    private final double[] mouseOldX = new double[NUM_VIEWPORTS];

    private final double[] mouseOldY = new double[NUM_VIEWPORTS];

    // ==================== 激活 / 最大化状态 ====================
    private final CameraSystem cameraSystem;

    private final Group moleculeGroup;

    // ==================== 注入的依赖 ====================
    private final ViewingAxes mainViewingAxes;

    private final PickingController pickingController;

    private final MouseInteraction mainMouseInteraction;

    /**
     * 每个视口的迷你坐标轴指示器(右下角 RGB 三色轴)
     */
    private final MiniAxes[] miniAxes = new MiniAxes[NUM_VIEWPORTS];

    /**
     * 各视口的环境光引用(用于移除)
     */
    private final AmbientLight[] ambientLights = new AmbientLight[NUM_VIEWPORTS];

    private int activeViewport = -1;

    // ==================== 迷你坐标轴 ====================
    private boolean maximized = false;

    // ==================== 构造方法 ====================
    // ==================== 共享模型克隆 ====================
    private Group sharedModel;

    // ==================== 视口初始化 ====================

    /**
     * 环境光是否开启
     */
    private boolean isLightOn = false;

    /**
     * @param cameraSystem         相机系统(提供主相机和共享旋转 Affine)
     * @param moleculeGroup        原始模型 Group(用于克隆)
     * @param mainViewingAxes      主视口的辅助轴(用以后续同步)
     * @param pickingController    拾取控制器(注入给主 MouseInteraction)
     * @param mainMouseInteraction 主 MouseInteraction(挂载到视口0)
     */
    public MultiViewportLayout(CameraSystem cameraSystem,
                               Group moleculeGroup,
                               ViewingAxes mainViewingAxes,
                               PickingController pickingController,
                               MouseInteraction mainMouseInteraction) {
        this.cameraSystem = cameraSystem;
        this.moleculeGroup = moleculeGroup;
        this.mainViewingAxes = mainViewingAxes;
        this.pickingController = pickingController;
        this.mainMouseInteraction = mainMouseInteraction;

        // 2×2 网格布局,2px 间隙
        setHgap(2);
        setVgap(2);

        for (int i = 0; i < 2; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(50);
            cc.setHgrow(Priority.ALWAYS);
            getColumnConstraints().add(cc);

            RowConstraints rc = new RowConstraints();
            rc.setPercentHeight(50);
            rc.setVgrow(Priority.ALWAYS);
            getRowConstraints().add(rc);
        }

        initializeViewports();
    }

    /**
     * 初始化 4 个视口:透视(主相机) + 正面 + 右面 + 顶部
     */
    private void initializeViewports() {
        createPerspectiveViewport();              // 视口 0: TL
        createOrthogonalViewport(1, false, false); // 视口 1: TR 正面(无旋转)
        createOrthogonalViewport(2, true, false);  // 视口 2: BL 右面(Y-90)
        createOrthogonalViewport(3, false, true);  // 视口 3: BR 底部(X-90)
    }

    /**
     * 创建视口0(透视主视角):使用主相机和 CameraSystem 的旋转 Affine.
     * <p>
     * root 作为 SubScene 的根节点,内部包含 modelContainer.
     * 相机设为主相机(cameraSystem.getCamera()),其变换链中的
     * RotationStrategy Affine 使此视口的旋转与主视图自动同步.
     * </p>
     */
    private void createPerspectiveViewport() {
        final int idx = 0;

        roots[idx] = new Group();
        modelContainers[idx] = new Group();
        roots[idx].getChildren().add(modelContainers[idx]);

        // 将主相机变换层级挂载到视口 0 的场景图中(从主 SubScene 移过来)
        roots[idx].getChildren().add(cameraSystem.getCameraRootTransform());

        subScenes[idx] = buildSubScene(roots[idx]);
        subScenes[idx].setCamera(cameraSystem.getCamera());
        subScenes[idx].setFill(Color.valueOf("#808080"));

        // 挂载主 MouseInteraction(共享旋转/平移/缩放)
        mainMouseInteraction.attachToSubScene(subScenes[idx]);

        // 构建 StackPane 容器(SubScene + 名称标签)
        viewPortStacks[idx] = buildViewPortStack(subScenes[idx], VIEWPORT_NAMES[idx]);
        setupActiveViewportClickHandler(idx);

        // 右下角迷你坐标轴(初始方向来自主相机旋转 Affine)
        miniAxes[idx] = new MiniAxes();
        miniAxes[idx].updateFromAffine(cameraSystem.getRotationStrategy().getRotationAffine());
        StackPane.setAlignment(miniAxes[idx].getCanvas(), Pos.BOTTOM_RIGHT);
        viewPortStacks[idx].getChildren().add(miniAxes[idx].getCanvas());

        add(viewPortStacks[idx], VIEWPORT_COLS[idx], VIEWPORT_ROWS[idx]);
    }

    /**
     * 创建正交视口(正面/右面/底部),每个拥有独立的 PerspectiveCamera.
     *
     * @param idx     视口索引 (1-3)
     * @param rotateY 是否绕 Y 轴旋转 -90 度(右面)
     * @param rotateX 是否绕 X 轴旋转 -90 度(顶部)
     */
    private void createOrthogonalViewport(int idx, boolean rotateY, boolean rotateX) {
        roots[idx] = new Group();
        modelContainers[idx] = new Group();
        roots[idx].getChildren().add(modelContainers[idx]);

        // 相机组(施加方向旋转)
        final int gIdx = idx - 1; // subCameras[] / cameraGroups[] 的索引
        cameraGroups[gIdx] = new Group();

        if (rotateY) {
            cameraGroups[gIdx].getTransforms().add(new Rotate(-90, Rotate.Y_AXIS));
        }
        if (rotateX) {
            cameraGroups[gIdx].getTransforms().add(new Rotate(-90, Rotate.X_AXIS));
        }
        // Z轴180度旋转补偿JavaFX的Y轴向下坐标系,使模型正立显示
        cameraGroups[gIdx].getTransforms().add(new Rotate(180, Rotate.Z_AXIS));

        subCameras[gIdx] = new PerspectiveCamera(true);
        subCameras[gIdx].setNearClip(CameraConfig.NEAR_CLIP);
        subCameras[gIdx].setFarClip(CameraConfig.FAR_CLIP);
        subCameras[gIdx].setTranslateZ(CameraConfig.INITIAL_DISTANCE);

        cameraGroups[gIdx].getChildren().add(subCameras[gIdx]);

        subScenes[idx] = buildSubScene(roots[idx]);
        subScenes[idx].setCamera(subCameras[gIdx]);
        subScenes[idx].setFill(Color.valueOf("#303030"));

        // 内联交互
        setupInlineInteraction(idx);

        viewPortStacks[idx] = buildViewPortStack(subScenes[idx], VIEWPORT_NAMES[idx]);
        setupActiveViewportClickHandler(idx);

        // 右下角迷你坐标轴(固定方向)
        miniAxes[idx] = new MiniAxes();
        if (idx == 1) {
            miniAxes[idx].setFixedAngles(0, 0);       // 正面:无旋转
        } else if (idx == 2) {
            miniAxes[idx].setFixedAngles(0, -90);      // 右面:绕 Y 轴 -90°
        } else {
            miniAxes[idx].setFixedAngles(-90, 0);      // 底部:绕 X 轴 -90°
        }
        StackPane.setAlignment(miniAxes[idx].getCanvas(), Pos.BOTTOM_RIGHT);
        viewPortStacks[idx].getChildren().add(miniAxes[idx].getCanvas());

        add(viewPortStacks[idx], VIEWPORT_COLS[idx], VIEWPORT_ROWS[idx]);
    }

    // ==================== 激活视口单击/双击 ====================

    /**
     * 创建一个 SubScene.
     */
    private SubScene buildSubScene(Group root) {
        SubScene ss = new SubScene(root, 400, 300, true, SceneAntialiasing.BALANCED);
        ss.setFill(Color.valueOf("#303030"));
        ss.setManaged(false); // 由 StackPane 控制大小
        return ss;
    }

    // ==================== 视口 1-3 内联交互 ====================

    /**
     * 构建 StackPane 容器:SubScene + 左上角半透明名称标签.
     */
    private StackPane buildViewPortStack(SubScene subScene, String name) {
        StackPane stack = new StackPane();
        stack.getChildren().add(subScene);

        // SubScene 尺寸跟随 StackPane
        subScene.widthProperty().bind(stack.widthProperty());
        subScene.heightProperty().bind(stack.heightProperty());

        // 名称标签(左上角,半透明背景)
        Label label = new Label(name);
        label.setStyle(
                "-fx-background-color: rgba(0,0,0,0.6);"
                        + "-fx-text-fill: white;"
                        + "-fx-padding: 4 8;"
                        + "-fx-font-size: 13;"
                        + "-fx-font-weight: bold;"
                        + "-fx-background-radius: 0 0 4 0;"
        );
        StackPane.setAlignment(label, Pos.TOP_LEFT);
        stack.getChildren().add(label);

        // 默认无边框
        stack.setStyle("-fx-border-color: transparent; -fx-border-width: 2px;");

        return stack;
    }

    // ==================== 激活 / 最大化 ====================

    /**
     * 为视口容器添加单击(激活)和双击(最大化/恢复)事件.
     */
    private void setupActiveViewportClickHandler(int idx) {
        viewPortStacks[idx].setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                if (e.getClickCount() == 1) {
                    setActiveViewport(idx);
                } else if (e.getClickCount() >= 2) {
                    toggleMaximize(idx);
                }
            }
        });
    }

    /**
     * 为视口1-3设置简单的内联鼠标交互:
     * - 左键拖拽:旋转 cameraGroup
     * - 右键拖拽:平移 modelContainer
     * - 滚轮:缩放 camera translateZ
     */
    private void setupInlineInteraction(int idx) {
        SubScene ss = subScenes[idx];
        final int gIdx = idx - 1;

        ss.setOnMousePressed(e -> {
            mousePosX[idx] = e.getSceneX();
            mousePosY[idx] = e.getSceneY();
            mouseOldX[idx] = mousePosX[idx];
            mouseOldY[idx] = mousePosY[idx];
        });

        ss.setOnMouseDragged(e -> {
            mouseOldX[idx] = mousePosX[idx];
            mouseOldY[idx] = mousePosY[idx];
            mousePosX[idx] = e.getSceneX();
            mousePosY[idx] = e.getSceneY();

            double deltaX = mousePosX[idx] - mouseOldX[idx];
            double deltaY = mousePosY[idx] - mouseOldY[idx];

            if (e.isPrimaryButtonDown()) {
                // 左键拖拽:旋转 cameraGroup
                Group camGroup = cameraGroups[gIdx];
                Rotate ry = new Rotate(-deltaX * InteractionConfig.MOUSE_SPEED, Rotate.Y_AXIS);
                Rotate rx = new Rotate(-deltaY * InteractionConfig.MOUSE_SPEED, Rotate.X_AXIS);
                camGroup.getTransforms().addAll(ry, rx);
            } else if (e.isSecondaryButtonDown()) {
                // 右键拖拽:平移模型
                Group container = modelContainers[idx];
                double factor = InteractionConfig.MOUSE_SPEED * InteractionConfig.TRACK_SPEED;
                if (e.isControlDown()) {
                    factor *= InteractionConfig.CONTROL_MULTIPLIER;
                }
                if (e.isShiftDown()) {
                    factor *= InteractionConfig.SHIFT_MULTIPLIER;
                }
                container.setTranslateX(container.getTranslateX() + deltaX * factor);
                container.setTranslateY(container.getTranslateY() + deltaY * factor);
            }
        });

        ss.setOnScroll((ScrollEvent e) -> {
            double delta = e.getDeltaY();
            PerspectiveCamera cam = subCameras[gIdx];
            double newZ = cam.getTranslateZ() - delta * InteractionConfig.MOUSE_SPEED * 0.1;
            newZ = Math.min(newZ, CameraConfig.MODEL_NEAR_CLIP);
            cam.setTranslateZ(newZ);
        });
    }

    // ==================== 模型克隆与分发 ====================

    /**
     * 切换最大化/恢复状态.
     * <p>
     * 最大化时:将指定视口跨所有行列,隐藏其他视口.
     * 恢复时:还原所有视口到原始位置.
     * </p>
     */
    private void toggleMaximize(int index) {
        if (maximized) {
            // === 恢复 ===
            // 先清空 GridPane,再重新添加所有视口
            getChildren().clear();
            for (int i = 0; i < NUM_VIEWPORTS; i++) {
                viewPortStacks[i].setVisible(true);
                viewPortStacks[i].setManaged(true);
                add(viewPortStacks[i], VIEWPORT_COLS[i], VIEWPORT_ROWS[i]);
                GridPane.setRowSpan(viewPortStacks[i], 1);
                GridPane.setColumnSpan(viewPortStacks[i], 1);
            }
            maximized = false;
            // 恢复后高亮该视口
            setActiveViewport(index);
        } else {
            // === 最大化 ===
            // 隐藏所有非目标视口
            for (int i = 0; i < NUM_VIEWPORTS; i++) {
                if (i != index) {
                    viewPortStacks[i].setVisible(false);
                    viewPortStacks[i].setManaged(false);
                }
            }
            // 移除所有子节点后重新添加目标视口,跨满网格
            getChildren().clear();
            add(viewPortStacks[index], 0, 0);
            GridPane.setRowSpan(viewPortStacks[index], GridPane.REMAINING);
            GridPane.setColumnSpan(viewPortStacks[index], GridPane.REMAINING);
            maximized = true;
            setActiveViewport(index);
        }
    }

    /**
     * 克隆分子模型组,并将克隆分发到所有4个视口.
     * <p>
     * 构建一个共享的模型子树(buildSharedModelGroup),
     * 再为每个视口的 modelContainer 创建独立的深拷贝.
     * </p>
     */
    public void populateFromModel() {
        this.sharedModel = buildSharedModelGroup();
        for (int i = 0; i < NUM_VIEWPORTS; i++) {
            modelContainers[i].getChildren().clear();
            Group clone = cloneNodeHierarchy(sharedModel);
            modelContainers[i].getChildren().add(clone);
        }
    }

    /**
     * 构建共享模型子树:
     * 从 moleculeGroup 克隆完整的节点树,跳过 "normalLines"(法线可视化).
     * <p>
     * 复制 moleculeGroup 的居中平移变换以及内部模型 Group 的 Rotate(-180, Z) 变换.
     * </p>
     */
    private Group buildSharedModelGroup() {
        Group result = new Group();
        // 复制 moleculeGroup 的 transforms 列表(含 Rotate/Scale 等)
        result.getTransforms().addAll(moleculeGroup.getTransforms());
        // 复制 Node 级别的居中平移(DragDropHandler 通过 setTranslateX/Y/Z 设置,不在 transforms 列表中)
        result.setTranslateX(moleculeGroup.getTranslateX());
        result.setTranslateY(moleculeGroup.getTranslateY());
        result.setTranslateZ(moleculeGroup.getTranslateZ());

        for (Node child : moleculeGroup.getChildren()) {
            // 跳过法线可视化组
            if ("normalLines".equals(child.getId())) {
                continue;
            }
            if (child instanceof Group groupChild) {
                result.getChildren().add(cloneNodeHierarchy(groupChild));
            } else if (child instanceof MeshView mesh) {
                // 直接挂载的 MeshView(异常情况,但也处理)
                MeshView copy = new MeshView(mesh.getMesh());
                copy.setMaterial(mesh.getMaterial());
                copy.setDrawMode(mesh.getDrawMode());
                copy.setCullFace(mesh.getCullFace());
                copy.getTransforms().addAll(mesh.getTransforms());
                result.getChildren().add(copy);
            }
        }
        return result;
    }

    /**
     * 递归克隆节点层级:Group → {MeshView | Group}
     * <p>
     * MeshView 共享同一个 TriangleMesh 引用,避免 GPU 内存浪费.
     * 所有变换(平移,旋转,缩放)均完整复制.
     * </p>
     *
     * @param source 源 Group
     * @return 克隆后的 Group
     */
    private Group cloneNodeHierarchy(Group source) {
        Group clone = new Group();
        clone.getTransforms().addAll(source.getTransforms());
        // 同时复制 Node 级别的平移(setTranslateX/Y/Z 不体现在 getTransforms() 中)
        clone.setTranslateX(source.getTranslateX());
        clone.setTranslateY(source.getTranslateY());
        clone.setTranslateZ(source.getTranslateZ());

        for (Node child : source.getChildren()) {
            if (child instanceof MeshView mesh) {
                MeshView copy = new MeshView(mesh.getMesh());
                copy.setMaterial(mesh.getMaterial());
                copy.setDrawMode(mesh.getDrawMode());
                copy.setCullFace(mesh.getCullFace());
                copy.getTransforms().addAll(mesh.getTransforms());
                clone.getChildren().add(copy);
            } else if (child instanceof Group groupChild) {
                clone.getChildren().add(cloneNodeHierarchy(groupChild));
            }
        }
        return clone;
    }

    // ==================== 环境光 ====================

    /**
     * 将主相机变换层级挂载到视口 0 并设置相机引用.
     * <p>
     * 仅在已有布局实例重新进入多视口模式时调用(初次由 createPerspectiveViewport 处理).
     * </p>
     */
    public void reattachCameraToViewport0() {
        // 确保相机变换层级在视口 0 的场景图中
        if (!roots[0].getChildren().contains(cameraSystem.getCameraRootTransform())) {
            roots[0].getChildren().add(cameraSystem.getCameraRootTransform());
        }
        subScenes[0].setCamera(cameraSystem.getCamera());
    }

    /**
     * 重置所有视口到默认视角.
     * <p>
     * 视口 0:通过 CameraSystem.resetCamera() 重置主相机.
     * 视口 1-3:清除用户拖拽累积的旋转,平移,恢复初始固定朝向和相机距离.
     * </p>
     */
    public void resetAllViewports() {
        // 视口 0:重置主相机
        cameraSystem.resetCamera();

        // 视口 1-3:重置独立相机
        for (int idx = 1; idx < NUM_VIEWPORTS; idx++) {
            int gIdx = idx - 1;
            if (cameraGroups[gIdx] == null) {
                continue;
            }

            // 清除所有变换(含用户拖拽累积的旋转)
            cameraGroups[gIdx].getTransforms().clear();

            // 重新施加初始固定朝向
            if (idx == 2) {
                cameraGroups[gIdx].getTransforms().add(new Rotate(-90, Rotate.Y_AXIS)); // 右面
            } else if (idx == 3) {
                cameraGroups[gIdx].getTransforms().add(new Rotate(-90, Rotate.X_AXIS)); // 底部
            }
            // idx == 1 正面:无方向旋转
            // Z轴180度旋转补偿JavaFX的Y轴向下坐标系
            cameraGroups[gIdx].getTransforms().add(new Rotate(180, Rotate.Z_AXIS));

            // 重置相机距离
            if (subCameras[gIdx] != null) {
                subCameras[gIdx].setTranslateZ(CameraConfig.INITIAL_DISTANCE);
            }

            // 重置模型平移
            modelContainers[idx].setTranslateX(0);
            modelContainers[idx].setTranslateY(0);
            modelContainers[idx].setTranslateZ(0);
        }

        // 同步迷你坐标轴
        syncViewport0MiniAxes();
        for (int i = 1; i < NUM_VIEWPORTS; i++) {
            if (miniAxes[i] != null) {
                if (i == 1) {
                    miniAxes[i].setFixedAngles(0, 0);
                } else if (i == 2) {
                    miniAxes[i].setFixedAngles(0, -90);
                } else {
                    miniAxes[i].setFixedAngles(-90, 0);
                }
            }
        }
    }

    /**
     * 切换所有四个视口的环境光开关.
     */
    public void toggleLighting() {
        isLightOn = !isLightOn;
        if (isLightOn) {
            for (int i = 0; i < NUM_VIEWPORTS; i++) {
                AmbientLight light = new AmbientLight(Color.WHITE);
                // 空 scope = 影响该 SubScene 内所有节点
                roots[i].getChildren().add(light);
                ambientLights[i] = light;
            }
        } else {
            for (int i = 0; i < NUM_VIEWPORTS; i++) {
                if (ambientLights[i] != null) {
                    roots[i].getChildren().remove(ambientLights[i]);
                    ambientLights[i] = null;
                }
            }
        }
    }

    // ==================== 公共方法 ====================

    /**
     * 同步视口 0 的迷你坐标轴(在旋转策略 Affine 更新后调用).
     */
    public void syncViewport0MiniAxes() {
        if (miniAxes[0] != null) {
            miniAxes[0].updateFromAffine(cameraSystem.getRotationStrategy().getRotationAffine());
        }
    }

    /**
     * 获取视口0的 SubScene(主透视视口).
     */
    public SubScene getMainSubScene() {
        return subScenes[0];
    }

    /**
     * 获取视口0的模型容器 Group(用于平移同步等操作).
     */
    public Group getMainModelContainer() {
        return modelContainers[0];
    }

    /**
     * 将 GridPane 的宽高绑定到指定的 Pane 上.
     *
     * @param pane 目标面板
     */
    public void bindToPane(Pane pane) {
        prefWidthProperty().bind(pane.widthProperty());
        prefHeightProperty().bind(pane.heightProperty());
    }

    /**
     * 当前激活的视口索引(-1 表示无激活视口).
     */
    public int getActiveViewport() {
        return activeViewport;
    }

    // ==================== 只读查询 ====================

    /**
     * 设置激活视口(CORNFLOWERBLUE 边框高亮).
     */
    private void setActiveViewport(int index) {
        if (maximized) {
            return;
        }

        // 取消上一个激活视口的高亮
        if (activeViewport >= 0 && activeViewport < NUM_VIEWPORTS) {
            viewPortStacks[activeViewport].setStyle(
                    "-fx-border-color: transparent; -fx-border-width: 2px;"
            );
        }

        activeViewport = index;
        viewPortStacks[index].setStyle(
                "-fx-border-color: cornflowerblue; -fx-border-width: 2px;"
        );
    }

    /**
     * 是否处于最大化模式.
     */
    public boolean isMaximized() {
        return maximized;
    }
}
