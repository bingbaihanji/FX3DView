package com.bingbaihanji.interaction;

import javafx.scene.paint.Color;
import javafx.scene.paint.Material;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;

/**
 *
 * @author bingbaihanji
 * @date 2026-01-08 19:17:55
 * @description 拾取控制器
 */
public class PickingController {

    // 当前选中的 Mesh
    private MeshView pickedMesh;

    // 记录原始材质(用于还原)
    private Material pickedOldMaterial;

    // 是否处于网格模式(仅对当前选中 Mesh)
    private boolean pickedWireframe = false;

    // 记录原始 DrawMode(用于还原)
    private DrawMode pickedOldDrawMode;


    // 选中模型高亮(状态型,高对比,不依赖光照)

    public void highlightPickedMesh(MeshView mesh) {

        // === 情况 1:再次点击同一个 Mesh → 还原 ===
        if (mesh == pickedMesh) {
            restorePickedMesh();
            return;
        }

        // === 情况 2:点了新的 Mesh,先还原旧的 ===
        restorePickedMesh();

        pickedMesh = mesh;
        pickedOldMaterial = mesh.getMaterial();
        pickedOldDrawMode = mesh.getDrawMode();
        pickedWireframe = false;

        // ===== 构建"选中态材质" =====
        PhongMaterial selectedMat = new PhongMaterial();

        // ① 纯选中色:完全去掉原有贴图与固有色
        selectedMat.setDiffuseColor(Color.DARKORANGE);

        // ② 极弱反光:只保留"轮廓存在感"
        selectedMat.setSpecularColor(Color.DARKORANGE.deriveColor(
                0,      // hue
                1.0,    // saturation
                0.6,    // brightness(刻意压暗)
                1.0     // opacity
        ));

        // ③ 钝高光,避免"亮斑"
        selectedMat.setSpecularPower(8);
        mesh.setMaterial(selectedMat);
    }


    // 恢复选中的 Mesh
    public void restorePickedMesh() {

        if (pickedMesh == null) {
            return;
        }

        if (pickedOldMaterial != null) {
            pickedMesh.setMaterial(pickedOldMaterial);
        }

        if (pickedOldDrawMode != null) {
            pickedMesh.setDrawMode(pickedOldDrawMode);
        }

        // 恢复缩放
        pickedMesh.setScaleX(1.0);
        pickedMesh.setScaleY(1.0);
        pickedMesh.setScaleZ(1.0);

        // 恢复背面剔除
        pickedMesh.setCullFace(CullFace.BACK);

        pickedMesh = null;
        pickedOldMaterial = null;
        pickedOldDrawMode = null;
        pickedWireframe = false;
    }

    // Shift + 左键 → 切换网格状态
    public void togglePickedWireframe() {

        if (pickedMesh == null) {
            return;
        }

        if (!pickedWireframe) {

            // 进入线框模式
            pickedMesh.setDrawMode(DrawMode.LINE);
            pickedMesh.setCullFace(CullFace.NONE);

            // 用纯色材质,避免高光干扰
            pickedMesh.setMaterial(new PhongMaterial(Color.CYAN));

            // 轻微放大,避免深度冲突
            pickedMesh.setScaleX(1.001);
            pickedMesh.setScaleY(1.001);
            pickedMesh.setScaleZ(1.001);

            pickedWireframe = true;

        } else {

            // 还原实体模式,但保持高亮状态
            pickedMesh.setDrawMode(pickedOldDrawMode);
            pickedMesh.setCullFace(CullFace.BACK);

            // 恢复高亮材质(橙色),而不是原始材质
            PhongMaterial selectedMat = new PhongMaterial();
            selectedMat.setDiffuseColor(Color.DARKORANGE);
            selectedMat.setSpecularColor(Color.DARKORANGE.deriveColor(
                    0,      // hue
                    1.0,    // saturation
                    0.6,    // brightness
                    1.0     // opacity
            ));
            selectedMat.setSpecularPower(8);
            pickedMesh.setMaterial(selectedMat);

            pickedMesh.setScaleX(1.0);
            pickedMesh.setScaleY(1.0);
            pickedMesh.setScaleZ(1.0);

            pickedWireframe = false;
        }
    }

}
