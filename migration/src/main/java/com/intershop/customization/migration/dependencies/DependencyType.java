package com.intershop.customization.migration.dependencies;

/**
 * Enum representing different types of dependencies in a migration context.
 * <ul>
 * <li>ROOT - Represents the root entry in the dependency tree.</li>
 * <li>CARTRIDGE - Represents a cartridge dependency.</li>
 * <li>ARTIFACT - Represents an artifact dependency, almost jar files</li>
 * <li>COMPONENT - Represents a component dependency, used to resolve the dependencies declared by the comonant
 * framework</li>
 * <li>LIBRARY - Represents a library dependency.</li>
 * <li>PACKAGE - Represents a package dependency.</li>
 * <li>UNKNOWN - Represents an unknown dependency type.</li>
 * </ul>
 */
public enum DependencyType
{
    ROOT("root"), 
    CARTRIDGE("cartridge"),
    ARTIFACT("artifact"), 
    COMPONENT("component"), 
    LIBRARY("library"),
    PACKAGE("package"), 
    APPLICATION("application"), 
    UNKNOWN("unknown");

    private final String value;

    /**
     * Constructor for DependencyType enum.
     * 
     * @param value
     */
    DependencyType(String value)
    {
        this.value = value;
    }

    /**
     * Returns the string representation of the dependency type.
     * 
     * @return the string value of the dependency type
     */
    public String getValue()
    {
        return value;
    }

    public static DependencyType fromValue(String input)
    {
        for (DependencyType type : DependencyType.values())
        {
            if (type.getValue().equals(input))
            {
                return type;
            }
        }
        return UNKNOWN;
    }

}
