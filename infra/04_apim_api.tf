#########################

# GPD TECHNICAL SUPPORT

#########################

data "azurerm_api_management_product" "technical_support_api_product" {
  product_id          = local.apim.technical_support_product
  api_management_name = local.apim.name
  resource_group_name = local.apim.rg
}

data "azurerm_api_management_product" "gpd_integration_product" {
  product_id          = local.apim.gpd_integration_product
  api_management_name = local.apim.name
  resource_group_name = local.apim.rg
}

data "azurerm_key_vault" "gps_kv" {
  name                = local.gps_kv.name
  resource_group_name = local.gps_kv.rg
}

resource "azurerm_api_management_api_version_set" "api_version_set" {
  name                = format("%s-%s", var.env_short, local.gpd_technical_support_api.name)
  resource_group_name = local.apim.rg
  api_management_name = local.apim.name
  display_name        = local.gpd_technical_support_api.display_name
  versioning_scheme   = "Segment"
}

module "api_v1" {
  source = "git::https://github.com/pagopa/terraform-azurerm-v3.git//api_management_api?ref=v6.7.0"

  name                  = format("%s-%s", var.env_short, local.gpd_technical_support_api.name)
  api_management_name   = local.apim.name
  resource_group_name   = local.apim.rg
  product_ids           = [data.azurerm_api_management_product.technical_support_api_product.product_id]
  subscription_required = local.gpd_technical_support_api.subscription_required

  version_set_id = azurerm_api_management_api_version_set.api_version_set.id
  api_version    = "v1"

  description  = local.gpd_technical_support_api.description
  display_name = local.gpd_technical_support_api.display_name
  path         = local.gpd_technical_support_api.path
  protocols    = ["https"]

  service_url = format("https://%s/%s", var.hostname, local.gpd_technical_support_api.path)

  content_format = "openapi"
  content_value = templatefile("./api/gpd-technical-support/v1/_openapi.json.tpl", {
    host = local.host
  })

  xml_content = templatefile("./policy/_base_policy.xml", {
    backend_url = format("https://%s/%s", var.hostname, local.gpd_technical_support_api.path)
  })
}

resource "azurerm_api_management_subscription" "gpd_technical_support_gpd_pay_subkey" {
  api_management_name = local.apim.name
  resource_group_name = local.apim.rg
  product_id          = data.azurerm_api_management_product.gpd_integration_product.id
  display_name        = "GPD Technical Support for GPD PAY recovery"
  allow_tracing       = false
  state               = "active"
}

resource "azurerm_key_vault_secret" "gpd_technical_support_gpd_pay_subkey_secret" {
  name         = "gpd-technical-support-gpd-pay-subkey"
  value        = azurerm_api_management_subscription.gpd_technical_support_gpd_pay_subkey.primary_key
  content_type = "text/plain"
  key_vault_id = data.azurerm_key_vault.gps_kv.id
}
