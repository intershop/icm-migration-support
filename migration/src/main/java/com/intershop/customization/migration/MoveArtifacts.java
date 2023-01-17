package com.intershop.customization.migration;

import java.nio.file.Path;

import com.intershop.beehive.tools.StaticFileMigrator;

public class MoveArtifacts
{
    public void migrate(Path projectDir)
    {
        String args[] = { projectDir.toAbsolutePath().toString() };
        StaticFileMigrator.main(args);
    }

}
