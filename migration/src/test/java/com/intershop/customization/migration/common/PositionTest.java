package com.intershop.customization.migration.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class PositionTest
{
    @Test
    void testListManipulation()
    {
        //                                     0    1    2    3    4    5    6
        List<String> existing = Arrays.asList("b", "{", "1", "2", "3", "}", "a");
        List<String> expectedExtract = Arrays.asList("1", "2", "3");
        List<String> expectedNotIn = Arrays.asList("b", "{", "}", "a");
        Position underTest = new Position(existing, 2, 4);
        assertEquals(expectedExtract, underTest.matchingLines());
        assertEquals(expectedNotIn, underTest.nonMatchingLines());
    }
}
