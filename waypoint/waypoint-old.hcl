project = "spark-vault-encryption-example"

app "scala-spark-job" {
  build {
    use "spark" {
      type = "scala"
      directory = "scala/"
      output_path = "waypoint-spark.jar"
    }
    registry {
      use "spark" {
        bucket = "waypoint-testing"
      }
    }
  }

  deploy {
    use "spark" {}
  }
}


