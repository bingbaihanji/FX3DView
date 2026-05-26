package com.bingbaihanji.menu;

import com.bingbaihanji.camera.CameraSystem;
import com.bingbaihanji.camera.ViewPreset;
import com.bingbaihanji.interaction.DragDropHandler;
import com.bingbaihanji.lighting.LightManager;
import com.bingbaihanji.rotation.MatrixRotation;
import com.bingbaihanji.rotation.QuaternionRotation;
import com.bingbaihanji.scene.Scene3DManager;
import com.bingbaihanji.ui.MainLayout;
import com.bingbaihanji.view.ViewingAxes;
import javafx.concurrent.Task;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.SubScene;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.function.Consumer;

/**
 * 菜单事件门面（实例级，支持多窗口独立状态）
 * <p>
 * 将具体操作委托给各专用处理器：
 * - {@link ScreenshotHandler} — 截图、背景色
 * - {@link PointCloudHandler} — 点云模式
 * - {@link LightingEventHandler} — 环境光
 * - {@link ShadingHandler} — 着色模式
 * </p>
 *
 * @author bingbaihanji
 */
@Slf4j
public class MenuEvent {

    /**
     * 着色模式处理器
     */
    private final ShadingHandler shadingHandler = new ShadingHandler();
    /**
     * 截图处理器
     */
    private final ScreenshotHandler screenshotHandler = new ScreenshotHandler();
    /**
     * 点云处理器
     */
    private final PointCloudHandler pointCloudHandler = new PointCloudHandler();
    /**
     * 环境光处理器
     */
    private final LightingEventHandler lightingEventHandler = new LightingEventHandler();
    /**
     * 是否为线框模式
     */
    private boolean isDrawModeIsLINE = false;
    /**
     * 是否进行背面剔除
     */
    private boolean isCullFace = false;

    // ==================== 菜单事件注册方法（委托给Handler） ====================

    /**
     * 递归遍历设置DrawMode
     */
    private static void traverseAndSetDrawMode(Node node, DrawMode targetMode) {
        if (node instanceof MeshView meshView) {
            meshView.setDrawMode(targetMode);
        } else if (node instanceof Group group) {
            for (Node child : group.getChildren()) {
                traverseAndSetDrawMode(child, targetMode);
            }
        }
    }

    /**
     * 递归查找所有MeshView并执行回调
     */
    private static void traverseAllMeshViews(Node node, Consumer<MeshView> callback) {
        if (node instanceof MeshView meshView) {
            callback.accept(meshView);
        } else if (node instanceof Group group) {
            for (Node child : group.getChildren()) {
                traverseAllMeshViews(child, callback);
            }
        }
    }

    /**
     * 递归遍历设置PolygonMeshView的CullFace
     */
    private static void traverseAndSetCullFace(Node node, CullFace targetCullFace) {
        if (node instanceof com.bingbaihanji.loading.PolygonMeshView polygonMeshView) {
            polygonMeshView.setCullFace(targetCullFace);
        } else if (node instanceof Group group) {
            for (Node child : group.getChildren()) {
                traverseAndSetCullFace(child, targetCullFace);
            }
        }
    }

    /**
     * 导入3D模型（含进度回调）
     */
    public void import3DModel(Stage primaryStage, MenuNode menuNode,
                              Group world, Group moleculeGroup,
                              Runnable onModelLoaded,
                              Consumer<Task<?>> onTaskCreated) {
        menuNode.getImportObj().setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Wavefront OBJ (*.obj)", "*.obj"));
            File filePath = fileChooser.showOpenDialog(primaryStage);

            if (filePath != null) {
                DragDropHandler.loadModelFile(
                        filePath, world, moleculeGroup, onModelLoaded,
                        task -> {
                            if (onTaskCreated != null) {
                                onTaskCreated.accept(task);
                            }
                        });
            }
        });
    }

    /**
     * 环境光菜单（委托给 LightingEventHandler，多视口模式下分发光源到所有视口）
     */
    public void menuSetupLighting(MenuNode menuNode, Group world, MainLayout mainLayout) {
        menuNode.getSetupLighting().setOnAction(event -> {
            if (mainLayout.isMultiViewport() && mainLayout.getMultiViewportLayout() != null) {
                mainLayout.getMultiViewportLayout().toggleLighting();
            } else {
                lightingEventHandler.setupLighting(world);
            }
        });
    }

    /**
     * 线框菜单
     */
    public void menuSetDrawMode(MenuNode menuNode, Group world) {
        menuNode.getWireframes().setOnAction(event -> setModelDrawMode(world));
    }

    /**
     * 点云菜单（委托给 PointCloudHandler）
     */
    public void menuSetDotPlots(MenuNode menuNode, Group world) {
        menuNode.getPlotsMode().setOnAction(event -> pointCloudHandler.toggleDotPlots(world));
    }

    /**
     * 包围盒菜单
     */
    public void menuToggleBoundingBox(MenuNode menuNode, Scene3DManager sceneManager) {
        menuNode.getBoundingBoxToggle().setOnAction(event -> sceneManager.toggleBoundingBox());
    }

    /**
     * 法线可视化菜单
     */
    public void menuToggleNormals(MenuNode menuNode, Scene3DManager sceneManager) {
        menuNode.getNormalToggle().setOnAction(event -> sceneManager.toggleNormalVisibility());
    }

    /**
     * 背面剔除菜单
     */
    public void menuToggleBackFaceCulling(MenuNode menuNode, Group moleculeGroup) {
        menuNode.getBackFaceCullingToggle().setOnAction(event -> setCullFace(moleculeGroup));
    }

    /**
     * 旋转引擎切换菜单
     */
    public void setupRotationEngineMenu(MenuNode menuNode, CameraSystem cameraSystem,
                                        ViewingAxes viewingAxes,
                                        Runnable onQuaternion, Runnable onMatrix) {
        menuNode.getEngineQuaternion().setOnAction(e -> {
            cameraSystem.setRotationStrategy(new QuaternionRotation());
            viewingAxes.updateAxes(cameraSystem.getRotationStrategy().getRotationAffine());
            onQuaternion.run();
        });
        menuNode.getEngineMatrix().setOnAction(e -> {
            cameraSystem.setRotationStrategy(new MatrixRotation());
            viewingAxes.updateAxes(cameraSystem.getRotationStrategy().getRotationAffine());
            onMatrix.run();
        });
    }

    /**
     * 重置视角菜单（与快捷键 Z 功能一致，支持多视口模式）
     */
    public void menuResetView(MenuNode menuNode, CameraSystem cameraSystem,
                              ViewingAxes viewingAxes, MainLayout mainLayout) {
        menuNode.getResetView().setOnAction(event -> {
            if (mainLayout.isMultiViewport() && mainLayout.getMultiViewportLayout() != null) {
                // 多视口模式：重置所有四个视口
                mainLayout.getMultiViewportLayout().resetAllViewports();
            } else {
                // 单视图模式：与快捷键 Z 一致
                cameraSystem.resetCamera();
            }
            viewingAxes.updateAxes(cameraSystem.getRotationStrategy().getRotationAffine());
            log.info("视角已重置");
        });
    }

    /**
     * 多视口切换菜单
     */
    public void menuToggleMultiView(MenuNode menuNode, MainLayout mainLayout) {
        menuNode.getMultiViewToggle().setOnAction(event -> mainLayout.toggleMultiViewport());
    }

    /**
     * 正交投影切换菜单
     */
    public void menuToggleOrtho(MenuNode menuNode, CameraSystem cameraSystem, SubScene subScene) {
        menuNode.getOrthoToggle().setOnAction(event -> cameraSystem.toggleProjection(subScene));
    }

    /**
     * 模型信息面板菜单
     */
    public void menuToggleModelInfo(MenuNode menuNode, javafx.scene.Node infoPanel) {
        menuNode.getModelInfoToggle().setOnAction(event -> {
            boolean show = !infoPanel.isVisible();
            infoPanel.setVisible(show);
            infoPanel.setManaged(show);
        });
    }

    /**
     * 背景颜色菜单（委托给 ScreenshotHandler）
     */
    public void setBackgroundColor(MenuNode menuNode, SubScene subScene) {
        screenshotHandler.setBackgroundColor(menuNode, subScene);
    }

    /**
     * 截图菜单（委托给 ScreenshotHandler）
     */
    public void screenshots(Stage primaryStage, MenuNode menuNode, SubScene subScene) {
        screenshotHandler.screenshots(primaryStage, menuNode, subScene);
    }

    /**
     * 预设视角菜单
     */
    public void setupPresetViews(MenuNode menuNode, CameraSystem cameraSystem, ViewingAxes viewingAxes) {
        menuNode.getViewFront().setOnAction(e -> applyPreset(cameraSystem, viewingAxes, ViewPreset.FRONT));
        menuNode.getViewBack().setOnAction(e -> applyPreset(cameraSystem, viewingAxes, ViewPreset.BACK));
        menuNode.getViewLeft().setOnAction(e -> applyPreset(cameraSystem, viewingAxes, ViewPreset.LEFT));
        menuNode.getViewRight().setOnAction(e -> applyPreset(cameraSystem, viewingAxes, ViewPreset.RIGHT));
        menuNode.getViewTop().setOnAction(e -> applyPreset(cameraSystem, viewingAxes, ViewPreset.TOP));
        menuNode.getViewBottom().setOnAction(e -> applyPreset(cameraSystem, viewingAxes, ViewPreset.BOTTOM));
    }

    private void applyPreset(CameraSystem cameraSystem, ViewingAxes viewingAxes, ViewPreset preset) {
        cameraSystem.setPresetView(preset);
        viewingAxes.updateAxes(cameraSystem.getRotationStrategy().getRotationAffine());
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 方向光开关菜单
     */
    public void menuToggleDirectionalLight(MenuNode menuNode, LightManager lightManager) {
        menuNode.getDirectionalLightToggle().setOnAction(event -> lightManager.toggle());
    }

    /**
     * 方向光设置菜单
     */
    public void menuDirectionalLight(MenuNode menuNode, LightManager lightManager) {
        menuNode.getDirectionalLight().setOnAction(event -> lightManager.openControlDialog());
    }

    /**
     * 清空模型菜单
     * <p>
     * 清空当前加载的模型、包围盒和法线可视化，
     * 重置线框/背面剔除等内部状态。
     * </p>
     *
     * @param menuNode     菜单节点
     * @param sceneManager 场景管理器
     * @param onCleared    清空后的UI更新回调（状态栏、信息面板等）
     */
    public void menuClearModel(MenuNode menuNode, Scene3DManager sceneManager, Runnable onCleared) {
        menuNode.getClearModel().setOnAction(event -> {
            sceneManager.clearModel();
            // 重置内部状态
            isDrawModeIsLINE = false;
            isCullFace = false;
            if (onCleared != null) {
                onCleared.run();
            }
            log.info("模型已清空");
        });
    }

    // ==================== 静态工具方法 ====================

    /**
     * 着色模式菜单（委托给 ShadingHandler）
     */
    public void setupShadingModes(MenuNode menuNode, Group moleculeGroup) {
        shadingHandler.setupShadingModes(menuNode, moleculeGroup);
    }

    /**
     * 切换线框/实体渲染模式
     * <p>
     * 递归遍历所有子节点，统一切换MeshView的DrawMode
     * </p>
     */
    public void setModelDrawMode(Group modelGroup) {
        DrawMode targetMode = isDrawModeIsLINE ? DrawMode.FILL : DrawMode.LINE;
        traverseAndSetDrawMode(modelGroup, targetMode);
        isDrawModeIsLINE = !isDrawModeIsLINE;
        log.info(isDrawModeIsLINE ? "线框模式" : "面模式");
    }

    /**
     * 切换背面剔除模式
     */
    public void setCullFace(Group modelGroup) {
        isCullFace = !isCullFace;
        CullFace targetCullFace = isCullFace ? CullFace.BACK : CullFace.NONE;

        log.info(isCullFace ? "背面剔除已开启" : "背面剔除已关闭");

        traverseAllMeshViews(modelGroup, meshView ->
                meshView.setCullFace(targetCullFace));

        traverseAndSetCullFace(modelGroup, targetCullFace);
    }
}
