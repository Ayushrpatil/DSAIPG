package com.phasmidsoftware.dsaipg.adt.pq;

import java.util.Comparator;

public class FourAryHeap<K> extends PriorityQueue<K> {

    public FourAryHeap(int capacity, Comparator<K> comparator) {
        super(capacity, comparator);
    }

    @Override
    protected int firstChild(int k) {
        return (k + 1 - first) * 4 + first - 3;
    }
}
