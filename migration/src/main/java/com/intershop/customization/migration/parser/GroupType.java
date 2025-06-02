package com.intershop.customization.migration.parser;

import java.util.Arrays;
import java.util.Objects;

/**
 * GroupType is used to identify the type of instruction in the dbinit.properties file.
 */
public enum GroupType
{
    PRE("pre.Class"),       // 'pre.Class' entries
    MAIN("Class"),          // 'Class' entries
    POST("post.Class"),     // 'post.Class' entries
    UNKNOWN;                       // lines that do not match any of the above

    private final String prefix;

    GroupType(String prefix)
    {
        this.prefix = prefix;
    }

    GroupType()
    {
        this.prefix = null;
    }

    public String prefix()
    {
        return prefix;
    }

    public static GroupType valueByPrefix(String prefix)
    {
        return Arrays.stream(values())
                     .filter(group -> Objects.nonNull(group.prefix()))
                     .filter(group -> prefix.startsWith(group.prefix()))
                     .findFirst()
                     .orElse(UNKNOWN);
    }
}