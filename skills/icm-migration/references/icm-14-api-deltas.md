# ICM 14.x API deltas found by compiling a 7.10 customization

**Provenance: this file is ours, not Intershop's.** Unlike the vendor-copied references, every row
here was found by compiling a real 7.10 customization against ICM 14.4
(`version/14.4.0-90-gf2925f2c4a0`) and then confirmed against the shipped platform sources. Add to it
from every migration; it is the only reference covering the 13-to-14 step, for which upstream ships
neither a step set nor OpenRewrite recipes.

**How to use it.** Match on the compiler's own wording, which is the column you will have in front of
you. Then confirm against the platform sources before applying: these rows are dated, ICM moves, and a
row that was right at 14.4.0-90 can be wrong at the next patch level. Treat every entry as a lead.

**Expect these in waves, not all at once.** A cartridge that fails to *resolve* never compiles, so its
API errors stay invisible until the resolution error is fixed. On the project this came from, five
consecutive compile runs each surfaced a new class of error, with actionable task counts climbing
102, 137, 146, 159, 162. A new class after a fix is progress, not regression.

---

## 1. Dependency and resolution deltas

These surface as `package ... does not exist`, `Could not find <coordinate>`, or `cannot access <X> /
class file for <Y> not found`. None of them is a source bug.

| Symptom | Cause | Fix |
|---|---|---|
| `package javax.inject does not exist` | ICM 14.x does not ship the `javax.inject` namespace at all | `jakarta.inject`. The tool's 065 package map and 040 dependency map both miss it, so it is left on the old namespace silently. Occurrences are almost always pure import lines, so verify that before a bulk edit |
| `Could not find io.swagger.core.v3:swagger-jaxrs2` | The javax-flavoured swagger artifacts are gone | The `-jakarta` variants: `swagger-jaxrs2-jakarta`, `swagger-core-jakarta`, `swagger-models-jakarta`, `swagger-annotations-jakarta`. 14.4 resolves 2.2.50. Step 040's rename map covers only `swagger-annotations` |
| `Could not find com.google.inject.extensions:guice-multibindings` | The artifact ceased to exist when multibindings moved into Guice core in 4.2; 14.4 ships Guice 7.0.0 | Delete the declaration. Step 045's removal list omits it |
| `cannot access Cache / class file for com.github.benmanes.caffeine.cache.Cache not found`, in a file that imports ICM's own `Cache` | `com.intershop.beehive.cache.capi.Cache` **extends** `com.github.benmanes.caffeine.cache.Cache`, so `getIfPresent`, `put` and friends are inherited Caffeine methods and every consumer needs Caffeine on its compile classpath | Declare `com.github.ben-manes.caffeine:caffeine` (versionless; 14.4 constrains 3.2.4) in **every** cartridge that uses the ICM cache API, not only the one that failed |
| `Could not find com.auth0:java-jwt` with no version | ICM 14.4 constrains no `java-jwt`; it moved to `com.nimbusds:nimbus-jose-jwt` | The project now owns the version. Declare it in the project's own `versions` platform. See the version-stripping trap below |
| `cannot access Marker / class file for org.slf4j.Marker not found`, on a plain `Logger.error(this, ...)` | ICM's `com.intershop.beehive.core.capi.log.Logger` has slf4j types in its signatures; 7.10 put slf4j on the compile classpath transitively | `implementation("org.slf4j:slf4j-api")`, as 74 platform cartridges declare it. Sweep **every** project that imports the ICM `Logger` or `org.slf4j`, not the ones that failed: on one project 5 failed, 29 needed it |
| `Could not find com.intershop.platform:dev_query` | Renamed | `cartridge("com.intershop.platform:pf_query_testextension")` |
| `Could not find com.intershop.content:pmc_rest` | Renamed with the REST split | `cartridge("com.intershop.content:app_sf_rest_pmc")`; check first whether anything imports it, often it was a runtime cartridge only |
| `Could not find org.hamcrest:hamcrest:` (empty version) in a main configuration | 7.10 `compile 'org.hamcrest:...'` became `implementation`; the BOM constrains hamcrest for test scope only | Grep `src/main` for `org.hamcrest`. Usually nothing uses it: delete. Otherwise `testImplementation` |
| `Could not find com.google.code.findbugs:jsr305:` | No version in the 14 BOM | Delete when nothing imports `javax.annotation.Nonnull` and friends, which is the usual case |
| `cannot find symbol builder()` / missing getters on a Lombok class, without any Lombok error | `implementation("org.projectlombok:lombok")` puts Lombok on the classpath but Gradle runs only processors declared as `annotationProcessor` | `compileOnly` + `annotationProcessor`, and the `test*` pair. On JDK 21 use 1.18.30 or later (from the Lombok changelog; not verified on a project yet) |
| `package com.intershop.beehive.pipeline.capi.annotation does not exist` | Annotated pipelets (`@PipelineNode`) need the `pipeline` cartridge, and its processor writes the pipelet descriptors | `cartridge("com.intershop.platform:pipeline")` **and** `annotationProcessor("com.intershop.platform:pipeline")`; without the second it compiles and the pipelet is missing at runtime |
| `cannot access AbstractModule` in a class that imports no `com.google.inject` | The module extends ICM's `AbstractNamingModule`, which extends Guice | Declare guice. An import-driven sweep cannot see this shape: also grep `extends Abstract.*Module` |
| ISML-generated JSP fails: `package com.intershop.beehive.bts.capi.state does not exist`, `...mvc.capi.catalog`, `...order.capi` | Templates compile against declared dependencies only; 7.10 provided these cartridges transitively | Declare the cartridge (`bts`, `bc_mvc`, `bc_order` here). The error points into `build/generated/jsp`, so find the template from the class name (`OrderDetails_005f52_jsp` is `OrderDetails_52.isml`) |

**The version-stripping trap, worth understanding once.** Step 030 `ConvertBuildGradle` strips the
version from every dependency it converts. That is right for coordinates the ICM platform constrains
and wrong for third-party ones it does not, which then resolve to nothing. Before running it, list the
7.10 declarations that carry an explicit version; that list is exactly the set at risk, and it is
usually tiny. Anything on it that ICM 14.4 does not constrain must get its version back, in the
project's own platform rather than inline.

## 2. Platform API deltas

| Symptom | Cause | Fix |
|---|---|---|
| `FileUtils.getSharedDirectory()`, `getSystemSharedDirectory()`, `getClusterConfigResourceBundle(String, boolean)` gone | Genuinely removed, not renamed: `IS_SHARE` ceased to be a server directory | 7.10 `${IS_SHARE}/sites` is 14.x `IS_SITES`, reachable as `FileUtils.getSiteShareDirectory()`. Cluster configuration is `getClusterConfigDirectory()`. `icm.properties.example` is the mapping authority |
| ~10 `ProductBO` methods gone (`getProductPriceBO` overloads, `getPriceTierPriceBOs`, `getProductAttachments`, `getBundleInformation`, `getDefaultVariationProduct`, `isProductVisibleForUser`, `getInventoryStatus()` now needing a `String`, …) | Moved off the interface across 11 to 13 | **Do not hand-fix.** Intershop ships OpenRewrite recipes for exactly this in the 12-to-13 set. Hand-editing duplicates and probably contradicts them |
| `package com.intershop.sellside.rest.common.patch does not exist` | ICM dropped its custom `@PATCH` annotation because JAX-RS gained one | `jakarta.ws.rs.PATCH`, present from `jakarta.ws.rs-api` 3.1.0. Check first whether the import is even used; a removed PATCH endpoint often leaves a dead import |
| `CatalogCategoryBO.hasOnlineProducts(Domain)` gone | Moved to `CatalogCategoryBOProductExtension` (`bc_product`, `EXTENSION_ID = "Product"`) and lost its parameter: the extension derives the product domain itself | `categoryBO.<CatalogCategoryBOProductExtension>getExtension(CatalogCategoryBOProductExtension.EXTENSION_ID).hasOnlineProducts()`. `BusinessObject.getExtension` is generic, `<E> E getExtension(String)`, so a cast is redundant |
| `VariationHandler.getExtendedVariationAttributes(ProductBO)` and `createVariationAttributeRO(ProductBO, VariableVariationAttributeBO, Object)` do not override anything | Both gained a `VariationInformationBO` parameter (inserted **second** in `createVariationAttributeRO`) | Accept the parameter and take the hoist with it: ICM derives the value once outside the per-product loop instead of per product. Callers pass `product.isMastered() ? product.getProductBOMaster().getProductVariationInformationBO() : null`; the **guard is required**, since a product master has no master |
| `new PageableIteratorImpl<>(iterator, size)` fails, often reported misleadingly as `cannot infer type arguments` | The only constructor is `protected PageableIteratorImpl(Iterator<E>, int, int, String)`, in `...core.internal.paging`. Customer code is not meant to construct it | `PagingHelper.createPageable(iterator, count, pagesize)` (`bc_foundation`). It returns a raw `PageableIterator`, so expect an unchecked warning. Typed alternative without a new dependency: inject `com.intershop.beehive.core.capi.paging.PageableIteratorFactory` and call `create(iterator, total, pageSize)`, as `CatalogMgrImpl` does |
| `unreported exception EncryptionException; must be caught or declared to be thrown` on `encryptString` / `decryptString` | `com.intershop.common.encryption.capi.EncryptionManager` now throws the checked `...capi.exception.EncryptionException` | Keep 7.10 behaviour. A helper that caught `RuntimeException` to fall back catches both (`RuntimeException \| EncryptionException`); a pipelet wraps it in `PipeletExecutionException`, as ICM's `DecryptString` does |
| `method does not override` on `onDBInit()` in a `Cartridge` subclass | DBInit is gone; the hooks are `onInitHook`, `onStartupHook`, `onPreReadyHook`, `onReadyHook`, cache and shutdown hooks | Remove the override. If it registered something that DBPrepare needs, that is a runtime question: record it and check in Phase 6 |
| `package com.intershop.sellside.cxml.punchout... / ...sellside.rest.b2b.punchout... does not exist` | The cXML punchout API moved to `com.intershop.component.punchout.cxml` (`bc_punchout_cxml`), `v2` kept where it was; the setup REST resource moved to the new cartridge `app_sf_rest_b2b_punchout_cxml` | Package rename only, class names unchanged. `rest.b2b.punchout.v2.capi.cxml.CXMLPunchoutConstants` lost its `v2`: `component.punchout.cxml.capi.CXMLPunchoutConstants`. Locate each class by name in the platform sources before mapping. Step 065 maps the five packages that are gone entirely; `v2.internal.cxml.mapper` is left to hand-mapping because its `session` subpackage stayed in the REST cartridge |
| `cannot find symbol getCXMLSetupRequestHandler()` in a subclass of `CXMLPunchoutSetupRequestResource` | The getter is gone; the base class injects `CXMLSetupRequestHandler` into a private field | Inject it in the subclass: `@Inject private CXMLSetupRequestHandler cXMLSetupRequestHandler;`, as such subclasses usually already do for `CXMLParser` and `CXMLSessionHandler` |
| `cannot find symbol getBasketNotFoundFeedbackResource()` on `BasketFeedbackHandler` | Removed; the whole interface is deprecated for removal since 12.1 | `feedbackHandler.getFeedbackResponseBuilder().withError(feedbackHandler.getFeedback(Response.Status.NOT_FOUND, BasketConstantsREST.ERROR_BASKET_NOT_FOUND)).build()`; the builder takes the 404 from the feedback |
| `incompatible types: Iterator<AttributeGroup> cannot be converted to Iterator<AttributeGroupPO>` from `ProductHandler.getAttributeGroups(Domain)` | The method is now typed with the capi interface `AttributeGroup`; 7.10 code that declared the internal `AttributeGroupPO` compiled only through the raw type | Declare `Iterator<AttributeGroup>` and drop the `internal.attributegroup.AttributeGroupPO` import |
| `cannot access HttpServletRequest` in a class that imports no servlet type | An inherited or called ICM signature exposes it | `implementation("jakarta.servlet:jakarta.servlet-api")`, as 40 platform cartridges declare it; also sweep the projects that import `jakarta.servlet` and compile only transitively |
| `package com.intershop.beehive.core.request.test does not exist` (`RequestRule`), `...component.service.test` (`ServiceConfigurationBORule`) | JUnit 4 rules replaced by JUnit 5 extensions in `pf_core_testextension` and `bc_service_testextension`: `RequestMockExtension`, `ServiceConfigurationBOExtension`, `ISMLTemplateExtension` | A test rewrite, not a rename. Decide the scope of the `*_test` cartridges first; parking them keeps the main ladder moving |
| `OIDCProviderConfigurationImpl` constructor "cannot be applied to given types" | 14.4 inserted `Instant clientSecretExpiration` as the **fifth** of seven parameters, backing a new `Optional<Instant> getClientSecretExpiration()` | Derive it as ICM does, from the `client_secret_expire_at` property of the provider JSON, rather than passing null. See "adopt the shape, do not null it out" in the method reference |

## 3. Migration-tool blind spots that produce silent gaps

Not API deltas, but the same failure shape: nothing errors, and the code quietly does less than it did.

**Step 050 wires only what it converts.** It renames `config/**/*.resource` to `*.properties` and
generates `configuration.xml` from the files it converted. A `.properties` file that was already sitting
in `config/` before the migration is neither converted nor wired, so it ends up referenced by nothing
and read by nothing. On the project this came from, 23 of 179 `config/domains` files were in that state,
holding live platform properties (`intershop.WebServerURL`, `intershop.file.analyzer.*`). Any 7.10
project that hand-placed configuration alongside the framework's own will have some.

Check it directly, per cartridge, rather than trusting the generated file:

```bash
find . -path '*config/domains*' -type f | wc -l          # files shipped
grep -c '<set ' <cartridge>/.../config/configuration.xml  # entries generated
```

The counts will not match exactly, because one `<set>` with `${environment}` and
`${staging.system.type}` placeholders covers several concrete files. What matters is the **set of
file-name suffixes**: list the suffixes present on disk, list the ones named in `configuration.xml`, and
investigate every suffix that appears only on disk.

**A file's git history tells you which step touched it**, which is the quickest way to separate these.
A file last modified by the `.resource` conversion step was part of the framework's content; one last
modified by the plain move step was already `.properties` and was skipped. That distinction is usually
deliberate on the original author's part, so do not assume the odd ones out are dead: check the content
for real property keys before proposing to delete anything.

## 3b. Configuration deltas that only a running server reveals

No compiler sees these, and the symptom is a warning at startup or nothing at all.

| Symptom | Cause | Fix |
|---|---|---|
| `Cache configuration key 'intershop.caches.<name>.guava.config' using Guava has been removed in 12.0.0, please use Caffeine configuration instead.` | ICM 12 replaced Guava caches with Caffeine and renamed the key (`CacheBinder.java:171-172`) | `intershop.caches.<name>.guava.config` to `intershop.caches.<name>.caffeine.config`. **Also strip spaces around `=` in the value**: while the key is wrong the value is never parsed, and renaming it hands the value to `CaffeineSpec.parse` for the first time, so `expireAfterWrite = 24h` becomes `expireAfterWrite=24h` |

The second column is the reason to care: until the key is fixed the cache silently uses ICM's defaults
(`maximumSize` 2000, `initialCapacity` 100) rather than the tuning the project intended. Any 7.10
project that tuned a cache has these.

## 4. Deprecations that are not yet errors

Compiling with `--warning-mode=none` hides these, so look for them once before declaring a cartridge
done. At 14.4, `com.intershop.sellside.rest.common.capi.RestException` has its constructors,
`status(int)`, `badRequest()`, `message(String)` and the `ERROR_STATUS_*` constants all deprecated and
**marked for removal**. Code that uses them compiles today and will not in a future release, so a
migration is the cheap moment to fix it.

Also worth a look rather than a fix: `unknown enum constant XmlAccessType.NONE / class file for
jakarta.xml.bind.annotation.XmlAccessType not found` means javac is reading JAXB annotations off a
dependency without `jakarta.xml.bind-api` on that cartridge's compile classpath. Harmless at compile
time; a candidate `NoClassDefFoundError` at runtime.
