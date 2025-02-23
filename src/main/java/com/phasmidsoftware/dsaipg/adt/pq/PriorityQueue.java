/*
 * Copyright (c) 2024. Robin Hillyard
 */

package com.phasmidsoftware.dsaipg.adt.pq;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * Priority Queue Data Structure which uses a binary or k-ary heap.
 * <p/>
 * It supports different configurations:
 * - Min/Max heap.
 * - Binary (default) or 4-ary heap.
 * - Floyd’s trick (optimized heapifying).
 * <p/>
 * Implements typical heap operations: insert (`give`) and remove (`take`).
 * <p/>
 * Requires a Comparator<K> to be passed in.
 */
public class PriorityQueue<K> implements Iterable<K> {

    /**
     * Primary constructor that takes the max value, an actual array of elements, and a comparator.
     *
     * @param max        Whether this is a Max Priority Queue (true) or Min PQ (false).
     * @param binHeap    A pre-formed array with length one greater than the required capacity.
     * @param first      The index of the root element.
     * @param last       The number of elements in binHeap.
     * @param comparator A comparator for the type K.
     * @param floyd      True if we use Floyd's trick.
     * @param kAry       The branching factor (2 for binary, 4 for 4-ary).
     */
    public PriorityQueue(boolean max, Object[] binHeap, int first, int last, Comparator<K> comparator, boolean floyd, int kAry) {
        this.max = max;
        this.first = first;
        this.comparator = comparator;
        this.last = last;
        this.kAry = kAry;
        //noinspection unchecked
        this.binHeap = (K[]) binHeap;
        this.floyd = floyd;
    }

    /**
     * Secondary constructor which takes only the priority queue's maximum capacity and a comparator.
     */
    public PriorityQueue(int n, int first, boolean max, Comparator<K> comparator, boolean floyd, int kAry) {
        this(max, new Object[n + first], first, 0, comparator, floyd, kAry);
    }

    /**
     * Default constructor for Binary Heap.
     */
    public PriorityQueue(int n, boolean max, Comparator<K> comparator, boolean floyd) {
        this(n, 1, max, comparator, floyd, 2);
    }

    /**
     * Constructor for a 4-ary heap.
     */
    public PriorityQueue(int n, boolean max, Comparator<K> comparator, boolean floyd, boolean fourAry) {
        this(n, 1, max, comparator, floyd, fourAry ? 4 : 2);
    }

    /**
     * @return true if the current size is zero.
     */
    public boolean isEmpty() {
        return last == 0;
    }

    /**
     * @return the number of elements actually stored in this Priority Queue.
     */
    public int size() {
        return last;
    }

    /**
     * Insert an element into this Priority Queue.
     */
    public void give(K key) {
        if (last == binHeap.length - first) last--;
        binHeap[++last + first - 1] = key;
        swimUp(last + first - 1);
    }

    /**
     * Remove the root element from this Priority Queue and adjust the binary heap accordingly.
     */
    public K take() throws PQException {
        if (isEmpty()) throw new PQException("Priority queue is empty");
        return floyd ? doTake(this::snake) : doTake(this::sink);
    }

    /**
     * Internal method for removing root.
     */
    K doTake(Consumer<Integer> f) {
        K result = binHeap[first];
        swap(first, last-- + first - 1);
        f.accept(first);
        binHeap[last + first] = null;
        return result;
    }

    /**
     * Sink the element at index k down.
     */
    void sink(int k) {
        doHeapify(k, (a, b) -> !unordered(a, b));
    }

    /**
     * Special snake method that sinks and then swims the element back.
     */
    void snake(int k) {
        swimUp(doHeapify(k, (a, b) -> !unordered(a, b)));
    }

    /**
     * Swim the element at index k up.
     */
    void swimUp(int k) {
        int i = k;
        while (i > first && unordered(parent(i), i)) {
            swap(i, parent(i));
            i = parent(i);
        }
    }

    /**
     * Check if elements are out of order.
     */
    boolean unordered(int i, int j) {
        return (comparator.compare(binHeap[i], binHeap[j]) > 0) ^ max;
    }

    /**
     * Non-mutating iterator over all values of this PriorityQueue.
     */
    public Iterator<K> iterator() {
        Collection<K> copy = new ArrayList<>(Arrays.asList(Arrays.copyOf(binHeap, last + first)));
        Iterator<K> result = copy.iterator();
        if (first > 0) result.next();
        return result;
    }

    /**
     * Heapify operation for maintaining heap properties.
     */
    private int doHeapify(int k, BiPredicate<Integer, Integer> p) {
        int i = k;
        while (firstChild(i) <= last + first - 1) {
            int j = firstChild(i);
            int end = Math.min(j + kAry - 1, last + first - 1);

            // Find the best child in a k-ary heap
            int best = j;
            for (int c = j + 1; c <= end; c++) {
                if (unordered(best, c)) best = c;
            }

            if (p.test(i, best)) break;
            swap(i, best);
            i = best;
        }
        return i;
    }

    /**
     * Exchange the values at indices i and j.
     */
    protected void swap(int i, int j) {
        K tmp = binHeap[i];
        binHeap[i] = binHeap[j];
        binHeap[j] = tmp;
    }

    /**
     * Get the index of the parent of the element at index k.
     */
    protected int parent(int k) {
        return (k + 1 - first) / kAry + first - 1;
    }

    /**
     * Get the index of the first child of the element at index k.
     */
    protected int firstChild(int k) {
        return (k + 1 - first) * kAry + first - 1;
    }

    /**
     * The following methods are for unit testing ONLY!!
     */
    @SuppressWarnings("unused")
    private K peek(int k) {
        return binHeap[k];
    }

    @SuppressWarnings("unused")
    private boolean getMax() {
        return max;
    }

    protected final boolean max;
    protected final int first;
    protected final Comparator<K> comparator;
    protected final K[] binHeap;
    protected int last;
    protected final boolean floyd;
    protected final int kAry; // 2 for binary heap, 4 for 4-ary heap.

    public static void main(String[] args) {
        doMain();
    }

    static void doMain() {
        System.out.println("PriorityQueue Test:");
    }
}
