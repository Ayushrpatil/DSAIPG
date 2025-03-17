/*
 * Copyright (c) 2024. Robin Hillyard
 */

package com.phasmidsoftware.dsaipg.sort;

import com.phasmidsoftware.dsaipg.util.Utilities;

import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * Interface Sort which defines the various sort methods for sorting elements of type X.
 * NOTE this definition does not assume that X extends Comparable of X.
 *
 * @param <X> the type of the elements to be sorted.
 */
public interface Sort<X> extends AutoCloseable {

    String getDescription();

    /**
     * Generic, non-mutating sort method which allows for explicit determination of the makeCopy option.
     *
     * @param xs       sort the array xs, returning the sorted result, leaving xs unchanged.
     * @param makeCopy if set to true, we make a copy first and sort that.
     */
    default X[] sort(X[] xs, boolean makeCopy) {
        init(xs.length);
        X[] result = makeCopy ? Arrays.copyOf(xs, xs.length) : xs;
        sort(result, 0, result.length);
        return result;
    }

    /**
     * Generic, non-mutating sort method.
     *
     * @param xs sort the array xs, returning the sorted result, leaving xs unchanged.
     */
    default X[] sort(X[] xs) {
        return sort(xs, true);
    }

    /**
     * Generic, mutating sort method.
     * Note that there is no return value.
     *
     * @param xs the array to be sorted.
     */
    default void mutatingSort(X[] xs) {
        sort(xs, false);
    }

    /**
     * Generic, mutating sort method which operates on a sub-array.
     *
     * @param xs   sort the array xs from "from" until "to" (exclusive of to).
     * @param from the index of the first element to sort.
     * @param to   the index of the first element not to sort.
     */
    void sort(X[] xs, int from, int to);

    /**
     * Method to take a Collection of X and return an Iterable of X in order.
     *
     * @param xs the collection of X elements.
     * @return a sorted iterable of X.
     */
    default Iterable<X> sort(Collection<X> xs) {
        if (xs.isEmpty()) return xs;
        final X[] array = Utilities.asArray(xs);
        mutatingSort(array);
        return Arrays.asList(array);
    }

    /**
     * Perform initializing step for this Sort.
     * <p>
     * CONSIDER merging this with preProcess logic.
     *
     * @param n the number of elements to be sorted.
     */
    void init(int n);

    /**
     * We redefine this method so that it does not throw an Exception.
     */
    void close();

    /**
     * Implementation of parallel sorting using a cutoff and recursion depth strategy.
     */
    class ParSort<X extends Comparable<X>> implements Sort<X> {
        private final int cutoff;
        private final int maxDepth;
        private final ForkJoinPool pool;

        public ParSort(int cutoff, int maxDepth, ForkJoinPool pool) {
            this.cutoff = cutoff;
            this.maxDepth = maxDepth;
            this.pool = pool;
        }

        @Override
        public String getDescription() {
            return "Parallel Sort with cutoff=" + cutoff + ", maxDepth=" + maxDepth;
        }

        @Override
        public void sort(X[] xs, int from, int to) {
            parallelSort(xs, from, to, cutoff, maxDepth, pool);
        }

        @Override
        public void init(int n) {
            // No initialization needed for this implementation
        }

        @Override
        public void close() {
            // No resources to close for this implementation
        }

        /**
         * Sorts the array in parallel using a cutoff and recursion depth strategy.
         *
         * @param array       the array to sort.
         * @param from        the starting index (inclusive).
         * @param to          the ending index (exclusive).
         * @param cutoff      the cutoff size below which the system sort is used.
         * @param maxDepth    the maximum recursion depth for parallel sorting.
         * @param pool        the ForkJoinPool to use for parallel execution.
         */
        private void parallelSort(X[] array, int from, int to, int cutoff, int maxDepth, ForkJoinPool pool) {
            if (to - from <= cutoff || maxDepth <= 0) {
                // Base case: use system sort if the array is small or recursion depth is exhausted
                Arrays.sort(array, from, to);
            } else {
                // Recursive case: split the array and sort each half in parallel
                int mid = (from + to) / 2;
                CompletableFuture<Void> leftFuture = CompletableFuture.runAsync(() ->
                        parallelSort(array, from, mid, cutoff, maxDepth - 1, pool), pool);
                CompletableFuture<Void> rightFuture = CompletableFuture.runAsync(() ->
                        parallelSort(array, mid, to, cutoff, maxDepth - 1, pool), pool);

                // Wait for both halves to complete
                CompletableFuture.allOf(leftFuture, rightFuture).join();

                // Merge the two sorted halves
                merge(array, from, mid, to);
            }
        }

        /**
         * Merges two sorted subarrays into a single sorted array.
         *
         * @param array the array containing the subarrays to merge.
         * @param from  the starting index of the first subarray.
         * @param mid   the ending index of the first subarray and the starting index of the second subarray.
         * @param to    the ending index of the second subarray.
         */
        private void merge(X[] array, int from, int mid, int to) {
            X[] temp = Arrays.copyOfRange(array, from, to);
            int i = 0, j = mid - from, k = from;

            while (i < mid - from && j < to - from) {
                if (temp[i].compareTo(temp[j]) <= 0) {
                    array[k++] = temp[i++];
                } else {
                    array[k++] = temp[j++];
                }
            }

            // Copy remaining elements from the left subarray
            while (i < mid - from) {
                array[k++] = temp[i++];
            }

            // Copy remaining elements from the right subarray
            while (j < to - from) {
                array[k++] = temp[j++];
            }
        }
    }
}

/**
 * Main class to test the parallel sorting implementation and generate CSV-friendly output.
 */
class Main {
    public static void main(String[] args) {
        // Print CSV header
        System.out.println("Array Size,Cutoff,Max Depth,Threads,Time (ms),Sorted Correctly");

        // Experiment with different configurations
        int[] arraySizes = {1_000_000, 10_000_000, 100_000_000}; // Array sizes to test
        int[] cutoffs = {500, 1000, 5000}; // Cutoff values to test
        int[] maxDepths = {2, 4, 8}; // Max depths to test
        int[] threadCounts = {2, 4, 8}; // Thread counts to test

        int rowCount = 0; // Counter to limit the number of rows to 100

        for (int arraySize : arraySizes) {
            for (int cutoff : cutoffs) {
                for (int maxDepth : maxDepths) {
                    for (int numThreads : threadCounts) {
                        // Stop after 100 rows
                        if (rowCount >= 50) {
                            return;
                        }

                        // Run the experiment
                        ForkJoinPool pool = new ForkJoinPool(numThreads);
                        Integer[] array = generateRandomArray(arraySize);

                        Sort<Integer> sorter = new Sort.ParSort<>(cutoff, maxDepth, pool);
                        long startTime = System.nanoTime();
                        sorter.mutatingSort(array);
                        long endTime = System.nanoTime();

                        long timeTaken = (endTime - startTime) / 1_000_000; // Time in milliseconds
                        boolean sortedCorrectly = isSorted(array);

                        // Print CSV row
                        System.out.println(arraySize + "," + cutoff + "," + maxDepth + "," + numThreads + "," + timeTaken + "," + sortedCorrectly);

                        rowCount++; // Increment the row counter
                    }
                }
            }
        }
    }

    private static Integer[] generateRandomArray(int size) {
        Random random = new Random();
        Integer[] array = new Integer[size];
        for (int i = 0; i < size; i++) {
            array[i] = random.nextInt(100000);
        }
        return array;
    }

    private static boolean isSorted(Integer[] array) {
        for (int i = 1; i < array.length; i++) {
            if (array[i - 1] > array[i]) {
                return false;
            }
        }
        return true;
    }
}