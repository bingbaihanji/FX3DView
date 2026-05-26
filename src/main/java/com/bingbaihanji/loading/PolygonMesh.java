package com.bingbaihanji.loading;

import javafx.collections.FXCollections;
import javafx.collections.ObservableFloatArray;
import javafx.collections.ObservableIntegerArray;

/**
 * 多边形网格类
 * 支持多边形面（而不仅仅是三角形）的3D网格数据结构
 * <p>
 * A Mesh where each face can be a Polygon (not just triangles)
 * <p>
 * Can be converted to use ObservableIntegerArray for better performance
 * <p>
 * 注意：这个类支持任意多边形面，而不仅仅是三角形
 */
public class PolygonMesh {
    // TODO: Hardcode to constants for FX 8 (only one vertex format)
    // 常量定义 - 顶点格式组件数量
    private static final int NUM_COMPONENTS_PER_POINT = 3;  // 每个顶点有3个分量 (x, y, z)
    private static final int NUM_COMPONENTS_PER_TEXCOORD = 2; // 每个纹理坐标有2个分量 (u, v)
    private static final int NUM_COMPONENTS_PER_FACE = 6; //  每个面索引有6个分量 (v1, uv1, v2, uv2, v3, uv3)

    // 网格数据存储
    private final ObservableFloatArray points; // 顶点坐标数组
    private final ObservableFloatArray texCoords; // 纹理坐标数组
    private final ObservableIntegerArray faceSmoothingGroups = FXCollections.observableIntegerArray();  // 面平滑组数组

    // 面数据 - 二维数组，每行代表一个多边形面
    protected int[][] faces;

    // 缓存的面边数（延迟计算）
    protected int numEdgesInFaces = -1; // TODO invalidate automatically by listening to faces (whenever it is an observable)


    public PolygonMesh() {
        this(FXCollections.observableFloatArray(), FXCollections.observableFloatArray(), new int[0][0]);
    }

    public PolygonMesh(float[] points, float[] texCoords, int[][] faces) {
        this(FXCollections.observableFloatArray(points), FXCollections.observableFloatArray(texCoords), faces);
    }

    public PolygonMesh(ObservableFloatArray points, ObservableFloatArray texCoords, int[][] faces) {
        this.points = points;
        this.texCoords = texCoords;
        this.faces = faces;
    }

    public ObservableFloatArray getPoints() {
        return points;
    }

    public ObservableFloatArray getTexCoords() {
        return texCoords;
    }

    public int[][] getFaces() {
        return faces;
    }

    public void setFaces(int[][] faces) {
        this.faces = faces;
    }

    public ObservableIntegerArray getFaceSmoothingGroups() {
        return faceSmoothingGroups;
    }

    public int getNumEdgesInFaces() {
        if (numEdgesInFaces == -1) {
            numEdgesInFaces = 0;
            for (int[] face : faces) {
                numEdgesInFaces += face.length;
            }
            numEdgesInFaces /= 2;
        }
        return numEdgesInFaces;
    }

    public int getPointElementSize() {
        return NUM_COMPONENTS_PER_POINT;
    }

    public int getTexCoordElementSize() {
        return NUM_COMPONENTS_PER_TEXCOORD;
    }

    public int getFaceElementSize() {
        return NUM_COMPONENTS_PER_FACE;
    }
}