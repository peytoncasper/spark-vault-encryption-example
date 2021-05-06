import org.apache.spark.sql.{Encoders, Row, SparkSession}
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}
import org.apache.spark.sql.{Encoder, Encoders, Row}
import ujson._

import java.nio.charset.StandardCharsets
import scalaj.http.{Http, HttpOptions}

import scala.annotation.tailrec

case class EncryptionRow(@JsonProperty("plain_text") plainText: String, @JsonProperty("ciphertext") cipherText: Option[String])
case class EncryptionBatch(@JsonProperty("batch_input") batchInput: scala.collection.mutable.MutableList[EncryptionRow])

case class EncryptResult(@JsonProperty("data") data: List[EncryptionRow])

object EncryptionExample {
  def main(args: Array[String]) {
    val spark = SparkSession
      .builder
      .appName("Encryption Example")
      .getOrCreate()

    val batchFile = spark.conf.get("spark.yarn.appMasterEnv.BATCH_FILE")
    val outputFile = spark.conf.get("spark.yarn.appMasterEnv.OUTPUT_FILE")
    val dialogueDf = spark.read.format("csv").option("header", "true").load(batchFile)

    val vaultAddress = spark.sparkContext.broadcast(spark.conf.get("spark.executorEnv.VAULT_ADDRESS"))
    val vaultNamespace = spark.sparkContext.broadcast(spark.conf.get("spark.executorEnv.VAULT_NAMESPACE"))
    val vaultToken = spark.sparkContext.broadcast(spark.conf.get("spark.executorEnv.VAULT_TOKEN"))
    val transitBatchSize = spark.sparkContext.broadcast(spark.conf.get("spark.executorEnv.TRANSIT_BATCH_SIZE").toInt)

    val start = System.currentTimeMillis()

    import spark.implicits._

    val finalCount = dialogueDf
      .mapPartitions(partition => {
        val batches = scala.collection.mutable.MutableList.empty[String]
        var batch = scala.collection.mutable.MutableList.empty[EncryptionRow]
        val mapper = new ObjectMapper()
        mapper.registerModule(new DefaultScalaModule)

        var i = 0
        partition.foreach(record => {


          val encryptionRow = new EncryptionRow(
            plainText = java.util.Base64.getEncoder.encodeToString(
              record.mkString.getBytes(StandardCharsets.UTF_8)
            ),
            cipherText = None
          )

          batch += encryptionRow
          i += 1

          if (i >= transitBatchSize.value) {
            batches += mapper.writeValueAsString(EncryptionBatch(
              batchInput = batch
            ))

            batch = scala.collection.mutable.MutableList.empty[EncryptionRow]
            i = 0
          }
        })

        if (batch.nonEmpty) {
          batches += mapper.writeValueAsString(EncryptionBatch(
            batchInput = batch
          ))
        }

        batches.iterator
      })
      .flatMap(batch => {
        var result: String = ""
        retry(3) {
          result = encrypt(batch, vaultAddress.value, vaultNamespace.value, vaultToken.value)
        }

        val json = ujson.read(result)
        json("data")("batch_results").arr.map(x => {
          x.toString()
        })
      }).count()

    val end = System.currentTimeMillis()
    val expectedCount = dialogueDf.count()
    val elapsed = end - start

    println("Expected Record Count: " + expectedCount)
    println("Final Record Count: " + finalCount)
    println("Elapsed Time: " + (elapsed).toString + "ms")
    println("Records per second: " + (finalCount / (elapsed / 1000)).toString)
  }


  def encrypt(batch: String, vaultAddress: String, vaultNamespace: String, vaultToken: String): String = {
    val result = Http(vaultAddress + "/v1/transit/encrypt/spark")
      .postData(batch)
      .header("Content-Type", "application/json")
      .header("X-Vault-Namespace", vaultNamespace)
      .header("X-Vault-Token", vaultToken)
      .option(HttpOptions.readTimeout(10000)).asString

    if (result.code != 200) {
      throw new Exception("Non success code")
    }

    result.body
  }

  @tailrec
  def retry[T](n: Int)(fn: => T): T = {
    try {
      fn
    } catch {
      case e: Throwable =>
        if (n > 1) retry(n - 1)(fn)
        else throw e
    }
  }
}
