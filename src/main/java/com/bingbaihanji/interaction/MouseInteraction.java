package com.bingbaihanji.interaction;

import com.bingbaihanji.camera.CameraConfig;
import com.bingbaihanji.camera.CameraSystem;
import com.bingbaihanji.view.ViewingAxes;
import com.bingbaihanji.world.GroupTransform;
import javafx.scene.Node;
import javafx.scene.SubScene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.ScrollEvent;
import javafx.scene.shape.MeshView;

/**
 * 鼠标交互处理器
 * <p>
 * 处理鼠标相关的交互事件:
 * - 左键拖拽:旋转(ArcBall)
 * - 右键拖拽:平移
 * - 滚轮:缩放
 * </p>
 *
 * @author bingbaihanji
 */
public class MouseInteraction {

    private final CameraSystem cameraSystem;

    private final ViewingAxes viewingAxes;

    private final PickingController pickingController;

    private double mousePosX, mousePosY;

    private double mouseOldX, mouseOldY;

    private double mouseDeltaX, mouseDeltaY;


    /**
     * 构造函数
     *
     * @param cameraSystem      相机系统
     * @param viewingAxes       辅助轴视图
     * @param pickingController 拾取控制器
     */
    /**
     * 旋转更新后的回调(用于多视口迷你轴同步等)
     */
    private Runnable onRotationUpdated;

    public MouseInteraction(CameraSystem cameraSystem, ViewingAxes viewingAxes, PickingController pickingController) {
        this.cameraSystem = cameraSystem;
        this.viewingAxes = viewingAxes;
        this.pickingController = pickingController;
    }

    /**
     * 设置旋转更新回调(拖拽旋转后触发,用于多视口迷你轴同步)
     */
    public void setOnRotationUpdated(Runnable callback) {
        this.onRotationUpdated = callback;
    }

    /**
     * 将鼠标事件处理器绑定到SubScene
     *
     * @param subScene 3D子场景
     */
    public void attachToSubScene(SubScene subScene) {
        subScene.setOnMousePressed(this::handleMousePressed);
        subScene.setOnMouseDragged(this::handleMouseDragged);
        subScene.setOnMouseClicked(this::handleMouseClicked);
        subScene.setOnScroll(this::handleScroll);
    }

    /**
     * 处理鼠标按下事件
     */
    private void handleMousePressed(MouseEvent me) {
        mousePosX = me.getSceneX();
        mousePosY = me.getSceneY();

        // 保存当前旋转状态
        cameraSystem.getRotationStrategy().updateFromAffine();
    }

    /**
     * 处理鼠标拖拽事件
     */
    private void handleMouseDragged(MouseEvent me) {
        mouseOldX = mousePosX;
        mouseOldY = mousePosY;
        mousePosX = me.getSceneX();
        mousePosY = me.getSceneY();

        mouseDeltaX = mousePosX - mouseOldX;
        mouseDeltaY = mousePosY - mouseOldY;

        if (me.isPrimaryButtonDown()) {
            // 左键旋转
            handleRotation(me);
        } else if (me.isSecondaryButtonDown()) {
            // 右键平移
            handleTranslation(me);
        }
    }

    /**
     * 处理旋转操作
     */
    private void handleRotation(MouseEvent me) {
        SubScene subScene = (SubScene) me.getSource();
        int width = (int) subScene.getWidth();
        int height = (int) subScene.getHeight();

        double factor = me.isControlDown() ? InteractionConfig.CONTROL_MULTIPLIER : 1.0;

        cameraSystem.getRotationStrategy().applyDragRotation(
                width, height, mouseOldX, mouseOldY, mousePosX, mousePosY, factor
        );

        viewingAxes.updateAxes(cameraSystem.getRotationStrategy().getRotationAffine());

        if (onRotationUpdated != null) {
            onRotationUpdated.run();
        }
    }

    /**
     * 处理平移操作
     */
    private void handleTranslation(MouseEvent me) {
        double factor = 1.0;
        if (me.isControlDown()) {
            factor = InteractionConfig.CONTROL_MULTIPLIER;
        }
        if (me.isShiftDown()) {
            factor = InteractionConfig.SHIFT_MULTIPLIER;
        }

        GroupTransform translateTransform = cameraSystem.getCameraTranslateTransform();
        translateTransform.setTx(
                translateTransform.translate.getX() + mouseDeltaX * InteractionConfig.MOUSE_SPEED * InteractionConfig.TRACK_SPEED * factor
        );
        translateTransform.setTy(
                translateTransform.translate.getY() + mouseDeltaY * InteractionConfig.MOUSE_SPEED * InteractionConfig.TRACK_SPEED * factor
        );
    }


    /**
     * 处理鼠标点击事件(用于拾取)
     */
    private void handleMouseClicked(MouseEvent me) {

        // 仅处理左键点击
        if (me.getButton() != MouseButton.PRIMARY) {
            return;
        }

        PickResult pickResult = me.getPickResult();
        Node hitNode = pickResult.getIntersectedNode();

        // === 情况 1:点在 MeshView 上 ===
        if (hitNode instanceof MeshView mesh) {

            boolean shift = me.isShiftDown();
            boolean ctrl = me.isControlDown();

            // Shift 优先(操作当前对象)
            if (shift) {
                pickingController.highlightPickedMesh(mesh); // 确保选中
                pickingController.togglePickedWireframe();

            }
            // Ctrl 次之(改变选中)
            else if (ctrl) {
                pickingController.highlightPickedMesh(mesh);
            }

            me.consume();
            return;
        }

        // === 情况 2:点在空白处 ===
        if (me.isControlDown()) {
            pickingController.restorePickedMesh();
            me.consume();
        }
    }

    /**
     * 处理滚轮缩放 / Ctrl+滚轮调节FOV
     */
    private void handleScroll(ScrollEvent se) {
        double delta = se.getDeltaY();
        if (se.isShiftDown() && delta == 0) {
            delta = se.getDeltaX();
        }

        // Ctrl+滚轮 → 调节视场角(FOV),正交模式下调节缩放
        if (se.isControlDown()) {
            double fovDelta = delta * InteractionConfig.FOV_SPEED;
            cameraSystem.adjustFieldOfView(fovDelta);
            return;
        }

        // 正交模式:缩放调节 Scale
        if (cameraSystem.isOrthographic()) {
            double zoomFactor = 1.0 + delta * 0.001;
            if (se.isShiftDown()) {
                zoomFactor = 1.0 + delta * 0.005;
            }
            cameraSystem.zoomOrtho(zoomFactor);
            return;
        }

        double factor = 1.0;
        if (se.isShiftDown()) {
            factor = InteractionConfig.SHIFT_MULTIPLIER;
        }

        double newZ = cameraSystem.getCamera().getTranslateZ()
                - delta * InteractionConfig.MOUSE_SPEED * 0.1 * factor;
        newZ = Math.min(newZ, CameraConfig.MODEL_NEAR_CLIP);
        cameraSystem.getCamera().setTranslateZ(newZ);
    }
}
