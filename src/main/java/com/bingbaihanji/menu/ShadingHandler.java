package com.bingbaihanji.menu;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.Material;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 着色模式处理器：管理贴图/纯色/线框/叠加/法线着色5种模式
 * <p>
 * 通过 {@link #shadingModeProperty()} 暴露当前着色模式，
 * 其他组件（如StatusBar）可观察此属性以响应着色变化。
 * </p>
 */
public class ShadingHandler {

    /**
     * 原始材质备份（用于贴图模式恢复）
     */
    private final Map<MeshView, Material> originalMaterials = new HashMap<>();
    /**
     * 线框叠加模式创建的覆盖MeshView列表
     */
    private final List<MeshView> overlayMeshViews = new ArrayList<>();
    /**
     * 当前着色模式（可观察属性）
     */
    private final ObjectProperty<ShadingMode> shadingMode = new SimpleObjectProperty<>(ShadingMode.TEXTURED);

    /**
     * 注册着色模式菜单事件
     */

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
     * 获取当前着色模式（只读属性，供外部观察）
     */
    public ReadOnlyObjectProperty<ShadingMode> shadingModeProperty() {
        return shadingMode;
    }

    public ShadingMode getShadingMode() {
        return shadingMode.get();
    }

    public void setupShadingModes(MenuNode menuNode, Group moleculeGroup) {
        menuNode.getTexturedMode().setOnAction(e ->
                applyShadingMode(ShadingMode.TEXTURED, moleculeGroup));
        menuNode.getSolidMode().setOnAction(e ->
                applyShadingMode(ShadingMode.SOLID, moleculeGroup));
        menuNode.getWireframeMode().setOnAction(e ->
                applyShadingMode(ShadingMode.WIREFRAME, moleculeGroup));
        menuNode.getOverlayMode().setOnAction(e ->
                applyShadingMode(ShadingMode.OVERLAY, moleculeGroup));
        menuNode.getNormalColorMode().setOnAction(e ->
                applyShadingMode(ShadingMode.NORMAL_COLOR, moleculeGroup));
    }

    private void applyShadingMode(ShadingMode mode, Group moleculeGroup) {
        if (mode == shadingMode.get()) return;
        shadingMode.set(mode);
        removeOverlays(moleculeGroup);

        switch (mode) {
            case TEXTURED -> applyTextured(moleculeGroup);
            case SOLID -> applySolid(moleculeGroup);
            case WIREFRAME -> applyWireframeShading(moleculeGroup);
            case OVERLAY -> applyOverlay(moleculeGroup);
            case NORMAL_COLOR -> applyNormalColor(moleculeGroup);
        }
    }

    private void applyTextured(Group modelGroup) {
        traverseAllMeshViews(modelGroup, mesh -> {
            mesh.setDrawMode(DrawMode.FILL);
            Material orig = originalMaterials.remove(mesh);
            if (orig != null) mesh.setMaterial(orig);
            mesh.setVisible(true);
        });
    }

    private void applySolid(Group modelGroup) {
        PhongMaterial solidMat = new PhongMaterial();
        solidMat.setDiffuseColor(Color.LIGHTGRAY);
        solidMat.setSpecularColor(Color.WHITE);

        traverseAllMeshViews(modelGroup, mesh -> {
            mesh.setDrawMode(DrawMode.FILL);
            backupMaterial(mesh);
            mesh.setMaterial(solidMat);
            mesh.setVisible(true);
        });
    }

    private void applyWireframeShading(Group modelGroup) {
        traverseAllMeshViews(modelGroup, mesh -> {
            originalMaterials.remove(mesh);
            mesh.setDrawMode(DrawMode.LINE);
            mesh.setVisible(true);
        });
    }

    private void applyOverlay(Group modelGroup) {
        PhongMaterial overlayMat = new PhongMaterial();
        overlayMat.setDiffuseColor(Color.BLACK);
        overlayMat.setSpecularColor(Color.BLACK);

        List<Node[]> pairs = new ArrayList<>();
        traverseAllMeshViews(modelGroup, mesh -> {
            mesh.setDrawMode(DrawMode.FILL);
            mesh.setVisible(true);
            Node parent = mesh.getParent();
            if (parent instanceof Group) pairs.add(new Node[]{mesh, parent});
        });

        for (Node[] pair : pairs) {
            MeshView mesh = (MeshView) pair[0];
            Group parent = (Group) pair[1];
            MeshView overlay = new MeshView(mesh.getMesh());
            overlay.setDrawMode(DrawMode.LINE);
            overlay.setMaterial(overlayMat);
            overlay.setScaleX(1.001);
            overlay.setScaleY(1.001);
            overlay.setScaleZ(1.001);
            overlay.setCullFace(CullFace.NONE);
            overlay.setMouseTransparent(true);
            parent.getChildren().add(overlay);
            overlayMeshViews.add(overlay);
        }
    }

    private void applyNormalColor(Group modelGroup) {
        traverseAllMeshViews(modelGroup, mesh -> {
            mesh.setDrawMode(DrawMode.FILL);
            backupMaterial(mesh);
            mesh.setCullFace(CullFace.NONE);

            if (mesh.getMesh() instanceof TriangleMesh triMesh) {
                // 从面数据计算面法线（getNormals() 在 OBJ 导入流程中可能为空）
                int[] faces = triMesh.getFaces().toArray(null);
                float[] points = triMesh.getPoints().toArray(null);
                double ax = 0, ay = 0, az = 0;
                int faceCount = 0;
                int maxFaces = Math.min(faces.length / 6, 5000); // 最多采样5000个三角面

                for (int i = 0; i < maxFaces * 6; i += 6) {
                    if (i + 4 >= faces.length) break;
                    int vi0 = faces[i] * 3;
                    int vi1 = faces[i + 2] * 3;
                    int vi2 = faces[i + 4] * 3;
                    if (vi0 + 2 >= points.length || vi1 + 2 >= points.length || vi2 + 2 >= points.length) continue;

                    // 两条边向量
                    double e1x = points[vi1] - points[vi0];
                    double e1y = points[vi1 + 1] - points[vi0 + 1];
                    double e1z = points[vi1 + 2] - points[vi0 + 2];
                    double e2x = points[vi2] - points[vi0];
                    double e2y = points[vi2 + 1] - points[vi0 + 1];
                    double e2z = points[vi2 + 2] - points[vi0 + 2];

                    // 叉积得面法线，不归一化（面积加权平均，大面对整体方向贡献更大）
                    double nx = e1y * e2z - e1z * e2y;
                    double ny = e1z * e2x - e1x * e2z;
                    double nz = e1x * e2y - e1y * e2x;

                    // 取绝对值，避免对称模型法线抵消
                    ax += Math.abs(nx);
                    ay += Math.abs(ny);
                    az += Math.abs(nz);
                    faceCount++;
                }

                if (faceCount > 0) {
                    double len = Math.sqrt(ax * ax + ay * ay + az * az);
                    if (len > 1e-9) {
                        ax /= len;
                        ay /= len;
                        az /= len;
                    }
                }
                double r = (ax + 1) / 2, g = (ay + 1) / 2, b = (az + 1) / 2;
                PhongMaterial mat = new PhongMaterial();
                mat.setDiffuseColor(new Color(r, g, b, 1));
                mat.setSpecularColor(Color.BLACK);
                mesh.setMaterial(mat);
            }
            mesh.setVisible(true);
        });
    }

    private void backupMaterial(MeshView mesh) {
        if (!originalMaterials.containsKey(mesh)) {
            originalMaterials.put(mesh, mesh.getMaterial());
        }
    }

    private void removeOverlays(Group modelGroup) {
        for (MeshView overlay : overlayMeshViews) {
            Node parent = overlay.getParent();
            if (parent instanceof Group group) group.getChildren().remove(overlay);
        }
        overlayMeshViews.clear();
    }
}
