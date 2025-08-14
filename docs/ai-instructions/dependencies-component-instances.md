# Component Instances

This document provides a comprehensive list of all ApplicationType component instances defined in the ICM workspace.

## REST API Instances

The following table lists all REST related instances:

| ApplicationType Instance                 | Cartridge                   | Category   | Dependency (icm-as)                                                          | Dependency (external)                                                |
|------------------------------------------|-----------------------------|------------|------------------------------------------------------------------------------|----------------------------------------------------------------------|
| `intershop.REST`                         | `app_sf_headless`           | Storefront | cartridgeRuntime(project(":business:a_storefront:app_sf_headless")           | cartridgeRuntime("com.intershop.business:app_sf_headless")           |
| `intershop.EnterpriseBackoffice.RESTAPI` | `sld_enterprise_app`        | Backoffice Enterprise | cartridgeRuntime(project(":business:a_backoffice:sld_enterprise_app")        | cartridgeRuntime("com.intershop.business:sld_enterprise_app")        |
| `intershop.CC`                           | `app_sf_contactcenter_rest` | Backoffice CC | cartridgeRuntime(project(":business:a_backoffice:app_sf_contactcenter_rest") | cartridgeRuntime("com.intershop.business:app_sf_contactcenter_rest") |

## ApplicationType Instances

For ICM application extensions, the following ApplicationType instances are defined:

| ApplicationType Instance         | Cartridge                | Category   | Dependency (icm-as)                                                       | Dependency (external)                                             |
|----------------------------------|--------------------------|------------|---------------------------------------------------------------------------|-------------------------------------------------------------------|
| `intershop.B2CBackoffice`        | `sld_ch_consumer_plugin` | Backoffice Sales Channel | cartridgeRuntime(project(":business:a_backoffice:sld_ch_consumer_plugin") | cartridgeRuntime("com.intershop.business:sld_ch_consumer_plugin") |
| `intershop.PartnerBackoffice`    | `sld_ch_partner_plugin`  | Backoffice Partner | cartridgeRuntime(project(":business:a_backoffice:sld_ch_partner_plugin")  | cartridgeRuntime("com.intershop.business:sld_ch_partner_plugin")  |
| `intershop.EnterpriseBackoffice` | `sld_enterprise_app`     | Backoffice Enterprise | cartridgeRuntime(project(":business:a_backoffice:sld_enterprise_app")     | cartridgeRuntime("com.intershop.business:sld_enterprise_app")     |
| `intershop.SLDSystem`            | `sld_system_app`         | System Operations     | cartridgeRuntime(project(":business:a_sldsystem:sld_system_app")          | cartridgeRuntime("com.intershop.business:sld_system_app")         |
| `intershop.SMC`                  | `smc`                    | System SMC | cartridgeRuntime(project(":business:a_sldsystem:smc")                     | cartridgeRuntime("com.intershop.business:smc")                    |
| `intershop.System`               | `core`                   | Platform   | cartridgeRuntime(project(":platform:core")                                | cartridgeRuntime("com.intershop.platform:core")                   |
| `intershop.Internal`             | `core`                   | Platform   | cartridgeRuntime(project(":platform/core")                                |                                                                   |

## ApplicationType Test Instances

For testing purposes, the following ApplicationType instances are also defined (sub set):

| ApplicationType Instance            | Cartridge         | Dependency (icm-as)                                                |
|-------------------------------------|-------------------|--------------------------------------------------------------------|
| `intershop.enfinity.BcPreviewTests` | `bc_preview_test` | cartridgeRuntime(project(":business:a_backoffice:bc_preview_test") |
| `intershop.BusinessTestSF`          | `test_app_sf`     | cartridgeRuntime(project(":business/a_test/test_app_sf")           |
| `intershop.ConsumerTestSF`          | `test_app_sf`     | cartridgeRuntime(project(":business/a_test/test_app_sf")           |
| `intershop.ETest`                   | `etest`           | cartridgeRuntime(project(":platform/etest")                        |

## Fixing Dependencies - Instruction

### Rules for Component Instances and Dependencies

1. **Single ApplicationType Rule**: Each non-`as_` cartridge should only provide components for ONE ApplicationType instance.
2. **Dependency Matching**: A cartridge's dependencies must match its ApplicationType category (don't mix Storefront/Backoffice/System/Platform).
3. **Component Location Rule**: Components must be in the correct cartridge according to the ApplicationType mapping table above.

### Automated Fix Process

#### Step 1: Check Component Files

For each cartridge with `*.component` files:

1. Scan all `.component` files in `src/main/resources/resources/{cartridge}/components/`
2. Look for `<fulfill requirement="app" with="intershop.{ApplicationType}" />` patterns
3. List all ApplicationType instances found in the cartridge

#### Step 2: Validate Component Placement

- **Correct**: Components match the cartridge's designated ApplicationType from the table above
- **Incorrect**: Components belong to different ApplicationTypes than the cartridge's designation (see "Category" in the table above)
- **Action**: Move misplaced components to appropriate cartridge or `as_` cartridge

#### Step 3: Fix Misplaced Components

For components that don't belong in their current cartridge:

1. **Target exists**: Move component to the correct cartridge (e.g., `intershop.B2CBackoffice` → `sld_ch_consumer_plugin_<project>`)
2. **Target doesn't exist**: Move component to appropriate `as_` cartridge (e.g., `as_backoffice_<project>`)
3. **Update dependencies**: Ensure `as_` cartridge has `cartridgeRuntime` dependencies to all referenced cartridges

#### Step 4: Dependency Validation

For each cartridge that provides ApplicationType instances:

1. **Check `build.gradle.kts`**: Must have `cartridge()` or `cartridgeRuntime()` dependency matching the table above
2. **Dependency Type Rules**:
   - Use `cartridge()` if the cartridge needs compile-time access (like `implementation`)
   - Use `cartridgeRuntime()` only if runtime access is sufficient (like `runtimeOnly`)
   - **Important**: If `cartridge()` dependency exists, `cartridgeRuntime()` is redundant and should be removed
3. **Missing dependency**: Add the required external dependency (e.g., `cartridgeRuntime("com.intershop.business:sld_enterprise_app")`) if component files/content is moved.
4. **Extra dependencies**: Only remove if they're not project-specific or referenced in component files

### Examples

**Good**: `sld_enterprise_app_<project>` only contains components for `intershop.EnterpriseBackoffice`
**Bad**: `sld_enterprise_app_<project>` contains components for `intershop.B2CBackoffice` and `intershop.PartnerBackoffice`
**Fix**: Move `B2CBackoffice` and `PartnerBackoffice` components to `as_backoffice_<project>` or `as_<project>` if exists.

### Validation Commands

- Find all cartridges with component files: `find . -name "*.component" -path "*/components/*"`
- Check ApplicationType references: `grep -r "intershop\." --include="*.component" */components/`
- Verify dependencies: Check each cartridge's `build.gradle.kts` for required `cartridgeRuntime` entries

### AI Task - Instruction

- validate my cartridge dependencies and component files. see component-instances.md. Are there cartridges, with missing dependencies.
- create new as_<project> cartridges, if there are not exist.
  - add cartridgeRuntime dependencies at as_ cartridges
  - register new as_ cartridges at ft_ cartridge
 