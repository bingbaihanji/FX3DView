package com.bingbaihanji.menu;

import com.bingbaihanji.scene.PointCloudBuilder;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Mesh;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 点云处理器：负责模型顶点采样、点云构建和点云模式切换
 *
 * @author bingbaihanji
 */
@Slf4j
public class PointCloudHandler {

    /**
     * 点云采样率：每N个顶点取1个
     */
    private static final int POINT_SAMPLE_RATE = 3;
    /**
     * 采样上限
     */
    private static final int MAX_POINTS = 100_000;

    /**
     * 记录被隐藏的MeshView（点云模式时隐藏原模型，用于恢复）
     */
    private final List<MeshView> hiddenMeshViews = new ArrayList<>();
    /**
     * 是否为点云模式
     */
    private boolean isDotPlotMode = false;
    /**
     * 当前点云Group引用（用于删除旧点云）
     */
    private Group dotPlotGroup = null;
    /**
     * 当前绑定的世界 Group（用于点云刷新）
     */
    private Group currentWorld = null;
    /**
     * 最近一次构建点云使用的采样数据缓存（用于相机旋转时重建 billboard）
     */
    private float[] cachedSamplePoints = null;
    /**
     * 上次 billboard 重建时间戳（纳秒），用于节流
     */
    private long lastRefreshNs = 0;
    /**
     * billboard 重建最小间隔（纳秒），约 60 FPS
     */
    private static final long REFRESH_INTERVAL_NS = 16_000_000;

    /**
     * 递归查找所有MeshView并执行回调
     */
    private static void traverseAllMeshViews(Node node, Consumer<MeshView> callback) {
        if (node instanceof MeshView meshView) {
            callback.accept(meshView);
        } else if (node instanceof Group group) {
            for (Node child : group.getChildren()) {
                traverseAllMeshViews(child, callback);
            }
        }
    }

    /**
     * 切换点云模式
     * <p>
     * 开启：隐藏原MeshView，从顶点采样生成点云（先以十字星渲染，随后由 refreshPointCloud 转为 billboard）
     * 关闭：删除点云，恢复原MeshView可见性
     * </p>
     */
    public void toggleDotPlots(Group world) {
        this.currentWorld = world;
        if (!isDotPlotMode) {
            // 删除旧点云（避免重复叠加）
            removeDotPlotFromWorld();
            // 恢复之前隐藏的MeshView
            hiddenMeshViews.forEach(meshView -> meshView.setVisible(true));
            hiddenMeshViews.clear();

            // 采样顶点并缓存
            cachedSamplePoints = samplePointsFromModel(world);
            // 初始用十字星渲染（不依赖相机方向），后续 refreshPointCloud 会转为 billboard
            dotPlotGroup = PointCloudBuilder.build(cachedSamplePoints, Color.LIGHTGRAY, null);
            world.getChildren().add(dotPlotGroup);
            isDotPlotMode = true;
            log.info("点云模式已开启");
        } else {
            removeDotPlotFromWorld();
            hiddenMeshViews.forEach(meshView -> meshView.setVisible(true));
            hiddenMeshViews.clear();
            cachedSamplePoints = null;
            currentWorld = null;
            isDotPlotMode = false;
            log.info("点云模式已关闭");
        }
    }

    /**
     * 根据相机旋转刷新点云为 billboard 正方形（始终面朝相机）
     * <p>
     * 仅在点云模式下生效。直接用缓存的采样点数据重建 Mesh，
     * 避免重新遍历模型节点树。内置 ~60fps 节流避免拖拽旋转时过度重建。
     * </p>
     *
     * @param cameraAffine 当前相机旋转仿射
     */
    public void refreshPointCloud(Affine cameraAffine) {
        if (!isDotPlotMode || cachedSamplePoints == null || currentWorld == null) return;

        long now = System.nanoTime();
        if (now - lastRefreshNs < REFRESH_INTERVAL_NS) return;
        lastRefreshNs = now;

        // 移除旧点云
        removeDotPlotFromWorld();

        // 用缓存数据 + 新相机方向重建 billboard 点云
        dotPlotGroup = PointCloudBuilder.build(cachedSamplePoints, Color.LIGHTGRAY, cameraAffine);
        currentWorld.getChildren().add(dotPlotGroup);
    }

    private void removeDotPlotFromWorld() {
        if (dotPlotGroup != null && currentWorld != null
                && currentWorld.getChildren().contains(dotPlotGroup)) {
            currentWorld.getChildren().remove(dotPlotGroup);
            dotPlotGroup = null;
        }
    }

    /**
     * 从模型顶点采样世界坐标点云数据
     * <p>
     * 递归查找所有MeshView，从其TriangleMesh中采样顶点，
     * 通过worldTransform将局部坐标转为世界坐标。
     * 返回的采样数据被缓存，用于后续 billboard 重建。
     * </p>
     *
     * @param modelGroup 模型根Group
     * @return 世界坐标采样点数组 {x,y,z, x,y,z, ...}，无采样点时返回空数组
     */
    private float[] samplePointsFromModel(Group modelGroup) {
        List<float[]> pointChunks = new ArrayList<>();
        int[] counter = {0};

        traverseAllMeshViews(modelGroup, meshView -> {
            if (counter[0] >= MAX_POINTS) return;

            meshView.setVisible(false);
            hiddenMeshViews.add(meshView);

            Mesh meshData = meshView.getMesh();
            if (!(meshData instanceof TriangleMesh triangleMesh)) {
                return;
            }

            float[] points = triangleMesh.getPoints().toArray(null);

            // 手动矩阵乘法，避免每点分配Point3D
            Transform t = meshView.getLocalToSceneTransform();
            double mxx = t.getMxx(), mxy = t.getMxy(), mxz = t.getMxz(), tx_ = t.getTx();
            double myx = t.getMyx(), myy = t.getMyy(), myz = t.getMyz(), ty_ = t.getTy();
            double mzx = t.getMzx(), mzy = t.getMzy(), mzz = t.getMzz(), tz_ = t.getTz();

            int sampleCount = points.length / (3 * POINT_SAMPLE_RATE);
            int available = MAX_POINTS - counter[0];
            int chunkSize = Math.min(sampleCount, available);
            if (chunkSize <= 0) return;
            float[] chunk = new float[chunkSize * 3];
            int ci = 0;

            for (int i = 0, added = 0; added < chunkSize; i += 3 * POINT_SAMPLE_RATE, added++) {
                float lx = points[i], ly = points[i + 1], lz = points[i + 2];
                chunk[ci++] = (float) (mxx * lx + mxy * ly + mxz * lz + tx_);
                chunk[ci++] = (float) (myx * lx + myy * ly + myz * lz + ty_);
                chunk[ci++] = (float) (mzx * lx + mzy * ly + mzz * lz + tz_);
            }
            counter[0] += chunkSize;
            pointChunks.add(chunk);
        });

        if (pointChunks.isEmpty()) return new float[0];

        // 合并所有块
        float[] allPoints = new float[counter[0] * 3];
        int offset = 0;
        for (float[] chunk : pointChunks) {
            System.arraycopy(chunk, 0, allPoints, offset, chunk.length);
            offset += chunk.length;
        }
        return allPoints;
    }
}
