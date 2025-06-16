package com.intershop.customization.migration.dependencies;

import java.util.ArrayList;
import java.util.List;

public class DependencyEntry<T> {
    T value;
    List<DependencyEntry<T>> children;

    public DependencyEntry(T value) {
        this.value = value;
        this.children = new ArrayList<>();
    }

    public void addChild(DependencyEntry<T> child) {
        this.children.add(child);
    }

    public T getValue() {
        return value;
    }
    public List<DependencyEntry<T>> getChildren() {
        return children;
    }

    boolean equals(DependencyEntry<T> other) {
        T thisValue = this.value;

        if (this != other) return false; // Check for reference equality
        if (other == null || getClass() != other.getClass()) return false;

        // Compare the root values
        return this.value.equals(other.value);
    }

}
