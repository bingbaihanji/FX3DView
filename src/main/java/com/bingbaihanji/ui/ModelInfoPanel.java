package com.bingbaihanji.ui;

import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

/**
 * 模型信息面板:显示已加载模型的详细统计信息
 */
public class ModelInfoPanel extends VBox {

    private final Label titleLabel = new Label("模型信息");

    private final Label fileNameLabel = new Label("未加载模型");

    private final Label vertexLabel = new Label("顶点: --");

    private final Label faceLabel = new Label("面: --");

    private final Label sizeLabel = new Label("尺寸: --");

    private final Label materialLabel = new Label("材质: --");

    private boolean isDark = true;

    public ModelInfoPanel() {
        setSpacing(8);
        setPadding(new javafx.geometry.Insets(12));
        setPrefWidth(220);
        setMinWidth(180);

        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        getChildren().addAll(
                titleLabel,
                new javafx.scene.control.Separator(),
                fileNameLabel,
                new javafx.scene.control.Separator(),
                vertexLabel,
                faceLabel,
                sizeLabel,
                new javafx.scene.control.Separator(),
                materialLabel
        );

        fileNameLabel.setWrapText(true);
        materialLabel.setWrapText(true);

        applyTheme(true);
    }

    public void updateFromModel(Group moleculeGroup, String fileName) {
        fileNameLabel.setText("文件: " + (fileName != null ? fileName : "--"));

        int[] vertCount = {0}, faceCount = {0};
        java.util.Set<String> materials = new java.util.HashSet<>();

        traverseModel(moleculeGroup, vertCount, faceCount, materials);

        vertexLabel.setText("顶点: " + String.format("%,d", vertCount[0]));
        faceLabel.setText("三角面: " + String.format("%,d", faceCount[0]));

        Bounds bounds = moleculeGroup.getBoundsInParent();
        if (bounds != null && !bounds.isEmpty()) {
            sizeLabel.setText(String.format("尺寸: %.2f x %.2f x %.2f",
                    bounds.getWidth(), bounds.getHeight(), bounds.getDepth()));
        } else {
            sizeLabel.setText("尺寸: --");
        }

        if (!materials.isEmpty()) {
            materialLabel.setText("材质: " + String.join(", ", materials));
        } else {
            materialLabel.setText("材质: 无");
        }
    }

    private void traverseModel(Node node, int[] vertCount, int[] faceCount,
                               java.util.Set<String> materials) {
        if (node instanceof MeshView mesh) {
            if (mesh.getMesh() instanceof TriangleMesh triMesh) {
                vertCount[0] += triMesh.getPoints().size() / 3;
                faceCount[0] += triMesh.getFaces().size() / 6;
            }
            if (mesh.getMaterial() != null) {
                materials.add(mesh.getMaterial().getClass().getSimpleName());
            }
        } else if (node instanceof Group group) {
            for (Node child : group.getChildren()) {
                traverseModel(child, vertCount, faceCount, materials);
            }
        }
    }

    public void applyTheme(boolean isDark) {
        this.isDark = isDark;
        String bg = isDark ? "#2b2b2b" : "#f0f0f0";
        String text = isDark ? "#e0e0e0" : "#1a1a1a";

        setBackground(new Background(new BackgroundFill(Color.web(bg), null, null)));

        for (Node child : getChildren()) {
            if (child instanceof Label label) {
                label.setTextFill(Color.web(text));
            }
        }
        titleLabel.setTextFill(isDark ? Color.LIGHTBLUE : Color.DARKBLUE);
    }
}
