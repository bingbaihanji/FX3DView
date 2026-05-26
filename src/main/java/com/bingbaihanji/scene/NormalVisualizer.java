package com.bingbaihanji.scene;

import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Transform;
import javafx.scene.transform.Translate;

/**
 * 法线可视化：在模型三角面中心处沿法线方向绘制短线段
 * <p>
 * 法线圆柱体放在 targetGroup 中，面中心和法线方向通过从 MeshView
 * 到 modelGroup（不含 modelGroup 自身）的累积变换进行转换，
 * 确保坐标在 modelGroup 的局部空间内（与 targetGroup 同空间）。
 * </p>
 */
public class NormalVisualizer {

    private static final int DEFAULT_STRIDE = 100;
    private static final int MAX_LINES = 5000;
    private static final Color NORMAL_COLOR = Color.YELLOW;

    /**
     * 为模型构建法线可视化
     * <p>
     * 法线长度根据模型包围盒对角线自动计算（对角线的 3%，最少 0.1），
     * 线段粗细为长度的 2%。
     * </p>
     *
     * @param modelGroup  模型根 Group，同时也是遍历起点
     * @param targetGroup 法线圆柱体目标 Group（应与 modelGroup 在同一局部空间）
     */
    public static void buildNormalLines(Group modelGroup, Group targetGroup) {
        javafx.geometry.Bounds bounds = modelGroup.getBoundsInLocal();
        double diagonal = Math.sqrt(
                bounds.getWidth() * bounds.getWidth()
                        + bounds.getHeight() * bounds.getHeight()
                        + bounds.getDepth() * bounds.getDepth());
        double lineLength = Math.max(0.1, diagonal * 0.03);
        double lineRadius = Math.max(0.002, lineLength * 0.02);

        buildNormalLines(modelGroup, targetGroup, lineLength, lineRadius, DEFAULT_STRIDE);
    }

    public static void buildNormalLines(Group modelGroup, Group targetGroup,
                                        double lineLength, double lineRadius, int sampleStride) {
        targetGroup.getChildren().clear();

        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseColor(NORMAL_COLOR);
        mat.setSpecularColor(Color.BLACK);

        int[] count = {0};
        int stride = Math.max(1, sampleStride);
        Transform identity = Transform.affine(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0);
        // 从 modelGroup 的子节点开始遍历，累积子节点到 modelGroup 的变换
        // （不含 modelGroup 自身），使坐标在 modelGroup.local 空间内
        for (Node child : modelGroup.getChildren()) {
            // 子节点到其父（= modelGroup）的变换
            Transform childToModelGroup = child.getLocalToParentTransform();
            traverseAndBuild(child, childToModelGroup, targetGroup, mat,
                    lineLength, lineRadius, stride, count);
        }
    }

    /**
     * 递归遍历节点树，累积从当前节点到 modelGroup 的变换
     *
     * @param node         当前节点
     * @param toModelGroup 从当前节点局部空间到 modelGroup 局部空间的累积变换
     */
    private static void traverseAndBuild(Node node, Transform toModelGroup,
                                         Group targetGroup, PhongMaterial mat,
                                         double lineLength, double lineRadius,
                                         int stride, int[] count) {
        if (count[0] >= MAX_LINES) return;

        if (node instanceof MeshView mesh) {
            // toModelGroup 已由调用方计算好：从该 MeshView 局部空间到 modelGroup 局部空间
            buildNormalsForMesh(mesh, toModelGroup, targetGroup, mat,
                    lineLength, lineRadius, stride, count);
        } else if (node instanceof Group group) {
            // 对每个子节点，计算 子节点.local → group.local → modelGroup.local 的累积变换
            for (Node child : group.getChildren()) {
                Transform childToParent = child.getLocalToParentTransform(); // child.local → group.local
                Transform childToModel = toModelGroup.createConcatenation(childToParent); // child→group→modelGroup
                traverseAndBuild(child, childToModel, targetGroup, mat,
                        lineLength, lineRadius, stride, count);
            }
        }
    }

    /**
     * 为单个 MeshView 构建法线圆柱体
     *
     * @param meshView     MeshView 节点
     * @param toModelGroup 从 MeshView 局部空间到 modelGroup 局部空间的变换
     */
    private static void buildNormalsForMesh(MeshView meshView, Transform toModelGroup,
                                            Group targetGroup, PhongMaterial mat,
                                            double lineLength, double lineRadius,
                                            int stride, int[] count) {
        if (!(meshView.getMesh() instanceof TriangleMesh mesh)) return;

        float[] points = mesh.getPoints().toArray(null);
        int[] faces = mesh.getFaces().toArray(null);

        if (points.length == 0 || faces.length == 0) return;

        for (int i = 0; i < faces.length && count[0] < MAX_LINES; i += 6 * stride) {
            // faces 格式: [v0, uv0/n0, v1, uv1/n1, v2, uv2/n2]
            int vi0 = faces[i] * 3;
            int vi1 = (i + 2 < faces.length) ? faces[i + 2] * 3 : -1;
            int vi2 = (i + 4 < faces.length) ? faces[i + 4] * 3 : -1;
            if (vi0 + 2 >= points.length || vi1 + 2 >= points.length || vi2 + 2 >= points.length) continue;

            // 三个顶点（MeshView 局部空间）
            double v0x = points[vi0], v0y = points[vi0 + 1], v0z = points[vi0 + 2];
            double v1x = points[vi1], v1y = points[vi1 + 1], v1z = points[vi1 + 2];
            double v2x = points[vi2], v2y = points[vi2 + 1], v2z = points[vi2 + 2];

            // 面中心（MeshView 局部空间）
            double cx = (v0x + v1x + v2x) / 3.0;
            double cy = (v0y + v1y + v2y) / 3.0;
            double cz = (v0z + v1z + v2z) / 3.0;

            // 面法线 = (v1-v0) × (v2-v0)，在 MeshView 局部空间中计算
            double e1x = v1x - v0x, e1y = v1y - v0y, e1z = v1z - v0z;
            double e2x = v2x - v0x, e2y = v2y - v0y, e2z = v2z - v0z;
            double nx = e1y * e2z - e1z * e2y;
            double ny = e1z * e2x - e1x * e2z;
            double nz = e1x * e2y - e1y * e2x;

            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-9) continue;
            nx /= len;
            ny /= len;
            nz /= len;

            // 变换到 modelGroup 局部空间
            Point3D pos = toModelGroup.transform(cx, cy, cz);
            Point3D dir = toModelGroup.deltaTransform(nx, ny, nz);
            double dn = Math.sqrt(dir.getX() * dir.getX()
                    + dir.getY() * dir.getY()
                    + dir.getZ() * dir.getZ());
            if (dn < 1e-9) continue;
            double dnx = dir.getX() / dn;
            double dny = dir.getY() / dn;
            double dnz = dir.getZ() / dn;

            // 法线起点从面中心向外偏移，避免与模型表面重叠
            double standoff = lineLength * 0.05;
            double sx = pos.getX() + dnx * standoff;
            double sy = pos.getY() + dny * standoff;
            double sz = pos.getZ() + dnz * standoff;
            double midX = sx + dnx * lineLength / 2;
            double midY = sy + dny * lineLength / 2;
            double midZ = sz + dnz * lineLength / 2;

            Cylinder cyl = new Cylinder(lineRadius, lineLength);
            cyl.setMaterial(mat);
            cyl.getTransforms().add(new Translate(midX, midY, midZ));

            // 将默认Y轴方向的圆柱旋转到法线方向
            double angle = Math.toDegrees(Math.acos(dny));
            if (Math.abs(angle) > 0.01 && Math.abs(angle - 180) > 0.01) {
                double ax = dnz;
                double az = -dnx;
                double alen = Math.sqrt(ax * ax + az * az);
                if (alen > 1e-9) {
                    cyl.getTransforms().add(new Rotate(angle, ax / alen, 0, az / alen));
                }
            } else if (angle > 90) {
                cyl.getTransforms().add(new Rotate(180, Rotate.X_AXIS));
            }

            targetGroup.getChildren().add(cyl);
            count[0]++;
        }
    }
}
