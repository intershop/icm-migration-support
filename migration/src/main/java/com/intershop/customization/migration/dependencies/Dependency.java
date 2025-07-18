package com.intershop.customization.migration.dependencies;

/**
 * Represents a code dependency in a migration context as value
 * in a @see DependencyTree<br/>
 * It holds the name, artifact name, and type of the dependency.
 * 
 * @param name the name of the dependency
 * @param artifactName the artifact name of the dependency
 * @param dependencyType the type of the dependency, e.g., CARTRIDGE, ARTIFACT, COMPONENT, LIBRARY, PACKAGE
 * 
 */
public record Dependency(
    String name,
    String artifactName,
    DependencyType dependencyType
) 
{
    public Dependency(String name, String artifactName, DependencyType dependencyType)
     {
        this.name = name;
        this.artifactName = artifactName;
        this.dependencyType = dependencyType;
    }

    public String getName() 
    {
        return name;
    }

    public String getArtifactName() 
    {
        return artifactName;
    }

    public DependencyType getDependencyType() 
    {
        return dependencyType;
    }


}
