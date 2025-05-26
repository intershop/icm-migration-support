# Development Environment

Clone or download the ICM Migration Support project to your computer, e.g.,

```bash
git clone git@github.com:intershop/icm-migration-support.git
```

After having cloned the project from the Git repository, open the IDE of your choice and import the project as a Gradle project. 
The project is based on Gradle 8.10, so make sure to use a compatible version of Gradle.

## Adjust Log level

By default, the logging prints only information of level INFO or higher. 
To enable DEBUG log, set a system property `COM_INTERSHOP_LOG_LEVEL` with the desired log level before running Gradle tasks.