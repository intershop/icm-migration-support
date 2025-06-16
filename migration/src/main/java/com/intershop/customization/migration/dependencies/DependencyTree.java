package com.intershop.customization.migration.dependencies;

import java.util.ArrayList;
import java.util.List;


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
    public static Dependency findElement(Dependency root, Dependency target) {
        if (root == null) {
            return null; // Base case: tree is empty
        }

        if (root.equals(target)) {
            return target; // Found the target
        }

        // Recursively search in the children
        for (DependencyEntry<T>  child : root.getChildren()) {
            if (findElement(child, target)) 
            {
                return target;
            }
        }

        return null; // Target not found
    }

}

/**
public class DependencyTree {
    public static void main(String[] args) {
        // Example usage
        DependencyTree<String> tree = new DependencyTree<>("Root");
        DependencyEntry<String> child1 = new DependencyEntry<>("Child1");
        DependencyEntry<String> child2 = new DependencyEntry<>("Child2");

        tree.getRoot().addChild(child1);
        tree.getRoot().addChild(child2);

        child1.addChild(new DependencyEntry<>("Child1.1"));
        child1.addChild(new DependencyEntry<>("Child1.2"));
        child2.addChild(new DependencyEntry<>("Child2.1"));

        // Traverse the tree
        System.out.println("Tree traversal:");
        tree.traverse(tree.getRoot());
    }
}
*/