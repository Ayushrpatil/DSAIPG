/*
 * Copyright (c) 2018-2024. Robin Hillyard
 */

package com.phasmidsoftware.dsaipg.util;

import com.phasmidsoftware.dsaipg.adt.pq.PriorityQueue;
import com.phasmidsoftware.dsaipg.adt.pq.FourAryHeap;
import com.phasmidsoftware.dsaipg.adt.pq.FibonacciHeap;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import com.phasmidsoftware.dsaipg.adt.pq.PQException;


import static com.phasmidsoftware.dsaipg.util.Utilities.formatWhole;

/**
 * This class implements a simple Benchmark utility for measuring the running time of algorithms.
 * It is part of the repository for the INFO6205 class, taught by Prof. Robin Hillyard.
 *
 * <p>
 * In general, the benchmark class handles three phases of a "run:"
 * <ol>
 *     <li>The pre-function which prepares the input to the study function (field fPre) (may be null);</li>
 *     <li>The study function itself (field fRun) -- assumed to be a mutating function since it does not return a result;</li>
 *     <li>The post-function which cleans up and/or checks the results of the study function (field fPost) (may be null).</li>
 * </ol>
 * </p>
 *
 * @param <T> The generic type T is that of the input to the function f which you will pass into the constructor.
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
     */
    public Benchmark_Timer(String description, UnaryOperator<T> fPre, Consumer<T> fRun, Consumer<T> fPost) {
        this.description = description;
        this.fPre = fPre;
        this.fRun = fRun;
        this.fPost = fPost;
    }

    /**
     * Constructor for a Benchmark_Timer with the option of specifying a pre-function and run function.
     */
    public Benchmark_Timer(String description, UnaryOperator<T> fPre, Consumer<T> fRun) {
        this(description, fPre, fRun, null);
    }

    /**
     * Constructor for a Benchmark_Timer with only fRun and fPost Consumer parameters.
     */
    public Benchmark_Timer(String description, Consumer<T> fRun, Consumer<T> fPost) {
        this(description, null, fRun, fPost);
    }

    /**
     * Constructor for a Benchmark_Timer where only the (timed) run function is specified.
     */
    public Benchmark_Timer(String description, Consumer<T> f) {
        this(description, null, f, null);
    }

    private final String description;
    private final UnaryOperator<T> fPre;
    private final Consumer<T> fRun;
    private final Consumer<T> fPost;

    private static final Random random = new Random();
    private static final int M = 4095; // Max heap size
    private static final int INSERTIONS = 16000;
    private static final int REMOVALS = 4000;

    public static void main(String[] args) {
        System.out.println("\n--- Heap Benchmarking ---");

        // Define heap implementations
        List<Supplier<Object>> heapSuppliers = Arrays.asList(
                () -> new PriorityQueue<Integer>(M, true, Comparator.naturalOrder(), false),  // ✅ Basic Binary Heap
                () -> new PriorityQueue<Integer>(M, true, Comparator.naturalOrder(), true),   // ✅ Binary Heap w/ Floyd’s Trick
                () -> new FourAryHeap<Integer>(M, Comparator.naturalOrder(), false, false),   // ✅ 4-ary Heap
                () -> new FourAryHeap<Integer>(M, Comparator.naturalOrder(), true, true),     // ✅ 4-ary Heap w/ Floyd’s Trick
                FibonacciHeap::new  // ✅ Fibonacci Heap
        );

        List<String> heapNames = Arrays.asList(
                "BinaryHeap", "BinaryHeapFloyd", "4AryHeap", "4AryHeapFloyd", "FibonacciHeap"
        );

        List<Double> insertionTimes = new ArrayList<>();
        List<Double> removalTimes = new ArrayList<>();

        for (int i = 0; i < heapSuppliers.size(); i++) {
            String heapName = heapNames.get(i);
            Supplier<Object> heapSupplier = heapSuppliers.get(i);

            System.out.println("\nHeap: " + heapName);

            Benchmark_Timer<Object> insertionBenchmark = new Benchmark_Timer<>(
                    heapName + " Insertions",
                    heap -> benchmarkHeap(heapName, heapSupplier, INSERTIONS, 0)
            );
            double insertionTime = insertionBenchmark.runFromSupplier(heapSupplier, 10);
            insertionTimes.add(insertionTime);

            Benchmark_Timer<Object> removalBenchmark = new Benchmark_Timer<>(
                    heapName + " Removals",
                    heap -> benchmarkHeap(heapName, heapSupplier, 0, REMOVALS)
            );
            double removalTime = removalBenchmark.runFromSupplier(heapSupplier, 10);
            removalTimes.add(removalTime);

            System.out.printf("Insertion Time: %.6f ms%n", insertionTime);
            System.out.printf("Removal Time: %.6f ms%n", removalTime);
        }

        exportToCSV(heapNames, insertionTimes, removalTimes);
    }

    static void benchmarkHeap(String description, Supplier<Object> heapSupplier, int inserts, int removes) {
        Object heapInstance = heapSupplier.get();

        if (heapInstance instanceof PriorityQueue) {
            PriorityQueue<Integer> pq = (PriorityQueue<Integer>) heapInstance;
            List<Integer> spilledElements = new ArrayList<>(); // ✅ Track spilled elements

            for (int i = 0; i < INSERTIONS; i++) {
                if (pq.size() >= 4095) {  // ✅ When limit is reached, remove & track element
                    try {
                        int spilledElement = pq.take();
                        spilledElements.add(spilledElement);
                        System.out.println("BinaryHeap - Spilled element added: " + spilledElement);
                    } catch (PQException e) {
                        System.err.println("Error in PriorityQueue during removal: " + e.getMessage());
                    }
                }
                pq.give(random.nextInt());
            }

            for (int i = 0; i < REMOVALS; i++) {
                if (!pq.isEmpty()) {
                    try {
                        pq.take();
                    } catch (PQException e) {
                        System.err.println("Error in PriorityQueue during removal: " + e.getMessage());
                    }
                }
            }

            if (!spilledElements.isEmpty()) {
                Integer highestSpilled = Collections.max(spilledElements);
                System.out.println("BinaryHeap - Highest priority spilled element: " + highestSpilled);
            }
        } else if (heapInstance instanceof FourAryHeap) {
            FourAryHeap<Integer> faHeap = (FourAryHeap<Integer>) heapInstance;
            for (int i = 0; i < INSERTIONS; i++) faHeap.insert(random.nextInt());
            for (int i = 0; i < REMOVALS; i++) {
                if (faHeap.size() > 0) faHeap.removeTop();
            }
            Integer highestSpilled = faHeap.getHighestPrioritySpilledElement();
            if (highestSpilled != null) {
                System.out.println("Highest priority spilled element: " + highestSpilled);
            }
        } else if (heapInstance instanceof FibonacciHeap) {
            FibonacciHeap<Integer> fibHeap = (FibonacciHeap<Integer>) heapInstance;
            for (int i = 0; i < INSERTIONS; i++) fibHeap.insert(random.nextInt());

            try {
                for (int i = 0; i < REMOVALS; i++) {
                    if (fibHeap.size() > 0) fibHeap.removeMin();
                }
            } catch (Exception e) {
                System.err.println("Error in FibonacciHeap: " + e.getMessage());
            }
        } else {
            System.err.println("Error: Unknown heap type for " + description);
        }
    }




    private static void exportToCSV(List<String> heapNames, List<Double> insertionTimes, List<Double> removalTimes) {
        try (PrintWriter writer = new PrintWriter(new File("benchmark_results.csv"))) {
            writer.println("HeapName,InsertionTime,RemovalTime");
            for (int i = 0; i < heapNames.size(); i++) {
                writer.println(heapNames.get(i) + "," + insertionTimes.get(i) + "," + removalTimes.get(i));
            }
        } catch (FileNotFoundException e) {
            System.err.println("Error writing to CSV: " + e.getMessage());
        }
    }
}