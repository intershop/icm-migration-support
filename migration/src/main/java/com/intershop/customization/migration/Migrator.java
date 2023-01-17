package com.intershop.customization.migration;

import java.io.File;

import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.common.MigrationStepFolder;

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
            if ("project".equals(args[POS_TASK]))
            {
                migrateProject(new File(args[POS_PATH]), new File(args[POS_STEPS]));
            }
            else if ("projects".equals(args[POS_TASK]))
            {
                migrateProjects(new File(args[POS_PATH]));
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
     */
    private static void migrateProjects(File projectDir)
    {
        // TODO traverse directories with build.gradle files
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
