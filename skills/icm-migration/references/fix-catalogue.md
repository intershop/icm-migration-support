# ICM Cartridge Validation & Fix — Phase 4

## Role

You are an AI agent that validates and fixes Java classes in a cartridge. You analyze the **entire class** — all used classes, methods, annotations, and types — and ensure everything is properly imported and has the required dependencies. Your goal: **make the class compile and work 100%**.

---

## Inputs

| Parameter | Description |
|-----------|-------------|

| `[CARTRIDGE_PATH]` | Directory containing the cartridge |
| `[CARTRIDGE_NAME]` | Name of the cartridge |
| `[JAVA_CLASSES_LIST]` | List of paths to all Java classes to validate |

---

## Workflow

### Step 1: Read & Understand Dependencies

Read `[CARTRIDGE_PATH]/build.gradle.kts` and build a **dependency map**:

1. Extract ALL dependencies from the file
2. Understand what packages/classes each dependency provides
3. Store this map for validation

| Import Pattern of cartridge classes | Dependency |
|-------------------------------------|------------|

| `com.intershop.adapter.bmecat.*` | `cartridge("com.intershop.business:ac_bmecat")` |
| `com.intershop.adapter.captcha_recaptcha.*` | `cartridge("com.intershop.platform:ac_captcha_recaptcha")` |
| `com.intershop.adapter.cloudinary.*` | `cartridge("com.intershop.business:ac_cloudinary")` |
| `com.intershop.adapter.cxml.*` | `cartridge("com.intershop.business:ac_cxml")` |
| `com.intershop.adapter.cxml.orderinjection.*` | `cartridge("com.intershop.b2b:ac_cxml_order_injection")` |
| `com.intershop.adapter.email.marketing.standard.*` | `cartridge("com.intershop.business:ac_email_marketing_std")` |
| `com.intershop.adapter.mail.*` | `cartridge("com.intershop.platform:ac_mail")` |
| `com.intershop.adapter.messaging.*` | `cartridge("com.intershop.platform:ac_messaging")` |
| `com.intershop.adapter.oci.*` | `cartridge("com.intershop.business:ac_oci")` |
| `com.intershop.adapter.oidc.*` | `cartridge("com.intershop.platform:ac_oidc")` |
| `com.intershop.adapter.oidc.rest.*` | `cartridge("com.intershop.platform:ac_oidc_rest")` |
| `com.intershop.adapter.order.approval.*` | `cartridge("com.intershop.b2b:ac_order_approval")` |
| `com.intershop.adapter.orderexport.xml.*` | `cartridge("com.intershop.business:ac_order_export_xml")` |
| `com.intershop.adapter.orderhistory.service.*` | `cartridge("com.intershop.business:ac_order_history_service")` |
| `com.intershop.adapter.ordersubmission.service.*` | `cartridge("com.intershop.business:ac_order_submission_service")` |
| `com.intershop.adapter.priceservice.webhook.*` | `cartridge("com.intershop.business:ac_price_service_webhook")` |
| `com.intershop.adapter.productprice.service.*` | `cartridge("com.intershop.business:ac_productprice_service")` |
| `com.intershop.adapter.taxation.std.*` | `cartridge("com.intershop.business:ac_taxation_std")` |
| `com.intershop.application.backoffice.catalog.*` | `cartridge("com.intershop.business:app_bo_catalog")` |
| `com.intershop.application.backoffice.dashboard.*` | `cartridge("com.intershop.business:app_bo_dashboard")` |
| `com.intershop.application.backoffice.rest.batch.*` | `cartridge("com.intershop.business:app_bo_rest_job")` |
| `com.intershop.application.backoffice.webshop.b2b.*` | `cartridge("com.intershop.b2b:app_bo_ch_sales_b2b")` |
| `com.intershop.application.contactcenter.*` | `cartridge("com.intershop.business:app_sf_contactcenter_rest")` |
| `com.intershop.application.customer_segments.*` | `cartridge("com.intershop.business:app_bo_rest_customer_segments")` |
| `com.intershop.application.pmc.*` | `cartridge("com.intershop.content:app_bo_rest_pmc")` |
| `com.intershop.application.pmc.common.*` | `cartridge("com.intershop.content:app_bo_rest_pmc")` |
| `com.intershop.application.rest.mediaassets.v1.*` | `cartridge("com.intershop.business:app_bo_rest_mediaassets")` |
| `com.intershop.application.rest.pim.v1.*` | `cartridge("com.intershop.business:app_bo_rest_pim")` |
| `com.intershop.application.smc.rest.*` | `cartridge("com.intershop.business:app_bo_rest_job")` |
| `com.intershop.application.smc.rest.domain.*` | `cartridge("com.intershop.business:app_bo_rest_job")` |
| `com.intershop.application.smc.rest.job.*` | `cartridge("com.intershop.business:app_bo_rest_job")` |
| `com.intershop.application.storefront.*` | `cartridge("com.intershop.business:app_sf_headless")` |
| `com.intershop.application.storefront.rest.b2b.*` | `cartridge("com.intershop.b2b:app_sf_rest_b2b")` |
| `com.intershop.application.storefront.rest.b2b.*` | `cartridge("com.intershop.b2b:app_sf_rest_b2b_punchout")` |
| `com.intershop.backoffice.rest.b2b.customer.v1.*` | `cartridge("com.intershop.b2b:app_bo_rest_b2b_customer")` |
| `com.intershop.backoffice.rest.customer.v1.*` | `cartridge("com.intershop.business:app_bo_rest_customer")` |
| `com.intershop.beehive.app.*` | `cartridge("com.intershop.platform:app")` |
| `com.intershop.beehive.btc.*` | `cartridge("com.intershop.business:btc")` |
| `com.intershop.beehive.bts.*` | `cartridge("com.intershop.business:bts")` |
| `com.intershop.beehive.businessobject.*` | `cartridge("com.intershop.platform:businessobject")` |
| `com.intershop.beehive.cache.*` | `cartridge("com.intershop.platform:cache")` |
| `com.intershop.beehive.component.*` | `cartridge("com.intershop.platform:component")` |
| `com.intershop.beehive.configuration.*` | `cartridge("com.intershop.platform:configuration")` |
| `com.intershop.beehive.core.*` | `cartridge("com.intershop.platform:core")` |
| `com.intershop.beehive.core.*` | `cartridge("com.intershop.platform:orm_oracle")` |
| `com.intershop.beehive.core.dbinit.*` | `cartridge("com.intershop.platform:core")` |
| `com.intershop.beehive.emf.*` | `cartridge("com.intershop.platform:emf")` |
| `com.intershop.beehive.file.*` | `cartridge("com.intershop.platform:file")` |
| `com.intershop.beehive.isml.*` | `cartridge("com.intershop.platform:isml")` |
| `com.intershop.beehive.jmx.*` | `cartridge("com.intershop.platform:jmx")` |
| `com.intershop.beehive.monitor.*` | `cartridge("com.intershop.business:monitor")` |
| `com.intershop.beehive.objectgraph.guice.*` | `cartridge("com.intershop.platform:pf_objectgraph_guice")` |
| `com.intershop.beehive.orm.*` | `cartridge("com.intershop.platform:orm")` |
| `com.intershop.beehive.orm.mssql.*` | `cartridge("com.intershop.platform:orm_mssql")` |
| `com.intershop.beehive.orm.oracle.*` | `cartridge("com.intershop.platform:orm_oracle")` |
| `com.intershop.beehive.pipeline.*` | `cartridge("com.intershop.platform:pipeline")` |
| `com.intershop.beehive.report.*` | `cartridge("com.intershop.platform:report")` |
| `com.intershop.beehive.smc.*` | `cartridge("com.intershop.business:smc")` |
| `com.intershop.beehive.xcs.*` | `cartridge("com.intershop.business:xcs")` |
| `com.intershop.common.*` | `cartridge("com.intershop.platform:pf_common")` |
| `com.intershop.component.address.*` | `cartridge("com.intershop.platform:bc_address")` |
| `com.intershop.component.address.orm.*` | `cartridge("com.intershop.platform:bc_address_orm")` |
| `com.intershop.component.addresscheck.*` | `cartridge("com.intershop.business:bc_addresscheck")` |
| `com.intershop.component.application.*` | `cartridge("com.intershop.platform:bc_application")` |
| `com.intershop.component.approval.*` | `cartridge("com.intershop.b2b:bc_order_approval")` |
| `com.intershop.component.approval.*` | `cartridge("com.intershop.b2b:bc_order_approval_orm")` |
| `com.intershop.component.approval.*` | `cartridge("com.intershop.platform:bc_approval")` |
| `com.intershop.component.approval.demo.*` | `cartridge("com.intershop.platform:bc_approval_demo")` |
| `com.intershop.component.auditing.*` | `cartridge("com.intershop.platform:bc_auditing")` |
| `com.intershop.component.authorization.*` | `cartridge("com.intershop.platform:bc_authorization")` |
| `com.intershop.component.b2b.*` | `cartridge("com.intershop.b2b:bc_b2b")` |
| `com.intershop.component.b2b.role.*` | `cartridge("com.intershop.b2b:bc_b2b_role")` |
| `com.intershop.component.basket.*` | `cartridge("com.intershop.business:bc_basket")` |
| `com.intershop.component.basket.*` | `cartridge("com.intershop.business:bc_basket_orm")` |
| `com.intershop.component.basket.*` | `cartridge("com.intershop.business:bc_basket_service")` |
| `com.intershop.component.budget.*` | `cartridge("com.intershop.b2b:bc_budget")` |
| `com.intershop.component.calculation.*` | `cartridge("com.intershop.business:bc_pricing")` |
| `com.intershop.component.calculation.*` | `cartridge("com.intershop.platform:bc_spreadsheet")` |
| `com.intershop.component.captcha.*` | `cartridge("com.intershop.platform:bc_captcha")` |
| `com.intershop.component.catalog.*` | `cartridge("com.intershop.business:bc_catalog")` |
| `com.intershop.component.contract.*` | `cartridge("com.intershop.b2b:bc_contract")` |
| `com.intershop.component.costcenter.*` | `cartridge("com.intershop.b2b:bc_costcenter")` |
| `com.intershop.component.costobject.*` | `cartridge("com.intershop.b2b:bc_costobject")` |
| `com.intershop.component.costobject.orm.*` | `cartridge("com.intershop.b2b:bc_costobject_orm")` |
| `com.intershop.component.customer.*` | `cartridge("com.intershop.business:bc_customer")` |
| `com.intershop.component.customer.catalog.filter.*` | `cartridge("com.intershop.business:bc_customer_catalog_filter")` |
| `com.intershop.component.customer.orm.*` | `cartridge("com.intershop.business:bc_customer_orm")` |
| `com.intershop.component.customer.segment.*` | `cartridge("com.intershop.business:bc_customer_segment")` |
| `com.intershop.component.customfields.*` | `cartridge("com.intershop.business:bc_custom_fields")` |
| `com.intershop.component.dashboard.*` | `cartridge("com.intershop.business:bc_dashboard")` |
| `com.intershop.component.foundation.*` | `cartridge("com.intershop.platform:bc_foundation")` |
| `com.intershop.component.gdpr.*` | `cartridge("com.intershop.business:bc_gdpr")` |
| `com.intershop.component.giftcard.*` | `cartridge("com.intershop.business:bc_giftcard")` |
| `com.intershop.component.gifting.*` | `cartridge("com.intershop.business:bc_giftpackaging")` |
| `com.intershop.component.handlerchain.*` | `cartridge("com.intershop.business:bc_handler_chain")` |
| `com.intershop.component.i18n.*` | `cartridge("com.intershop.platform:bc_i18n")` |
| `com.intershop.component.image.*` | `cartridge("com.intershop.business:bc_image")` |
| `com.intershop.component.mail.*` | `cartridge("com.intershop.platform:bc_mail")` |
| `com.intershop.component.marketing.*` | `cartridge("com.intershop.business:bc_marketing")` |
| `com.intershop.component.marketing.*` | `cartridge("com.intershop.business:bc_recommendation")` |
| `com.intershop.component.marketing.product.*` | `cartridge("com.intershop.business:bc_marketing")` |
| `com.intershop.component.mvc.*` | `cartridge("com.intershop.business:bc_mvc")` |
| `com.intershop.component.order.*` | `cartridge("com.intershop.business:bc_order")` |
| `com.intershop.component.order.*` | `cartridge("com.intershop.business:bc_order_service")` |
| `com.intershop.component.order.approval.orm.*` | `cartridge("com.intershop.b2b:bc_order_approval_orm")` |
| `com.intershop.component.order.impex.*` | `cartridge("com.intershop.business:bc_order_impex")` |
| `com.intershop.component.orderprocess.*` | `cartridge("com.intershop.business:bc_orderprocess")` |
| `com.intershop.component.organization.*` | `cartridge("com.intershop.platform:bc_organization")` |
| `com.intershop.component.organizationhierarchies.*` | `cartridge("com.intershop.business:bc_organization_hierarchies")` |
| `com.intershop.component.payment.*` | `cartridge("com.intershop.business:bc_payment")` |
| `com.intershop.component.payment.*` | `cartridge("com.intershop.business:bc_payment_orm")` |
| `com.intershop.component.pdf.*` | `cartridge("com.intershop.platform:bc_pdf")` |
| `com.intershop.component.pmc.*` | `cartridge("com.intershop.content:bc_pmc")` |
| `com.intershop.component.pmc.*` | `cartridge("com.intershop.content:bc_pmc_auditing")` |
| `com.intershop.component.pmc.auditing.*` | `cartridge("com.intershop.content:bc_pmc_auditing")` |
| `com.intershop.component.preview.*` | `cartridge("com.intershop.content:bc_preview")` |
| `com.intershop.component.preview.orm.*` | `cartridge("com.intershop.content:bc_preview_orm")` |
| `com.intershop.component.pricing.*` | `cartridge("com.intershop.business:bc_pricing")` |
| `com.intershop.component.processchain.*` | `cartridge("com.intershop.platform:bc_processchain")` |
| `com.intershop.component.product.*` | `cartridge("com.intershop.business:bc_product")` |
| `com.intershop.component.product.pricing.*` | `cartridge("com.intershop.business:bc_product_pricing")` |
| `com.intershop.component.product.validation.*` | `cartridge("com.intershop.business:bc_product_validation")` |
| `com.intershop.component.productbinding.*` | `cartridge("com.intershop.business:bc_productbinding")` |
| `com.intershop.component.productconfiguration.*` | `cartridge("com.intershop.business:bc_product_configuration")` |
| `com.intershop.component.productrating.*` | `cartridge("com.intershop.business:bc_product_rating")` |
| `com.intershop.component.profanitycheck.*` | `cartridge("com.intershop.business:bc_profanitycheck")` |
| `com.intershop.component.promotion.*` | `cartridge("com.intershop.business:bc_promotion")` |
| `com.intershop.component.punchout.*` | `cartridge("com.intershop.b2b:bc_punchout")` |
| `com.intershop.component.punchout.cxml.*` | `cartridge("com.intershop.b2b:bc_punchout_cxml")` |
| `com.intershop.component.punchout.cxml.v2.*` | `cartridge("com.intershop.b2b:bc_punchout_cxml")` |
| `com.intershop.component.quote.*` | `cartridge("com.intershop.b2b:bc_quote")` |
| `com.intershop.component.rating.*` | `cartridge("com.intershop.business:bc_rating")` |
| `com.intershop.component.recommendation.*` | `cartridge("com.intershop.business:app_sf_rest_recomm")` |
| `com.intershop.component.recommendation.*` | `cartridge("com.intershop.business:bc_recommendation")` |
| `com.intershop.component.region.*` | `cartridge("com.intershop.platform:bc_region")` |
| `com.intershop.component.repository.*` | `cartridge("com.intershop.platform:bc_repository")` |
| `com.intershop.component.requisition.*` | `cartridge("com.intershop.business:bc_requisition")` |
| `com.intershop.component.rest.*` | `cartridge("com.intershop.platform:rest")` |
| `com.intershop.component.rest.resources.v1.*` | `cartridge("com.intershop.platform:pf_rest_resources")` |
| `com.intershop.component.rma.*` | `cartridge("com.intershop.business:bc_rma")` |
| `com.intershop.component.ruleengine.*` | `cartridge("com.intershop.platform:bc_ruleengine")` |
| `com.intershop.component.search.*` | `cartridge("com.intershop.business:bc_search")` |
| `com.intershop.component.service.*` | `cartridge("com.intershop.platform:bc_service")` |
| `com.intershop.component.shipping.*` | `cartridge("com.intershop.business:bc_shipping")` |
| `com.intershop.component.shipping_data.*` | `cartridge("com.intershop.business:bc_shipping_data")` |
| `com.intershop.component.spreadsheet.*` | `cartridge("com.intershop.platform:bc_spreadsheet")` |
| `com.intershop.component.store.*` | `cartridge("com.intershop.business:bc_store")` |
| `com.intershop.component.taxation.*` | `cartridge("com.intershop.business:bc_taxation")` |
| `com.intershop.component.tendering.*` | `cartridge("com.intershop.business:bc_tendering")` |
| `com.intershop.component.transport.*` | `cartridge("com.intershop.platform:bc_transport")` |
| `com.intershop.component.transport.azure.*` | `cartridge("com.intershop.platform:bc_transport_azure")` |
| `com.intershop.component.urlrewrite.*` | `cartridge("com.intershop.business:bc_urlrewrite")` |
| `com.intershop.component.user.*` | `cartridge("com.intershop.platform:bc_user")` |
| `com.intershop.component.validation.*` | `cartridge("com.intershop.platform:bc_validation")` |
| `com.intershop.component.warranty.*` | `cartridge("com.intershop.business:bc_warranty")` |
| `com.intershop.component.wishlist.*` | `cartridge("com.intershop.business:bc_wishlist")` |
| `com.intershop.jwkstorage.*` | `cartridge("com.intershop.platform:pf_jwkstorage")` |
| `com.intershop.platform.cartridge.*` | `cartridge("com.intershop.platform:pf_cartridge")` |
| `com.intershop.platform.extension.*` | `cartridge("com.intershop.platform:pf_extension")` |
| `com.intershop.platform.objectgraph.*` | `cartridge("com.intershop.platform:pf_objectgraph")` |
| `com.intershop.platform.property.*` | `cartridge("com.intershop.platform:pf_property")` |
| `com.intershop.replication.*` | `cartridge("com.intershop.platform:pf_replication")` |
| `com.intershop.sellside.appbase.b2c.*` | `cartridge("com.intershop.business:sld_ch_b2c_base")` |
| `com.intershop.sellside.application.b2c.*` | `cartridge("com.intershop.business:sld_ch_sf_base")` |
| `com.intershop.sellside.channel.base.*` | `cartridge("com.intershop.business:sld_ch_base")` |
| `com.intershop.sellside.channel.consumer.*` | `cartridge("com.intershop.business:sld_ch_consumer_plugin")` |
| `com.intershop.sellside.enterprise.*` | `cartridge("com.intershop.business:sld_enterprise_app")` |
| `com.intershop.sellside.enterprise.dashboard.*` | `cartridge("com.intershop.business:sld_enterprise_app")` |
| `com.intershop.sellside.image.*` | `cartridge("com.intershop.business:sld_ch_b2c_image")` |
| `com.intershop.sellside.pmc.*` | `cartridge("com.intershop.business:sld_pmc")` |
| `com.intershop.sellside.preview.*` | `cartridge("com.intershop.business:sld_preview")` |
| `com.intershop.sellside.rest.b2b.*` | `cartridge("com.intershop.b2b:app_sf_rest_b2b_basket")` |
| `com.intershop.sellside.rest.b2b.approval.*` | `cartridge("com.intershop.b2b:app_sf_rest_b2b_order_approval")` |
| `com.intershop.sellside.rest.b2b.basket.v1.*` | `cartridge("com.intershop.b2b:app_sf_rest_b2b_basket")` |
| `com.intershop.sellside.rest.b2b.costcenter.*` | `cartridge("com.intershop.b2b:app_sf_rest_b2b_costcenter")` |
| `com.intershop.sellside.rest.b2b.costcenter.v2.*` | `cartridge("com.intershop.b2b:app_sf_rest_b2b_costcenter")` |
| `com.intershop.sellside.rest.b2b.punchout.cxml.v3.*` | `cartridge("com.intershop.b2b:app_sf_rest_b2b_punchout_cxml")` |
| `com.intershop.sellside.rest.b2b.punchout.v2.*` | `cartridge("com.intershop.b2b:app_sf_rest_b2b_punchout")` |
| `com.intershop.sellside.rest.b2b.punchout.v2.*` | `cartridge("com.intershop.b2b:app_sf_rest_b2b_punchout_cxml")` |
| `com.intershop.sellside.rest.b2c.*` | `cartridge("com.intershop.business:app_sf_rest_customer")` |
| `com.intershop.sellside.rest.basket.v1.*` | `cartridge("com.intershop.business:app_sf_rest_basket")` |
| `com.intershop.sellside.rest.common.*` | `cartridge("com.intershop.business:app_sf_rest")` |
| `com.intershop.sellside.rest.common.*` | `cartridge("com.intershop.business:app_sf_rest_common")` |
| `com.intershop.sellside.rest.common.v1.*` | `cartridge("com.intershop.business:app_sf_rest_common")` |
| `com.intershop.sellside.rest.common.v1.*` | `cartridge("com.intershop.business:bc_organization_hierarchies")` |
| `com.intershop.sellside.rest.configuration.v1.*` | `cartridge("com.intershop.business:app_sf_rest_configuration")` |
| `com.intershop.sellside.rest.country.v1.*` | `cartridge("com.intershop.business:app_sf_rest_country")` |
| `com.intershop.sellside.rest.gdpr.v1.*` | `cartridge("com.intershop.business:app_sf_rest_gdpr")` |
| `com.intershop.sellside.rest.inventory.*` | `cartridge("com.intershop.business:app_sf_rest_inventory")` |
| `com.intershop.sellside.rest.inventory.v1.*` | `cartridge("com.intershop.business:app_sf_rest_inventory")` |
| `com.intershop.sellside.rest.order.v1.*` | `cartridge("com.intershop.business:app_sf_rest_order")` |
| `com.intershop.sellside.rest.payment.v1.*` | `cartridge("com.intershop.business:app_sf_rest_payment")` |
| `com.intershop.sellside.rest.pmc.*` | `cartridge("com.intershop.content:app_sf_rest_pmc")` |
| `com.intershop.sellside.rest.pricing.*` | `cartridge("com.intershop.business:app_sf_rest_pricing")` |
| `com.intershop.sellside.rest.pricing.v1.*` | `cartridge("com.intershop.business:app_sf_rest_pricing")` |
| `com.intershop.sellside.rest.smb.*` | `cartridge("com.intershop.business:app_sf_rest_customer")` |
| `com.intershop.sellside.storefront.base.*` | `cartridge("com.intershop.business:sld_ch_sf_base")` |
| `com.intershop.sellside.system.*` | `cartridge("com.intershop.business:sld_system_app")` |
| `com.intershop.swagger.*` | `cartridge("com.intershop.platform:ac_swagger")` |
| `com.intershop.trace.*` | `cartridge("com.intershop.platform:pf_trace")` |
| `com.intershop.ui.web.*` | `cartridge("com.intershop.platform:ui_web_library")` |
| `com.intershop.web.adapter.*` | `cartridge("com.intershop.platform:pf_webadapter")` |

| Import Pattern of other classes | Dependency |
|---------------------------------|------------|
| `com.google.inject.*` | `implementation("com.google.inject:guice")` | 
| `jakarta.inject.*` | `implementation("jakarta.inject:jakarta.inject-api")` |
| `org.slf4j.*` / `Logger`, `LoggerFactory` | `implementation("org.slf4j:slf4j-api")` |
| `org.apache.commons.lang3.*` / `StringUtils` | `implementation("org.apache.commons:commons-lang3")` |
| `org.apache.commons.text.*` | `implementation("org.apache.commons:commons-text")` |
| `com.google.guava.*` / `com.google.common.*` | `implementation("com.google.guava:guava")` |
| `com.google.code.findbugs.*` / `@Nullable` | `implementation("com.google.code.findbugs:jsr305")` |
| `org.junit.*` / `@Test` | `testImplementation("junit:junit")` |
| `org.junit.jupiter.api.*` | `testImplementation("org.junit.jupiter:junit-jupiter-api")` |
| `org.mockito.*` / `@Mock`, `Mockito.*` | `testImplementation("org.mockito:mockito-core")` |
| `org.hamcrest.*` | `testImplementation("org.hamcrest:hamcrest")` |

### Step 6: Import-to-Dependency Mapping

Use this reference when adding missing dependencies:

---

### Step 2: Process Each Java Class

For **each file path** in `[JAVA_CLASSES_LIST]`:

1. **Read** the entire Java file
2. **Analyze** all usages in the class body
3. **Validate** that everything used is imported and available
4. **Fix** any issues found
5. **Save** the corrected file

---

### Step 3: Analyze Class Usage

Scan the **entire class body** and identify ALL:

#### 3.1 — Used Classes & Types

```java
private Domain domain;              // ← Uses "Domain" class
private List<Customer> customers;   // ← Uses "List" and "Customer"
Map<String, Object> data;           // ← Uses "Map", "String", "Object"
```

#### 3.2 — Used Annotations

```java
@Inject                             // ← Uses Inject annotation
@Singleton                          // ← Uses Singleton annotation
@Override                           // ← JDK, no import needed
@Named("config")                    // ← Uses Named annotation
```

#### 3.3 — Used Static Methods & Constants

```java
StringUtils.isBlank(value);         // ← Uses StringUtils from commons-lang3
Objects.requireNonNull(obj);        // ← Uses Objects from JDK
Logger.getLogger(MyClass.class);    // ← Uses Logger
```

#### 3.4 — Used Exceptions

```java
throw new IllegalArgumentException();  // ← JDK, no import needed
throw new PipelineException();         // ← Uses PipelineException
catch (ORMException e)                 // ← Uses ORMException
```

#### 3.5 — Extended Classes & Implemented Interfaces

```java
public class MyClass extends BaseClass implements Serializable {
    // ← Uses "BaseClass" and "Serializable"
}
```

---

### Step 4: Validate & Fix

For **each usage** found in Step 3:

#### Check 1: Is it imported?

- If **YES** → Continue to Check 2
- If **NO** → **ADD** the missing import

#### Check 2: Is the dependency available?

- If **YES** → ✅ Valid
- If **NO** → **ADD** the missing dependency to `build.gradle.kts`

#### Check 3: Are there unused imports?

- If import exists but class/type is **NOT used** → **REMOVE** the import

---

### Step 5: Fix Examples

#### Issue A: Used but NOT Imported

```java
// BEFORE — StringUtils used but not imported
public class MyClass {
    public boolean isEmpty(String value) {
        return StringUtils.isBlank(value);  // ← ERROR: Cannot resolve
    }
}

// AFTER — Import added
import org.apache.commons.lang3.StringUtils;

public class MyClass {
    public boolean isEmpty(String value) {
        return StringUtils.isBlank(value);  // ← Now works
    }
}
```

#### Issue B: Imported but Dependency Missing

```java
// Java file uses:
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// BUT build.gradle.kts is missing:
implementation("org.slf4j:slf4j-api")

// FIX → Add to build.gradle.kts:
implementation("org.slf4j:slf4j-api")
```

### Step 7: Save Changes

1. **Save each fixed Java file** — overwrite the original
2. **Save updated `build.gradle.kts`** — if dependencies were added

---

## Standard Java — No Dependencies Needed

These are part of the JDK and require **no import or dependency**:

| Package | Common Classes |
|---------|----------------|

| `java.lang.*` | `String`, `Object`, `Integer`, `Exception`, `Override`, `System` |
| `java.util.*` | `List`, `Map`, `Set`, `ArrayList`, `HashMap`, `Optional` |
| `java.io.*` | `File`, `InputStream`, `OutputStream`, `IOException` |
| `java.time.*` | `LocalDate`, `LocalDateTime`, `Instant` |
| `java.net.*` | `URL`, `URI`, `HttpURLConnection` |
| `java.nio.*` | `Path`, `Files`, `ByteBuffer` |
| `java.sql.*` | `Connection`, `Statement`, `ResultSet` |
| `java.math.*` | `BigDecimal`, `BigInteger` |

**Note:** `java.lang.*` classes don't even need an import statement.

---

## Constraints

- **Process ALL files** in `[JAVA_CLASSES_LIST]` — skip none
- **Analyze full class body** — not just imports
- **Fix everything** — class must compile 100%
- **Remove unused imports** — clean up each class
- **Add missing imports** — for any used but not imported class
- **Add missing dependencies** — update `build.gradle.kts` as needed
- **Preserve functionality** — do not modify business logic

---

## Output

After processing all files:

```text
✅ Cartridge validation complete: [CARTRIDGE_NAME]

Files processed: [NUMBER]
Files modified: [NUMBER]

Fixes applied:

- Missing imports added: [NUMBER]
- Unused imports removed: [NUMBER]
- Missing dependencies added: [NUMBER]

Updated files:

- [LIST OF MODIFIED JAVA FILES]
- build.gradle.kts (if modified)

Status: ✅ Cartridge ready to build
```

## Commit CHANGES

Commit changes. Add former Output test as comment and use caption:

```text
([CARTRIDGE_NAME]) [id] Cartridge Validation & Fix
```text

[Output text]

---
