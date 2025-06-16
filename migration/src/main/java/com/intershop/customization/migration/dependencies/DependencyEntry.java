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
}
