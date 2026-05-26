package com.bingbaihanji.scene;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;

/**
 * 批量点云构建器：将大量采样点合成为一个TriangleMesh，用单个MeshView渲染
 * <p>
 * 替代之前为每个采样点创建独立Sphere节点的做法，
 * 将渲染调用从N次降至1次，避免大模型点云模式下的严重卡顿。
 * 每个采样点渲染为一个微小的3D十字星（3个正交平面上的小方块）。
 * </p>
 */
public final class PointCloudBuilder {

    /**
     * 每个采样点的十字星半径（世界单位）
     */
    private static final float STAR_RADIUS = 0.003f;

    private PointCloudBuilder() { /* 工具类 */ }

    /**
     * 构建整个点云Group（包含单个MeshView）
     *
     * @param samplePoints 采样点坐标数组 {x,y,z, x,y,z, ...}，使用世界坐标
     * @param color        点云颜色
     * @return 包含单个MeshView的点云Group
     */
    public static Group build(float[] samplePoints, Color color) {
        int pointCount = samplePoints.length / 3;
        if (pointCount == 0) return new Group();

        // 每个采样点 = 3个正交方块 × (4顶点 + 6 face条目)
        float[] vertices = new float[pointCount * 36]; // 12 vertices × 3 components
        int[] faces = new int[pointCount * 36];         // 18 face entries × 2 ints

        int vi = 0;
        int fi = 0;
        for (int p = 0; p < pointCount; p++) {
            float px = samplePoints[p * 3];
            float py = samplePoints[p * 3 + 1];
            float pz = samplePoints[p * 3 + 2];

            vi = addXYQuad(vertices, vi, px, py, pz);
            fi = addFaces(faces, fi, (vi - 12) / 3);

            vi = addXZQuad(vertices, vi, px, py, pz);
            fi = addFaces(faces, fi, (vi - 12) / 3);

            vi = addYZQuad(vertices, vi, px, py, pz);
            fi = addFaces(faces, fi, (vi - 12) / 3);
        }

        TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_TEXCOORD);
        mesh.getPoints().setAll(vertices);
        mesh.getTexCoords().setAll(0, 0);
        mesh.getFaces().setAll(faces);

        PhongMaterial material = new PhongMaterial();
        material.setDiffuseColor(color);
        material.setSpecularColor(color.brighter());

        MeshView meshView = new MeshView(mesh);
        meshView.setMaterial(material);
        meshView.setMouseTransparent(true);

        Group root = new Group();
        root.setId("dotPlotGroup");
        root.getChildren().add(meshView);
        return root;
    }

    // ---- 三个平面上的小方块顶点生成 ----

    /**
     * XY平面方块：Z固定, X±r, Y±r
     */
    private static int addXYQuad(float[] v, int i, float px, float py, float pz) {
        float r = STAR_RADIUS;
        setVertex(v, i, px - r, py - r, pz);
        i += 3;
        setVertex(v, i, px + r, py - r, pz);
        i += 3;
        setVertex(v, i, px + r, py + r, pz);
        i += 3;
        setVertex(v, i, px - r, py + r, pz);
        i += 3;
        return i;
    }

    /**
     * XZ平面方块：Y固定, X±r, Z±r
     */
    private static int addXZQuad(float[] v, int i, float px, float py, float pz) {
        float r = STAR_RADIUS;
        setVertex(v, i, px - r, py, pz - r);
        i += 3;
        setVertex(v, i, px + r, py, pz - r);
        i += 3;
        setVertex(v, i, px + r, py, pz + r);
        i += 3;
        setVertex(v, i, px - r, py, pz + r);
        i += 3;
        return i;
    }

    /**
     * YZ平面方块：X固定, Y±r, Z±r
     */
    private static int addYZQuad(float[] v, int i, float px, float py, float pz) {
        float r = STAR_RADIUS;
        setVertex(v, i, px, py - r, pz - r);
        i += 3;
        setVertex(v, i, px, py + r, pz - r);
        i += 3;
        setVertex(v, i, px, py + r, pz + r);
        i += 3;
        setVertex(v, i, px, py - r, pz + r);
        i += 3;
        return i;
    }

    private static void setVertex(float[] v, int i, float x, float y, float z) {
        v[i] = x;
        v[i + 1] = y;
        v[i + 2] = z;
    }

    /**
     * 为4顶点方块添加2个三角形面索引(CCW winding)，texCoord固定为0
     */
    private static int addFaces(int[] f, int i, int baseVp) {
        // 三角形1: v0, v1, v2
        f[i++] = baseVp;
        f[i++] = 0;
        f[i++] = baseVp + 1;
        f[i++] = 0;
        f[i++] = baseVp + 2;
        f[i++] = 0;
        // 三角形2: v0, v2, v3
        f[i++] = baseVp;
        f[i++] = 0;
        f[i++] = baseVp + 2;
        f[i++] = 0;
        f[i++] = baseVp + 3;
        f[i++] = 0;
        return i;
    }
}
