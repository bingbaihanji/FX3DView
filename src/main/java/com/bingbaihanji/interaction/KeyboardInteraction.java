package com.bingbaihanji.interaction;

import com.bingbaihanji.animation.AutoRotationAnimation;
import com.bingbaihanji.camera.CameraSystem;
import com.bingbaihanji.core.Lifecycle;
import com.bingbaihanji.scene.Scene3DManager;
import com.bingbaihanji.view.ViewingAxes;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * 键盘交互处理器
 * <p>
 * 处理键盘快捷键：
 * - Z: 重置相机
 * - X: 显示/隐藏坐标轴
 * - V: 显示/隐藏模型
 * - 空格: 自动旋转开关
 * - R: 随机旋转
 * </p>
 *
 * @author bingbaihanji
 */
@Slf4j
public class KeyboardInteraction implements Lifecycle {

    private final CameraSystem cameraSystem;
    private final Scene3DManager sceneManager;
    private final ViewingAxes viewingAxes;
    private final AutoRotationAnimation autoRotation;

    private Runnable onMultiViewportToggle;

    /**
     * 构造函数
     *
     * @param cameraSystem 相机系统
     * @param sceneManager 场景管理器
     * @param viewingAxes  辅助轴视图
     */
    public KeyboardInteraction(CameraSystem cameraSystem,
                               Scene3DManager sceneManager,
                               ViewingAxes viewingAxes) {
        this.cameraSystem = cameraSystem;
        this.sceneManager = sceneManager;
        this.viewingAxes = viewingAxes;
        this.autoRotation = new AutoRotationAnimation(cameraSystem, viewingAxes);
    }

    /**
     * 将键盘事件处理器绑定到Scene
     *
     * @param scene 场景
     */
    public void attachToScene(Scene scene) {
        scene.setOnKeyPressed(this::handleKeyPressed);
    }

    /**
     * 处理键盘按下事件
     */
    private void handleKeyPressed(KeyEvent event) {
        switch (event.getCode()) {
            case Z -> resetCamera();
            case X -> sceneManager.toggleAxesVisibility();
            case V -> sceneManager.toggleModelVisibility();
            case SPACE -> autoRotation.toggle();
            case R -> applyRandomRotation();
            case M -> {
                if (onMultiViewportToggle != null) {
                    onMultiViewportToggle.run();
                }
            }
        }
    }

    /**
     * 重置相机
     */
    private void resetCamera() {
        cameraSystem.resetCamera();
        viewingAxes.updateAxes(cameraSystem.getRotationStrategy().getRotationAffine());
        log.info("相机已重置");
    }

    /**
     * 应用随机旋转
     */
    private void applyRandomRotation() {
        cameraSystem.getRotationStrategy().applyRandomRotation();
        viewingAxes.updateAxes(cameraSystem.getRotationStrategy().getRotationAffine());
    }

    /**
     * 设置多视口切换回调
     */
    public void setOnMultiViewportToggle(Runnable action) {
        this.onMultiViewportToggle = action;
    }

    @Override
    public void stop() {
        autoRotation.stop();
    }

    @Override
    public void dispose() {
        autoRotation.dispose();
    }

    /**
     * 获取自动旋转动画控制器
     */
    public AutoRotationAnimation getAutoRotation() {
        return autoRotation;
    }
}
