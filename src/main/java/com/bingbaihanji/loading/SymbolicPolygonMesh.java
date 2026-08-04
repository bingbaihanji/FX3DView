package com.bingbaihanji.loading;


/**
 * Polygon mesh where the points are symbolic. That is, the values of the
 * points depend on other variables and they can be updated appropriately.
 */
public class SymbolicPolygonMesh {

    // 符号化点数组
    public SymbolicPointArray points;

    // 纹理坐标数组
    public float[] texCoords;

    // 面数据数组,每个面包含顶点和纹理坐标索引
    public int[][] faces;

    // 面平滑组数组
    public int[] faceSmoothingGroups;

    // 面边数缓存(延迟计算)
    private int numEdgesInFaces = -1;

    public SymbolicPolygonMesh(SymbolicPointArray points, float[] texCoords,
                               int[][] faces, int[] faceSmoothingGroups) {
        this.points = points;
        this.texCoords = texCoords;
        this.faces = faces;
        this.faceSmoothingGroups = faceSmoothingGroups;
    }

    public SymbolicPolygonMesh(PolygonMesh mesh) {
        this.points = new OriginalPointArray(mesh);
        this.texCoords = mesh.getTexCoords().toArray(this.texCoords);
        this.faces = mesh.getFaces();
        this.faceSmoothingGroups = mesh.getFaceSmoothingGroups().toArray(null);
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
}