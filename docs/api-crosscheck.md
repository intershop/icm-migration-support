# API cross-check: OpenRewrite recipes for 7.10 to current

This replaces the former `migration-11-12.md` and `migration-12-13.md`.

## Why this is no longer a migration step

ICM 11, 12 and 13 are not deployment targets any more, so nobody migrates *to* them. There is one
route: **7.10 straight to the current release**.

On that route the compiler surfaces the union of every intermediate API delta at once, so by the time
the project compiles green against the target, these recipes have nothing left to change. Running them
is therefore a **cross-check, not a migration step**:

- **no change** confirms the compiler-driven fixes landed where Intershop's own recipes would have put
  them;
- **any change** is a finding, and the diff says where the project was hand-fixed differently from the
  way Intershop intends.

Run them version by version only if the project genuinely has to deploy at an intermediate release,
which is now rare. The former per-version step folders are in git history if that case ever comes up.

**Read the diff before committing it.** These recipes match on signature and cannot tell that a call
site is already correct, so on already-migrated code they can rewrite something that was right.

## What survived the flattening

The recipe *content* keeps full value once the version framing is dropped, because a rename that
happened in ICM 12 is still a rename between 7.10 and the current release. Both sets are merged into
one file, `src/main/resources/gradle/rewrite-7x10-to-current.yml`, under a single aggregate recipe
`com.intershop.migration.MigrationFrom7x10`.

Provenance is preserved in the sub-recipe names (`com.intershop.migration.icm12.*`,
`...icm13.*`), so per-version sets can be reconstructed if a 14-to-N upgrade path is ever wanted.

The same mappings are also available as queryable data, flattened across the step definitions and the
recipes, with a `since` tag per entry:

```
tools/build_rename_corpus.py            # regenerate corpus/rename-corpus.json
tools/build_rename_corpus.py --check    # CI: fail if the corpus has fallen behind
```

## Running it

Automated step, which only copies the configuration into the project root:

```
gradlew migration:migrateAll -Ptarget=$ICM -Psteps=src/main/resources/migration/002_api_crosscheck
```

Then, in the project:

```
gradlew --init-script rewrite.gradle rewriteRun
```

> **Note:** OpenRewrite needs a lot of heap. Set `GRADLE_OPTS=-Xmx4G` (or higher) before running it.

## Recipes in the set

Generic, from the OpenRewrite catalogue:

- Migration to Jakarta EE 10
- Migration to Java 21
- Migration to Gradle 8

Intershop, originally the ICM 12 set:

- Migration of custom `SAXParserPool`
- Migration of `EncryptionManager`. **Adjust the thrown exceptions in your custom code**
- Migration of `ProductListResource`
- Migration of ProcessChain XSD
- Migration of a custom `JobMgr` implementation. Adds implementations for
  `setEnableJobProcessors(Collection<String>)` and
  `createJobCrontabTimeCondition(Domain, Date, String)`. **You must still implement
  `isJobAllowedOnServer(ServerInfo, JobConfiguration)` yourself**

Intershop, originally the ICM 13 set:

- `EmailSendingHandler#send()` to the new signature
- `BitSetDeserializer` / `BitSetSerializer` to the new package
- `OrderListResource#getOrders_v1` to the new signature
- `BasketFeedbackHandler` to `FeedbackHandler`
- `EXTENSION_ID` constant of `CatalogBORepositoryExtension`
- Deprecated `ProductVariationMgr`, `MVCatalogMgr` and `ProductBOAttachmentsExtension` methods to their
  replacements
- `ProductBORepository` usages, `ProductBO` method names
- `ProductConfigurationValidatorValueListBO` method names
- `AbstractProductConfigurationBO`, `AbstractProductConfigurationOptionSelectionBO` and
  `ProductPreConfigurationBORepository` usages
- `NumberSeriesProvider` to `NumberSequenceProvider`
- `ChannelBO#getOwnedRepository` to `getOwnedRepositoryBO`

## Manual residue no recipe covers

**ISML expression adaption.** The ISML expression logic had an evaluation failure when a conditional
value was undefined. Fixed in ICM 12, but it requires adapting the ISML expressions in project code.
See [Guide - 12.x.x API Changes](https://knowledge.intershop.com/kb/index.php/Display/312H13).
