provider "hcp" {}
//data "google_project" "project" {}

///
// DataProc
///
resource "google_dataproc_cluster" "waypoint_cluster" {
  name   = "waypoint-cluster"
  region = "us-east1"

  cluster_config {
    gce_cluster_config {
      zone = "us-east1-d"
    }
  }
}

///
// HCP Vault
///

data "hcp_hvn" "east" {
  hvn_id = "hvn-east"
}

resource "hcp_vault_cluster" "waypoint" {
  cluster_id = "waypoint-vault-cluster"
  hvn_id     = data.hcp_hvn.east.hvn_id
  public_endpoint = true
}

resource "hcp_vault_cluster_admin_token" "waypoint" {
  cluster_id = hcp_vault_cluster.waypoint.cluster_id
}

//
//resource "local_file" "cluster_config" {
//  content     = templatefile("cluster_config_template_old.json", {
//    "cluster_name": google_dataproc_cluster.waypoint_cluster.name,
//    "project_id": data.google_project.project.project_id
//    "region": google_dataproc_cluster.waypoint_cluster.region
//  })
//  filename = "${path.module}/cluster_config.json"
//}

resource "local_file" "step_2_tf_vars" {
  content = <<EOT
  vault_address = "https://${hcp_vault_cluster.waypoint.vault_public_endpoint_url}:8200"
  vault_token = "${hcp_vault_cluster_admin_token.waypoint.token}"
  EOT
  filename = "../step-2/terraform.tfvars"
}