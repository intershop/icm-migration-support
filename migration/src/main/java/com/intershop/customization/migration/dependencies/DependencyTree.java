package com.intershop.customization.migration.dependencies;

public class DependencyTree<T> {
    DependencyEntry<T> root;

    public DependencyTree(T rootValue) {
        this.root = new DependencyEntry<>(rootValue);
    }

    public DependencyEntry<T> getRoot() {
        return root;
    }

    public void traverse(DependencyEntry<T> DependencyEntry) {
        if (DependencyEntry == null) return;

        // Print the current DependencyEntry's value
        System.out.print(DependencyEntry.value + " ");

        // Recursively traverse each child
        for (DependencyEntry<T> child : DependencyEntry.children) {
            traverse(child);
        }
    }

    // Recursive method to find an element in the n-ary tree
    public static <T> Dependency findElement(DependencyEntry<T> root, Dependency target) {
        if (root == null) {
            return null; // Base case: tree is empty
        }
    
        if (root.getValue().equals(target)) {
            return target; // Found the target
        }
    
        // Recursively search in the children
        for (DependencyEntry<T> child : root.getChildren()) {
            if (findElement(child, target) != null) 
            {
                return target;
            }
        }
    
        return null; // Target not found
    }

}

