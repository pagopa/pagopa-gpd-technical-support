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