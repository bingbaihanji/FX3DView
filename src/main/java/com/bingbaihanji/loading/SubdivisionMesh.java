package com.bingbaihanji.loading;


import java.util.ArrayList;
import java.util.List;

/**
 * Catmull Clark subdivision surface polygon mesh
 */
public class SubdivisionMesh extends PolygonMesh {
    // 原始网格数据
    private final PolygonMesh originalMesh;
    // 符号网格列表，存储不同细分级别的网格
    private final List<SymbolicPolygonMesh> symbolicMeshes;

    // 细分参数
    private int subdivisionLevel;
    private BoundaryMode boundaryMode;
    private MapBorderMode mapBorderMode;

    // 脏标记，用于优化更新
    private boolean pointValuesDirty;
    private boolean meshDirty;
    private boolean subdivisionLevelDirty;

    /**
     * 构造函数
     *
     * @param originalMesh     原始网格数据
     * @param subdivisionLevel 细分级别
     * @param boundaryMode     边界处理模式
     * @param mapBorderMode    纹理边界处理模式
     * @throws IllegalArgumentException 如果参数无效
     */
    public SubdivisionMesh(PolygonMesh originalMesh, int subdivisionLevel, BoundaryMode boundaryMode, MapBorderMode mapBorderMode) {
        this.originalMesh = originalMesh;
        // 强制设置初始参数
        setSubdivisionLevelForced(subdivisionLevel);
        setBoundaryModeForced(boundaryMode);
        setMapBorderModeForced(mapBorderMode);

        symbolicMeshes = new ArrayList<>(4); // 通常最多细分3次，预分配容量

        // 设置原始网格变化监听器
        originalMesh.getPoints().addListener((observableArray, sizeChanged, from, to) -> {
            if (sizeChanged) {
                meshDirty = true;
            } else {
                pointValuesDirty = true;
            }
        });
        originalMesh.getTexCoords().addListener((observableArray, sizeChanged, from, to) -> meshDirty = true);
    }

    /**
     * Updates the variables of the underlying polygon mesh.
     * It only updates the fields that need to be updated.
     */
    public void update() {
        if (meshDirty) {
            symbolicMeshes.clear();
            symbolicMeshes.add(new SymbolicPolygonMesh(originalMesh));
            pointValuesDirty = true;
            subdivisionLevelDirty = true;
        }

        while (subdivisionLevel >= symbolicMeshes.size()) {
            symbolicMeshes.add(SymbolicSubdivisionBuilder.subdivide(symbolicMeshes.get(symbolicMeshes.size() - 1), boundaryMode, mapBorderMode));
            pointValuesDirty = true;
            subdivisionLevelDirty = true;
        }

        if (pointValuesDirty) {
            for (int i = 0; i <= subdivisionLevel; i++) {
                SymbolicPolygonMesh symbolicMesh = symbolicMeshes.get(i);
                symbolicMesh.points.update();
            }
        }

        if (pointValuesDirty || subdivisionLevelDirty) {
            getPoints().setAll(symbolicMeshes.get(subdivisionLevel).points.data);
        }

        if (subdivisionLevelDirty) {
            setFaces(symbolicMeshes.get(subdivisionLevel).faces);
            numEdgesInFaces = -1;
            getFaceSmoothingGroups().setAll(symbolicMeshes.get(subdivisionLevel).faceSmoothingGroups);
            getTexCoords().setAll(symbolicMeshes.get(subdivisionLevel).texCoords);
        }

        meshDirty = false;
        pointValuesDirty = false;
        subdivisionLevelDirty = false;
    }


    // 强制设置细分级别
    private void setSubdivisionLevelForced(int subdivisionLevel) {
        this.subdivisionLevel = subdivisionLevel;
        subdivisionLevelDirty = true;
    }

    // 强制设置边界模式
    private void setBoundaryModeForced(BoundaryMode boundaryMode) {
        this.boundaryMode = boundaryMode;
        meshDirty = true;
    }

    // 强制设置纹理边界模式
    private void setMapBorderModeForced(MapBorderMode mapBorderMode) {
        this.mapBorderMode = mapBorderMode;
        meshDirty = true;
    }

    public PolygonMesh getOriginalMesh() {
        return originalMesh;
    }

    public int getSubdivisionLevel() {
        return subdivisionLevel;
    }

    public void setSubdivisionLevel(int subdivisionLevel) {
        if (subdivisionLevel != this.subdivisionLevel) {
            setSubdivisionLevelForced(subdivisionLevel);
        }
    }

    public BoundaryMode getBoundaryMode() {
        return boundaryMode;
    }

    public void setBoundaryMode(BoundaryMode boundaryMode) {
        if (boundaryMode != this.boundaryMode) {
            setBoundaryModeForced(boundaryMode);
        }
    }

    public MapBorderMode getMapBorderMode() {
        return mapBorderMode;
    }

    public void setMapBorderMode(MapBorderMode mapBorderMode) {
        if (mapBorderMode != this.mapBorderMode) {
            setMapBorderModeForced(mapBorderMode);
        }
    }

    /**
     * Describes whether the edges and points at the boundary are treated as creases
     */
    public enum BoundaryMode {
        /**
         * Only edges at the boundary are treated as creases
         */
        CREASE_EDGES,
        /**
         * Edges and points at the boundary are treated as creases
         */
        CREASE_ALL
    }

    /**
     * Describes how the new texture coordinate for the control point is defined
     */
    public enum MapBorderMode {
        /**
         * Jeeps the same uvs for all control points
         */
        NOT_SMOOTH,
        /**
         * Smooths uvs of points at corners
         */
        SMOOTH_INTERNAL,
        /**
         * Smooths uvs of points at boundaries and original control points (and creases [in the future when creases are defined])
         */
        SMOOTH_ALL
    }
}