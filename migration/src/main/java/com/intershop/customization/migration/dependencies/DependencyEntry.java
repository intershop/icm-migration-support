package com.intershop.customization.migration.dependencies;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an entry in a dependency tree.
 * <p>
 * This class holds a value of type T and a list of child entries, allowing for the construction
 * of a hierarchical structure where each entry can have multiple children.
 *
 * @param <T> the type of the value held by this entry
 */
public class DependencyEntry<T> {
    T value;
    List<DependencyEntry<T>> children;

    /**
     * Constructs a DependencyEntry with the specified value.
     *
     * @param value the value to be held by this entry
     */
    public DependencyEntry(T value) {
        this.value = value;
        this.children = new ArrayList<>();
    }

    /**
     * Adds a child DependencyEntry to this entry.
     *
     * @param child the child DependencyEntry to be added
     */
    public void addChild(DependencyEntry<T> child) {
        this.children.add(child);
    }

    /**
     * Returns the value held by this entry.
     *
     * @return the value of type T
     */
    public T getValue() {
        return value;
    }
    public List<DependencyEntry<T>> getChildren() {
        return children;
    }

    /**
     * Checks if this DependencyEntry is equal to another DependencyEntry.
     * <p>
     * Two DependencyEntries are considered equal if they have the 
     * same value using there equals()  method
     * NULL vallues are not allowed.
     *
     * @param other the other DependencyEntry to compare with
     * @return true if both entries are equal, false otherwise
     */
    boolean equals(DependencyEntry<T> other) {

        if (this == other) return true;; // Check for reference equality
        if (other == null || getClass() != other.getClass()) return false;

        // Compare the root values
        return this.value.equals(other.value);
    }

}
