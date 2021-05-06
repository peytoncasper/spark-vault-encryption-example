project = "vault-encryption-performance-testing"

app "encryption-async" {
  build {
    use "docker-pull" {
      image = "mozilla/sbt"
    }

    registry {
      use "spark" {
        directory = "./bin"
      }
    }
  }
}