/*
 * Copyright (c) 2024. Robin Hillyard
 */

package com.phasmidsoftware.dsaipg.util;

import com.phasmidsoftware.dsaipg.sort.SortWithHelper;

import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.phasmidsoftware.dsaipg.util.Utilities.formatWhole;

/**
 * Class to extend Benchmark_Timer for sorting an array of T values.
 * The default implementation of run in this class randomly selects a subset of the array to be sorted.
 * Each sort is preceded (optionally) by a preProcessor and succeeded (optionally) by a postProcessor.
 *
 * @param <T> the underlying type to be sorted.
 */
public class SorterBenchmark<T extends Comparable<T>> extends Benchmark_Timer<T[]> {

    /**
     * Run a benchmark on a sorting problem with N elements.
     *
     * @param description the description of the task being timed.
     * @param N           the number of elements.
     */
    public void run(String description, int N) {
        if (nRuns > 0) {
            logger.info("run: sort " + formatWhole(N) + " elements with " + this);
            sorter.init(N);
            final double time = super.runFromSupplier(() -> generateRandomArray(ts), nRuns);
            for (TimeLogger timeLogger : timeLoggers)
                timeLogger.log(description, time, N);
        } else {
            logger.warn("run: skipping " + this);
        }
    }

    @Override
    public String toString() {
        return "SorterBenchmark on " + tClass + " from " + formatWhole(ts.length) + " total elements and " + formatWhole(nRuns) + " runs";
    }

    /**
     * Constructor for a SorterBenchmark where we provide the following parameters:
     *
     * @param tClass       The class of T.
     * @param preProcessor An optional pre-processor applied before sorting.
     * @param sorter       The sorting algorithm.
     * @param postProcessor An optional post-processor applied after sorting.
     * @param ts           The array of Ts to be sorted.
     * @param nRuns        Number of runs for this benchmark.
     * @param timeLoggers  Loggers for recording sorting times.
     */
    public SorterBenchmark(Class<T> tClass, UnaryOperator<T[]> preProcessor, SortWithHelper<T> sorter, Consumer<T[]> postProcessor, T[] ts, int nRuns, TimeLogger[] timeLoggers) {
        super(sorter.toString(), sorter::mutatingSort); // ✅ Fixed constructor call
        this.sorter = sorter;
        this.tClass = tClass;
        this.ts = ts;
        this.nRuns = nRuns;
        this.timeLoggers = timeLoggers;
    }

    /**
     * Constructor for a SorterBenchmark where the post-processor always checks that the sort was successful.
     *
     * @param tClass       The class of T.
     * @param preProcessor An optional pre-processor applied before sorting.
     * @param sorter       The sorting algorithm.
     * @param ts           The array of Ts to be sorted.
     * @param nRuns        Number of runs for this benchmark.
     * @param timeLoggers  Loggers for recording sorting times.
     */
    public SorterBenchmark(Class<T> tClass, UnaryOperator<T[]> preProcessor, SortWithHelper<T> sorter, T[] ts, int nRuns, TimeLogger[] timeLoggers) {
        this(tClass, preProcessor, sorter, sorter::postProcess, ts, nRuns, timeLoggers);
    }

    /**
     * Constructor for a SorterBenchmark with a default post-processor.
     *
     * @param tClass      The class of T.
     * @param sorter      The sorting algorithm.
     * @param ts          The array of Ts to be sorted.
     * @param nRuns       Number of runs for this benchmark.
     * @param timeLoggers Loggers for recording sorting times.
     */
    public SorterBenchmark(Class<T> tClass, SortWithHelper<T> sorter, T[] ts, int nRuns, TimeLogger[] timeLoggers) {
        this(tClass, null, sorter, ts, nRuns, timeLoggers);
    }

    /**
     * Generates a random array of type T based on a given lookup array.
     *
     * @param lookupArray The array of elements used as the source for generating random values.
     * @return A new array of randomly selected elements of type T, chosen from the lookup array.
     */
    private T[] generateRandomArray(T[] lookupArray) {
        return sorter.getHelper().random(tClass, (r) -> lookupArray[r.nextInt(lookupArray.length)]);
    }

    protected final SortWithHelper<T> sorter;
    protected final T[] ts;
    protected final int nRuns;
    protected final TimeLogger[] timeLoggers;
    private final static LazyLogger logger = new LazyLogger(SorterBenchmark.class);
    private final Class<T> tClass;

}
