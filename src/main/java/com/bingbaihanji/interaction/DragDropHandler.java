package com.bingbaihanji.interaction;

import com.bingbaihanji.loading.Model3D;
import com.bingbaihanji.loading.ObjImporter;
import javafx.concurrent.Task;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * 拖放加载处理器：将.obj文件拖入窗口即可加载模型
 */
@Slf4j
public class DragDropHandler {

    private final Group world;
    private final Group moleculeGroup;
    private final Runnable onModelLoaded;
    /**
     * 加载任务创建回调（在 FX 线程调用，用于 UI 绑定进度条/取消按钮）
     */
    private final Consumer<Task<Group>> onTaskCreated;

    /**
     * @param world         世界Group（用于添加moleculeGroup）
     * @param moleculeGroup 模型Group（模型挂载点）
     * @param onModelLoaded 模型加载完成回调（可null）
     * @param onTaskCreated 加载任务创建回调，用于UI绑定进度条（可null，在FX线程调用）
     */
    public DragDropHandler(Group world, Group moleculeGroup,
                           Runnable onModelLoaded, Consumer<Task<Group>> onTaskCreated) {
        this.world = world;
        this.moleculeGroup = moleculeGroup;
        this.onModelLoaded = onModelLoaded;
        this.onTaskCreated = onTaskCreated;
    }

    /**
     * 从文件加载3D模型并挂载到场景中（异步，支持进度回调和取消）
     * <p>
     * MenuEvent和DragDropHandler共用此方法。
     * 通过 ObjImporter.ProgressCallback 将解析进度映射到 Task.updateProgress()，
     * 取消操作通过 Task.cancel() → Thread.interrupt() → ObjImporter.read() 中断检测实现。
     * </p>
     *
     * @param file          .obj文件
     * @param world         世界Group
     * @param moleculeGroup 模型Group
     * @param onLoaded      加载完成回调（UI线程，可null）
     * @param onTaskCreated 任务创建回调（FX线程，可null），用于UI绑定进度条/取消按钮
     * @return 正在执行的后台加载任务
     */
    public static Task<Group> loadModelFile(File file, Group world, Group moleculeGroup,
                                            Runnable onLoaded, Consumer<Task<Group>> onTaskCreated) {
        Task<Group> loadTask = new Task<>() {
            @Override
            protected Group call() throws Exception {
                ObjImporter importer = new ObjImporter();
                // 使用带进度回调的 load 方法，将 OBJ 解析进度映射到 Task 的 progress 属性
                Model3D load = importer.load(file.toURI().toURL(), (progress, status) -> {
                    updateProgress(progress, 1.0);
                    updateMessage(status);
                });

                Group model = new Group();
                Rotate rotateFix = new Rotate(-180, Rotate.Z_AXIS);
                model.getTransforms().add(rotateFix);

                for (Node n : load.getMeshViews()) {
                    if (n instanceof MeshView mesh) {
                        mesh.setCullFace(javafx.scene.shape.CullFace.NONE);
                        model.getChildren().add(mesh);
                    }
                }
                return model;
            }
        };

        loadTask.setOnSucceeded(event -> {
            Group model = loadTask.getValue();
            if (model == null) return;

            moleculeGroup.getChildren().clear();
            moleculeGroup.getChildren().add(model);

            var bounds = model.getBoundsInParent();
            double centerX = (bounds.getMinX() + bounds.getMaxX()) / 2;
            double centerY = (bounds.getMinY() + bounds.getMaxY()) / 2;
            double centerZ = (bounds.getMinZ() + bounds.getMaxZ()) / 2;
            moleculeGroup.setTranslateX(-centerX);
            moleculeGroup.setTranslateY(-centerY);
            moleculeGroup.setTranslateZ(-centerZ);

            if (!world.getChildren().contains(moleculeGroup)) {
                world.getChildren().add(moleculeGroup);
            }

            if (onLoaded != null) {
                onLoaded.run();
            }
        });

        loadTask.setOnFailed(event -> {
            Throwable ex = loadTask.getException();
            if (loadTask.isCancelled()) {
                log.info("模型加载已被用户取消");
            } else {
                log.error("拖放加载模型失败", ex);
            }
        });

        // 同步回调（loadModelFile 已在 FX 线程被调用），让 UI 在后台线程启动前绑定进度条
        if (onTaskCreated != null) {
            onTaskCreated.accept(loadTask);
        }

        Thread thread = new Thread(loadTask);
        thread.setDaemon(true);
        thread.start();
        return loadTask;
    }

    /**
     * 从文件加载3D模型并挂载到场景中（异步，无进度UI绑定）
     * <p>
     * 保留此重载以兼容旧调用方
     * </p>
     */
    public static void loadModelFile(File file, Group world, Group moleculeGroup, Runnable onLoaded) {
        loadModelFile(file, world, moleculeGroup, onLoaded, null);
    }

    private static boolean hasObjFile(List<File> files) {
        return findObjFile(files) != null;
    }

    // ==================== 共享的模型加载逻辑 ====================

    private static File findObjFile(List<File> files) {
        for (File f : files) {
            if (f.getName().toLowerCase().endsWith(".obj")) {
                return f;
            }
        }
        return null;
    }

    /**
     * 将拖放处理器绑定到面板
     */
    public void attachToPane(Pane pane) {
        pane.setOnDragOver(this::handleDragOver);
        pane.setOnDragDropped(this::handleDragDropped);
    }

    // ==================== 辅助 ====================

    private void handleDragOver(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles() && hasObjFile(db.getFiles())) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (!db.hasFiles()) return;

        File objFile = findObjFile(db.getFiles());
        if (objFile != null) {
            loadModelFile(objFile, world, moleculeGroup, onModelLoaded, onTaskCreated);
        }
        event.setDropCompleted(objFile != null);
        event.consume();
    }
}
