package com.bingbaihanji.ui;

import com.bingbaihanji.camera.CameraSystem;
import com.bingbaihanji.core.Lifecycle;
import com.bingbaihanji.scene.Scene3DManager;
import javafx.animation.AnimationTimer;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.text.Font;

/**
 * 状态栏:底部显示FPS,模型统计,相机位置,旋转策略,加载进度
 */
public class StatusBar extends HBox implements Lifecycle {

    private static final double UPDATE_INTERVAL_NS = 200_000_000.0; // 200ms

    private static final Font STATUS_FONT = new Font("System", 11);

    private final Label fpsLabel = new Label("FPS: --");

    private final Label vertexLabel = new Label("顶点: --");

    private final Label faceLabel = new Label("面: --");

    private final Label sizeLabel = new Label("尺寸: --");

    private final Label camLabel = new Label("相机: --");

    private final Label stratLabel = new Label("策略: --");

    /**
     * 加载进度条(默认隐藏,加载时显示)
     */
    private final ProgressBar progressBar = new ProgressBar(0);

    /**
     * 取消加载按钮(默认隐藏,加载时显示)
     */
    private final Button cancelButton = new Button("取消");

    private final CameraSystem cameraSystem;

    private final Scene3DManager sceneManager;

    private final AnimationTimer fpsTimer;

    private long frameCount;

    private long lastUpdateNs;

    /**
     * 当前绑定的加载任务(用于取消操作)
     */
    private volatile Task<?> currentTask;

    public StatusBar(CameraSystem cameraSystem, Scene3DManager sceneManager) {
        this.cameraSystem = cameraSystem;
        this.sceneManager = sceneManager;

        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(2, 8, 2, 8));
        setSpacing(6);
        setStyle("-fx-background-color: #2b2b2b;");

        for (Label lbl : new Label[]{fpsLabel, vertexLabel, faceLabel, sizeLabel, camLabel, stratLabel}) {
            lbl.setFont(STATUS_FONT);
            lbl.setTextFill(Color.LIGHTGRAY);
        }

        // 进度条样式
        progressBar.setPrefWidth(120);
        progressBar.setMaxHeight(12);
        progressBar.setVisible(false);

        // 取消按钮样式
        cancelButton.setFont(STATUS_FONT);
        cancelButton.setTextFill(Color.LIGHTGRAY);
        cancelButton.setStyle("-fx-background-color: #444; -fx-border-color: #666; -fx-border-radius: 2;");
        cancelButton.setVisible(false);
        cancelButton.setOnAction(e -> {
            if (currentTask != null && currentTask.isRunning()) {
                currentTask.cancel();
            }
        });

        getChildren().addAll(
                fpsLabel, sep(), vertexLabel, sep(),
                faceLabel, sep(), sizeLabel, sep(),
                camLabel, sep(), stratLabel, sep(),
                progressBar, cancelButton
        );

        updateStrategyLabel();
        updateCameraLabel();

        fpsTimer = new AnimationTimer() {

            @Override
            public void handle(long now) {
                frameCount++;
                if (now - lastUpdateNs > UPDATE_INTERVAL_NS) {
                    double fps = frameCount / ((now - lastUpdateNs) / 1_000_000_000.0);
                    fpsLabel.setText(String.format("FPS: %.0f", fps));
                    frameCount = 0;
                    lastUpdateNs = now;
                }
            }
        };
        fpsTimer.start();
    }

    private static String formatNumber(int n) {
        if (n >= 1_000_000) {
            return String.format("%.1fM", n / 1_000_000.0);
        }
        if (n >= 1_000) {
            return String.format("%.1fK", n / 1_000.0);
        }
        return String.valueOf(n);
    }

    // ==================== 公开更新方法 ====================

    private Separator sep() {
        Separator s = new Separator();
        s.setVisible(false); // JavaFX Separator is horizontal by default; use as invisible spacer
        return s;
    }

    /**
     * 模型加载后调用
     */
    public void updateModelStats() {
        Group moleculeGroup = sceneManager.getMoleculeGroup();
        int totalVertices = 0;
        int totalFaces = 0;

        int[] stats = new int[2];
        for (Node child : moleculeGroup.getChildren()) {
            accumulateStats(child, stats);
        }
        totalVertices = stats[0];
        totalFaces = stats[1];

        vertexLabel.setText("顶点: " + (totalVertices > 0 ? formatNumber(totalVertices) : "--"));
        faceLabel.setText("面: " + (totalFaces > 0 ? formatNumber(totalFaces) : "--"));

        Bounds b = moleculeGroup.getBoundsInParent();
        if (b.getWidth() > 0 || b.getHeight() > 0 || b.getDepth() > 0) {
            sizeLabel.setText(String.format("尺寸: %.1f×%.1f×%.1f", b.getWidth(), b.getHeight(), b.getDepth()));
        } else {
            sizeLabel.setText("尺寸: --");
        }
    }

    /**
     * 每帧或定时更新相机信息
     */
    public void updateCameraLabel() {
        double z = cameraSystem.getCamera().getTranslateZ();
        camLabel.setText(String.format("相机: Z=%.0f", z));
    }

    /**
     * 清空模型统计(无模型时)
     */
    public void clearModelStats() {
        vertexLabel.setText("顶点: --");
        faceLabel.setText("面: --");
        sizeLabel.setText("尺寸: --");
    }

    public void updateStrategyLabel() {
        stratLabel.setText("策略: " + cameraSystem.getRotationStrategy().getStrategyName());
    }

    // ==================== 主题 ====================

    @Override
    public void start() {
        fpsTimer.start();
    }

    @Override
    public void stop() {
        fpsTimer.stop();
    }

    @Override
    public void dispose() {
        fpsTimer.stop();
    }

    /**
     * 将状态栏的进度条绑定到后台加载任务
     * <p>
     * 任务开始时显示进度条和取消按钮,任务结束(成功/失败/取消)时隐藏.
     * 取消按钮调用 {@link Task#cancel()} 中断后台线程.
     * </p>
     *
     * @param task 后台加载任务
     */
    public void bindToLoadingTask(Task<?> task) {
        this.currentTask = task;
        progressBar.setVisible(true);
        cancelButton.setVisible(true);
        progressBar.progressProperty().bind(task.progressProperty());

        // 使用 stateProperty 监听而不覆盖已有的 onSucceeded/onFailed/onCancelled 处理器
        task.stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED
                    || newState == Worker.State.FAILED
                    || newState == Worker.State.CANCELLED) {
                hideProgress(task);
            }
        });

        cancelButton.setOnAction(e -> {
            if (currentTask != null && currentTask.isRunning()) {
                currentTask.cancel();
            }
        });
    }

    /**
     * 隐藏进度条和取消按钮,解绑 progress 属性
     */
    private void hideProgress(Task<?> finishedTask) {
        if (currentTask != finishedTask) {
            return;
        }
        progressBar.setVisible(false);
        cancelButton.setVisible(false);
        progressBar.progressProperty().unbind();
        progressBar.setProgress(0);
        currentTask = null;
    }

    // ==================== 辅助 ====================

    /**
     * 应用主题:isDark=true深色,false浅色
     */
    public void applyTheme(boolean isDark) {
        String bg = isDark ? "#2b2b2b" : "#e0e0e0";
        Color fg = isDark ? Color.LIGHTGRAY : Color.BLACK;
        setStyle("-fx-background-color: " + bg + ";");
        for (Node n : getChildren()) {
            if (n instanceof Label lbl) {
                lbl.setTextFill(fg);
            }
        }
    }

    private void accumulateStats(Node node, int[] stats) {
        if (node instanceof MeshView mv && mv.getMesh() instanceof TriangleMesh tm) {
            stats[0] += tm.getPoints().size() / tm.getPointElementSize();
            stats[1] += tm.getFaces().size() / tm.getFaceElementSize();
        } else if (node instanceof Group g) {
            for (Node child : g.getChildren()) {
                accumulateStats(child, stats);
            }
        }
    }

}
