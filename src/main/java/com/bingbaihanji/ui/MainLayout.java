package com.bingbaihanji.ui;

import com.bingbaihanji.camera.CameraSystem;
import com.bingbaihanji.interaction.MouseInteraction;
import com.bingbaihanji.interaction.PickingController;
import com.bingbaihanji.scene.Scene3DManager;
import com.bingbaihanji.view.ViewingAxes;
import javafx.scene.Group;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.MenuBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

/**
 * 主布局管理器
 * <p>
 * 负责组装应用程序的UI布局：
 * - 顶部：菜单栏
 * - 中心：3D子场景 + 辅助轴画布
 * </p>
 *
 * @author bingbaihanji
 */
public class MainLayout {

    private final BorderPane rootPane = new BorderPane();
    private final Pane centerPane = new Pane();
    private final SubScene subScene;
    private final Canvas axesCanvas;
    private final MenuBar menuBar;
    private final StatusBar statusBar;
    private final ModelInfoPanel modelInfoPanel;
    private final Scene3DManager sceneManager;
    private final CameraSystem cameraSystem;
    private final MouseInteraction mouseInteraction;
    private final PickingController pickingController;
    private final ViewingAxes viewingAxes;
    private MultiViewportLayout multiViewportLayout;
    private boolean isMultiViewport = false;

    /**
     * 构造函数
     *
     * @param sceneManager 场景管理器
     * @param cameraSystem 相机系统
     * @param viewingAxes  辅助轴视图
     * @param menuBar      菜单栏（已配置好的）
     * @param statusBar    状态栏
     */
    public MainLayout(Scene3DManager sceneManager,
                      CameraSystem cameraSystem,
                      ViewingAxes viewingAxes,
                      MenuBar menuBar,
                      StatusBar statusBar,
                      ModelInfoPanel modelInfoPanel,
                      MouseInteraction mouseInteraction,
                      PickingController pickingController) {

        // 创建3D子场景
        this.subScene = new SubScene(
                sceneManager.getRoot(),
                800, 600,
                true,
                SceneAntialiasing.BALANCED
        );
        subScene.setCamera(cameraSystem.getCamera());
        subScene.setFill(Color.valueOf("#808080"));

        // 辅助轴画布
        this.axesCanvas = viewingAxes.getCanvas();
        setupAxesCanvas();

        // 布局组装
        centerPane.setPrefSize(800, 600);
        centerPane.getChildren().addAll(subScene, axesCanvas);
        axesCanvas.setLayoutX(15);
        axesCanvas.setLayoutY(15);

        // 用监听器替代 bind，避免初始化时 centerPane 尺寸为 0 导致 SubScene 零尺寸
        centerPane.widthProperty().addListener((obs, old, w) -> {
            double width = w.doubleValue();
            if (width > 0) {
                subScene.setWidth(width);
                cameraSystem.syncOrthoScale(subScene);
            }
        });
        centerPane.heightProperty().addListener((obs, old, h) -> {
            double height = h.doubleValue();
            if (height > 0) {
                subScene.setHeight(height);
                cameraSystem.syncOrthoScale(subScene);
            }
        });

        // 菜单栏（直接使用传入的已配置好的MenuBar）
        this.menuBar = menuBar;

        this.sceneManager = sceneManager;
        this.cameraSystem = cameraSystem;
        this.statusBar = statusBar;
        this.modelInfoPanel = modelInfoPanel;
        this.mouseInteraction = mouseInteraction;
        this.pickingController = pickingController;
        this.viewingAxes = viewingAxes;
        rootPane.setCenter(centerPane);
        rootPane.setTop(menuBar);
        rootPane.setBottom(statusBar);
        rootPane.setRight(modelInfoPanel);
        modelInfoPanel.setVisible(false);
        modelInfoPanel.setManaged(false);
    }

    /**
     * 配置辅助轴画布
     */
    private void setupAxesCanvas() {
        axesCanvas.setMouseTransparent(true);
        axesCanvas.setTranslateX(10);
        axesCanvas.setTranslateY(-10);
        axesCanvas.setStyle("-fx-background-color: rgba(255,255,255,0.8);");
    }

    /**
     * 获取根面板
     */
    public BorderPane getRootPane() {
        return rootPane;
    }

    /**
     * 获取3D子场景
     */
    public SubScene getSubScene() {
        return subScene;
    }

    /**
     * 获取中心面板
     */
    public Pane getCenterPane() {
        return centerPane;
    }

    /**
     * 获取菜单栏
     */
    public MenuBar getMenuBar() {
        return menuBar;
    }

    /**
     * 获取状态栏
     */
    public StatusBar getStatusBar() {
        return statusBar;
    }

    /**
     * 获取模型信息面板
     */
    public ModelInfoPanel getModelInfoPanel() {
        return modelInfoPanel;
    }

    /**
     * 切换多视口模式
     */
    public void toggleMultiViewport() {
        isMultiViewport = !isMultiViewport;

        if (isMultiViewport) {
            // 1. 将相机变换层级从主场景图移到视口 0（Camera 节点不能同时属于两个场景图）
            sceneManager.getRoot().getChildren().remove(cameraSystem.getCameraRootTransform());
            // 2. 释放主 SubScene 的相机引用（JavaFX Camera 只能属于一个 SubScene）
            subScene.setCamera(null);

            if (multiViewportLayout == null) {
                multiViewportLayout = new MultiViewportLayout(
                        cameraSystem,
                        sceneManager.getMoleculeGroup(),
                        viewingAxes,
                        pickingController,
                        mouseInteraction
                );
                multiViewportLayout.populateFromModel();
                multiViewportLayout.bindToPane(centerPane);
            } else {
                // 已有布局：将相机层级移回视口 0 并重新挂载相机引用
                multiViewportLayout.reattachCameraToViewport0();
                multiViewportLayout.populateFromModel();
            }
            centerPane.getChildren().clear();
            centerPane.getChildren().add(multiViewportLayout);
        } else {
            // 切回单视图：释放视口 0 的相机引用
            if (multiViewportLayout != null && multiViewportLayout.getMainSubScene() != null) {
                multiViewportLayout.getMainSubScene().setCamera(null);
            }
            // 将相机变换层级从视口 0 移回主场景图
            if (cameraSystem.getCameraRootTransform().getParent() instanceof Group g) {
                g.getChildren().remove(cameraSystem.getCameraRootTransform());
            }
            sceneManager.getRoot().getChildren().add(cameraSystem.getCameraRootTransform());
            // 恢复主 SubScene 的相机引用
            subScene.setCamera(cameraSystem.getCamera());

            centerPane.getChildren().clear();
            centerPane.getChildren().addAll(subScene, axesCanvas);
            // 切回单视图后重新绑定鼠标交互到主 SubScene
            mouseInteraction.attachToSubScene(subScene);
        }
    }

    /**
     * 是否为多视口模式
     */
    public boolean isMultiViewport() {
        return isMultiViewport;
    }

    /**
     * 获取多视口布局（可能为 null）
     */
    public MultiViewportLayout getMultiViewportLayout() {
        return multiViewportLayout;
    }
}
