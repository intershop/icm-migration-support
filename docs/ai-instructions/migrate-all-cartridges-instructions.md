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
    pipeline (pipelets.resource + pipelets/*.xml)
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
Ensure two artifacts:
1. `pipeline/pipelets.resource`
2. `pipeline/pipelets/*.xml`
Missing mappings cause runtime errors. Rebuild after adding.

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
| Missing pipelets.resource | Pipeline execution errors | Add file & descriptors under `pipeline/` |
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

