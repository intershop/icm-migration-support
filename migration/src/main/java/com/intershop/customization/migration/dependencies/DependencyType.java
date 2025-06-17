package com.intershop.customization.migration.dependencies;

public enum DependencyType 
{
    ROOT        ("root"),
    CARTRIDGE   ("cartridge"),
    ARTIFACT    ("artifact"),
    COMPONENT   ("comoonen"),
    LIBRARY     ("library"),
    UNKNOWN     ("unknown");

    private final String value;

    // Constructor
    DependencyType(String value)
    {
        this.value = value;
    }

    // Getter method
    public String getValue()
    {
        return value;
    }

    public static DependencyType fromValue(String input) {
        for (DependencyType type : DependencyType.values()) {
            if (type.getValue().equals(input)) {
                return type;
            }
        }
        return UNKNOWN;
    }

}
