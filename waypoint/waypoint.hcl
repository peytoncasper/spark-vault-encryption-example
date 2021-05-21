project = "spark-vault-encryption-example"

app "scala-spark-job" {
  build {
    use "sbt" {
      command = "assembly"
      source_dir = "../scala"
      output_dir = "./"
      file_name = "spark-vault-encryption.jar"
    }
    registry {
      use "gcs" {
        bucket = "waypoint-testing"
        object_name = "spark-vault-encryption.jar"
        object_path = ""
        file_path = "spark-vault-encryption.jar"
      }
    }
  }
  deploy {
    use "dataproc" {
      cluster_name = "waypoint-cluster"
      region = "us-east1"

      main_class = "EncryptionAsyncExample"
      job_uri = "gs://waypoint-testing/spark-vault-encryption.jar"

      master_env_variables = {
        BATCH_FILE = "gs://spark-data-1d21/dialogue-short.csv"
        OUTPUT_FILE = "gs://spark-data-1d21/encrypted.csv"
      }

      executor_env_variables = {
        VAULT_ADDRESS = "https://waypoint-vault-cluster.vault.11eb5aaf-7f10-27fb-8c33-0242ac110016.aws.hashicorp.cloud:8200"
        VAULT_NAMESPACE = "admin"
        VAULT_TOKEN = "s.Ios9vTQer6XdIrnuhCZooKmL.VKi7S"
        TRANSIT_BATCH_SIZE = 5000
        MAX_HTTP_THREADS = 5
      }
    }
  }
}
//  build {
//      use "spark" {
//        type = "scala"
//        directory = "scala/"
//        output_path = "waypoint-spark.jar"
//      }
//      registry {
//        use "spark" {
//          bucket = "waypoint-testing"
//        }
////      }
//    }


