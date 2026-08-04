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

import java.util.ArrayList;
import java.util.List;

/**
 * 法线可视化:在模型三角面中心处沿法线方向绘制短线段
 * <p>
 * 使用两遍算法确保法线均匀覆盖全部网格:
 * 第一遍收集所有 MeshView 及其变换并统计总面数,
 * 第二遍按全局动态步长采样,避免前置网格耗尽配额导致后置网格无法显示.
 * </p>
 */
public class NormalVisualizer {

    private static final int MAX_LINES = 6000;

    private static final Color NORMAL_COLOR = Color.YELLOW;

    /**
     * 为模型构建法线可视化
     * <p>
     * 法线长度 = 模型包围盒对角线的 1%(最少 0.02),
     * 粗细 = 长度的 1.2%(最少 0.001).
     * 通过两遍遍历确保法线均匀分布在全部子网格上.
     * </p>
     *
     * @param modelGroup  模型根 Group,同时也是遍历起点
     * @param targetGroup 法线圆柱体目标 Group(应与 modelGroup 在同一局部空间)
     */
    public static void buildNormalLines(Group modelGroup, Group targetGroup) {
        javafx.geometry.Bounds bounds = modelGroup.getBoundsInLocal();
        double diagonal = Math.sqrt(
                bounds.getWidth() * bounds.getWidth()
                        + bounds.getHeight() * bounds.getHeight()
                        + bounds.getDepth() * bounds.getDepth());
        double lineLength = Math.max(0.02, diagonal * 0.01);
        double lineRadius = Math.max(0.001, lineLength * 0.012);

        buildNormalLines(modelGroup, targetGroup, lineLength, lineRadius);
    }

    /**
     * 带自定义参数的法线构建(供外部精细控制)
     */
    public static void buildNormalLines(Group modelGroup, Group targetGroup,
                                        double lineLength, double lineRadius) {
        targetGroup.getChildren().clear();

        // 第一遍:收集所有有效网格及其累积变换
        List<MeshEntry> entries = new ArrayList<>();
        for (Node child : modelGroup.getChildren()) {
            collectEntries(child, child.getLocalToParentTransform(), entries);
        }
        if (entries.isEmpty()) {
            return;
        }

        // 统计总面数,动态计算全局采样步长(保证总输出约 MAX_LINES 条,均匀分布)
        int totalFaces = entries.stream().mapToInt(e -> e.faces().length / 6).sum();
        int stride = Math.max(1, totalFaces / MAX_LINES);

        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseColor(NORMAL_COLOR);
        mat.setSpecularColor(Color.BLACK);

        // 第二遍:按统一步长采样所有网格
        int[] count = {0};
        for (MeshEntry entry : entries) {
            if (count[0] >= MAX_LINES) {
                break;
            }
            buildNormalsForEntry(entry, targetGroup, mat, lineLength, lineRadius, stride, count);
        }
    }

    /**
     * 递归收集所有 MeshView 及其到 modelGroup 局部空间的累积变换
     * <p>
     * createConcatenation 语义:A.createConcatenation(B)(p) = B(A(p)),
     * 即 A 先作用于点,再作用 B.
     * 调用时 toModelGroup = childToParent.createConcatenation(parentToModelGroup),
     * 正确实现 child.local → parent.local → modelGroup.local 的链式变换.
     * </p>
     */
    private static void collectEntries(Node node, Transform toModelGroup, List<MeshEntry> entries) {
        if (node instanceof MeshView meshView) {
            if (!(meshView.getMesh() instanceof TriangleMesh mesh)) {
                return;
            }
            float[] points = mesh.getPoints().toArray(null);
            int[] faces = mesh.getFaces().toArray(null);
            if (points.length > 0 && faces.length >= 6) {
                entries.add(new MeshEntry(toModelGroup, points, faces));
            }
        } else if (node instanceof Group group) {
            for (Node child : group.getChildren()) {
                // child.local → group.local → modelGroup.local
                Transform childToModel = child.getLocalToParentTransform().createConcatenation(toModelGroup);
                collectEntries(child, childToModel, entries);
            }
        }
    }

    /**
     * 为单个 MeshEntry 构建法线圆柱体
     */
    private static void buildNormalsForEntry(MeshEntry entry, Group targetGroup, PhongMaterial mat,
                                             double lineLength, double lineRadius,
                                             int stride, int[] count) {
        float[] points = entry.points();
        int[] faces = entry.faces();

        for (int i = 0; i < faces.length && count[0] < MAX_LINES; i += 6 * stride) {
            // faces 格式: [v0, uv0, v1, uv1, v2, uv2]
            int vi0 = faces[i] * 3;
            if (i + 4 >= faces.length) {
                continue;
            }
            int vi1 = faces[i + 2] * 3;
            int vi2 = faces[i + 4] * 3;
            if (vi0 + 2 >= points.length || vi1 + 2 >= points.length || vi2 + 2 >= points.length) {
                continue;
            }

            double v0x = points[vi0], v0y = points[vi0 + 1], v0z = points[vi0 + 2];
            double v1x = points[vi1], v1y = points[vi1 + 1], v1z = points[vi1 + 2];
            double v2x = points[vi2], v2y = points[vi2 + 1], v2z = points[vi2 + 2];

            // 面中心(MeshView 局部空间)
            double cx = (v0x + v1x + v2x) / 3.0;
            double cy = (v0y + v1y + v2y) / 3.0;
            double cz = (v0z + v1z + v2z) / 3.0;

            // 面法线 = (v1-v0) × (v2-v0)
            double e1x = v1x - v0x, e1y = v1y - v0y, e1z = v1z - v0z;
            double e2x = v2x - v0x, e2y = v2y - v0y, e2z = v2z - v0z;
            double nx = e1y * e2z - e1z * e2y;
            double ny = e1z * e2x - e1x * e2z;
            double nz = e1x * e2y - e1y * e2x;
            double nlen = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nlen < 1e-9) {
                continue;
            }
            nx /= nlen;
            ny /= nlen;
            nz /= nlen;

            // 变换到 modelGroup 局部空间
            Point3D pos = entry.toModelGroup().transform(cx, cy, cz);
            Point3D dir = entry.toModelGroup().deltaTransform(nx, ny, nz);
            double dn = Math.sqrt(dir.getX() * dir.getX() + dir.getY() * dir.getY() + dir.getZ() * dir.getZ());
            if (dn < 1e-9) {
                continue;
            }
            double dnx = dir.getX() / dn;
            double dny = dir.getY() / dn;
            double dnz = dir.getZ() / dn;

            // 法线中点坐标(偏移 5% standoff 避免与表面重叠)
            double standoff = lineLength * 0.05;
            double midX = pos.getX() + dnx * (standoff + lineLength / 2);
            double midY = pos.getY() + dny * (standoff + lineLength / 2);
            double midZ = pos.getZ() + dnz * (standoff + lineLength / 2);

            Cylinder cyl = new Cylinder(lineRadius, lineLength);
            cyl.setMaterial(mat);
            // Translate 在前,Rotate 在后:JavaFX transforms 列表后项先作用于点
            // 即 Rotate 先对圆柱做轴对齐,再由 Translate 移到目标位置
            cyl.getTransforms().add(new Translate(midX, midY, midZ));

            // 将默认 Y 轴圆柱旋转到法线方向:旋转轴 = Y × normal = (dnz, 0, -dnx)
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dny))));
            if (Math.abs(angle) > 0.01 && Math.abs(angle - 180) > 0.01) {
                double ax = dnz, az = -dnx;
                double alen = Math.sqrt(ax * ax + az * az);
                if (alen > 1e-9) {
                    cyl.getTransforms().add(new Rotate(angle, new Point3D(ax / alen, 0, az / alen)));
                }
            } else if (angle > 90) {
                cyl.getTransforms().add(new Rotate(180, Rotate.X_AXIS));
            }

            targetGroup.getChildren().add(cyl);
            count[0]++;
        }
    }

    private record MeshEntry(Transform toModelGroup, float[] points, int[] faces) {}
}
