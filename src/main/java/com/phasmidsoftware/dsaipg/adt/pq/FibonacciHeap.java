package com.phasmidsoftware.dsaipg.adt.pq;

import java.util.*;

/**
 * Implementation of a Fibonacci Heap.
 * Supports insert, extractMin, decreaseKey, and delete operations.
 */
public class FibonacciHeap<T> {
    private Node<T> min;
    private int size;
    private final Comparator<T> comparator;

    public FibonacciHeap(Comparator<T> comparator) {
        this.comparator = comparator;
    }

    /**
     * Inner class for the Fibonacci Heap node.
     */
    private static class Node<T> {
        T key;
        Node<T> parent;
        Node<T> child;
        Node<T> left;
        Node<T> right;
        int degree;
        boolean mark;

        Node(T key) {
            this.key = key;
            this.left = this;
            this.right = this;
        }
    }

    /**
     * Inserts a new element into the heap.
     *
     * @param key Element to insert
     * @return The inserted node
     */
    public Node<T> insert(T key) {
        Node<T> newNode = new Node<>(key);
        min = mergeLists(min, newNode);
        size++;
        return newNode;
    }

    /**
     * Extracts the minimum element from the heap.
     *
     * @return The minimum element
     */
    public T extractMin() {
        if (min == null) return null;

        Node<T> oldMin = min;

        // Merge children of min into root list
        if (oldMin.child != null) {
            Node<T> child = oldMin.child;
            do {
                child.parent = null;
                child = child.right;
            } while (child != oldMin.child);

            mergeLists(min, oldMin.child);
        }

        // Remove min from root list
        if (oldMin.right == oldMin) {
            min = null;
        } else {
            min = oldMin.right;
            removeNode(oldMin);
            consolidate();
        }

        size--;
        return oldMin.key;
    }

    /**
     * Decreases the key of a node.
     *
     * @param node The node to decrease
     * @param newKey The new key value
     */
    public void decreaseKey(Node<T> node, T newKey) {
        if (comparator.compare(newKey, node.key) > 0) {
            throw new IllegalArgumentException("New key is greater than current key");
        }
        node.key = newKey;
        Node<T> parent = node.parent;

        if (parent != null && comparator.compare(node.key, parent.key) < 0) {
            cut(node, parent);
            cascadingCut(parent);
        }

        if (comparator.compare(node.key, min.key) < 0) {
            min = node;
        }
    }

    /**
     * Deletes a node by decreasing its key to minimum and extracting it.
     */
    public void delete(Node<T> node) {
        decreaseKey(node, min.key);
        extractMin();
    }

    /**
     * Merges two Fibonacci heaps.
     */
    public void union(FibonacciHeap<T> other) {
        min = mergeLists(min, other.min);
        size += other.size;
        other.min = null;
        other.size = 0;
    }

    /**
     * Consolidates trees to optimize heap structure.
     */
    private void consolidate() {
        Map<Integer, Node<T>> degreeTable = new HashMap<>();
        List<Node<T>> rootList = new ArrayList<>();

        Node<T> x = min;
        if (x != null) {
            do {
                rootList.add(x);
                x = x.right;
            } while (x != min);
        }

        for (Node<T> node : rootList) {
            int degree = node.degree;

            while (degreeTable.containsKey(degree)) {
                Node<T> other = degreeTable.remove(degree);

                if (comparator.compare(other.key, node.key) < 0) {
                    Node<T> temp = node;
                    node = other;
                    other = temp;
                }

                linkHeaps(other, node);
                degree++;
            }

            degreeTable.put(degree, node);
        }

        min = null;
        for (Node<T> node : degreeTable.values()) {
            min = mergeLists(min, node);
        }
    }

    /**
     * Links two trees of the same degree.
     */
    private void linkHeaps(Node<T> child, Node<T> parent) {
        removeNode(child);
        child.left = child.right = child;
        parent.child = mergeLists(parent.child, child);
        child.parent = parent;
        parent.degree++;
        child.mark = false;
    }

    /**
     * Cuts a node from its parent.
     */
    private void cut(Node<T> node, Node<T> parent) {
        removeNode(node);
        parent.degree--;
        min = mergeLists(min, node);
        node.parent = null;
        node.mark = false;
    }

    /**
     * Cascading cut for maintaining Fibonacci heap properties.
     */
    private void cascadingCut(Node<T> node) {
        Node<T> parent = node.parent;
        if (parent != null) {
            if (!node.mark) {
                node.mark = true;
            } else {
                cut(node, parent);
                cascadingCut(parent);
            }
        }
    }

    /**
     * Removes a node from the circular doubly linked list.
     */
    private void removeNode(Node<T> node) {
        if (node.right == node) {
            node.parent.child = null;
        } else {
            node.left.right = node.right;
            node.right.left = node.left;
            if (node.parent != null && node.parent.child == node) {
                node.parent.child = node.right;
            }
        }
        node.left = node.right = node;
    }

    /**
     * Merges two circular doubly linked lists.
     */
    private Node<T> mergeLists(Node<T> a, Node<T> b) {
        if (a == null) return b;
        if (b == null) return a;

        Node<T> temp = a.right;
        a.right = b.right;
        a.right.left = a;
        b.right = temp;
        b.right.left = b;

        return comparator.compare(a.key, b.key) < 0 ? a : b;
    }

    /**
     * @return True if heap is empty.
     */
    public boolean isEmpty() {
        return min == null;
    }

    /**
     * @return Current size of the heap.
     */
    public int size() {
        return size;
    }

    /**
     * @return The minimum element without extracting it.
     */
    public T getMin() {
        return min == null ? null : min.key;
    }
}