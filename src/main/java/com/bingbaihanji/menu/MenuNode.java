package com.bingbaihanji.menu;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author 冰白寒祭
 * @date 2024-11-05
 * @description 支持暗/亮主题切换的菜单栏
 */
public class MenuNode {

    private final LinearGradient lightGradient = new LinearGradient(
            0.0, 0.0, 1.0, 0.0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, new Color(0.99, 0.55, 1.0, 1.0)),
            new Stop(0.5, new Color(0.17, 0.83, 0.99, 1.0)),
            new Stop(1.0, new Color(0.18, 1.0, 0.54, 1.0))
    );

    private final LinearGradient darkGradient = new LinearGradient(
            0.0, 0.0, 1.0, 0.0, true, CycleMethod.NO_CYCLE,
            new Stop(0.0, new Color(0.25, 0.14, 0.26, 1.0)),
            new Stop(0.5, new Color(0.04, 0.21, 0.26, 1.0)),
            new Stop(1.0, new Color(0.05, 0.26, 0.14, 1.0))
    );
    @Getter
    private final MenuItem importObj = new MenuItem("导入3D模型文件");
    @Getter
    private final MenuItem screenshots = new MenuItem("截图");
    @Getter
    private final MenuItem setBackgroundColor = new MenuItem("设置背景颜色");
    @Getter
    private final MenuItem setupLighting = new MenuItem("模型光源");
    @Getter
    private final MenuItem wireframes = new MenuItem("线框模式");
    @Getter
    private final MenuItem plotsMode = new MenuItem("点模式");
    @Getter
    private final CheckMenuItem boundingBoxToggle = new CheckMenuItem("包围盒");
    @Getter
    private final CheckMenuItem normalToggle = new CheckMenuItem("法线可视化");
    @Getter
    private final CheckMenuItem backFaceCullingToggle = new CheckMenuItem("背面剔除");
    @Getter
    private final CheckMenuItem modelInfoToggle = new CheckMenuItem("模型信息");
    @Getter
    private final CheckMenuItem orthoToggle = new CheckMenuItem("正交投影");
    @Getter
    private final MenuItem resetView = new MenuItem("重置视角");
    @Getter
    private final CheckMenuItem multiViewToggle = new CheckMenuItem("多视口");
    @Getter
    private final CheckMenuItem directionalLightToggle = new CheckMenuItem("方向光");
    @Getter
    private final MenuItem directionalLight = new MenuItem("方向光设置...");

    // 设置菜单 — 旋转引擎选择
    private final ToggleGroup engineGroup = new ToggleGroup();
    @Getter
    private final RadioMenuItem engineQuaternion = new RadioMenuItem("四元数旋转");
    @Getter
    private final RadioMenuItem engineMatrix = new RadioMenuItem("矩阵旋转");

    // 预设视角子菜单
    private final Menu presetViewMenu = new Menu("预设视角");
    @Getter
    private final MenuItem viewFront = new MenuItem("正面");
    @Getter
    private final MenuItem viewBack = new MenuItem("背面");
    @Getter
    private final MenuItem viewLeft = new MenuItem("左面");
    @Getter
    private final MenuItem viewRight = new MenuItem("右面");
    @Getter
    private final MenuItem viewTop = new MenuItem("顶部");
    @Getter
    private final MenuItem viewBottom = new MenuItem("底部");

    // 着色模式 RadioMenuItem 组
    private final ToggleGroup shadingGroup = new ToggleGroup();
    @Getter
    private final RadioMenuItem texturedMode = new RadioMenuItem("贴图模式");
    @Getter
    private final RadioMenuItem solidMode = new RadioMenuItem("纯色模式");
    @Getter
    private final RadioMenuItem wireframeMode = new RadioMenuItem("线框模式");
    @Getter
    private final RadioMenuItem overlayMode = new RadioMenuItem("线框叠加");
    @Getter
    private final RadioMenuItem normalColorMode = new RadioMenuItem("法线着色");

    // 所有菜单引用
    private final List<Menu> menus = new ArrayList<>();
    private MenuBar menuBar;
    @Getter
    private boolean isDarkTheme = true;

    public MenuBar getMenuBar() {
        if (menuBar != null) {
            return menuBar; // 懒加载：菜单栏只构建一次，避免重复添加菜单项导致异常
        }
        menuBar = new MenuBar();
        menuBar.setBackground(new Background(new BackgroundFill(darkGradient, null, null)));

        // 创建菜单并设置文字 Label
        Menu file = createStyledMenu("文件");
        Menu tools = createStyledMenu("工具");
        Menu set = createStyledMenu("视图");
        Menu settings = createStyledMenu("设置");

        // 添加菜单项
        file.getItems().addAll(importObj);
        tools.getItems().addAll(screenshots, directionalLight);
        // 预设视角子菜单
        presetViewMenu.getItems().addAll(viewFront, viewBack, viewLeft, viewRight, viewTop, viewBottom);

        directionalLightToggle.setSelected(true); // 方向光默认开启

        // 设置菜单 — 旋转引擎
        engineQuaternion.setToggleGroup(engineGroup);
        engineMatrix.setToggleGroup(engineGroup);
        settings.getItems().addAll(engineQuaternion, engineMatrix);

        // 着色模式 ToggleGroup
        texturedMode.setToggleGroup(shadingGroup);
        solidMode.setToggleGroup(shadingGroup);
        wireframeMode.setToggleGroup(shadingGroup);
        overlayMode.setToggleGroup(shadingGroup);
        normalColorMode.setToggleGroup(shadingGroup);
        texturedMode.setSelected(true);

        set.getItems().addAll(resetView, setBackgroundColor, setupLighting, directionalLightToggle, wireframes, plotsMode, presetViewMenu,
                new SeparatorMenuItem(), texturedMode, solidMode, wireframeMode, overlayMode, normalColorMode,
                new SeparatorMenuItem(), orthoToggle, multiViewToggle, boundingBoxToggle, normalToggle, backFaceCullingToggle, modelInfoToggle);

        // 添加到菜单栏
        menuBar.getMenus().addAll(file, tools, set, settings);
        menus.addAll(List.of(file, tools, set, settings));

        // 设置每个菜单的显示事件处理，用于处理其下拉菜单样式
//        menus.forEach(menu -> menu.setOnShowing(event -> updatePopupMenuStyle()));

        return menuBar;
    }

    public void toggleMenuTheme() {
        isDarkTheme = !isDarkTheme;
        applyTheme();
    }

    /**
     * 应用主题样式到界面元素
     * 根据当前主题设置（深色/浅色）更新菜单栏、菜单和菜单项的背景颜色和文字颜色
     */
    private void applyTheme() {
        menuBar.setBackground(new Background(new BackgroundFill(isDarkTheme ? darkGradient : lightGradient, null, null)));

        for (Menu menu : menus) {
            Node graphic = menu.getGraphic();
            if (graphic instanceof Label label) {
                label.setTextFill(isDarkTheme ? Color.LIGHTGRAY : Color.BLACK);
            }
        }
    }

    private Menu createStyledMenu(String title) {
        Label label = new Label(title);
        label.setTextFill(isDarkTheme ? Color.LIGHTGRAY : Color.BLACK);
        label.setMouseTransparent(true); // 让鼠标事件穿透Label传递给Menu
        Menu menu = new Menu();
        menu.setGraphic(label);
        return menu;
    }
}
