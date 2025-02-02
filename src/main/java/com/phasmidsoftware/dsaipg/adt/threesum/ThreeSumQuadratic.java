/*
 * Copyright (c) 2024. Robin Hillyard
 */

package com.phasmidsoftware.dsaipg.adt.threesum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of ThreeSum which follows the approach of dividing the solution-space into
 * N sub-spaces where each sub-space corresponds to a fixed value for the middle index of the three values.
 * Each sub-space is then solved by expanding the scope of the other two indices outwards from the starting point.
 * Since each sub-space can be solved in O(N) time, the overall complexity is O(N^2).
 * <p>
 * NOTE: The array provided in the constructor MUST be ordered.
 */
public class ThreeSumQuadratic implements ThreeSum {
    /**
     * Construct a ThreeSumQuadratic on a.
     *
     * @param a a sorted array.
     */
    public ThreeSumQuadratic(int[] a) {
        this.a = a;
        length = a.length;
    }

    /**
     * Retrieves an array of unique Triples. Each Triple represents a unique combination of three integers from
     * the source array that sum to zero.
     *
     * @return an array of distinct Triples, sorted in natural order, where each Triple satisfies the condition that
     * the sum of its three integers is zero.
     */
    public Triple[] getTriples() {
        List<Triple> triples = new ArrayList<>();
        for (int i = 0; i < length; i++) triples.addAll(getTriples(i));
        Collections.sort(triples);
        return triples.stream().distinct().toArray(Triple[]::new);
    }

    /**
     * Get a list of Triples such that the middle index is the given value j.
     *
     * @param j the index of the middle value.
     * @return a list of Triples such that a[i] + a[j] + a[k] = 0.
     */
    List<Triple> getTriples(int j) {
        List<Triple> triples = new ArrayList<>();
        int target = -a[j]; // Since a[i] + a[j] + a[k] = 0 => a[i] + a[k] = -a[j]
        int i = 0;
        int k = length - 1;

        while (i < j && j < k) {
            int sum = a[i] + a[k];
            if (sum < target) {
                i++; // Move the left pointer to increase the sum
            } else if (sum > target) {
                k--; // Move the right pointer to decrease the sum
            } else {
                // Found a valid triple
                triples.add(new Triple(a[i], a[j], a[k]));
                i++;
                k--;

                // Skip duplicates to ensure unique triples
                while (i < j && a[i] == a[i - 1]) i++;
                while (j < k && a[k] == a[k + 1]) k--;
            }
        }

        return triples;
    }

    private final int[] a;
    private final int length;
}