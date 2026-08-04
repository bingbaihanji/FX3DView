package com.bingbaihanji.scene;

import com.bingbaihanji.world.GroupTransform;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.transform.Affine;

/**
 * 3D场景管理器
 * <p>
 * 管理3D场景图的层次结构,包括:
 * - root: 根节点(包含viewing旋转变换)
 * - world: 世界坐标系
 * - axisGroup: 坐标轴组
 * - moleculeGroup: 模型组
 * </p>
 *
 * @author bingbaihanji
 */
public class Scene3DManager {

    /**
     * 场景根节点
     */
    private final Group root = new Group();

    /**
     * 世界变换组
     */
    private final GroupTransform world = new GroupTransform();

    /**
     * 坐标轴组
     */
    private final GroupTransform axisGroup = new GroupTransform();

    /**
     * 模型组
     */
    private final GroupTransform moleculeGroup = new GroupTransform();

    /**
     * 包围盒组
     */
    private final Group boundingBoxGroup;

    /**
     * 法线可视化组
     */
    private final Group normalGroup;

    /**
     * 辅助视图旋转变换
     */
    private final Affine viewingRotate = new Affine();

    /**
     * 构造函数
     * <p>
     * 初始化场景结构并构建坐标轴
     * </p>
     */
    public Scene3DManager() {
        root.getTransforms().setAll(viewingRotate);
        root.getChildren().add(world);

        // 构建坐标轴
        AxesBuilder.buildAxes(axisGroup);
        world.getChildren().add(axisGroup);

        world.getChildren().add(moleculeGroup);

        // 构建包围盒(初始为空,模型加载后重建)
        boundingBoxGroup = new Group();
        boundingBoxGroup.setId("boundingBox");
        boundingBoxGroup.setVisible(false);
        world.getChildren().add(boundingBoxGroup);

        // 构建法线可视化组(初始为空,模型加载后重建)
        // 放在 moleculeGroup 内,确保法线与模型共享同一坐标空间
        normalGroup = new Group();
        normalGroup.setId("normalLines");
        normalGroup.setVisible(false);
        moleculeGroup.getChildren().add(normalGroup);
    }

    /**
     * 切换坐标轴可见性
     */
    public void toggleAxesVisibility() {
        axisGroup.setVisible(!axisGroup.isVisible());
    }

    /**
     * 切换模型可见性
     */
    public void toggleModelVisibility() {
        moleculeGroup.setVisible(!moleculeGroup.isVisible());
    }

    /**
     * 切换包围盒可见性
     */
    public void toggleBoundingBox() {
        boundingBoxGroup.setVisible(!boundingBoxGroup.isVisible());
    }

    /**
     * 根据模型边界重建包围盒
     */
    public void rebuildBoundingBox(Bounds bounds) {
        boundingBoxGroup.getChildren().clear();
        if (bounds != null && !bounds.isEmpty()) {
            boundingBoxGroup.getChildren().add(BoundingBoxRenderer.buildBoundingBox(bounds));
        }
    }

    /**
     * 切换法线可视化
     */
    public void toggleNormalVisibility() {
        normalGroup.setVisible(!normalGroup.isVisible());
    }

    /**
     * 清空加载的模型
     * <p>
     * 清空 moleculeGroup 中的所有模型节点(保留 normalGroup),
     * 清空包围盒,并隐藏法线可视化.
     * </p>
     */
    public void clearModel() {
        moleculeGroup.getChildren().clear();
        moleculeGroup.getChildren().add(normalGroup);
        boundingBoxGroup.getChildren().clear();
        normalGroup.setVisible(false);
    }

    /**
     * 重建法线可视化
     * <p>
     * 法线组放在 moleculeGroup 内,确保与模型共享同一坐标空间.
     * 模型加载时 moleculeGroup 子节点会被清空,重建后需重新添加 normalGroup.
     * </p>
     */
    public void rebuildNormals() {
        NormalVisualizer.buildNormalLines(moleculeGroup, normalGroup);
        // 模型加载后 moleculeGroup 子节点被清空,需重新添加(放在最后以保证渲染顺序正确)
        if (!moleculeGroup.getChildren().contains(normalGroup)) {
            moleculeGroup.getChildren().add(normalGroup);
        }
    }

    // ==================== Getters ====================

    /**
     * 获取根节点
     */
    public Group getRoot() {
        return root;
    }

    /**
     * 获取世界变换组
     */
    public GroupTransform getWorld() {
        return world;
    }

    /**
     * 获取坐标轴组
     */
    public GroupTransform getAxisGroup() {
        return axisGroup;
    }

    /**
     * 获取模型组
     */
    public GroupTransform getMoleculeGroup() {
        return moleculeGroup;
    }

    /**
     * 获取辅助视图旋转变换
     */
    public Affine getViewingRotate() {
        return viewingRotate;
    }
}
