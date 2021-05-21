provider "vault" {
  address = var.vault_address
  token = var.vault_token
}

resource "vault_mount" "transit" {
  path                      = "transit"
  type                      = "transit"
  default_lease_ttl_seconds = 3600
  max_lease_ttl_seconds     = 86400
}

resource "vault_transit_secret_backend_key" "transit" {
  backend = vault_mount.transit.path
  name    = "spark"
}