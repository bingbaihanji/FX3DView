package com.bingbaihanji.interaction;

import com.bingbaihanji.core.Lifecycle;
import com.bingbaihanji.loading.Importer;
import com.bingbaihanji.loading.ImporterRegistry;
import com.bingbaihanji.loading.Model3D;
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
 * 拖放加载处理器：将支持的3D文件拖入窗口即可加载模型
 * <p>
 * 通过 {@link ImporterRegistry} 按文件扩展名自动匹配合适的导入器，
 * 支持的文件格式由注册表中的导入器工厂决定。
 * </p>
 */
@Slf4j
public class DragDropHandler implements Lifecycle {

    private final Group world;
    private final Group moleculeGroup;
    private final Runnable onModelLoaded;
    /**
     * 加载任务创建回调（在 FX 线程调用，用于 UI 绑定进度条/取消按钮）
     */
    private final Consumer<Task<Group>> onTaskCreated;
    /**
     * 导入器注册表，按扩展名查找 Importer 工厂
     */
    private final ImporterRegistry importerRegistry;
    private Pane attachedPane;

    /**
     * @param world            世界Group（用于添加moleculeGroup）
     * @param moleculeGroup    模型Group（模型挂载点）
     * @param onModelLoaded    模型加载完成回调（可null）
     * @param onTaskCreated    加载任务创建回调，用于UI绑定进度条（可null，在FX线程调用）
     * @param importerRegistry 导入器注册表，按扩展名获取 Importer 实例
     */
    public DragDropHandler(Group world, Group moleculeGroup,
                           Runnable onModelLoaded, Consumer<Task<Group>> onTaskCreated,
                           ImporterRegistry importerRegistry) {
        this.world = world;
        this.moleculeGroup = moleculeGroup;
        this.onModelLoaded = onModelLoaded;
        this.onTaskCreated = onTaskCreated;
        this.importerRegistry = importerRegistry;
    }

    // ==================== 共享的模型加载逻辑 ====================

    /**
     * 从文件扩展名提取不含点号的后缀（小写）
     */
    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && dot < fileName.length() - 1
                ? fileName.substring(dot + 1).toLowerCase()
                : "";
    }

    /**
     * 判断文件列表中是否包含注册表支持的文件
     */
    private static boolean hasSupportedFile(List<File> files, ImporterRegistry registry) {
        return findSupportedFile(files, registry) != null;
    }

    /**
     * 在文件列表中查找第一个受支持的文件
     */
    private static File findSupportedFile(List<File> files, ImporterRegistry registry) {
        for (File f : files) {
            if (registry.isExtensionSupported(getExtension(f.getName()))) {
                return f;
            }
        }
        return null;
    }

    /**
     * 从文件加载3D模型并挂载到场景中（异步，支持进度回调和取消）
     * <p>
     * MenuEvent和DragDropHandler共用此方法。
     * 通过 {@link ImporterRegistry} 按文件扩展名查找对应的 {@link Importer} 工厂，
     * 使用 {@link Importer.ProgressCallback} 将解析进度映射到 Task.updateProgress()，
     * 取消操作通过 Task.cancel() → Thread.interrupt() 实现。
     * </p>
     *
     * @param file          模型文件（扩展名需在注册表中已注册）
     * @param world         世界Group
     * @param moleculeGroup 模型Group
     * @param onLoaded      加载完成回调（UI线程，可null）
     * @param onTaskCreated 任务创建回调（FX线程，可null），用于UI绑定进度条/取消按钮
     * @param registry      导入器注册表，按扩展名获取 Importer
     * @return 正在执行的后台加载任务
     */
    public static Task<Group> loadModelFile(File file, Group world, Group moleculeGroup,
                                            Runnable onLoaded, Consumer<Task<Group>> onTaskCreated,
                                            ImporterRegistry registry) {
        Task<Group> loadTask = new Task<>() {
            @Override
            protected Group call() throws Exception {
                String ext = getExtension(file.getName());
                Importer importer = registry.getImporter(ext);
                if (importer == null) {
                    throw new IllegalArgumentException("不支持的文件格式: " + ext);
                }
                // 使用带进度回调的 load 方法，将解析进度映射到 Task 的 progress 属性
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
                log.error("加载模型失败", ex);
            }
        });

        // 同步回调（loadModelFile 已在 FX 线程被调用），让 UI 在后台线程启动前绑定进度条
        if (onTaskCreated != null) {
            onTaskCreated.accept(loadTask);
        }

        Thread thread = new Thread(loadTask);
        thread.setName("Load Model Thread");
        thread.setDaemon(true);
        thread.start();
        return loadTask;
    }

    /**
     * 从文件加载3D模型并挂载到场景中（异步，无进度UI绑定）
     * <p>
     * 便捷重载，不需要进度条/取消按钮时使用。
     * </p>
     */
    public static void loadModelFile(File file, Group world, Group moleculeGroup, Runnable onLoaded,
                                     ImporterRegistry registry) {
        loadModelFile(file, world, moleculeGroup, onLoaded, null, registry);
    }

    /**
     * 将拖放处理器绑定到面板
     */
    public void attachToPane(Pane pane) {
        this.attachedPane = pane;
        pane.setOnDragOver(this::handleDragOver);
        pane.setOnDragDropped(this::handleDragDropped);
    }

    @Override
    public void dispose() {
        if (attachedPane != null) {
            attachedPane.setOnDragOver(null);
            attachedPane.setOnDragDropped(null);
            attachedPane = null;
        }
    }

    // ==================== 辅助 ====================

    private void handleDragOver(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles() && hasSupportedFile(db.getFiles(), importerRegistry)) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void handleDragDropped(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (!db.hasFiles()) return;

        File supportedFile = findSupportedFile(db.getFiles(), importerRegistry);
        if (supportedFile != null) {
            loadModelFile(supportedFile, world, moleculeGroup, onModelLoaded, onTaskCreated, importerRegistry);
        }
        event.setDropCompleted(supportedFile != null);
        event.consume();
    }
}
