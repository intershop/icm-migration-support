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

}

