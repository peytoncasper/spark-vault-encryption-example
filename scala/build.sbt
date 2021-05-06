lazy val commonSettings = Seq(
  name := "scala",
  version := "0.1",
  scalaVersion := "2.12.13",
)


lazy val shaded = (project in file("."))
  .settings(commonSettings)

mainClass in (Compile, packageBin) := Some("EncryptionExample")
libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.1.1" % "provided",
  "org.apache.spark" %% "spark-sql" % "3.1.1" % "provided",
  "org.scalaj" %% "scalaj-http" % "2.4.2",
  "com.bettercloud" % "vault-java-driver" % "5.1.0",
  "com.lihaoyi" %% "ujson" % "1.3.12"
)

assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("com.google.common.**" -> "repackaged.com.google.common.@1").inAll
)


