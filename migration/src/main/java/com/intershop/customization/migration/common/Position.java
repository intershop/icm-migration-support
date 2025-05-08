package com.intershop.customization.migration.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Position to remember location of cough content
 */
public class Position
{
    public static Position NOT_FOUND(List<String> lines)
    {
    	return new Position(lines, -1, -1);
    }

    /**
     * @param startMarker block marker
     * @param lines
     * @return position for given block
     */
    public static Optional<Position> findBracketBlock(String startMarker, List<String> lines)
    {
        int startIntershop = -1;
        int counterBrackets = 0;
        for (int i = 0; i < lines.size(); i++)
        {
            String line = lines.get(i).trim();
            if (line.startsWith(startMarker))
            {
                startIntershop = i;
            }
            if (startIntershop > 0)
            {
                if (line.contains("{"))
                {
                    counterBrackets++;
                }
                if (line.contains("}"))
                {
                    counterBrackets--;
                    if (counterBrackets == 0)
                    {
                        return Optional.of(new Position(lines, startIntershop, i));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private final int begin;
    private final int end;
    private final List<String> lines;

    /**
     * Position included
     * @param begin
     * @param end
     */
    public Position(List<String> lines, int begin, int end)
    {
        this.lines = lines;
        this.begin = begin;
        this.end = end;
    }

    /**
     * @return lines which are not included in position
     */
    public List<String> nonMatchingLines()
    {
        if (this.begin == -1)
        {
            return lines;
        }
        List<String> result = new ArrayList<>();
        result.addAll(lines.subList(0, this.begin));
        result.addAll(lines.subList(this.end + 1, lines.size()));
        return result;
    }

    /**
     * @return lines which are not included in and before the identified position
     */
    public List<String> nonMatchingLinesBefore()
    {
        if (this.begin == -1)
        {
            return lines;
        }
        return lines.subList(0, this.begin);
    }

    /**
     * @return lines which are not included in and after the identified position
     */
    public List<String> nonMatchingLinesAfter()
    {
        if (this.begin == -1)
        {
            return lines;
        }
        return lines.subList(this.end + 1, lines.size());
    }

    /**
     * @return lines which are included in position
     */
    public List<String> matchingLines()
    {
        if (this.begin > -1 && this.end >= this.begin)
        {
            return lines.subList(this.begin, this.end + 1);
        }
        return Collections.emptyList();
    }
}
