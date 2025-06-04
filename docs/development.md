# Development Environment

Clone or download the ICM Migration Support project to your computer, for example by using the following command:

```bash
git clone git@github.com:intershop/icm-migration-support.git
```

After cloning the project, open your preferred IDE and import the project as a Gradle project.
Since the project is based on Gradle 8.10, be sure to use a compatible Gradle version.

## Adjust Log Level

By default, the logging prints only information of level INFO or higher. 
To enable DEBUG-level logging, set the system property `COM_INTERSHOP_LOG_LEVEL` to the desired log level before executing any Gradle tasks.