package com.bingbaihanji.menu;

import com.bingbaihanji.scene.PointCloudBuilder;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.Mesh;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
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
     * 开启：隐藏原MeshView，从顶点采样生成点云
     * 关闭：删除点云，恢复原MeshView可见性
     * </p>
     */
    public void toggleDotPlots(Group world) {
        if (!isDotPlotMode) {
            // 删除旧点云（避免重复叠加）
            if (dotPlotGroup != null) {
                world.getChildren().remove(dotPlotGroup);
            }
            // 恢复之前隐藏的MeshView
            hiddenMeshViews.forEach(meshView -> meshView.setVisible(true));
            hiddenMeshViews.clear();

            // 生成新点云
            dotPlotGroup = buildDotPlots(world);
            world.getChildren().add(dotPlotGroup);
            isDotPlotMode = true;
            log.info("点云模式已开启");
        } else {
            if (dotPlotGroup != null && world.getChildren().contains(dotPlotGroup)) {
                world.getChildren().remove(dotPlotGroup);
                dotPlotGroup = null;
            }
            hiddenMeshViews.forEach(meshView -> meshView.setVisible(true));
            hiddenMeshViews.clear();
            isDotPlotMode = false;
            log.info("点云模式已关闭");
        }
    }

    /**
     * 从模型顶点生成点云Group（批量渲染，单个TriangleMesh）
     * <p>
     * 递归查找所有MeshView，从其TriangleMesh中采样顶点，
     * 通过worldTransform将局部坐标转为世界坐标，
     * 用PointCloudBuilder合成为单个MeshView，避免N个Sphere节点的性能问题。
     * </p>
     */
    private Group buildDotPlots(Group modelGroup) {
        List<float[]> pointChunks = new ArrayList<>();
        int[] counter = {0}; // int包装以支持在lambda中修改

        traverseAllMeshViews(modelGroup, meshView -> {
            if (counter[0] >= MAX_POINTS) return;

            meshView.setVisible(false);
            hiddenMeshViews.add(meshView);

            Mesh meshData = meshView.getMesh();
            if (!(meshData instanceof TriangleMesh triangleMesh)) {
                log.error("不支持非TriangleMesh类型的模型");
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

        if (pointChunks.isEmpty()) return new Group();

        // 合并所有块
        float[] allPoints = new float[counter[0] * 3];
        int offset = 0;
        for (float[] chunk : pointChunks) {
            System.arraycopy(chunk, 0, allPoints, offset, chunk.length);
            offset += chunk.length;
        }

        return PointCloudBuilder.build(allPoints, Color.LIGHTGRAY);
    }
}
