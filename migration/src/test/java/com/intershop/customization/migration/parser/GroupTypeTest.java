package com.intershop.customization.migration.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class GroupTypeTest {

    @Test
    void testValueByPrefix_preClass()
    {
        assertEquals(GroupType.PRE, GroupType.valueByPrefix("pre.Class1"));
    }

    @Test
    void testValueByPrefix_mainClass()
    {
        assertEquals(GroupType.MAIN, GroupType.valueByPrefix("Class1"));
    }

    @Test
    void testValueByPrefix_postClass()
    {
        assertEquals(GroupType.POST, GroupType.valueByPrefix("post.Class1"));
    }

    @Test
    void testValueByPrefix_unknown()
    {
        assertEquals(GroupType.UNKNOWN, GroupType.valueByPrefix("randomKey"));
        assertEquals(GroupType.UNKNOWN, GroupType.valueByPrefix(""));
        assertEquals(GroupType.UNKNOWN, GroupType.valueByPrefix("preclass"));
    }

    @Test
    void testPrefixMethod()
    {
        assertEquals("pre.Class", GroupType.PRE.prefix());
        assertEquals("Class", GroupType.MAIN.prefix());
        assertEquals("post.Class", GroupType.POST.prefix());
        assertNull(GroupType.UNKNOWN.prefix());
    }
}
