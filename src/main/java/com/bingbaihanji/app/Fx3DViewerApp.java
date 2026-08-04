package com.bingbaihanji.app;

import com.bingbaihanji.WindowsThemeJavaFXApp;
import com.bingbaihanji.animation.AutoRotationAnimation;
import com.bingbaihanji.camera.CameraSystem;
import com.bingbaihanji.interaction.DragDropHandler;
import com.bingbaihanji.interaction.KeyboardInteraction;
import com.bingbaihanji.interaction.MouseInteraction;
import com.bingbaihanji.interaction.PickingController;
import com.bingbaihanji.lighting.LightManager;
import com.bingbaihanji.loading.ImporterRegistry;
import com.bingbaihanji.loading.ModelLoadService;
import com.bingbaihanji.loading.ObjImporter;
import com.bingbaihanji.menu.MenuEvent;
import com.bingbaihanji.menu.MenuNode;
import com.bingbaihanji.rotation.QuaternionRotation;
import com.bingbaihanji.rotation.RotationStrategy;
import com.bingbaihanji.scene.Scene3DManager;
import com.bingbaihanji.ui.MainLayout;
import com.bingbaihanji.ui.ModelInfoPanel;
import com.bingbaihanji.ui.StatusBar;
import com.bingbaihanji.view.ViewingAxes;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;

/**
 * 统一的3D查看器应用程序
 * <p>
 * 通过策略模式支持不同的旋转算法(四元数/矩阵)
 * 使用 ViewerComponents 聚合核心组件,将启动过程拆分为小方法
 * </p>
 *
 * @author bingbaihanji
 */
@Slf4j
public class Fx3DViewerApp extends WindowsThemeJavaFXApp {

    private final RotationStrategy rotationStrategy;

    private final String windowTitle;

    private final ViewerComponents c = new ViewerComponents();

    /**
     * 无参构造器,供 GraalVM native-image 反射实例化使用
     */
    public Fx3DViewerApp() {
        this(new QuaternionRotation(), "Fx3DView");
    }

    public Fx3DViewerApp(RotationStrategy rotationStrategy, String windowTitle) {
        this.rotationStrategy = rotationStrategy;
        this.windowTitle = windowTitle;
    }

    @Override
    public void startWindowsThemeUI(Stage primaryStage) {
        log.info("启动 {} 版本", rotationStrategy.getStrategyName());

        createCoreComponents();
        addCameraToScene();
        createInteractions();
        createMenuAndLayout();
        attachDragDrop();
        addThemeToggleMenu(primaryStage);
        bindInteractions(primaryStage);
        configureWindow(primaryStage);
        setupMenuFunctions(primaryStage);
        syncViewingAxes();
        setupMiniAxesSync();

        log.info("应用程序启动完成");
    }

    /**
     * 创建核心组件并注入旋转策略
     */
    private void createCoreComponents() {
        c.sceneManager = new Scene3DManager();
        c.cameraSystem = new CameraSystem(rotationStrategy);
        c.viewingAxes = new ViewingAxes(180.0);
        c.lightManager = new LightManager(c.sceneManager.getWorld());
        // 方向光默认关闭,不调用 attachToScene(),由用户通过菜单手动开启
        c.statusBar = new StatusBar(c.cameraSystem, c.sceneManager);
        c.modelInfoPanel = new ModelInfoPanel();

        // 创建导入器注册表并注册 OBJ 格式(新增格式在此注册即可)
        c.importerRegistry = new ImporterRegistry();
        c.importerRegistry.register(ObjImporter.SUPPORTED_EXT, ObjImporter::new);
        c.modelLoadService = new ModelLoadService(
                c.sceneManager.getWorld(),
                c.sceneManager.getMoleculeGroup(),
                c.importerRegistry,
                this::beforeModelInstall,
                this::onModelLoaded,
                task -> c.statusBar.bindToLoadingTask(task));
    }

    /**
     * 将相机添加到场景图
     */
    private void addCameraToScene() {
        c.sceneManager.getRoot().getChildren().add(c.cameraSystem.getCameraRootTransform());
    }

    /**
     * 创建交互处理器
     */
    private void createInteractions() {
        c.pickingController = new PickingController();
        c.mouseInteraction = new MouseInteraction(c.cameraSystem, c.viewingAxes, c.pickingController);
        c.keyboardInteraction = new KeyboardInteraction(c.cameraSystem, c.sceneManager, c.viewingAxes);
        c.keyboardInteraction.setOnMultiViewportToggle(() -> c.mainLayout.toggleMultiViewport());
    }

    /**
     * 创建菜单和UI布局
     */
    private void createMenuAndLayout() {
        // 菜单事件处理器(实例级状态管理)
        c.menuEvent = new MenuEvent();
        c.menuNode = new MenuNode();

        // 根据当前策略预选旋转引擎菜单项
        String strategyName = rotationStrategy.getStrategyName();
        if (strategyName.contains("四元数")) {
            c.menuNode.getEngineQuaternion().setSelected(true);
        } else {
            c.menuNode.getEngineMatrix().setSelected(true);
        }

        // 创建UI布局
        c.mainLayout = new MainLayout(c.sceneManager, c.cameraSystem, c.viewingAxes,
                c.menuNode.getMenuBar(), c.statusBar, c.modelInfoPanel,
                c.mouseInteraction, c.pickingController);
    }

    /**
     * 绑定拖放加载(含进度条绑定)
     */
    private void attachDragDrop() {
        c.dragDrop = new DragDropHandler(c.modelLoadService, c.importerRegistry);
        c.dragDrop.attachToPane(c.mainLayout.getCenterPane());
    }

    /**
     * 安装新模型前清理依赖旧模型节点的可视化状态.
     */
    private void beforeModelInstall() {
        c.menuEvent.resetModelVisualization(
                c.menuNode, c.sceneManager.getMoleculeGroup(), c.sceneManager.getWorld());
    }

    /**
     * 模型加载完成后的UI更新回调
     */
    private void onModelLoaded() {
        c.statusBar.updateModelStats();
        c.sceneManager.rebuildBoundingBox(c.sceneManager.getMoleculeGroup().getBoundsInParent());
        c.sceneManager.rebuildNormals();
        c.modelInfoPanel.updateFromModel(c.sceneManager.getMoleculeGroup(), "已加载模型");
    }

    /**
     * 添加主题切换菜单
     */
    private void addThemeToggleMenu(Stage primaryStage) {
        MenuBar menuBar = c.menuNode.getMenuBar();
        for (Menu menu : menuBar.getMenus()) {
            if (menu.getGraphic() instanceof Label label && "视图".equals(label.getText())) {
                MenuItem wTheme = new MenuItem("切换主题");
                wTheme.setOnAction(event -> toggleTheme(primaryStage));
                menu.getItems().add(wTheme);
                break;
            }
        }
    }

    /**
     * 切换主题(标题栏 + 菜单 + 背景 + 状态栏 + 信息面板)
     */
    private void toggleTheme(Stage primaryStage) {
        toggleDarkTitleBar(primaryStage);
        c.menuNode.toggleMenuTheme();
        setThemeBackground(primaryStage, c.mainLayout.getSubScene());
        c.statusBar.applyTheme(c.menuNode.isDarkTheme());
        c.modelInfoPanel.applyTheme(c.menuNode.isDarkTheme());
    }

    /**
     * 根据主题设置场景背景色
     */
    private void setThemeBackground(Stage primaryStage, SubScene subScene) {
        subScene.setFill(isDark(primaryStage) ? Color.valueOf("#808080") : Color.valueOf("#e6e6e6"));
    }

    /**
     * 绑定输入事件到场景
     */
    private void bindInteractions(Stage primaryStage) {
        c.mouseInteraction.attachToSubScene(c.mainLayout.getSubScene());
        Scene scene = new Scene(c.mainLayout.getRootPane());
        c.keyboardInteraction.attachToScene(scene);
        primaryStage.setScene(scene);
    }

    /**
     * 配置窗口属性
     */
    private void configureWindow(Stage primaryStage) {
        setWindowIcon(primaryStage);
        primaryStage.setTitle(windowTitle + " - " + rotationStrategy.getStrategyName());
        primaryStage.show();
    }

    /**
     * 注册所有菜单功能
     */
    private void setupMenuFunctions(Stage primaryStage) {
        SubScene subScene = c.mainLayout.getSubScene();
        c.menuEvent.import3DModel(primaryStage, c.menuNode,
                c.modelLoadService,
                c.importerRegistry);
        c.menuEvent.screenshots(primaryStage, c.menuNode, subScene);
        c.menuEvent.setBackgroundColor(c.menuNode, subScene);
        c.menuEvent.menuSetupLighting(c.menuNode, c.sceneManager.getWorld(), c.mainLayout);
        c.menuEvent.menuSetDrawMode(c.menuNode, c.sceneManager.getMoleculeGroup());
        c.menuEvent.menuSetDotPlots(c.menuNode, c.sceneManager.getMoleculeGroup());
        c.menuEvent.menuToggleBoundingBox(c.menuNode, c.sceneManager);
        c.menuEvent.menuToggleNormals(c.menuNode, c.sceneManager);
        c.menuEvent.menuToggleBackFaceCulling(c.menuNode, c.sceneManager.getMoleculeGroup());
        c.menuEvent.menuToggleModelInfo(c.menuNode, c.modelInfoPanel);
        c.menuEvent.setupShadingModes(c.menuNode, c.sceneManager.getMoleculeGroup());
        c.menuEvent.menuToggleDirectionalLight(c.menuNode, c.lightManager);
        c.menuEvent.menuDirectionalLight(c.menuNode, c.lightManager);
        c.menuEvent.menuClearModel(c.menuNode, c.sceneManager, () -> {
            c.modelLoadService.cancelCurrentLoad();
            c.statusBar.updateModelStats();
            c.modelInfoPanel.setVisible(false);
            c.modelInfoPanel.setManaged(false);
        });
        c.menuEvent.setupPresetViews(c.menuNode, c.cameraSystem, c.viewingAxes);
        c.menuEvent.menuToggleOrtho(c.menuNode, c.cameraSystem, subScene);
        // 同步菜单勾选状态与相机实际投影模式(多视口切换等情况可能程序化切回透视)
        c.cameraSystem.orthographicProperty().addListener((obs, old, isOrtho) ->
                c.menuNode.getOrthoToggle().setSelected(isOrtho));
        c.menuEvent.menuResetView(c.menuNode, c.cameraSystem, c.viewingAxes, c.mainLayout);
        c.menuEvent.menuToggleMultiView(c.menuNode, c.mainLayout);
        c.menuEvent.setupRotationEngineMenu(c.menuNode, c.cameraSystem, c.viewingAxes,
                () -> updateStrategyUI(primaryStage),
                () -> updateStrategyUI(primaryStage));

        primaryStage.setOnCloseRequest(event -> disposeComponents());
    }

    /**
     * 旋转策略切换后更新UI
     */
    private void updateStrategyUI(Stage primaryStage) {
        c.statusBar.updateStrategyLabel();
        primaryStage.setTitle(windowTitle + " - " + c.cameraSystem.getRotationStrategy().getStrategyName());
    }

    /**
     * 初始同步辅助轴
     */
    private void syncViewingAxes() {
        c.viewingAxes.updateAxes(c.cameraSystem.getRotationStrategy().getRotationAffine());
    }

    /**
     * 设置拖拽旋转后的同步回调(迷你轴 + 点云 billboard)
     */
    private void setupMiniAxesSync() {
        c.mouseInteraction.setOnRotationUpdated(() -> {
            // 同步迷你轴
            if (c.mainLayout.isMultiViewport() && c.mainLayout.getMultiViewportLayout() != null) {
                c.mainLayout.getMultiViewportLayout().syncViewport0MiniAxes();
            }
            // 刷新点云 billboard 方向,使其始终面朝相机
            c.menuEvent.refreshPointCloud(
                    c.cameraSystem.getRotationStrategy().getRotationAffine());
        });
    }

    /**
     * 设置窗口图标
     */
    private void setWindowIcon(Stage primaryStage) {
        URL iconUrl = getClass().getResource("/icon.png");
        if (iconUrl != null) {
            primaryStage.getIcons().add(new Image(iconUrl.toString()));
        } else {
            log.warn("窗口图标文件未找到");
        }
    }

    // ==================== Getters ====================

    public Scene3DManager getSceneManager() {
        return c.sceneManager;
    }

    public CameraSystem getCameraSystem() {
        return c.cameraSystem;
    }

    public ViewingAxes getViewingAxes() {
        return c.viewingAxes;
    }

    public AutoRotationAnimation getAutoRotation() {
        return c.keyboardInteraction.getAutoRotation();
    }

    private void disposeComponents() {
        if (c.modelLoadService != null) {
            c.modelLoadService.dispose();
        }
        if (c.dragDrop != null) {
            c.dragDrop.dispose();
        }
        if (c.keyboardInteraction != null) {
            c.keyboardInteraction.dispose();
        }
        if (c.statusBar != null) {
            c.statusBar.dispose();
        }
    }
}
