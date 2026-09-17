# AI Agent Migration & Project Guide (Updated Mirror)

## 1. Scope & Audience
Applies to every cartridge. Ignore generated/build output (`bin/`, `build/`, `target/`). Focus only on source (`src/main/**`, `staticfiles/**`, legacy resources) and Gradle build scripts.

## 2. Target Post-Migration Structure (per cartridge)
```
<cartridge>/
  build.gradle.kts
  staticfiles/cartridge/configdef/
  src/main/java/...
  src/main/isml/<cartridge>/...
  src/main/resources/cartridges/...
  src/main/resources/resources/<cartridge>/
    components
    config
    dbinit.properties
    edl (ALL *.edl flattened here – no subfolders)
    extensions
    localizations
    pagelets
    pipeline (pipelets.resource only, and it is GENERATED - see 9b)
    pipelines (legacy only if still referenced)
    queries
    static
    webforms
```
Avoid alternative layouts like `src/main/resources/edl/<cartridge>` or `src/main/resources/<cartridge>/resources`.

## 3. High-Level Migration Sequence
1. Convert `build.gradle` → `build.gradle.kts`.
2. Replace dependency configurations & perform Jakarta namespace replacement.
3. Move resources (staticfiles, EDL, queries, ISML, dbinit, pipelet descriptors)
4. The resources `staticfiles/cartridge/configdef/*` should not be moved
5. Move all `*.properties` files from `staticfiles/share/system/config/cartridges/` to `src/main/resources/cartridges/`
6. Adjust Java imports `javax.*` → `jakarta.*` (except `javax.annotation.processing`).
7. Add required annotation processors and supplemental Jakarta dependencies.
8. Remove obsolete files & legacy folders.

## 4. Build Script Migration Essentials
Plugins mapping:
| Old | New (Kotlin DSL) |
|-----|------------------|
| java-cartridge | `java` |
| static-cartridge | `id("com.intershop.icm.cartridge.product")` |
| test-cartridge | `id("com.intershop.icm.cartridge.test")` |
| com.intershop.gradle.cartridge-resourcelist | `id("com.intershop.gradle.cartridge-resourcelist")` |
| com.intershop.gradle.isml | `id("com.intershop.gradle.isml")` |

Dependencies rule of thumb:
| Purpose | Configuration |
|---------|---------------|
| Intershop cartridge (compile) | `cartridge("group:artifact")` or `cartridge(project(":x"))` |
| Intershop cartridge runtime-only | `cartridgeRuntime(...)` |
| Third-party library | `implementation("g:a:v")` |
| Annotation processing | `annotationProcessor("com.intershop.platform:...")` |

Add/replace Jakarta variants per mapping (javax.* → jakarta.*) and exclude transitive `slf4j-api` where swagger pulls 2.x.

Some dependencies have to be renamed. 
| Old | New |
|-----|-----|
| com.intershop.business:app_sf_rest_b2c | com.intershop.business:app_sf_rest_customer |
| com.intershop.business:app_sf_rest_b2c_test | com.intershop.business:app_sf_rest_customer_test |
| com.intershop.business:app_sf_rest_smb | com.intershop.business:app_sf_rest_customer |
| com.intershop.business:app_sf_rest_smb_test | com.intershop.business:app_sf_rest_customer_test |

Do not duplicate with both `cartridge` and `cartridgeRuntime` for the same coordinate.

Move `displayname` inside of section `intershop` to `description` on top-level below `plugins`.

## 5. Resource & Asset Migration Highlights
Move from `staticfiles/cartridge/...` into `src/main/resources/resources/<cartridge>/...` and ISML to `src/main/isml/<cartridge>/...`.
Flatten all `.edl` files into `.../edl` (no subfolders). Remove non-empty legacy directories.

## 6. Pipelets
A cartridge that owns pipelets must apply `id("com.intershop.gradle.cartridge-resourcelist")`, which
generates `resources/<cartridge>/pipeline/pipelets.resource`. Missing it means every pipelet in the
cartridge is unresolvable at runtime. See 9b for the mechanism, the error text and the audit.

**Do not move the pipelet descriptor XMLs.** They belong next to the class, on the classpath at
`src/main/resources/<package path>/<Pipelet>.xml`, which is where 7.10 already put them and where
ICM 14 keeps its own. Verified against the platform: `bc_punchout` ships
`src/main/resources/com/intershop/component/punchout/pipelet/CreateOCIFormatHelper.xml`, and there
are **zero** `pipeline/pipelets/` directories anywhere in the ICM 14.4 source tree. An earlier
revision of this file called for `pipeline/pipelets/*.xml`; that is wrong and would break working
cartridges.

## 7. Jakarta Migration Reminders
Regex safe replace for imports:
```
find: ^import javax\.(?!annotation\.processing)(.*);
replace: import jakarta.$1;
```
Do not touch `javax.annotation.processing`.

## 8. New Edge Cases & Resolutions
| Case | Symptom | Resolution |
|------|---------|------------|
| Outdated AuthorizationService instance name | `ComponentConfigurationException` referencing `intershop.B2CWebShop.RESTAPI.AuthorizationService` | Update fulfill to `intershop.WebShop.RESTAPI.AuthorizationService` and rebuild |
| Duplicate Prometheus collectors | Guice errors: Collector already in use (`SessionMetricCollector`, `JDBCConnectionMetricsCollector`) | `./gradlew stopAS` then fresh `startAS`; avoid custom manual collector registrations |
| Legacy preparedStuff component duplicates | Edits ignored or old fulfills persist | Delete obsolete `staticfiles/preparedStuff/*.component` after migrating components |
| Transitive slf4j drift | Logging binding / ClassCast / NOP logger | Exclude `slf4j-api` from swagger Jakarta deps |
| Oracle dialect/processor left | Query load/parse failure | Replace with Microsoft/JDBC |
| Eager injected usage | `NullPointerException` in preparer field init | Lazy initialization inside method / constructor inject |
| Missing pipelets.resource | `Could not compute pipelet-class-name`, pipeline runs with a placeholder | Apply `cartridge-resourcelist` to that cartridge and rebuild; the file is generated, never hand-written (9b) |
| EDL left nested | Runtime warnings / missing definitions | Flatten all `.edl` at `.../edl` |

## 9. REST Components Naming Change
Modern platform instances use the `intershop.WebShop.RESTAPI.*` naming. Any legacy component fulfill referencing `intershop.B2CWebShop.*` or `intershop.B2BWebShop.*` must be updated.

Additionally some other renamings have to be done in `*.component` files:
| Old | New |
|-----|-----|
|intershop.B2BWebShop.RESTAPI.basket.v1.BasketQuoteListResource|intershop.WebShop.RESTAPI.BasketQuoteListResource|
|intershop.B2BWebShop.RESTAPI.b2c.CustomerListResource|intershop.WebShop.RESTAPI.PrivateCustomerListResource|
|com.intershop.sellside.rest.b2c.capi.resource.customer.PrivateCustomerItemResource|com.intershop.sellside.rest.b2c.capi.resource.customer.PrivateCustomerItemResource|

Also check all `*.java` files if the following pattern is used `@Named("intershop.WebShop.RESTAPI.*")`. This has to be adapted accordingly.

### `name=` matters too, and only a missed `of=` is loud

A stale `of=` fails the server start with `Can't find declaration of instance '...'`, so it gets
fixed. A stale `name=` fails **silently**, and whether it matters depends on what the declaration is
for:

- **The cartridge's own new resource** attached through `<fulfill requirement="subResource"
  of="intershop.WebShop.RESTAPI...">`: the `name=` is a free identifier. A `B2BWebShop` name here is
  cosmetic. Leave it; renaming is churn.
- **An override of a platform instance**: the `name=` *is* the join. Under the old namespace it
  matches nothing, the platform's own implementation stays in place, and nothing is logged. The
  customization is simply gone.

Telling them apart is mechanical: list the platform's instance names and intersect. If the platform
declares the name under `intershop.WebShop.*`, the declaration was an override and must be renamed.

```sh
find /icm-as -name '*.component' | grep -v '/build/' | grep -v '/bin/' > comp.list
xargs grep -ho 'name="intershop\.WebShop\.[^"]*"' < comp.list | sort -u
```

Two tells that a declaration is an intended override, both seen in one project: the `with=` class is
the platform's class name with a suffix (`SMBCustomerUserROValidatorImpl` becomes
`SMBCustomerUserROValidatorImpl2`), or it is the platform's name with a vendor prefix
(`TokenAuthenticationProvider` becomes `AcmeTokenAuthenticationProvider`). Three overrides
(`AuthProvider`, `SMBCustomerUserValidator`, `PunchoutHandler`) had been inactive since the
namespace rename, including the project's custom REST authentication.

### Verifying overrides at runtime

The server logs a WARN for every instance that is actually replaced:

```
Instance intershop.WebShop.RESTAPI.ProductResource is replaced via resource
location: file:/.../acme_search_rest/.../components/productresource.component.
```

Grep the startup log for `is replaced via resource` and diff that list against the overrides the
project declares. Anything declared but absent from the log did not take effect. This is the only
positive confirmation available; the failure mode produces no message at all.

## 9b. Generated resource lists: pipelets, ORM and templates

ICM 11+ finds several kinds of cartridge content through a **generated index file**, not by scanning
the classpath. If the cartridge does not apply the plugin that generates its index, the content is
invisible at runtime while the build stays perfectly green.

| content | index file | generated by |
|---|---|---|
| pipelets | `resources/<cartridge>/pipeline/pipelets.resource` | `id("com.intershop.gradle.cartridge-resourcelist")` |
| ORM classes | `resources/<cartridge>/orm.resource` | same plugin |
| ISML templates | `resources/<cartridge>/isml/isml.resource` | `id("com.intershop.gradle.isml")` |
| pipeline nodes | `resources/<cartridge>/pipeline/pipelinenodes.resource` | `annotationProcessor(...)`, see 9c |

All land under `build/generated/resourcelist/<kind>/`, so the ISML plugin drives the same machinery.
**A cartridge that ships pipelets or templates must apply the matching plugin.** Both, if it ships both:

```kotlin
plugins {
    id("com.intershop.gradle.cartridge-resourcelist")
    id("com.intershop.gradle.isml")
    id("com.intershop.icm.cartridge.product")
    java
}
```

The pipelet case is the one that bites, because the error is indirect.
`PipeletClassNameProviderImpl` (`platform/core`) reads `pipelets.resource`, a plain list of fully
qualified class names, one per line with `#` for comments, and derives the pipelet name from the
class's simple name. No list means an empty map and every pipelet in the cartridge unresolvable:

```
Unable to load pipelet PerformSuggestQuery
PipelineException: Could not compute pipelet-class-name for acme_search_rest:PerformSuggestQuery
Pipeline 'ProcessSearchBySearchIndex' (Cartridge acme_search_rest) : Pipelet 'PerformSuggestQuery'
  could not be loaded, inserting placeholder instead
```

"Inserting placeholder instead" means the pipeline keeps running with a hole in it, so the visible
symptom is a feature quietly doing nothing rather than an error page.

Audit every cartridge; the tool applies these plugins unevenly. Detect content by location rather
than by base class, so subclasses of an intermediate base are not missed:

```sh
find . -path '*/src/main/java/*/pipelet*/*.java' -not -path '*/build/*' \
  | sed -E 's|^\./([a-z_0-9]+)/.*|\1|' | sort -u        # cartridges owning pipelets
find . -name '*.isml' -not -path '*/build/*' \
  | sed -E 's|^\./([a-z_0-9]+)/.*|\1|' | sort -u        # cartridges owning templates
grep -rln 'cartridge-resourcelist' --include=build.gradle.kts .
grep -rln 'gradle.isml'            --include=build.gradle.kts .
```

In one project 10 cartridges had the resourcelist plugin and exactly one cartridge owning a pipelet
did not, which was the one that failed; all four template cartridges were already correct.

**Do not move the pipelet descriptor XMLs.** They belong next to the class, on the classpath at
`src/main/resources/<package path>/<Pipelet>.xml`, which is where 7.10 already puts them and where
ICM 14 keeps its own. Verified against the platform: `bc_punchout` ships
`src/main/resources/com/intershop/component/punchout/pipelet/CreateOCIFormatHelper.xml`, and there
are **zero** `pipeline/pipelets/` directories anywhere in the ICM 14.4 source tree. An earlier
revision of this file called for `pipeline/pipelets/*.xml`; that is wrong and would break working
cartridges.

## 9c. Prefer pipeline nodes over pipelets for new code

`Pipelet` is the 7.10 form: extend a base class, hand-maintain an XML descriptor beside it, and get
indexed through `pipelets.resource`. ICM has a better mechanism, and new or reworked code should use
it instead.

A **pipeline node** is a plain class with annotations. No base class, no hand-written descriptor. The
platform's own `com.intershop.beehive.core.capi.request.Redirect` is the reference example:

```java
@PipelineNode(type = Types.EndNode,
              attributes = { @Attribute(name = EndNodeTypeConstants.ATTRIBUTE_NAME,
                                        value = EndNodeTypeConstants.INTERACTION_NODE) })
public class Redirect
{
    public final static String INPUT_NAME = "Input";

    @PipelineNodeInput(name = INPUT_NAME)
    public void execute(Input input) throws Exception { ... }

    interface Input
    {
        URI getRedirectURL();
    }
}
```

Inputs and outputs are declared as interfaces with getters, so the pipeline dictionary binding is
typed and the compiler checks it. That is the main argument over pipelets, whose dictionary keys are
strings resolved at runtime.

The annotations live in `com.intershop.beehive.pipeline.capi.annotation`: `PipelineNode`,
`PipelineNodeInput`, `PipelineNodeOutput`, `PipelineNodeParameter`, `Attribute`, `Description`,
`DefinedBy`. `@PipelineNode` takes `name`, `type` (`Types.StartNode`, `Types.EndNode` or the default
`Types.Node`), `transactional` and `attributes`.

It needs an annotation processor. In the platform's own build that is
`annotationProcessor(project(":platform:pipeline"))`; a customization uses the coordinate instead:

```kotlin
dependencies {
    annotationProcessor("com.intershop.platform:pipeline")
}
```

The processor generates `resources/<cartridge>/pipeline/pipelinenodes.resource` plus a
`<package path>/<Class>.pipelinenode` descriptor per node, which is why no XML is hand-maintained and
why the index cannot drift from the code the way `pipelets.resource` can.

Converting existing pipelets is a **nice to have, not a migration step**. Migration ends with a
working server; this is maintenance and belongs after that. The rule is for new code and for anything
being reworked anyway.

## 10. Change Registration of cartridges to application-specific cartridge lists
In case a cartridge contains files `app-extension.component` or `apps-extension.component` the content of this file must be splitted up and moved to different `apps.component` files in different `as_` cartridges.
The location for the `apps.component` is `src/main/resources/resources/<cartridge>/components`.

All lines of `app-extension.component` or `apps-extension.component` containing `<fulfill requirement="selectedCartridge"` have to be moved. The used cartridge-list (see table) determines to which cartridge the line has to be moved.
Before a line can be moved it has to be checked if there's is already a cartridge with the cartridge prefix from table. If the cartridge does not exist it must be created.
Its name is the prefix from the table `cartridge prefix` concatenated with a project name which the user of this script should enter. 
When a line is moved, the cartridge must be added as runtime dependency in the `build.gradle.kts` of the `as_` cartridge.

| cartridge-list | cartridge prefix|
|----------------|-----------------|
|intershop.REST.Cartridges|as_headless_|
|intershop.EnterpriseBackoffice.Cartridges|as_backoffice_|
|intershop.SLDSystem.Cartridges|as_sldsystem_|
|intershop.SMC.Cartridges|as_smc_|

## 11. Dependency Configuration Clarification

| Need | Use | Notes |
|------|-----|-------|
| Compile & runtime access to another cartridge | `cartridge(project(":other"))` | Replaces old `compile project()` |
| Runtime-only presence of another cartridge (assembly style) | `cartridgeRuntime(project(":other"))` | Only if you truly do NOT compile against its code |
| Intershop provided cartridge (group starts with `com.intershop.`) | `cartridge("com.intershop...:artifact")` or `cartridgeRuntime(...)` | Same rule as above re: compile vs runtime |
| Third-party library | `implementation("group:artifact:version")` | Keep versions centralized if possible |
| Annotation processors | `annotationProcessor("com.intershop.platform:...")` | Only when required annotations present |

## 12. Cleanup Checklist
* Delete obsolete `*.version` files in root directory
* Delete legacy `build.gradle`
* Delete `staticfiles/cartridge/*` except `configdef`
* Delete all `app-extension.component` and `apps-extension.component` files that do not contain any `fulfill` statements anymore
* Remove `staticfiles/preparedStuff` component duplicates post-validation.
* Remove unused empty legacy resource dirs (after verifying classpath).
* Ensure no stray `javax.` imports (except processing).

