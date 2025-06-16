package com.intershop.customization.migration.dependencies;

public record Dependency(
    String carteidgeName,
    String artifactName,
    DependencyType dependencyType
) 
{
    public Dependency(String carteidgeName, String artifactName, DependencyType dependencyType)
     {
        this.carteidgeName = carteidgeName;
        this.artifactName = artifactName;
        this.dependencyType = dependencyType;
    }

    public String getCarteidgeName() 
    {
        return carteidgeName;
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
