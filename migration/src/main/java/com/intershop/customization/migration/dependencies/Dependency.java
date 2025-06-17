package com.intershop.customization.migration.dependencies;

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
