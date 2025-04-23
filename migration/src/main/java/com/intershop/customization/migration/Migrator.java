package com.intershop.customization.migration;

import java.io.File;

import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.common.MigrationStepFolder;

import org.slf4j.LoggerFactory;

public class Migrator
{
    private static final int POS_TASK = 0;
    private static final int POS_PATH = 1;
    private static final int POS_STEPS = 2;

    /**
     * @param args
     * <li>"project" as task</li>
     * <li>directory to project hackathon2301-rewrite/app_sf_responsive</li>
     * <li>directory to migration steps like src/main/resources/001_migration_7.10-11.0.8</li>
     */
    public static void main(String[] args)
    {
        if (args.length == POS_STEPS + 1)
        {
            File projectPath = new File(args[POS_PATH]);
            if (!projectPath.exists() || !projectPath.isDirectory())
            {
                LoggerFactory.getLogger(Migrator.class).error("Project path '{}' is not a directory.", projectPath);
                System.exit(1);
            }
            if ("project".equals(args[POS_TASK]))
            {
                LoggerFactory.getLogger(Migrator.class).info("Convert project at {}.", projectPath);
                migrateProject(projectPath, new File(args[POS_STEPS]));
            }
            else if ("projects".equals(args[POS_TASK]))
            {
                LoggerFactory.getLogger(Migrator.class).info("Convert projects at {}.", projectPath);
                migrateProjects(projectPath, new File(args[POS_STEPS]));
            }
        }
        else
        {
            System.err.printf("Missing parameter %d.", args.length );
        }
        return;
    }

    /**
     * Migrate on root project
     * @param projectDir
     * @param migrationStepFolder 
     */
    private static void migrateProjects(File projectDir, File migrationStepFolder)
    {
        File[] files = projectDir.listFiles();
        if (files == null)
        {
            System.err.printf("Project directory not found %f.", projectDir.toString());
            System.exit(1);
        }
        for(File fileOrDir: files)
        {
            if (fileOrDir.isDirectory() && (new File(fileOrDir, "build.gradle")).exists())
            {
                if (!fileOrDir.getName().startsWith("."))
                {
                    migrateProject(fileOrDir, migrationStepFolder);
                }
            }
        }
    }

    /**
     * Migrate on project
     * @param projectDir
     */
    private static void migrateProject(File projectDir, File migrationStepFolder)
    {
        MigrationStepFolder steps = MigrationStepFolder.valueOf(migrationStepFolder.toPath());
        for(MigrationStep step: steps.getSteps())
        {
            step.getMigrator().migrate(projectDir.toPath());
        }
    }
}
