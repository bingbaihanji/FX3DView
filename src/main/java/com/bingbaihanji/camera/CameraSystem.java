package com.bingbaihanji.camera;

import com.bingbaihanji.interaction.InteractionConfig;
import com.bingbaihanji.rotation.RotationStrategy;
import com.bingbaihanji.world.GroupTransform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Group;
import javafx.scene.ParallelCamera;
import javafx.scene.PerspectiveCamera;
import javafx.scene.SubScene;
import javafx.scene.transform.Scale;

/**
 * 3D相机系统管理器
 * <p>
 * 封装相机的层次结构、变换链和旋转策略
 * 采用三层GroupTransform结构支持复杂的相机控制：
 * - 第一层：旋转变换
 * - 第二层：平移变换
 * - 第三层：相机本身
 * </p>
 *
 * @author bingbaihanji
 */
public class CameraSystem {

    /**
     * 透视相机
     */
    private final PerspectiveCamera camera;

    /**
     * 正交相机
     */
    private final ParallelCamera orthoCamera;

    /**
     * 是否为正交投影模式（可观察属性）
     */
    private final BooleanProperty orthographic = new SimpleBooleanProperty(false);

    /**
     * 正交缩放组（在 cameraTransform3 和 orthoCamera 之间）
     */
    private final Group orthoScaleGroup = new Group();
    private final Scale orthoScale = new Scale(1, 1);

    /**
     * 第一层变换组（旋转）
     */
    private final GroupTransform cameraTransform1;

    /**
     * 第二层变换组（平移）
     */
    private final GroupTransform cameraTransform2;

    /**
     * 第三层变换组（相机容器）
     */
    private final GroupTransform cameraTransform3;

    /**
     * 旋转策略（四元数或矩阵）
     */
    private RotationStrategy rotationStrategy;

    /**
     * 构造函数
     *
     * @param rotationStrategy 旋转策略实现
     */
    public CameraSystem(RotationStrategy rotationStrategy) {
        this.rotationStrategy = rotationStrategy;
        this.camera = new PerspectiveCamera(true);
        this.orthoCamera = new ParallelCamera();
        this.cameraTransform1 = new GroupTransform(GroupTransform.RotateOrder.YZX);
        this.cameraTransform2 = new GroupTransform(GroupTransform.RotateOrder.YZX);
        this.cameraTransform3 = new GroupTransform(GroupTransform.RotateOrder.YZX);

        orthoScaleGroup.getTransforms().add(orthoScale);

        buildCameraHierarchy();
        configureCameraTransforms();
        initializeCameraParameters();
    }

    /**
     * 构建相机层次结构
     */
    private void buildCameraHierarchy() {
        cameraTransform1.getChildren().add(cameraTransform2);
        cameraTransform2.getChildren().add(cameraTransform3);
        cameraTransform3.getChildren().add(camera);
    }

    /**
     * 配置相机变换链
     * <p>
     * 第一层变换组包含旋转策略提供的Affine变换
     * </p>
     */
    private void configureCameraTransforms() {
        cameraTransform1.getTransforms().clear();
        cameraTransform1.getTransforms().addAll(
                cameraTransform1.translate,
                cameraTransform1.pivot,
                rotationStrategy.getRotationAffine(),  // 策略提供旋转
                cameraTransform1.s,
                cameraTransform1.inversePivot
        );
    }

    /**
     * 初始化相机参数
     */
    private void initializeCameraParameters() {
        camera.setNearClip(CameraConfig.NEAR_CLIP);
        camera.setFarClip(CameraConfig.FAR_CLIP);
        camera.setTranslateZ(CameraConfig.INITIAL_DISTANCE);
        cameraTransform3.setRotateZ(180); // 相机朝向调整

        rotationStrategy.reset(
                CameraConfig.INITIAL_X_ANGLE,
                CameraConfig.INITIAL_Y_ANGLE
        );
    }

    /**
     * 重置相机到初始状态
     */
    public void resetCamera() {
        cameraTransform2.translate.setX(0.0);
        cameraTransform2.translate.setY(0.0);
        camera.setTranslateZ(CameraConfig.INITIAL_DISTANCE);
        rotationStrategy.reset(
                CameraConfig.INITIAL_X_ANGLE,
                CameraConfig.INITIAL_Y_ANGLE
        );
    }

    /**
     * 切换到预设视角
     */
    public void setPresetView(ViewPreset preset) {
        cameraTransform2.translate.setX(0.0);
        cameraTransform2.translate.setY(0.0);
        rotationStrategy.reset(preset.xAngle, preset.yAngle);
    }

    /**
     * 调节视场角（FOV）
     *
     * @param delta 变化量（正值放大，负值缩小），自动钳位到[FOV_MIN, FOV_MAX]
     */
    public void adjustFieldOfView(double delta) {
        if (orthographic.get()) {
            // 正交模式：调整缩放比例
            double factor = 1.0 - delta * 0.01;
            orthoScale.setX(orthoScale.getX() * factor);
            orthoScale.setY(orthoScale.getY() * factor);
            return;
        }
        double newFov = camera.getFieldOfView() + delta;
        newFov = Math.max(InteractionConfig.FOV_MIN, Math.min(InteractionConfig.FOV_MAX, newFov));
        camera.setFieldOfView(newFov);
    }

    /**
     * 正交模式滚轮缩放
     */
    public void zoomOrtho(double factor) {
        if (orthographic.get()) {
            orthoScale.setX(orthoScale.getX() * factor);
            orthoScale.setY(orthoScale.getY() * factor);
        }
    }

    /**
     * 获取当前视场角
     */
    public double getFieldOfView() {
        return camera.getFieldOfView();
    }

    /**
     * 切换透视/正交投影
     * <p>
     * 正交投影没有FOV，通过Scale(s,s,1)控制可见范围：可见世界高度 = screenH × scale。
     * 例如：FOV=30° 在80单位距离可见~43单位高，scale = 43/600 ≈ 0.071 使正交与透视匹配。
     * </p>
     */
    public void toggleProjection(SubScene subScene) {
        orthographic.set(!orthographic.get());
        cameraTransform3.getChildren().removeAll(camera, orthoScaleGroup);
        orthoScaleGroup.getChildren().remove(orthoCamera);
        if (orthographic.get()) {
            orthoCamera.setNearClip(0.01);
            orthoCamera.setFarClip(CameraConfig.FAR_CLIP);
            // ParallelCamera 坐标系原点在视口左上角，需要偏移半个视口使世界原点居中
            orthoCamera.setTranslateX(-subScene.getWidth() / 2);
            orthoCamera.setTranslateY(-subScene.getHeight() / 2);
            orthoCamera.setTranslateZ(camera.getTranslateZ());
            updateOrthoScale(subScene);
            orthoScaleGroup.getChildren().add(orthoCamera);
            cameraTransform3.getChildren().add(orthoScaleGroup);
            subScene.setCamera(orthoCamera);
        } else {
            orthoScale.setX(1.0);
            orthoScale.setY(1.0);
            cameraTransform3.getChildren().add(camera);
            subScene.setCamera(camera);
        }
    }

    /**
     * 根据当前FOV和相机距离计算正交缩放比，使正交视图与透视视图的可见范围匹配
     */
    private void updateOrthoScale(SubScene subScene) {
        double fov = camera.getFieldOfView();
        double dist = Math.abs(camera.getTranslateZ());
        double visibleHeight = 2.0 * dist * Math.tan(Math.toRadians(fov / 2.0));
        // Scale 施加在包含相机的 Group 上：可见世界高度 = screenH × scale
        // 因此 scale = visibleHeight / screenH 才能使正交与透视的可见范围匹配
        double scale = visibleHeight / subScene.getHeight();
        orthoScale.setX(scale);
        orthoScale.setY(scale);
    }

    /**
     * 更新正交缩放（当FOV或zoom改变时调用）
     */
    public void syncOrthoScale(SubScene subScene) {
        if (orthographic.get()) {
            orthoCamera.setTranslateX(-subScene.getWidth() / 2);
            orthoCamera.setTranslateY(-subScene.getHeight() / 2);
            updateOrthoScale(subScene);
        }
    }

    /**
     * 正交投影模式（只读属性，供外部观察）
     */
    public ReadOnlyBooleanProperty orthographicProperty() {
        return orthographic;
    }

    /**
     * 强制切换到透视投影（如当前为正交投影则切换）
     *
     * @param subScene 当前 SubScene，用于恢复透视相机引用
     */
    public void setPerspective(SubScene subScene) {
        if (orthographic.get()) {
            toggleProjection(subScene);
        }
    }

    /**
     * 是否为正交投影模式
     */
    public boolean isOrthographic() {
        return orthographic.get();
    }

    /**
     * 获取透视相机
     */
    public PerspectiveCamera getCamera() {
        return camera;
    }

    // ==================== Getters ====================

    /**
     * 获取第一层变换组（根）
     */
    public GroupTransform getCameraRootTransform() {
        return cameraTransform1;
    }

    /**
     * 获取第二层变换组（平移控制）
     */
    public GroupTransform getCameraTranslateTransform() {
        return cameraTransform2;
    }

    /**
     * 获取第三层变换组（相机容器）
     */
    public GroupTransform getCameraContainerTransform() {
        return cameraTransform3;
    }

    /**
     * 获取旋转策略
     */
    public RotationStrategy getRotationStrategy() {
        return rotationStrategy;
    }

    /**
     * 运行时切换旋转策略（保持当前视角）
     */
    public void setRotationStrategy(RotationStrategy newStrategy) {
        // 从旧策略复制当前旋转状态到新策略
        newStrategy.getRotationAffine().setToTransform(rotationStrategy.getRotationAffine());
        newStrategy.updateFromAffine();

        this.rotationStrategy = newStrategy;
        configureCameraTransforms();
    }
}
