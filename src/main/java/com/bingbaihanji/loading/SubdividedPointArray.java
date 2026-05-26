package com.bingbaihanji.loading;


import java.util.Arrays;

public class SubdividedPointArray extends SymbolicPointArray {
    // 控制点数据（上一细分级别的点）
    private final float[] controlPoints;

    // 控制点索引和权重因子
    private final int[][] controlInds;      // 控制点索引数组
    private final float[][] controlFactors; // 控制点权重因子数组

    // 细分点索引和权重因子
    private final int[][] inds;             // 细分点索引数组
    private final float[][] factors;        // 细分点权重因子数组

    // 边界处理模式
    private final SubdivisionMesh.BoundaryMode boundaryMode;

    // 当前点索引
    private int currPoint = 0;


    public SubdividedPointArray(SymbolicPointArray controlPointArray, int numPoints, SubdivisionMesh.BoundaryMode boundaryMode) {
        super(new float[NUM_COMPONENTS_PER_POINT * numPoints]);

        this.controlPoints = controlPointArray.data;
        this.controlInds = new int[numPoints][];
        this.controlFactors = new float[numPoints][];
        this.inds = new int[numPoints][];
        this.factors = new float[numPoints][];

        this.boundaryMode = boundaryMode;
    }

    /**
     * 添加面点（Face Point）
     * 在细分过程中为每个面创建的新点
     *
     * @param vertices 面的顶点索引数组
     * @return 新创建点的索引
     * @throws IllegalArgumentException 如果顶点数组无效
     */
    public int addFacePoint(int[] vertices) {
        controlInds[currPoint] = vertices;
        controlFactors[currPoint] = new float[vertices.length];
        Arrays.fill(controlFactors[currPoint], 1.0f / vertices.length);

        inds[currPoint] = new int[0];
        factors[currPoint] = new float[0];

        return currPoint++;
    }

    /**
     * 添加边点（Edge Point）
     * 在细分过程中为每条边创建的新点
     *
     * @param facePoints 相邻面的面点索引数组
     * @param fromPoint  边的起始顶点索引
     * @param toPoint    边的结束顶点索引
     * @param isBoundary 是否为边界边
     * @return 新创建点的索引
     * @throws IllegalArgumentException 如果参数无效
     */
    public int addEdgePoint(int[] facePoints, int fromPoint, int toPoint, boolean isBoundary) {
        if (isBoundary) {
            controlInds[currPoint] = new int[]{fromPoint, toPoint};
            controlFactors[currPoint] = new float[]{0.5f, 0.5f};

            inds[currPoint] = new int[0];
            factors[currPoint] = new float[0];
        } else {
            int n = facePoints.length + 2;
            controlInds[currPoint] = new int[]{fromPoint, toPoint};
            controlFactors[currPoint] = new float[]{1.0f / n, 1.0f / n};

            inds[currPoint] = facePoints;
            factors[currPoint] = new float[facePoints.length];
            Arrays.fill(factors[currPoint], 1.0f / n);
        }
        return currPoint++;
    }

    /**
     * 添加控制点（Control Point）
     * 更新原始控制点的位置
     *
     * @param facePoints      相邻面的面点索引数组
     * @param edgePoints      相邻边的边点索引数组
     * @param fromEdgePoints  从当前点出发的边点索引数组
     * @param toEdgePoints    到达当前点的边点索引数组
     * @param isEdgeBoundary  边是否为边界的布尔数组
     * @param origPoint       原始控制点索引
     * @param isBoundary      当前点是否为边界点
     * @param hasInternalEdge 是否有内部边
     * @return 更新后点的索引
     * @throws IllegalArgumentException 如果参数无效
     */
    public int addControlPoint(int[] facePoints, int[] edgePoints, int[] fromEdgePoints, int[] toEdgePoints, boolean[] isEdgeBoundary, int origPoint, boolean isBoundary, boolean hasInternalEdge) {
        if (isBoundary) {
            if ((boundaryMode == SubdivisionMesh.BoundaryMode.CREASE_EDGES) || hasInternalEdge) {
                controlInds[currPoint] = new int[]{origPoint};
                controlFactors[currPoint] = new float[]{0.5f};

                int numBoundaryEdges = 0;
                for (int i = 0; i < edgePoints.length; i++) {
                    if (isEdgeBoundary[i]) {
                        numBoundaryEdges++;
                    }
                }
                inds[currPoint] = new int[numBoundaryEdges];
                factors[currPoint] = new float[numBoundaryEdges];
                int boundaryEdgeInd = 0;
                for (int i = 0; i < edgePoints.length; i++) {
                    if (isEdgeBoundary[i]) {
                        inds[currPoint][boundaryEdgeInd] = edgePoints[i];
                        factors[currPoint][boundaryEdgeInd] = 0.25f;
                        boundaryEdgeInd++;
                    }
                }
            } else {
                controlInds[currPoint] = new int[]{origPoint};
                controlFactors[currPoint] = new float[]{1.0f};

                inds[currPoint] = new int[0];
                factors[currPoint] = new float[0];
            }
        } else {
            int n = facePoints.length;

            controlInds[currPoint] = new int[1 + edgePoints.length * 2];
            controlFactors[currPoint] = new float[1 + edgePoints.length * 2];
            controlInds[currPoint][0] = origPoint;
            controlFactors[currPoint][0] = (n - 3.0f) / n;
            for (int i = 0; i < edgePoints.length; i++) {
                controlInds[currPoint][1 + 2 * i] = fromEdgePoints[i];
                controlFactors[currPoint][1 + 2 * i] = 1.0f / (n * n);
                controlInds[currPoint][1 + 2 * i + 1] = toEdgePoints[i];
                controlFactors[currPoint][1 + 2 * i + 1] = 1.0f / (n * n);
            }

            inds[currPoint] = facePoints;
            factors[currPoint] = new float[facePoints.length];
            Arrays.fill(factors[currPoint], 1.0f / (n * n));
        }
        return currPoint++;
    }

    @Override
    public void update() {
        int ci;
        float f;
        float x, y, z;
        for (int i = 0; i < numPoints; i++) {
            x = y = z = 0.0f;
            for (int j = 0; j < controlInds[i].length; j++) {
                ci = 3 * controlInds[i][j];
                f = controlFactors[i][j];
                x += controlPoints[ci] * f;
                y += controlPoints[ci + 1] * f;
                z += controlPoints[ci + 2] * f;
            }
            for (int j = 0; j < inds[i].length; j++) {
                ci = 3 * inds[i][j];
                f = factors[i][j];
                x += data[ci] * f;
                y += data[ci + 1] * f;
                z += data[ci + 2] * f;
            }
            data[3 * i] = x;
            data[3 * i + 1] = y;
            data[3 * i + 2] = z;
        }
    }
}