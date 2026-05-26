package com.bingbaihanji.loading;

/**
 * A 3D geometric point array that has the x, y, z coordinates of every point
 * as a function of other variables.
 */
public abstract class SymbolicPointArray {
    // 每个点的组件数量（x, y, z）
    static final int NUM_COMPONENTS_PER_POINT = 3;
    final public float[] data;
    final public int numPoints;

    protected SymbolicPointArray(float[] data) {
        this.data = data;
        this.numPoints = data.length / NUM_COMPONENTS_PER_POINT;
    }

    /**
     * Updates the variables x, y, z based on the state of the other variables
     * that this symbolic point depends on.
     */
    public abstract void update();
}