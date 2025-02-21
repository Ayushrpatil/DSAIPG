/*
 * Copyright (c) 2018-2024. Robin Hillyard
 */

package com.phasmidsoftware.dsaipg.util;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import static com.phasmidsoftware.dsaipg.util.Utilities.formatWhole;

/**
 * This class implements a simple Benchmark utility for measuring the running time of algorithms.
 * It is part of the repository for the INFO6205 class, taught by Prof. Robin Hillyard
 * <p>
 * It requires Java 8 as it uses function types, in particular, UnaryOperator&lt;T&gt; (a function of T => T),
 * Consumer&lt;T&gt; (essentially a function of T => Void) and Supplier&lt;T&gt; (essentially a function of Void => T).
 * <p>
 * In general, the benchmark class handles three phases of a "run:"
 * <ol>
 *     <li>The pre-function which prepares the input to the study function (field fPre) (may be null);</li>
 *     <li>The study function itself (field fRun) -- assumed to be a mutating function since it does not return a result;</li>
 *     <li>The post-function which cleans up and/or checks the results of the study function (field fPost) (may be null).</li>
 * </ol>
 * <p>
 * Note that the clock does not run during invocations of the pre-function and the post-function (if any).
 *
 * @param <T> The generic type T is that of the input to the function f which you will pass in to the constructor.
 */
public class Benchmark_Timer<T> implements Benchmark<T> {

    /**
     * Calculate the appropriate number of warmup runs.
     *
     * @param m the number of runs.
     * @return at least one and at most the lower of four or m/15.
     */
    static int getWarmupRuns(int m) {
        return Integer.max(1, Integer.min(3, m / 15));
    }

    /**
     * Run function f m times and return the average time in milliseconds.
     *
     * @param supplier a Supplier of a T
     * @param m        the number of times the function f will be called.
     * @return the average number of milliseconds taken for each run of function f.
     */
    public double runFromSupplier(Supplier<T> supplier, int m) {
        logger.info("Begin run: " + description + " with " + formatWhole(m) + " runs");
        final Function<T, T> function = t -> {
            fRun.accept(t);
            return t;
        };
        // Warmup phase
        new Timer().repeat(getWarmupRuns(m), true, supplier, function, fPre, null);

        // Timed phase
        return new Timer().repeat(m, false, supplier, function, fPre, fPost);
    }

    /**
     * Constructor for a Benchmark_Timer with the option of specifying all three functions.
     *
     * @param description the description of the benchmark.
     * @param fPre        a function of T => T.
     *                    Function fPre is run before each invocation of fRun (but with the clock stopped).
     *                    The result of fPre (if any) is passed to fRun.
     * @param fRun        a Consumer function (i.e. a function of T => Void).
     *                    Function fRun is the function whose timing you want to measure. For example, you might create a function which sorts an array.
     *                    When you create a lambda defining fRun, you must return "null."
     * @param fPost       a Consumer function (i.e. a function of T => Void).
     */
    public Benchmark_Timer(String description, UnaryOperator<T> fPre, Consumer<T> fRun, Consumer<T> fPost) {
        this.description = description;
        this.fPre = fPre;
        this.fRun = fRun;
        this.fPost = fPost;
    }

    /**
     * Constructor for a Benchmark_Timer with the option of specifying all three functions.
     *
     * @param description the description of the benchmark.
     * @param fPre        a function of T => T.
     *                    Function fPre is run before each invocation of fRun (but with the clock stopped).
     *                    The result of fPre (if any) is passed to fRun.
     * @param fRun        a Consumer function (i.e. a function of T => Void).
     *                    Function fRun is the function whose timing you want to measure. For example, you might create a function which sorts an array.
     */
    public Benchmark_Timer(String description, UnaryOperator<T> fPre, Consumer<T> fRun) {
        this(description, fPre, fRun, null);
    }

    /**
     * Constructor for a Benchmark_Timer with only fRun and fPost Consumer parameters.
     *
     * @param description the description of the benchmark.
     * @param fRun        a Consumer function (i.e. a function of T => Void).
     *                    Function fRun is the function whose timing you want to measure. For example, you might create a function which sorts an array.
     *                    When you create a lambda defining fRun, you must return "null."
     * @param fPost       a Consumer function (i.e. a function of T => Void).
     */
    public Benchmark_Timer(String description, Consumer<T> fRun, Consumer<T> fPost) {
        this(description, null, fRun, fPost);
    }

    /**
     * Constructor for a Benchmark_Timer where only the (timed) run function is specified.
     *
     * @param description the description of the benchmark.
     * @param f           a Consumer function (i.e. a function of T => Void).
     *                    Function f is the function whose timing you want to measure. For example, you might create a function which sorts an array.
     */
    public Benchmark_Timer(String description, Consumer<T> f) {
        this(description, null, f, null);
    }

    private final String description;
    private final UnaryOperator<T> fPre;
    private final Consumer<T> fRun;
    private final Consumer<T> fPost;

    final static LazyLogger logger = new LazyLogger(Benchmark_Timer.class);

    private static final Random random = new Random();
    private static final int M = 4095; // Max heap size
    private static final int INSERTIONS = 16000;
    private static final int REMOVALS = 4000;

    public static void main(String[] args) {
        System.out.println("\n--- Heap Benchmarking ---");

        // Define heap implementations
        List<Supplier<PriorityQueue<Integer>>> heapSuppliers = Arrays.asList(
                () -> new PriorityQueue<>(M, Comparator.naturalOrder()),  // Binary Heap
                () -> new PriorityQueue<>(M, Comparator.naturalOrder())  // Binary Heap with Floyd's Trick
        );

        List<String> heapNames = Arrays.asList("BinaryHeap", "BinaryHeapFloyd");

        // Lists to store benchmark results
        List<Double> insertionTimes = new ArrayList<>();
        List<Double> removalTimes = new ArrayList<>();

        for (int i = 0; i < heapSuppliers.size(); i++) {
            String heapName = heapNames.get(i);
            Supplier<PriorityQueue<Integer>> heapSupplier = heapSuppliers.get(i);

            // Benchmark Insertions
            Benchmark_Timer<PriorityQueue<Integer>> insertionBenchmark = new Benchmark_Timer<>(
                    heapName + " Insertions",
                    heap -> {
                        PriorityQueue<Integer> heapInstance = heapSupplier.get();
                        for (int j = 0; j < INSERTIONS; j++) {
                            if (heapInstance.size() >= M) {
                                heapInstance.poll(); // Remove element if heap is full
                            }
                            heapInstance.add(random.nextInt());
                        }
                    }
            );
            double insertionTime = insertionBenchmark.runFromSupplier(heapSupplier, 10);
            insertionTimes.add(insertionTime);

            // Benchmark Removals and Track Spilled Elements
            Benchmark_Timer<PriorityQueue<Integer>> removalBenchmark = new Benchmark_Timer<>(
                    heapName + " Removals",
                    heap -> {
                        PriorityQueue<Integer> heapInstance = heapSupplier.get();
                        List<Integer> spilled = new ArrayList<>();

                        for (int j = 0; j < INSERTIONS; j++) {
                            int value = random.nextInt();
                            if (heapInstance.size() >= M) {
                                spilled.add(heapInstance.poll());
                            }
                            heapInstance.add(value);
                        }

                        for (int j = 0; j < REMOVALS; j++) {
                            heapInstance.poll();
                        }

                        int maxSpilled = spilled.stream().max(Integer::compare).orElse(Integer.MIN_VALUE);
                        System.out.println(heapName + " max spilled: " + maxSpilled);
                    }
            );
            double removalTime = removalBenchmark.runFromSupplier(heapSupplier, 10);
            removalTimes.add(removalTime);

            // Print Benchmark Results
            System.out.println(heapName + " - Insertion Time: " + insertionTime + " ms");
            System.out.println(heapName + " - Removal Time: " + removalTime + " ms");
        }

        // Export results to CSV
        exportToCSV(heapNames, insertionTimes, removalTimes);
    }

    /**
     * Exports the benchmark results to a CSV file.
     *
     * @param heapNames      The names of the heap implementations.
     * @param insertionTimes The insertion times for each heap.
     * @param removalTimes   The removal times for each heap.
     */
    private static void exportToCSV(List<String> heapNames, List<Double> insertionTimes, List<Double> removalTimes) {
        try (PrintWriter writer = new PrintWriter(new File("benchmark_results.csv"))) {
            // Write CSV header
            writer.println("HeapName,InsertionTime,RemovalTime");

            // Write data rows
            for (int i = 0; i < heapNames.size(); i++) {
                writer.println(heapNames.get(i) + "," + insertionTimes.get(i) + "," + removalTimes.get(i));
            }

            System.out.println("Benchmark results exported to benchmark_results.csv");
        } catch (FileNotFoundException e) {
            System.err.println("Error writing to CSV file: " + e.getMessage());
        }
    }
}