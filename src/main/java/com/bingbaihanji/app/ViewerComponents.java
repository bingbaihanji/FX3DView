package com.bingbaihanji.app;

import com.bingbaihanji.camera.CameraSystem;
import com.bingbaihanji.interaction.DragDropHandler;
import com.bingbaihanji.interaction.KeyboardInteraction;
import com.bingbaihanji.interaction.MouseInteraction;
import com.bingbaihanji.interaction.PickingController;
import com.bingbaihanji.lighting.LightManager;
import com.bingbaihanji.loading.ImporterRegistry;
import com.bingbaihanji.menu.MenuEvent;
import com.bingbaihanji.menu.MenuNode;
import com.bingbaihanji.scene.Scene3DManager;
import com.bingbaihanji.ui.MainLayout;
import com.bingbaihanji.ui.ModelInfoPanel;
import com.bingbaihanji.ui.StatusBar;
import com.bingbaihanji.view.ViewingAxes;

/**
 * 核心组件持有类，避免 Fx3DViewerApp 中字段过多和方法过长
 * <p>
 * 将启动时创建的所有核心组件聚合成一个对象，便于在拆分的各个方法间传递
 * </p>
 *
 * @author bingbaihanji
 */
class ViewerComponents {

    Scene3DManager sceneManager;
    CameraSystem cameraSystem;
    ViewingAxes viewingAxes;
    PickingController pickingController;
    MouseInteraction mouseInteraction;
    KeyboardInteraction keyboardInteraction;
    MainLayout mainLayout;
    MenuEvent menuEvent;
    LightManager lightManager;
    StatusBar statusBar;
    ModelInfoPanel modelInfoPanel;
    MenuNode menuNode;
    DragDropHandler dragDrop;
    ImporterRegistry importerRegistry;
}
