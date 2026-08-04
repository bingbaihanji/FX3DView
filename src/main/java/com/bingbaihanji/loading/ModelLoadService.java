package com.bingbaihanji.loading;

import com.bingbaihanji.core.Lifecycle;
import javafx.concurrent.Task;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 模型加载协调器.
 * <p>
 * 每个查看器窗口持有一个实例,用于串联菜单导入和拖放导入:
 * 新导入开始时取消旧任务,并用递增版本号保证只有最后一次导入可以更新场景.
 * </p>
 */
@Slf4j
public class ModelLoadService implements Lifecycle {

    private final Group world;

    private final Group moleculeGroup;

    private final ImporterRegistry registry;

    private final Runnable beforeModelInstall;

    private final Runnable onModelLoaded;

    private final Consumer<Task<Group>> onTaskCreated;

    private final AtomicLong loadSequence = new AtomicLong();

    private volatile Task<Group> currentTask;

    public ModelLoadService(Group world, Group moleculeGroup,
                            ImporterRegistry registry,
                            Runnable beforeModelInstall,
                            Runnable onModelLoaded,
                            Consumer<Task<Group>> onTaskCreated) {
        this.world = Objects.requireNonNull(world, "world 不能为空");
        this.moleculeGroup = Objects.requireNonNull(moleculeGroup, "moleculeGroup 不能为空");
        this.registry = Objects.requireNonNull(registry, "registry 不能为空");
        this.beforeModelInstall = beforeModelInstall;
        this.onModelLoaded = onModelLoaded;
        this.onTaskCreated = onTaskCreated;
    }

    private static String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 && dot < fileName.length() - 1
                ? fileName.substring(dot + 1).toLowerCase()
                : "";
    }

    /**
     * 兼容旧调用点的一次性加载入口.新代码优先使用每个窗口独立的实例方法.
     */
    public static Task<Group> loadModelFile(File file, Group world, Group moleculeGroup,
                                            Runnable onLoaded, Consumer<Task<Group>> onTaskCreated,
                                            ImporterRegistry registry) {
        ModelLoadService service = new ModelLoadService(
                world, moleculeGroup, registry, null, onLoaded, onTaskCreated);
        return service.load(file);
    }

    /**
     * 启动一次模型加载.
     *
     * @param file 模型文件
     * @return 后台加载任务
     */
    public Task<Group> load(File file) {
        Objects.requireNonNull(file, "file 不能为空");

        Task<Group> previous = currentTask;
        if (previous != null && previous.isRunning()) {
            previous.cancel();
        }

        long loadId = loadSequence.incrementAndGet();
        Task<Group> loadTask = createLoadTask(file);
        currentTask = loadTask;

        loadTask.setOnSucceeded(event -> {
            if (!isCurrent(loadId, loadTask)) {
                log.debug("忽略过期模型加载结果: {}", file);
                return;
            }

            Group model = loadTask.getValue();
            if (model == null) {
                return;
            }

            installModel(model);
            currentTask = null;

            if (onModelLoaded != null) {
                onModelLoaded.run();
            }
        });

        loadTask.setOnFailed(event -> {
            if (!isCurrent(loadId, loadTask)) {
                return;
            }
            currentTask = null;
            Throwable ex = loadTask.getException();
            if (loadTask.isCancelled()) {
                log.info("模型加载已被用户取消: {}", file);
            } else {
                log.error("加载模型失败: {}", file, ex);
            }
        });

        loadTask.setOnCancelled(event -> {
            if (isCurrent(loadId, loadTask)) {
                currentTask = null;
                log.info("模型加载已取消: {}", file);
            }
        });

        if (onTaskCreated != null) {
            onTaskCreated.accept(loadTask);
        }

        Thread thread = new Thread(loadTask, "Load Model Thread");
        thread.setDaemon(true);
        thread.start();
        return loadTask;
    }

    private Task<Group> createLoadTask(File file) {
        return new Task<>() {

            @Override
            protected Group call() throws Exception {
                String ext = getExtension(file.getName());
                Importer importer = registry.getImporter(ext);
                if (importer == null) {
                    throw new IllegalArgumentException("不支持的文件格式: " + ext);
                }

                Model3D loaded = importer.load(file.toURI().toURL(), (progress, status) -> {
                    updateProgress(progress, 1.0);
                    updateMessage(status);
                });

                Group model = new Group();
                model.getTransforms().add(new Rotate(-180, Rotate.Z_AXIS));

                for (Node node : loaded.getMeshViews()) {
                    if (node instanceof MeshView mesh) {
                        mesh.setCullFace(CullFace.NONE);
                    }
                    model.getChildren().add(node);
                }
                return model;
            }
        };
    }

    private void installModel(Group model) {
        if (beforeModelInstall != null) {
            beforeModelInstall.run();
        }

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
    }

    private boolean isCurrent(long loadId, Task<Group> task) {
        return loadSequence.get() == loadId && currentTask == task;
    }

    @Override
    public void dispose() {
        cancelCurrentLoad();
    }

    /**
     * 取消当前加载任务,并阻止其后续结果落到场景中.
     */
    public void cancelCurrentLoad() {
        Task<Group> task = currentTask;
        if (task != null && task.isRunning()) {
            task.cancel();
        }
        currentTask = null;
    }
}
