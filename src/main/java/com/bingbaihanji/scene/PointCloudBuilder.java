package com.bingbaihanji.scene;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import javafx.scene.transform.Affine;

/**
 * 批量点云构建器：将大量采样点合成为一个TriangleMesh，用单个MeshView渲染
 * <p>
 * 替代之前为每个采样点创建独立Sphere节点的做法，
 * 将渲染调用从N次降至1次，避免大模型点云模式下的严重卡顿。
 * 每个采样点渲染为一个始终面朝相机的正方形（billboard），
 * 从任意视角均可看到点云。
 * </p>
 */
public final class PointCloudBuilder {

    /**
     * 每个采样点的正方形半边长（世界单位）
     */
    private static final float STAR_RADIUS = 0.003f;

    private PointCloudBuilder() { /* 工具类 */ }

    /**
     * 构建整个点云Group（包含单个MeshView），正方形始终面朝相机
     *
     * @param samplePoints 采样点坐标数组 {x,y,z, x,y,z, ...}，使用世界坐标
     * @param color        点云颜色
     * @param cameraAffine 相机的旋转仿射矩阵（用于提取right/up向量），null 时回退到十字星
     * @return 包含单个MeshView的点云Group
     */
    public static Group build(float[] samplePoints, Color color, Affine cameraAffine) {
        int pointCount = samplePoints.length / 3;
        if (pointCount == 0) {
            return new Group();
        }

        // 从相机的旋转矩阵中提取 world-space 的 right 和 up 方向
        float rx, ry, rz, ux, uy, uz;
        if (cameraAffine != null) {
            rx = (float) cameraAffine.getMxx();
            ry = (float) cameraAffine.getMyx();
            rz = (float) cameraAffine.getMzx();
            ux = (float) cameraAffine.getMxy();
            uy = (float) cameraAffine.getMyy();
            uz = (float) cameraAffine.getMzy();
        } else {
            // 回退：默认朝向 +Z（十字星模式）
            Group g = buildCrossStar(samplePoints, color);
            return g;
        }

        // 每个点 = 1个正方形 × (4顶点 + 6 face条目)
        float[] vertices = new float[pointCount * 12]; // 4 vertices × 3 components
        int[] faces = new int[pointCount * 12];         // 6 face entries × 2 ints

        int vi = 0;
        int fi = 0;
        for (int p = 0; p < pointCount; p++) {
            float px = samplePoints[p * 3];
            float py = samplePoints[p * 3 + 1];
            float pz = samplePoints[p * 3 + 2];

            float r = STAR_RADIUS;
            // 正方形四个角 = P ± right*r ± up*r
            // v0 = P + right*r + up*r  (右上)
            vertices[vi] = px + rx * r + ux * r;
            vertices[vi + 1] = py + ry * r + uy * r;
            vertices[vi + 2] = pz + rz * r + uz * r;
            vi += 3;
            // v1 = P - right*r + up*r  (左上)
            vertices[vi] = px - rx * r + ux * r;
            vertices[vi + 1] = py - ry * r + uy * r;
            vertices[vi + 2] = pz - rz * r + uz * r;
            vi += 3;
            // v2 = P - right*r - up*r  (左下)
            vertices[vi] = px - rx * r - ux * r;
            vertices[vi + 1] = py - ry * r - uy * r;
            vertices[vi + 2] = pz - rz * r - uz * r;
            vi += 3;
            // v3 = P + right*r - up*r  (右下)
            vertices[vi] = px + rx * r - ux * r;
            vertices[vi + 1] = py + ry * r - uy * r;
            vertices[vi + 2] = pz + rz * r - uz * r;
            vi += 3;

            int baseVp = p * 4;
            // 三角形1: v0, v1, v2 (CCW — 面朝相机方向)
            faces[fi++] = baseVp;
            faces[fi++] = 0;
            faces[fi++] = baseVp + 1;
            faces[fi++] = 0;
            faces[fi++] = baseVp + 2;
            faces[fi++] = 0;
            // 三角形2: v0, v2, v3
            faces[fi++] = baseVp;
            faces[fi++] = 0;
            faces[fi++] = baseVp + 2;
            faces[fi++] = 0;
            faces[fi++] = baseVp + 3;
            faces[fi++] = 0;
        }

        return buildMeshView(vertices, faces, color);
    }

    /**
     * 十字星模式（回退方案，不依赖相机方向）
     * <p>每个点为3个正交平面上的小正方形，确保从任意角度可见</p>
     */
    private static Group buildCrossStar(float[] samplePoints, Color color) {
        int pointCount = samplePoints.length / 3;
        float[] vertices = new float[pointCount * 36];
        int[] faces = new int[pointCount * 36];

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

        return buildMeshView(vertices, faces, color);
    }

    /**
     * 创建MeshView并包裹到Group中，关闭背面剔除确保双面可见
     */
    private static Group buildMeshView(float[] vertices, int[] faces, Color color) {
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
        meshView.setCullFace(CullFace.NONE);

        Group root = new Group();
        root.setId("dotPlotGroup");
        root.getChildren().add(meshView);
        return root;
    }

    // ========== 十字星顶点/面的构建方法（回退模式使用） ==========

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

    private static int addFaces(int[] f, int i, int baseVp) {
        f[i++] = baseVp;
        f[i++] = 0;
        f[i++] = baseVp + 1;
        f[i++] = 0;
        f[i++] = baseVp + 2;
        f[i++] = 0;
        f[i++] = baseVp;
        f[i++] = 0;
        f[i++] = baseVp + 2;
        f[i++] = 0;
        f[i++] = baseVp + 3;
        f[i++] = 0;
        return i;
    }
}
