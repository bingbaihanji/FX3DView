package com.bingbaihanji.scene;

import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.transform.Translate;

/**
 * 包围盒渲染器：用12条Box线框渲染模型的AABB包围盒
 */
public class BoundingBoxRenderer {

    private static final double EDGE_THICKNESS = 0.03;
    private static final Color EDGE_COLOR = Color.CYAN;

    public static Group buildBoundingBox(Bounds bounds) {
        Group group = new Group();
        group.setId("boundingBox");

        if (bounds == null || bounds.isEmpty()) {
            return group;
        }

        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseColor(EDGE_COLOR);
        mat.setSpecularColor(Color.BLACK);

        double minX = bounds.getMinX(), maxX = bounds.getMaxX();
        double minY = bounds.getMinY(), maxY = bounds.getMaxY();
        double minZ = bounds.getMinZ(), maxZ = bounds.getMaxZ();

        double t = EDGE_THICKNESS;
        double w = maxX - minX;
        double h = maxY - minY;
        double d = maxZ - minZ;

        // 4 X轴方向边（水平，前后各2条）
        addEdge(group, mat, w, t, t, (minX + maxX) / 2, minY, minZ);
        addEdge(group, mat, w, t, t, (minX + maxX) / 2, maxY, minZ);
        addEdge(group, mat, w, t, t, (minX + maxX) / 2, minY, maxZ);
        addEdge(group, mat, w, t, t, (minX + maxX) / 2, maxY, maxZ);

        // 4 Y轴方向边（垂直，4个角）
        addEdge(group, mat, t, h, t, minX, (minY + maxY) / 2, minZ);
        addEdge(group, mat, t, h, t, maxX, (minY + maxY) / 2, minZ);
        addEdge(group, mat, t, h, t, minX, (minY + maxY) / 2, maxZ);
        addEdge(group, mat, t, h, t, maxX, (minY + maxY) / 2, maxZ);

        // 4 Z轴方向边（深度，左右各2条）
        addEdge(group, mat, t, t, d, minX, minY, (minZ + maxZ) / 2);
        addEdge(group, mat, t, t, d, maxX, minY, (minZ + maxZ) / 2);
        addEdge(group, mat, t, t, d, minX, maxY, (minZ + maxZ) / 2);
        addEdge(group, mat, t, t, d, maxX, maxY, (minZ + maxZ) / 2);

        return group;
    }

    private static void addEdge(Group group, PhongMaterial mat,
                                double sx, double sy, double sz,
                                double tx, double ty, double tz) {
        Box box = new Box(sx, sy, sz);
        box.setMaterial(mat);
        box.getTransforms().add(new Translate(tx, ty, tz));
        group.getChildren().add(box);
    }
}
