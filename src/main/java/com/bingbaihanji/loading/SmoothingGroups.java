package com.bingbaihanji.loading;

import com.bingbaihanji.matrix.Vector3;
import javafx.scene.shape.TriangleMesh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * 法线转平滑组的工具类
 * 核心功能:根据3D模型的面,法线数据,计算每个面所属的平滑组,用于模型的光影平滑渲染
 */
public class SmoothingGroups {

    // 法线夹角的余弦阈值(对应2度夹角),点积结果大于该值则认为两个法线方向足够接近
    private static final double normalAngle = 0.9994; // cos(2°)

    // 标记已处理过的面的位集合
    private final BitSet visited;

    // 标记未处理的面的位集合
    private final BitSet notVisited;

    // 存储所有面的二维数组:每个面包含顶点和UV坐标的索引(格式:[顶点1, UV1, 顶点2, UV2, ...])
    private final int[][] faces;

    // 存储每个面对应的法线索引二维数组:每个面的每个顶点对应一个法线索引
    private final int[][] faceNormals;

    // 存储所有法线的一维数组:按[x1,y1,z1,x2,y2,z2,...]格式存储三维法线向量
    private final float[] normals;

    // 广度优先搜索(BFS)的队列,用于遍历连通的面
    private Queue<Integer> q;

    // 存储每个面的所有边的数组:faceEdges[面索引] = 该面的所有边
    private Edge[][] faceEdges;

    /**
     * 构造方法:初始化平滑组计算的核心数据
     *
     * @param faces       面的二维数组,每个面包含顶点和UV索引
     * @param faceNormals 每个面对应的法线索引二维数组
     * @param normals     法线一维数组,存储所有三维法线向量
     */
    public SmoothingGroups(int[][] faces, int[][] faceNormals, float[] normals) {
        this.faces = faces;
        this.faceNormals = faceNormals;
        this.normals = normals;
        // 初始化位集合,大小为面的总数
        visited = new BitSet(faces.length);
        notVisited = new BitSet(faces.length);
        // 初始时所有面都标记为未处理
        notVisited.set(0, faces.length, true);
        // 初始化BFS队列
        q = new LinkedList<>();
    }

    /**
     * 判断两个法线向量是否"相等"(方向足够接近)
     *
     * @param n1 第一个法线向量
     * @param n2 第二个法线向量
     * @return true=法线方向足够接近,false=法线方向差异较大
     */
    private static boolean isNormalsEqual(Vector3 n1, Vector3 n2) {
        // 过滤特殊的"未锁定"法线值(1.0e20f为自定义的无效标记)
        if (n1.x == 1.0e20f || n1.y == 1.0e20f || n1.z == 1.0e20f
                || n2.x == 1.0e20f || n2.y == 1.0e20f || n2.z == 1.0e20f) {
            return false;
        }
        // 法线向量归一化(转为单位向量),确保点积结果仅反映方向
        Vector3 myN1 = new Vector3(n1);
        myN1.normalizeLocal();
        Vector3 myN2 = new Vector3(n2);
        myN2.normalizeLocal();
        // 计算点积并与阈值比较:点积越大,法线方向越接近
        return myN1.dot(myN2) >= normalAngle;
    }

    /**
     * 计算PolygonMesh格式数据的平滑组
     *
     * @param faces       面的二维数组:每个面为[顶点1, UV1, 顶点2, UV2, ...]
     * @param faceNormals 面的法线索引二维数组:每个面的每个顶点对应一个法线索引
     * @param normals     法线一维数组:按[x1,y1,z1,x2,y2,z2,...]存储三维法线
     * @return 每个面对应的平滑组ID数组,长度等于面的总数
     */
    public static int[] calcSmoothGroups(int[][] faces, int[][] faceNormals, float[] normals) {
        SmoothingGroups smoothGroups = new SmoothingGroups(faces, faceNormals, normals);
        return smoothGroups.calcSmoothGroups();
    }

    /**
     * 计算TriangleMesh格式数据的平滑组(适配JavaFX的TriangleMesh)
     * 核心逻辑:将扁平的一维数组转换为PolygonMesh风格的二维数组,再调用重载方法
     *
     * @param mesh            JavaFX的TriangleMesh对象,用于获取面和法线的元素大小
     * @param flatFaces       扁平的面索引一维数组:按[顶点1,UV1,顶点2,UV2,顶点3,UV3,...]存储
     * @param flatFaceNormals 扁平的法线索引一维数组:每个三角形面对应3个法线索引
     * @param normals         法线一维数组:按[x1,y1,z1,x2,y2,z2,...]存储三维法线
     * @return 每个面对应的平滑组ID数组,长度等于面的总数
     */
    public static int[] calcSmoothGroups(TriangleMesh mesh, int[] flatFaces, int[] flatFaceNormals, float[] normals) {
        // 获取TriangleMesh中每个面的元素数量(如三角形面为6:3个顶点+3个UV)
        int faceElementSize = mesh.getFaceElementSize();
        int[][] faces = new int[flatFaces.length / faceElementSize][faceElementSize];
        // 将扁平数组转换为二维面数组
        for (int f = 0; f < faces.length; f++) {
            System.arraycopy(flatFaces, f * faceElementSize, faces[f], 0, faceElementSize);
        }

        // 获取TriangleMesh中每个法线组的元素数量(如三角形面为3)
        int pointElementSize = mesh.getPointElementSize();
        int[][] faceNormals = new int[flatFaceNormals.length / pointElementSize][pointElementSize];
        // 将扁平法线索引数组转换为二维数组
        for (int f = 0; f < faceNormals.length; f++) {
            System.arraycopy(flatFaceNormals, f * pointElementSize, faceNormals[f], 0, pointElementSize);
        }

        return calcSmoothGroups(faces, faceNormals, normals);
    }

    /**
     * 从相邻面映射中获取下一个连通组件(BFS遍历)
     * 连通组件:通过平滑边连接的一组面,属于同一个平滑组
     *
     * @param adjacentFaces 边到相邻面的映射:Key=边,Value=该边关联的两个面的索引
     * @return 一个连通组件的面索引列表
     */
    private List<Integer> getNextConnectedComponent(Map<Edge, List<Integer>> adjacentFaces) {
        // 获取最后一个未处理的面索引
        int index = notVisited.previousSetBit(faces.length - 1);
        q.add(index);
        // 标记为已访问
        visited.set(index);
        notVisited.set(index, false);

        List<Integer> res = new ArrayList<>();
        // BFS遍历连通的面
        while (!q.isEmpty()) {
            Integer faceIndex = q.remove();
            res.add(faceIndex);
            // 遍历当前面的所有边
            for (Edge edge : faceEdges[faceIndex]) {
                List<Integer> adjFaces = adjacentFaces.get(edge);
                if (adjFaces == null) {
                    continue;
                }
                // 获取当前边关联的另一个面索引
                Integer adjFaceIndex = adjFaces.get(adjFaces.get(0).equals(faceIndex) ? 1 : 0);
                // 若未访问过,则加入队列并标记
                if (!visited.get(adjFaceIndex)) {
                    q.add(adjFaceIndex);
                    visited.set(adjFaceIndex);
                    notVisited.set(adjFaceIndex, false);
                }
            }
        }
        return res;
    }

    /**
     * 判断是否还有未处理的连通组件
     *
     * @return true=存在未处理的组件,false=所有组件已处理
     */
    private boolean hasNextConnectedComponent() {
        return !notVisited.isEmpty();
    }

    /**
     * 计算每个面的所有边
     * 边由顶点索引和法线索引构成,且统一顶点顺序(min/max)保证边的唯一性
     */
    private void computeFaceEdges() {
        faceEdges = new Edge[faces.length][];
        for (int f = 0; f < faces.length; f++) {
            int[] face = faces[f];
            int[] faceNormal = faceNormals[f];
            // 计算面的顶点数量(每个顶点对应2个元素:顶点索引+UV索引)
            int n = face.length / 2;
            faceEdges[f] = new Edge[n];

            // 从面的最后一个顶点开始,构建环形边(最后一个顶点连第一个顶点)
            int from = face[(n - 1) * 2];
            int fromNormal = faceNormal[n - 1];
            for (int i = 0; i < n; i++) {
                int to = face[i * 2];
                int toNormal = faceNormal[i];
                // 构建边(内部会统一顶点/法线索引的顺序)
                Edge edge = new Edge(from, to, fromNormal, toNormal);
                faceEdges[f][i] = edge;
                // 更新起点为当前终点,继续构建下一条边
                from = to;
                fromNormal = toNormal;
            }
        }
    }

    /**
     * 构建边到相邻面的映射
     *
     * @return 边到关联面的映射(仅保留关联2个面的边,过滤边界边)
     */
    private Map<Edge, List<Integer>> getAdjacentFaces() {
        Map<Edge, List<Integer>> adjacentFaces = new HashMap<>();
        // 遍历所有面的所有边,构建边与面的关联
        for (int f = 0; f < faceEdges.length; f++) {
            for (Edge edge : faceEdges[f]) {
                adjacentFaces.computeIfAbsent(edge, k -> new ArrayList<>()).add(f);
            }
        }
        // 移除仅关联1个面的边(边界边,无相邻面)
        adjacentFaces.entrySet().removeIf(e -> e.getValue().size() != 2);
        return adjacentFaces;
    }

    /**
     * 根据法线索引从法线数组中获取三维法线向量
     *
     * @param index 法线索引
     * @return 三维法线向量(Vector3对象)
     */
    Vector3 getNormal(int index) {
        return new Vector3(normals[index * 3], normals[index * 3 + 1], normals[index * 3 + 2]);
    }

    /**
     * 从相邻面映射中筛选出"平滑边"
     * 平滑边:关联的两个面的法线方向足够接近,满足平滑条件
     *
     * @param adjacentFaces 边到相邻面的映射
     * @return 平滑边到相邻面的映射
     */
    private Map<Edge, List<Integer>> getSmoothEdges(Map<Edge, List<Integer>> adjacentFaces) {
        Map<Edge, List<Integer>> smoothEdges = new HashMap<>();

        for (int face = 0; face < faceEdges.length; face++) {
            for (Edge edge : faceEdges[face]) {
                List<Integer> adjFaces = adjacentFaces.get(edge);
                // 过滤边界边或无效边
                if (adjFaces == null || adjFaces.size() != 2) {
                    continue;
                }
                // 获取当前边关联的另一个面索引
                int adjFace = adjFaces.get(adjFaces.get(0) == face ? 1 : 0);
                Edge[] adjFaceEdges = faceEdges[adjFace];
                // 找到相邻面中对应的边
                int adjEdgeInd = Arrays.asList(adjFaceEdges).indexOf(edge);
                if (adjEdgeInd == -1) {
                    System.out.println("无法在面 " + adjFace + " 中找到边 " + edge);
                    System.out.println(Arrays.asList(adjFaceEdges));
                    continue;
                }
                Edge adjEdge = adjFaceEdges[adjEdgeInd];

                // 判断边是否满足平滑条件,满足则加入平滑边映射
                if (edge.isSmooth(adjEdge)) {
                    smoothEdges.put(edge, adjFaces);
                }
            }
        }
        return smoothEdges;
    }

    /**
     * 计算所有平滑的连通组件
     *
     * @param smoothEdges 平滑边到相邻面的映射
     * @return 所有连通组件的列表:每个组件是一组连续的平滑面索引
     */
    private List<List<Integer>> calcConnComponents(Map<Edge, List<Integer>> smoothEdges) {
        List<List<Integer>> groups = new ArrayList<>();
        // 遍历所有未处理的连通组件,直到全部处理完毕
        while (hasNextConnectedComponent()) {
            List<Integer> smoothGroup = getNextConnectedComponent(smoothEdges);
            groups.add(smoothGroup);
        }
        return groups;
    }

    /**
     * 根据连通组件生成平滑组ID数组
     * 规则:单个面的组件设为0(不平滑),多个面的组件分配位掩码(1 << curGroup),31位后循环(避免int溢出)
     *
     * @param groups 连通组件列表
     * @return 每个面对应的平滑组ID数组
     */
    private int[] generateSmGroups(List<List<Integer>> groups) {
        int[] smGroups = new int[faceNormals.length];
        int curGroup = 0;
        for (List<Integer> list : groups) {
            if (list.size() == 1) {
                // 单个面的组件,平滑组ID设为0(表示不平滑)
                smGroups[list.get(0)] = 0;
            } else {
                // 多个面的组件,分配位掩码形式的平滑组ID
                for (Integer faceIndex : list) {
                    smGroups[faceIndex] = 1 << curGroup;
                }
                // 最多支持31个不同的平滑组(int的符号位不使用),超过则循环
                if (curGroup++ == 31) {
                    curGroup = 0;
                }
            }
        }
        return smGroups;
    }

    /**
     * 核心方法:执行平滑组计算的完整流程
     *
     * @return 每个面对应的平滑组ID数组
     */
    private int[] calcSmoothGroups() {
        // 1. 计算每个面的所有边
        computeFaceEdges();
        // 2. 构建边到相邻面的映射
        Map<Edge, List<Integer>> adjacentFaces = getAdjacentFaces();
        // 3. 筛选出满足平滑条件的边
        Map<Edge, List<Integer>> smoothEdges = getSmoothEdges(adjacentFaces);
        // 4. 计算所有平滑的连通组件
        List<List<Integer>> groups = calcConnComponents(smoothEdges);
        // 5. 根据连通组件生成平滑组ID
        return generateSmGroups(groups);
    }

    /**
     * 内部类:表示3D模型中的边
     * 包含边的起点/终点顶点索引,对应的法线索引,并重写hashCode和equals保证边的唯一性
     */
    private class Edge {

        // 边的起点/终点顶点索引(已按min/max排序,保证方向无关)
        int from, to;

        // 边的起点/终点法线索引(已按min/max排序)
        int fromNormal, toNormal;

        /**
         * 构造方法:初始化边的顶点和法线索引,并统一顺序(min/max)保证边的唯一性
         *
         * @param from       起点顶点索引
         * @param to         终点顶点索引
         * @param fromNormal 起点法线索引
         * @param toNormal   终点法线索引
         */
        public Edge(int from, int to, int fromNormal, int toNormal) {
            this.from = Math.min(from, to);
            this.to = Math.max(from, to);
            this.fromNormal = Math.min(fromNormal, toNormal);
            this.toNormal = Math.max(fromNormal, toNormal);
        }

        /**
         * 判断当前边与另一个边是否满足"平滑条件"
         * 逻辑:两边的法线索引对应的法线向量需匹配(考虑边的方向反转)
         *
         * @param edge 另一个边
         * @return true=平滑,false=不平滑
         */
        public boolean isSmooth(Edge edge) {
            return (isNormalsEqual(getNormal(fromNormal), getNormal(edge.fromNormal))
                    && isNormalsEqual(getNormal(toNormal), getNormal(edge.toNormal)))
                    || (isNormalsEqual(getNormal(fromNormal), getNormal(edge.toNormal))
                    && isNormalsEqual(getNormal(toNormal), getNormal(edge.fromNormal)));
        }

        /**
         * 重写hashCode:仅基于顶点索引计算(法线索引用于平滑判断,不影响边的唯一性)
         *
         * @return 边的哈希值
         */
        @Override
        public int hashCode() {
            int hash = 7;
            hash = 41 * hash + this.from;
            hash = 41 * hash + this.to;
            return hash;
        }

        /**
         * 重写equals:仅比较顶点索引(边的唯一性由顶点决定)
         *
         * @param obj 待比较的对象
         * @return true=两个边是同一条边,false=不同边
         */
        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Edge other = (Edge) obj;
            if (this.from != other.from) {
                return false;
            }
            return this.to == other.to;
        }
    }
}