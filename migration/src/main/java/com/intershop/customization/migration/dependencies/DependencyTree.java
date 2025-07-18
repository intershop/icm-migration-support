package com.intershop.customization.migration.dependencies;
/**
 * * An n-ary tree structure for managing dependencies of cartridges and other components
 *  (libraries, INTERSHOP component framework) of an ICM 11+ project<br/>
 * Values are of type T, which can be any object representing a dependency,
 * @see DependencyEntry objects are referred here<br/>
 * 
 */
public class DependencyTree<T> {
    DependencyEntry<T> root;

    /** constructor
     * * @param rootValue the value of the root entry
     */
    public DependencyTree(T rootValue) {
        this.root = new DependencyEntry<>(rootValue);
    }

    /**
     * Returns the root entry of the dependency tree.
     * @return the root entry of the dependency tree.
     */
    public DependencyEntry<T> getRoot() {
        return root;
    }

    /**
     * walks recursively through the dependency tree.
     * @param DependencyEntry the entry to start the traversal from
     */
    public void traverse(DependencyEntry<T> DependencyEntry) {
        if (DependencyEntry == null) return;

        // Print the current DependencyEntry's value
        System.out.print(DependencyEntry.value + " ");

        // Recursively traverse each child
        for (DependencyEntry<T> child : DependencyEntry.children) {
            traverse(child);
        }
    }

    /** searches for a specific Dependency object in the tree.
     * 
     * @param root the root entry of the tree to start searching from
     * @param target the Dependency object to search for
     * @return the Dependency object if found, otherwise null
     */
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

