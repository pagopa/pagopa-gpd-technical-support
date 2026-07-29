resource "azurerm_key_vault_secret" "gpd_technical_support_gpd_pay_subkey_secret" {
  name         = "gpd-technical-support-gpd-pay-subkey"
  value        = azurerm_api_management_subscription.gpd_technical_support_gpd_pay_subkey.primary_key
  content_type = "text/plain"
  key_vault_id = data.azurerm_key_vault.gps_kv.id
}