# Development Environment

[ICM Migration Support](https://github.com/intershop/icm-migration-support) is an open source project provided by Intershop.  

## Clone or Download

Clone or download the [ICM Migration Support](https://github.com/intershop/icm-migration-support) project to your computer, for example by using the following command:

```bash
git clone git@github.com:intershop/icm-migration-support.git
```

## Compile and Test

Use Java 21.  
E.g. set JAVA_HOME in your command line (Windows):
```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot
```

Execute:
```bash
gradlew clean compileTestJava
```

## Import into IDE

After cloning the project, open your preferred IDE and import the project as a Gradle project.
Since the project is based on Gradle 8.10, be sure to use a compatible Gradle version.

## Adjust Log Level

By default, the logging prints only information of level `INFO` or higher.  
To enable DEBUG-level logging, set the system property `COM_INTERSHOP_LOG_LEVEL` to the desired log level before executing any Gradle tasks.  
In `/migration/src/main/resources/logback.xml`, switch between these lines:
```
<logger name="com.intershop" level="${COM_INTERSHOP_LOG_LEVEL:-INFO}" />
<logger name="com.intershop" level="${COM_INTERSHOP_LOG_LEVEL:-DEBUG}" />
<logger name="com.intershop" level="${COM_INTERSHOP_LOG_LEVEL:-WARN}" />
<logger name="com.intershop" level="${COM_INTERSHOP_LOG_LEVEL:-ERROR}" />
<logger name="com.intershop" level="${COM_INTERSHOP_LOG_LEVEL:-TRACE}" />
```
