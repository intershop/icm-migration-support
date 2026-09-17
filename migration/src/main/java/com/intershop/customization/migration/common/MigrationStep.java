package com.intershop.customization.migration.common;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import com.intershop.customization.migration.utils.FileUtils;
import org.yaml.snakeyaml.Yaml;

public class MigrationStep
{
    public static MigrationStep valueOf(URI resourceURI)
    {
        MigrationStep result = new MigrationStep();
        result.importOptions(resourceURI);
        result.name = nameOf(Paths.get(resourceURI));
        return result;
    }

    public static MigrationStep valueOf(Path optionsPath)
    {
        MigrationStep result = new MigrationStep();
        result.importOptions(optionsPath);
        result.name = nameOf(optionsPath);
        return result;
    }

    /**
     * Derives the step name from its descriptor file, for example {@code 020_MoveFolder.yml} becomes
     * {@code 020_MoveFolder}. The name is what attributes an operation to a step in the migration log, so it has to be
     * stable and human-recognisable, and the descriptor file name is both.
     *
     * @param path the step descriptor path
     * @return the step name without its file extension
     */
    private static String nameOf(Path path)
    {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static final String MIGRATOR_KEY = "migrator";
    private static final String OPTIONS_KEY = "options";
    private static final String MESSAGE_KEY = "message";

    private Map<String, Object> yamlConf = Collections.emptyMap();
    private String name = "unknown";

    /**
     * @return the step name, derived from the descriptor file name
     */
    public String getName()
    {
        return name;
    }

    public Map<String, Object> importOptions(String content)
    {
        Yaml yaml = new Yaml();
        yamlConf = yaml.load(content);
        return yamlConf;
    }

    @SuppressWarnings("unchecked")
    private <T> T getRootKey(String key)
    {
        return (T) yamlConf.get(key);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getOption(String key)
    {
        Map<String, Object> op = getRootKey(OPTIONS_KEY);
        return (T) op.get(key);
    }

    @SuppressWarnings("unchecked")
    public String getMessage()
    {
        return getRootKey(MESSAGE_KEY);
    }

    @SuppressWarnings("unchecked")
    public MigrationPreparer getMigrator()
    {
        String clazzName = getRootKey(MIGRATOR_KEY);
        MigrationPreparer result;
        try
        {
            Class<MigrationPreparer> clazz = (Class<MigrationPreparer>) getClass().getClassLoader().loadClass(clazzName);
            result = clazz.getConstructor().newInstance();
            result.setStep(this);
        }
        catch(InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                        | NoSuchMethodException | SecurityException | ClassNotFoundException e)
        {
            throw new IllegalArgumentException("not an existing migrator class", e);
        }
        return result;
    }

    Map<String, Object> importOptions(Path path)
    {
        try
        {
            return importOptions(FileUtils.readString(path));
        }
        catch(IOException e)
        {
            throw new IllegalArgumentException("can't load resource", e);
        }
    }

    private Map<String, Object>  importOptions(URI resourceURI)
    {
        return importOptions(Paths.get(resourceURI));
    }

}
