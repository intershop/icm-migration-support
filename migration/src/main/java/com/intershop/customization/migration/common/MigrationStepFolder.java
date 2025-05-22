package com.intershop.customization.migration.common;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.intershop.customization.migration.utils.FileUtils;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.slf4j.LoggerFactory;

/**
 * A MigrationStepFolder represents a resource folder containing migrations steps for a specific release
 */
public class MigrationStepFolder
{
    public static MigrationStepFolder valueOf(Path path)
    {
        try
        {
            Comparator<Path> comparator = Comparator.comparing(p -> p.getFileName().toString());
            List<Path> steps = FileUtils.listFiles(path, Files::isRegularFile, comparator);

            return new MigrationStepFolder(steps);
        }
        catch(IOException e)
        {
            throw new RuntimeException("Can't import resource '" + path + "'", e);
        }
    }

    public static MigrationStepFolder valueOf(String resourceName)
    {
        List<URI> uris = getURIs(resourceName);
        List<Path> paths = uris.stream().map(Paths::get).toList();
        return new MigrationStepFolder(paths);
    }

    private final List<Path> steps;

    public MigrationStepFolder(List<Path> steps)
    {
        this.steps = steps;
    }

    public List<MigrationStep> getSteps()
    {
        return steps.stream().map(MigrationStep::valueOf).toList();
    }

    static List<URI> getURIs(String pkg)
    {
        return getURIs(pkg, ".yml");
    }

    static List<URI> getURIs(String pkg, String extension)
    {
        List<URI> result = new ArrayList<>();
        try (ScanResult scanResult = new ClassGraph()// .verbose() // Log to stderr
                                                     .enableAllInfo() // Scan classes, methods, fields, annotations
                                                     .acceptPackages(pkg) // Scan com.xyz and subpackages (omit to
                                                                          // scan all packages)
                                                     .scan())
        { 
            scanResult.getAllResources().forEach(r -> {
                if (r.getPath().endsWith(extension))
                {
                    result.add(r.getURI());
                }
            });
        }
        LoggerFactory.getLogger(MigrationStepFolder.class).debug("found resources {}", result);
        return result;
    }
}
