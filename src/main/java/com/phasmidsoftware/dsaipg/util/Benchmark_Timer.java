package com.phasmidsoftware.dsaipg.util;

import com.phasmidsoftware.dsaipg.adt.pq.PriorityQueue;
import com.phasmidsoftware.dsaipg.adt.pq.FourAryHeap;
import com.phasmidsoftware.dsaipg.adt.pq.FibonacciHeap;
import com.phasmidsoftware.dsaipg.adt.pq.PQException;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class Benchmark_Timer<T> implements Benchmark<T> {
    private final String description;
    private final UnaryOperator<T> fPre;
    private final Consumer<T> fRun;
    private final Consumer<T> fPost;

    private static final Random random = new Random();
    private static final int M = 4095;
    private static final int INSERTIONS = 16000;
    private static final int REMOVALS = 4000;

    public Benchmark_Timer(String description, Consumer<T> f) {
        this(description, null, f, null);
    }

    public Benchmark_Timer(String description, UnaryOperator<T> fPre, Consumer<T> fRun, Consumer<T> fPost) {
        this.description = description;
        this.fPre = fPre;
        this.fRun = fRun;
        this.fPost = fPost;
    }

    @Override
    public double runFromSupplier(Supplier<T> supplier, int m) {
        fRun.accept(supplier.get());
        new Timer().repeat(Math.max(1, Math.min(3, m / 15)), true, supplier, t -> { fRun.accept(t); return t; }, fPre, null);
        return new Timer().repeat(m, false, supplier, t -> { fRun.accept(t); return t; }, fPre, fPost);
    }

    public static void benchmarkHeap(String heapName, Supplier<Object> heapSupplier, int inserts, int removes, Map<String, Integer> highestSpilledElements) {
        Object heapInstance = heapSupplier.get();
        List<Integer> spilledElements = new ArrayList<>();

        if (heapInstance instanceof PriorityQueue) {
            PriorityQueue<Integer> pq = (PriorityQueue<Integer>) heapInstance;
            for (int i = 0; i < inserts; i++) {
                if (pq.size() >= M) {
                    try {
                        int spilledElement = pq.take();
                        spilledElements.add(spilledElement);
                    } catch (PQException e) {
                        System.err.println("Error in PriorityQueue: " + e.getMessage());
                    }
                }
                pq.give(random.nextInt());
            }
        } else if (heapInstance instanceof FourAryHeap) {
            FourAryHeap<Integer> faHeap = (FourAryHeap<Integer>) heapInstance;
            for (int i = 0; i < inserts; i++) faHeap.insert(random.nextInt());
        } else if (heapInstance instanceof FibonacciHeap) {
            FibonacciHeap<Integer> fibHeap = (FibonacciHeap<Integer>) heapInstance;
            for (int i = 0; i < inserts; i++) fibHeap.insert(random.nextInt());
        }

        if (!spilledElements.isEmpty()) {
            highestSpilledElements.put(heapName, Collections.max(spilledElements));
        }
    }

    public static void main(String[] args) {
        List<Supplier<Object>> heapSuppliers = Arrays.asList(
                () -> new PriorityQueue<Integer>(M, true, Comparator.naturalOrder(), false),
                () -> new PriorityQueue<Integer>(M, true, Comparator.naturalOrder(), true),
                () -> new FourAryHeap<Integer>(M, Comparator.naturalOrder(), false, false),
                () -> new FourAryHeap<Integer>(M, Comparator.naturalOrder(), true, true),
                FibonacciHeap::new
        );

        List<String> heapNames = Arrays.asList(
                "BinaryHeap", "BinaryHeapFloyd", "4AryHeap", "4AryHeapFloyd", "FibonacciHeap"
        );

        List<Double> insertionTimes = new ArrayList<>();
        List<Double> removalTimes = new ArrayList<>();
        Map<String, Integer> highestSpilledElements = new HashMap<>();

        for (int i = 0; i < heapSuppliers.size(); i++) {
            String heapName = heapNames.get(i);
            Supplier<Object> heapSupplier = heapSuppliers.get(i);

            Benchmark_Timer<Object> insertionBenchmark = new Benchmark_Timer<>(
                    heapName + " Insertions",
                    t -> Benchmark_Timer.benchmarkHeap(heapName, heapSupplier, INSERTIONS, 0, highestSpilledElements)
            );
            double insertionTime = insertionBenchmark.runFromSupplier(heapSupplier, 10);
            insertionTimes.add(insertionTime);

            Benchmark_Timer<Object> removalBenchmark = new Benchmark_Timer<>(
                    heapName + " Removals",
                    t -> Benchmark_Timer.benchmarkHeap(heapName, heapSupplier, 0, REMOVALS, highestSpilledElements)
            );
            double removalTime = removalBenchmark.runFromSupplier(heapSupplier, 10);
            removalTimes.add(removalTime);
        }
    }
}