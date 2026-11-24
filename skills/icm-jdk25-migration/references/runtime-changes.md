# JDK behavior changes that compile fine and fail at runtime

Every item compiles without a warning. Most were found in `icm-as`, some only in the server startup log.
Work through the list with the grep patterns; each item states whether it was **observed** in the ICM
migration or is a **JDK change to check** that did not hit ICM code.

Contents:

- [R1 - non-local `file:` URLs are rejected](#r1---non-local-file-urls-are-rejected)
- [R2 - closing a shared `JarFile`](#r2---closing-a-shared-jarfile)
- [R3 - `new File("")` is the current directory](#r3---new-file-is-the-current-directory)
- [R4 - the Security Manager is gone](#r4---the-security-manager-is-gone)
- [R5 - tools missing from the base image](#r5---tools-missing-from-the-base-image)
- [R6 - deprecated and removed JVM flags](#r6---deprecated-and-removed-jvm-flags)
- [R7 - Guice: `sun.misc.Unsafe` and AOP](#r7---guice-sunmiscunsafe-and-aop)
- [R8 - runtime compilers: ECJ, Jasper, Drools](#r8---runtime-compilers-ecj-jasper-drools)
- [R9 - Javadoc rejects unknown tags](#r9---javadoc-rejects-unknown-tags)

## R1 - non-local `file:` URLs are rejected

**Observed (icm-as).** Since JDK 25 ([JDK-8353440](https://bugs.openjdk.org/browse/JDK-8353440)) a
`file:` URL with a host part - `file://host/path`, and also `jar:file://dummy.jar!/` - throws
`MalformedURLException: Unsupported non-local file URL` on `openConnection()`. Before, the JDK silently
tried FTP. `new URL(...)` itself still succeeds, so the failure moves to wherever the connection is
opened.

The dangerous case is a swallowed exception. `icm-as` used exactly that dummy URL to switch off jar URL
caching, inside `try { ... } catch (Exception e) { }` - the call stopped working without a trace, and the
effect surfaced far away as R2.

```shell
git grep -n -E "file://[^/]|jar:file://[^/]"
```

Fix: `file:/path` or `Path.toUri()`; for the caching case `URLConnection.setDefaultUseCaches("jar",
false)`, which needs no URL at all. Never swallow exceptions around URL handling. `-Djdk.net.file.ftpfallback=true`
restores the old behavior, but only hides the bug.

## R2 - closing a shared `JarFile`

**Observed (icm-as), consequence of R1.** With jar URL caching on (the JDK default),
`JarURLConnection.getJarFile()` returns **one `JarFile` shared by every connection to that jar**.
Closing it - e.g. in a `try (JarFile jf = connection.getJarFile())` that lists resources - closes the
streams other threads are reading. Symptoms are random and change between starts:
`IOException: Stream closed` from an XML parser, `IllegalStateException: zip file closed`. In ICM it hit
the parallel pipeline preloading at startup.

```shell
git grep -n -E "getJarFile\(\)"
```

Fix: close the `JarFile` only if `!connection.getUseCaches()`, or open the connection with
`setUseCaches(false)` so it gets its own instance. A race needs at least two restarts to call it fixed.

## R3 - `new File("")` is the current directory

**Observed (icm-as test).** Since JDK 24, `new File("")` behaves as the current working directory:
`exists()` is true, `listFiles()` lists the working directory, `isDirectory()` is true. Code that used
`""` as "no file" now acts on the working directory - a staging test synchronized the whole project
folder.

```shell
git grep -n -E "new File\(\"\"\)|Paths?\.(get|of)\(\"\"\)"
```

Fix: check for an empty path explicitly; in tests use `@TempDir`.

## R4 - the Security Manager is gone

**JDK change to check** (JEP 486, JDK 24). `System.setSecurityManager(...)` throws
`UnsupportedOperationException`; `System.getSecurityManager()` always returns `null`.

```shell
git grep -n -E "SecurityManager|AccessController|checkPermission"
```

## R5 - tools missing from the base image

**Observed (icm-as image).** The `eclipse-temurin:25` images no longer contain `curl`; the `:21` images
did. The ICM Helm chart's liveness and readiness probes run `curl`, so a server on such an image starts
normally and is **killed by Kubernetes after about 30 seconds** - which looks like a crash, not like a
missing tool. `icm-as` now installs `curl` explicitly; images `FROM` the ICM image inherit it.

For an image built on Temurin directly, compare the tools of old and new base image instead of assuming:

```shell
for i in eclipse-temurin:21-jre-noble eclipse-temurin:25-jre-noble; do
  docker run --rm --entrypoint sh $i -c 'for t in curl wget bash ps jq; do command -v $t >/dev/null || echo "$0: no $t"; done' $i
done
```

## R6 - deprecated and removed JVM flags

**Observed (icm-as start scripts).** `-XX:+PrintGCDetails` is deprecated (use `-Xlog:gc*`). A removed
flag is worse: the JVM refuses to start. Test every flag a start script, Helm value or `JAVA_OPTS` can
add - including the optional branches (GC log, heap dump, JMX, debug):

```shell
java <all flags of the script> -version
```

Warnings and errors name the offending flag.

## R7 - Guice: `sun.misc.Unsafe` and AOP

**Observed (icm-as).** Guice 7 prints a warning on JDK 25 that it calls the terminally deprecated
`sun.misc.Unsafe::staticFieldBase`; a future JDK will make this an error. The ICM start scripts
therefore set `-Dguice_bytecode_gen_option=DISABLED`.

That switches off Guice AOP. **A customization using `bindInterceptor` then fails at injector creation**
with `[Guice/AopDisabled]: Binding interceptor is not supported when bytecode generation is disabled`.

```shell
git grep -n -E "bindInterceptor|org\.aopalliance"
```

If there are hits, set `ADDITIONAL_JVM_ARGUMENTS=-Dguice_bytecode_gen_option=ENABLED` (the ICM script
places its default before the additional arguments, and the last `-D` wins) - or better, replace the
interceptor. Also expected and harmless: line numbers in Guice error messages show as "Unknown Source".

## R8 - runtime compilers: ECJ, Jasper, Drools

**Observed (icm-as).** ICM compiles code at runtime with the Eclipse compiler (ECJ): JSPs generated from
ISML (development mode, through Jasper) and Drools rules. Compatibility between JDK, ECJ, Jasper and
Drools historically breaks only at runtime:

- ECJ has to know the running Java version. ECJ 3.40 supports Java up to 23; `icm-as` 15 forces a newer
  version for all modules. **Do not pin ECJ, Drools or Tomcat in a customization** - take them from the
  `com.intershop.icm:versions` platform.
- Jasper silently falls back to the Ant/javac compiler if ECJ cannot be loaded.
- Drools 10.2 knows Java language levels up to 21 and silently uses 17 for anything newer. ICM sets
  `drools.dialect.java.compiler.lnglevel=21`, so **rules can use Java 21 features, not Java 25 ones**.
- ISML scriptlets compile at Jasper's default source level (17) unless `compilerSourceVM` is configured.

`icm-as` guards the combination with `EclipseCompilerCompatibilityTest` and `JspCompilationTest`
(`platform/servletengine`) and `JavaDialectCompilerTest` (`platform/ac_ruleengine_drools`). A
customization that ships Drools rules or complex ISML scriptlets should exercise them in an ISH unit test
or on a started server.

## R9 - Javadoc rejects unknown tags

**Observed (icm-as).** Since JDK 23, Javadoc reports unknown tags (`@generated`, `@model`, custom
generator tags, ...) even with `-Xdoclint:none`. With `-Werror` that fails the build. Register the tags
- JDK convention tags with a heading (`opt.tags("apiNote:a:API Note:")`), generator tags hidden (`opt.tags("generated:X")`) - or drop `-Werror` for Javadoc. The standard customizations use
`-Xdoclint:none` without `-Werror` and only get warnings.
