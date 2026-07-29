locals {
  product = "${var.prefix}-${var.env_short}"

  apim = {
    name                      = "${local.product}-apim"
    rg                        = "${local.product}-api-rg"
    technical_support_product = "technical_support_api"
    gpd_integration_product   = "debt-positions-integration"
  }

  gps_kv = {
    name = "${local.product}-gps-kv"
    rg   = "${local.product}-gps-sec-rg"
  }

  gpd_technical_support_api = {
    name                  = "api-gpd-technical-support-api"
    display_name          = "GPD Technical Support"
    description           = "Technical support APIs for GPD"
    path                  = "gpd-technical-support"
    subscription_required = true
  }

  gpd_payments_rest_api_name = format("%s-gpd-payments-rest-api-aks-v1", var.env_short)

  host = "api.${var.apim_dns_zone_prefix}.${var.external_domain}"
}