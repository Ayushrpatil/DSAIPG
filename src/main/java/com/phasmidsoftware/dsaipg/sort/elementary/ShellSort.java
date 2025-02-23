/*
 * Copyright (c) 2024. Robin Hillyard
 */
package com.phasmidsoftware.dsaipg.sort.elementary;

import com.phasmidsoftware.dsaipg.sort.*;
import com.phasmidsoftware.dsaipg.util.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Implementation of ShellSort, a generalization of insertion sort that allows
 * the exchange of items that are far apart, defined by "gap" sequences.
 *
 * This implementation supports various gap sequences, selectable by specified modes.
 *
 * @param <X> the type parameter which extends Comparable, allowing comparison of elements.
 */
public class ShellSort<X extends Comparable<X>> extends SortWithComparableHelper<X> {

    /**
     * Primary constructor for ShellSort with configuration and size.
     *
     * @param m      the mode (gap sequence to follow):
     *               1: Ordinary insertion sort
     *               2: Powers of two minus one
     *               3: Sequence based on 3 (1, 4, 13, etc.)
     *               4: Sedgewick's sequence
     *               5: Pratt Sequence 2^i * 3^j with i, j >= 0
     * @param N      the number of elements expected to be sorted.
     * @param nRuns  the number of runs.
     * @param config the configuration.
     */
    public ShellSort(int m, int N, int nRuns, Config config) {
        super(DESCRIPTION + m, N, nRuns, config);
        this.m = m;
        trackInversions = false;
    }

    /**
     * Secondary constructor for ShellSort using the Sedgewick sequence.
     */
    public ShellSort() throws IOException {
        this(4);
    }

    /**
     * Secondary constructor for ShellSort using the Pratt sequence and the standard configuration-based helper.
     *
     * @param m the mode (gap sequence).
     */
    public ShellSort(int m) throws IOException {
        this(m, new InstrumentedComparableHelper<>(DESCRIPTION + m, Config.load(ShellSort.class)));
    }

    /**
     * Primary constructor for ShellSort with explicit mode and helper.
     *
     * @param m      the mode (gap sequence).
     * @param helper an explicit instance of Helper to be used.
     */
    public ShellSort(int m, Helper<X> helper) {
        super(helper);
        this.m = m;
        trackInversions = false;
    }

    /**
     * Sort a sub-array of an array of Xs.
     *
     * @param xs   the array of Xs to be sorted in place.
     * @param from the start index.
     * @param to   the end index (exclusive).
     */
    public void sort(X[] xs, int from, int to) {
        H h = new H(to - from, m);
        int gap = h.first();
        while (gap > 0) {
            hSort(gap, xs, from, to);
            if (shellFunction != null)
                shellFunction.accept(getHelper());
            gap = h.next();
        }
    }

    /**
     * Set the function executed after each h-sort iteration.
     *
     * @param shellFunction a consumer of Helper of X.
     */
    public void setShellFunction(Consumer<AutoCloseable> shellFunction) {
        this.shellFunction = shellFunction;
    }

    public static final String DESCRIPTION = "Shell sort in mode ";

    /**
     * Private method to h-sort an array.
     *
     * @param h    the gap value.
     * @param xs   the array to be sorted.
     * @param from the starting index.
     * @param to   the ending index.
     */
    private void hSort(int h, X[] xs, int from, int to) {
        final Helper<X> helper = getHelper();
        for (int i = h + from; i < to; i++) {
            int j = i;
            while (j >= h + from && helper.swapConditional(xs, j - h, j))
                j -= h;
        }
    }

    private final int m;
    private final boolean trackInversions;
    private Consumer<AutoCloseable> shellFunction = null;

    /**
     * Inner class to generate gap sequences for ShellSort.
     */
    static class H {
        private final int m;
        private int h = 1;
        private int i;
        private boolean started = false;
        final List<Integer> data = new ArrayList<>();

        H(int N, int m) {
            this.m = m;
            switch (m) {
                case 1:
                    break;
                case 2:
                    while (h <= N) h = 2 * (h + 1) - 1;
                    break;
                case 3:
                    while (h <= N / 3) h = h * 3 + 1;
                    break;
                case 4:
                    i = 0;
                    while (sedgewick(i) < N) i++;
                    i--;
                    h = (int) sedgewick(i);
                    break;
                case 5:
                    int j = 1;
                    while (j <= N) {
                        i = j;
                        while (i <= N) {
                            data.add(i);
                            i *= 2;
                        }
                        j *= 3;
                    }
                    Collections.sort(data);
                    this.i = data.size() - 1;
                    h = data.get(this.i);
                    break;
                default:
                    throw new RuntimeException("Invalid mode value: " + m);
            }
        }

        int first() {
            if (started) throw new RuntimeException("Cannot call first more than once");
            started = true;
            return h;
        }

        int next() {
            if (!started) {
                started = true;
                return h;
            }
            switch (m) {
                case 1 -> { return 0; }
                case 2 -> { h = (h + 1) / 2 - 1; return h; }
                case 3 -> { h /= 3; return h; }
                case 4 -> { i--; return (int) sedgewick(i); }
                case 5 -> { i--; return (i < 0) ? 0 : data.get(i); }
                default -> throw new RuntimeException("Invalid mode value: " + m);
            }
        }

        long sedgewick(int k) {
            if (k < 0) return 0;
            return (k % 2 == 0) ? 9L * (powerOf2(k) - powerOf2(k / 2)) + 1
                    : 8L * powerOf2(k) - 6 * powerOf2((k + 1) / 2) + 1;
        }

        private long powerOf2(int k) {
            return 1L << k;
        }
    }

    public static void main(String[] args) {
        int N = 64000;

        while (N <= 100000) {
            final int arraySize = N;
            InstrumentedComparableHelper<Integer> instrumentedHelper =
                    new InstrumentedComparableHelper<>("ShellSort", arraySize, 20, Config_Benchmark.setupConfig("true", "false", "0", "0", "", ""));
            ShellSort<Integer> s = new ShellSort<>(5, instrumentedHelper);
            s.init(arraySize);
            final Integer[] xsCopy = instrumentedHelper.random(Integer.class, r -> r.nextInt(arraySize));

            Benchmark<Void> benchmark = (Benchmark<Void>) new Benchmark_Timer<Void>(
                    "Sorting",
                    b -> {
                        s.sort(xsCopy, 0, arraySize); // ✅ Sorting without returning a value
                    }
            );


            double nTime = benchmark.run(null, 20);
            System.out.println("Array size: " + arraySize + ", Time: " + nTime);
            N *= 2;
        }
    }
}
