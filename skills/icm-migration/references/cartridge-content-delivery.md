# Getting cartridge content onto a running ICM 11+ server

**Provenance: this file is ours, not Intershop's.** In 7.10 most non-Java content reached the server by
being copied into server directories under `IS_SHARE`. In ICM 11+ it is cartridge content, and each kind
of content has its own delivery mechanism. Migrations break quietly here: nothing fails to compile, and
the first symptom is usually a missing file or an empty form at runtime.

The single most useful habit: for each kind of content, find **what reads it** before deciding where to
put it. `com.intershop.platform.cartridge.ClassLoaderResourceTools` is the marker of a feature that can
read classpath resources; a feature that resolves a `File` under the artifact directory may still be on
the legacy path.

---

## staticfiles is backward compatibility, not a target

`staticfiles/cartridge/...` still works, through
`com.intershop.beehive.runtime.classpath.StaticFilesAwareCartridgeClassLoader`. It is a fallback, and
the resource tree is primary. From `runtime/.../classpath/Cartridge.java`:

```java
artifactLocation    = existingDirectory(location, "staticfiles/cartridge");
artifactSrcLocation = existingDirectory(location, "src/main/resources/resources/" + name);
```

`getArtifactDirectory(String)` tries **`artifactSrcLocation` first**. So content under
`src/main/resources/resources/<cartridge>/` wins, and `staticfiles` is the compatibility route.

The platform still ships `staticfiles/cartridge/configdef` in several of its own cartridges, which makes
leaving content there look endorsed. It is not; it is the platform carrying its own legacy. Treat any
allow-list of "content that may stay under staticfiles" as unfinished business, not as a decision.

## Configuration: `config/cluster` and `config/domains` need `configuration.xml`

Properties under `config/` are inert until a `<set>` in that cartridge's `config/configuration.xml`
names them. The two shapes, both taken from the platform's own files:

```xml
<!-- cluster-scope, cf. platform/cache -->
<set finder="resource" scope="cluster,server,domain" required="true"
     resourceName="config/cluster/cache.properties" cartridge="cache"/>

<!-- domain-scope, with the placeholders the converter emits -->
<set finder="domain-resource" scope="domain" domainName="<Domain>"
     resourceName="config/domains/<Domain>/${environment}_transport.properties"
     priority="62" cartridge="<cartridge>" required="false"/>
```

Priorities form tiers: plain name 60, `${environment}_` 62, `${environment}_${staging.system.type}_` 64.

**Classify by content, not by path.** A `config/cluster` directory is not all configuration. On one
project a third of it was not: locale definitions and `localization.properties` (read by
`LocaleInformationPreparer`, a DBPrepare preparer, not by a configuration set), active-currency dbinit
input, a `.pkcs12` keystore, JSON schemas, and impex process chains. Wiring those as configuration sets
would be wrong. Read a file before you wire it.

**Verify statically**, because no build command reads `configuration.xml`: parse every file, glob each
`resourceName` with `${...}` replaced by `*` and confirm it matches a shipped file, check each
`cartridge` attribute matches the cartridge it lives in, then reverse the check to find shipped files no
set covers. Resolve `resourceName` against `resources/<cartridge>/`, not against `config/`.

**Step 050 wires only what it converts, so a file that was already `.properties` is missed.** The step
renames `config/**/*.resource` to `*.properties` and generates the `<set>` entries from what it renamed.
A `.properties` file that was sitting under `config/` *before* the migration is therefore neither
converted nor wired: it ends up shipped, syntactically fine, and read by nothing. This is silent at
every stage. On one project it was 23 of 179 domain files, carrying live `intershop.*` platform
properties that had simply stopped taking effect.

Any 7.10 project that hand-placed configuration alongside the framework's own has this. Find it by
listing every `.properties` under `config/` and subtracting those named by a `<set>`; the remainder is
either missed configuration or content that belongs on another mechanism, and the paragraph above says
how to tell which.

**The tool names the affected cartridges and then stops.** Step 020 logs `need to be wired in
configuration.xml` per cartridge. That list is worth harvesting from the step-020 output at the time it
runs, because it is the closest thing to a work list for this action, but nothing is generated from it
and cartridges with no `configuration.xml` at all still need one created by hand.

## Sites content: `sites/<name>/...` and a `SiteContentPreparer` entry

`SiteContentBuilder` calls
`ClassLoaderResourceTools.visitCartridgeResources(cartridgeName, "sites", …)`, so site content is a
classpath lookup. It treats the **first path segment under `sites/` as the target directory name**
directly under `IS_SITES`, with no check that a site of that name is registered. That makes it usable
for non-site content: `sites/truststore/x` lands at `IS_SITES/truststore/x`.

The preparer builds content for **the cartridge that registers it**
(`siteContentBuilder.path(getConfiguration().getCartridgeName())`), so every cartridge shipping `sites/`
content needs its own `dbinit.properties` entry.

## Preparers scope themselves to the registering cartridge

`Preparer.getCartridge()` resolves `getConfiguration().getCartridgeName()`, which is the cartridge whose
`dbinit.properties` carries the entry. Combined with
`runtime.classpath.Cartridge.get(name).getArtifactDirectory(folder)`, that gives a preparer that reads
only its own cartridge's resources, so no central list of cartridges has to be maintained anywhere.

Note the two `Cartridge` types: `Preparer.getCartridge()` returns
`com.intershop.beehive.core.capi.cartridge.Cartridge`, which has no `getArtifactDirectory`;
`com.intershop.beehive.runtime.classpath.Cartridge` has it, and the static `Cartridge.get(String)`
bridges by name.

## dbinit and dbmigrate are different questions, with different defaults

**dbinit runs for new installations only.** Anything an existing installation also needs must be wired
for dbmigrate as well, in a `migration-to-<version>.properties`. The version in that file name is for
ordering only and need not be a release number.

Phases: all cartridges' `pre.ClassN`, then all `ClassN`, then all `post.ClassN`. Registering as a
post- step is the reliable way to depend on something another cartridge created, without relying on a
dependency edge. Both dbinit and dbmigrate honour the prefixes, though a project whose migration files
use only plain `ClassN` makes it look otherwise.

**Check each preparer's dbmigrate default before reusing a dbinit entry.** `SiteContentPreparer`
defaults to `noReplace` for dbinit (copy non-existing files) and **`managedFileOnly`** for dbmigrate
(only replace files a sites-preparer created before). For a file that has never been deployed by a
preparer, the dbmigrate default therefore does nothing, silently. Pass `mode=noReplace` explicitly.

---

## Registering cartridges at ApplicationType lists

**Use a Guice module. Do not use a `*.component` fulfillment.** This is the single highest-value
conversion in a 7.10 migration: on one project it replaced a planned `as_` cartridge split across 24
cartridges, and without it the server does not start.

### The 7.10 form, and why it stops working

Every cartridge that belongs to an ApplicationType carries something like this in
`components/app-extension.component`:

```xml
<fulfill requirement="selectedCartridge" of="intershop.REST.Cartridges" value="my_cartridge"/>
```

A fulfillment requires the instance to be **declared** in a context visible from the fulfilling
cartridge. In ICM 14.x `intershop.REST.Cartridges` is declared in the headless storefront cartridges
(`as_headless`, `app_sf_headless`), so a cartridge outside that context aborts startup:

```
ComponentException: Configuration of context 'global' failed
Caused by: ComponentConfigurationException:
  Can't find declaration of instance 'intershop.REST.Cartridges'
  for adding fulfillments in context 'app_sf_base_cm'
```

Note what the message does **not** say: which cartridge is at fault, or that the problem is placement.
The documented remedy is to move components into `as_<project>` cartridges, which is a large piece of
work and needs naming decisions before it can even start.

### The Guice form

```java
package com.example.project.internal.modules;

import com.intershop.beehive.core.capi.app.CartridgeListModule;

public class ProjectCartridgeListModule extends CartridgeListModule
{
    @Override
    protected void configure()
    {
        addCartridge("intershop.REST.Cartridges", "my_cartridge");
        addCartridge("intershop.EnterpriseBackoffice.Cartridges", "my_other_cartridge");
    }
}
```

registered in that cartridge's `resources/<cartridge>/objectgraph/objectgraph.properties`:

```properties
global.modules = com.example.project.internal.modules.ProjectCartridgeListModule
```

`global.modules`, not `global.overrideModules`: this contributes to a set, it overrides nothing.

### Why it is equivalent, and why it cannot have the same failure

Worth verifying once in the platform sources rather than taking on trust, because it is the whole
argument:

- `CartridgeListModule.addCartridge(listName, cartridgeName)` does
  `Multibinder.newSetBinder(binder(), Key.get(String.class).withAnnotation(Names.named(listName)))`
  and adds the cartridge name.
- `CartridgeListProviderImpl` then does
  `injector.getInstance(Key.get(new TypeLiteral<Set<String>>(){}, Names.named(name)))` and calls
  `forEach(this::addSelectedCartridge)`, **the same method the component fulfillment reaches**.

`Multibinder.newSetBinder` is idempotent, so any number of modules may contribute to one list and none
needs a declaration to exist first. The lookup is wrapped in a `catch(ConfigurationException)` that
logs at debug, so a list nobody contributes to is a non-event.

### Practical notes

- **One central module or one per cartridge.** ICM's own uses are per-cartridge (`CoreTestListModule`
  and three others, all test or benchmark cartridges). A single central module in a cartridge that is
  always deployed is usually cheaper on a migration: resource-only cartridges then need no Java, and
  the whole mapping is reviewable in one file. Per-cartridge is better if cartridges move between
  products independently.
- **A resource-only cartridge can still host a module.** These cartridges apply the `java` plugin
  already, which is why `compileJava` shows as `NO-SOURCE` rather than being absent, so adding one
  class needs no build changes.
- **`addSelectedCartridge` throws** outside test mode if the named cartridge does not exist, so a
  central list that falls out of step with the product fails startup loudly. `addOptionalCartridge` is
  the tolerant variant.
- **This covers cartridge-list registration only.** Other component content, `namedObject`
  fulfillments and the like, has no Guice equivalent and stays as XML. Convert what has an
  alternative; leave the rest.

## Changing the cartridge set means removing the container

The cartridge set is fixed when the AS container is created, so rebuilding alone does not change it.
Add, rename or remove a cartridge and the next `startServer` reuses the old set, failing inside the
container at JVM startup with

```
IllegalStateException: Could not find cartridge.descriptor of cartridge '<name>'
```

raised from `ClasspathComputer.getClasspathURLs` via `Cartridge.getDependsOn`. The name can be a
cartridge that no longer exists anywhere in the build, which sends you hunting through descriptors and
build output that are all perfectly correct. Run `removeServer` first, then `startServer`.

The tell that it is stale container state rather than a real build problem: the stack trace begins at
`java.lang.ClassLoader.initSystemClassLoader`, so it is the runtime classloader, not Gradle.

## Patching platform classes

Sometimes a project ships classes under the platform's own fully-qualified names to change behaviour.
This is **not a supported ICM feature**. ICM's override-by-cartridge-order concept
(`core/.../override/OverrideHierarchy`) covers "templates, pipelines, queries" and not classes. Class
shadowing rides on classpath URL order, which is deterministic and inspectable but not a promised
contract. Prefer re-implementing the intent as ordinary customization; the patched bodies are usually
decompiled platform code around a handful of changed lines.

Where patches must be kept, this is the shape that works, and the reasoning matters more than the
recipe:

**A separate `<project>_patch` cartridge whose dependencies are all `implementation` and none
`cartridge`.**

- Only `cartridge (...)` declarations are written to `cartridge.dependsOn` in the generated
  `cartridge.descriptor`.
- `Container.visit` is depth-first **post-order**: it walks `getDependsOn()` and only then adds the
  container's own URLs.
- `ClasspathComputer` accumulates into a **`LinkedHashSet`**, so re-adding an existing URL neither
  duplicates it nor moves it: **first insertion wins**.
- `ClassLoaderFactory.create` passes that set into `IntershopURLClassLoader`, which resolves in order.

So every cartridge named in `dependsOn` contributes its URLs **before** the cartridge that names it. A
patch cartridge that declares `cartridge ("com.intershop.business:app_sf_rest_basket")` loses to the
very cartridge it means to override. With an empty `dependsOn` it contributes its classes earlier.

Three things that are easy to miss:

1. **A root `subprojects` block that injects `cartridge (...)` dependencies into every project will
   inject them here too.** Exclude the patch cartridge by name, or its descriptor is not empty.
2. **`implementation` keeps the compile classpath unchanged**, so the sources still compile; the
   cartridge artifacts are classified separately from libraries and disappear from the descriptor
   entirely rather than moving into `dependsOnLibs`. Confirm that: `dependsOnLibs` should contain plain
   libraries only, no `com.intershop.platform:*` or `com.intershop.business:*`.
3. **Adding a `cartridge (...)` line later silently disables the patch.** It compiles, deploys and
   overrides nothing. Put a comment in the build script saying so.

**Verify, do not assume.** An empty `dependsOn` is necessary but not sufficient: the patch cartridge
must also be *visited* before the cartridges it shadows, and `dependsOn` is written alphabetically
rather than in declaration order. `ClasspathComputer.printClasspathURLs(PrintStream)` prints the
ordered list from the same computer the runtime uses, so the first occurrence of each jar there is
exactly what the classloader will pick.

**Prebuilt JARs are not an alternative.** `staticfiles/local/lib` is not a mechanism in ICM 14: no
platform cartridge uses such a path and nothing in the runtime classpath code references it. A 7.10
project shipping patches that way needs them rehomed or dropped, and the JARs will simply stop being
loaded either way.

### Empty `dependsOn` is only half of it. Position in the root cartridge list is the other half

An empty `dependsOn` stops the patch cartridge being dragged *behind* the cartridges it shadows. It does
not by itself put it *ahead* of them. A cartridge nothing depends on is visited **last**, because
`Container.visit` is post-order, so on its own the patch loses anyway. It was measured in one project at
classpath line 1160 against targets at 1013 and 1017: present, compiled, deployed, and completely inert.

The lever is the **root cartridge list**, which is walked in the order given, unlike `cartridge.dependsOn`
which the descriptor writer sorts alphabetically. Put the patch cartridge first:

```kotlin
val cartridgeSet = mutableSetOf("<project>_patch", "ft_production", "ft_icm_as")
```

fed to the supported hook that most projects already have:

```kotlin
intershop_docker { developmentConfig { cartridgeList.set(cartridgeSet) } }
```

`cartridgeList` is a public `SetProperty<String>` on `AbstractICMASContainerTask`, and
`createCartridgeList()` returns it unchanged. Check for this line before reaching for anything else:
an `icm.properties` passthrough and a `tasks.withType { }` override were both attempted in one project
and neither was ever needed.

Verify against the classpath dump rather than reasoning about it. `ORIGINAL CARTRIDGES` must begin with
the patch cartridge, and its jar must appear before the cartridges it shadows:

```
ORIGINAL CARTRIDGES: [<project>_patch, ft_production, ft_icm_as, ...]
RESOLVED CARTRIDGES: [<project>_patch, pf_common, pf_trace, runtime, ...]
```

A non-feature cartridge leading an otherwise all-`ft_*` list is accepted without complaint, and resolves
ahead of `pf_common`. That was the one part not predictable from reading the plugin.

**The half that is an operations hand-over, not a code change.** `cartridgeSet` usually configures
`developmentConfig` **only**. The deployed environments take their cartridge list from the deployment
configuration, Helm values or equivalent, which lives outside the repository. So "the patch cartridge is
in the production list" is not sufficient: **it has to be first**, in every environment, and if it is
merely present the patched behaviour is silently inert in exactly the environments where it matters.

Treat this as the most losable piece of knowledge in the migration. It is invisible in code review,
invisible at build time, invisible at startup, and observable only by reading the classpath dump or by
noticing the patched behaviour is gone. Record it in the root build script beside the list, in the patch
cartridge's own build script, and as an explicit hand-over item to whoever owns the deployment.
