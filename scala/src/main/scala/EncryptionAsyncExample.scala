import EncryptionExample.retry
import org.apache.spark.sql.{Encoders, Row, SparkSession}
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, ScalaObjectMapper}

import java.nio.charset.StandardCharsets
import scalaj.http.{Http, HttpOptions, HttpResponse}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object EncryptionAsyncExample {
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
    val maxHttpThreads = spark.sparkContext.broadcast(spark.conf.get("spark.executorEnv.MAX_HTTP_THREADS").toInt)

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
      .mapPartitions(partition => {
        val queue = new mutable.Queue[Future[Any]]
        var results = new mutable.ListBuffer[String]

        partition.foreach(batch => {
          queue.enqueue(encrypt(batch, vaultAddress.value, vaultNamespace.value, vaultToken.value, 0))
          if (queue.size >= maxHttpThreads.value) {
            val res = Await.result(queue.dequeue(), Duration.Inf)

            res match {
              case result: HttpResponse[String] =>
                val json = ujson.read(result.body)
                print(json)
                json("data")("batch_results").arr.foreach(x => {
                  results += x.toString()
                })
            }
          }
        })

        if (queue.nonEmpty) {
          queue.foreach(y => {
            val res = Await.result(y, Duration.Inf)

            res match {
              case result: HttpResponse[String] =>
                val json = ujson.read(result.body)
                print(json)
                json("data")("batch_results").arr.foreach(x => {
                  results += x.toString()
                })
            }


          })
        }

        results.iterator
      }).count()

    val end = System.currentTimeMillis()
    val expectedCount = dialogueDf.count()
    val elapsed = end - start

    println("Expected Record Count: " + expectedCount)
    println("Final Record Count: " + finalCount)
    println("Elapsed Time: " + (elapsed).toString + "ms")
    println("Records per second: " + (finalCount / (elapsed / 1000)).toString)
  }


  def encrypt(batch: String, vaultAddress: String, vaultNamespace: String, vaultToken: String, n: Int): Future[Any] = Future {
      val req = Http(vaultAddress + "/v1/transit/encrypt/spark")
        .postData(batch)
        .header("Content-Type", "application/json")
        .header("X-Vault-Namespace", vaultNamespace)
        .header("X-Vault-Token", vaultToken)
        .option(HttpOptions.readTimeout(10000)).asString


      if (req.code != 200) {
      throw new Exception("Error POST to Vault")
    }

    req
  } recoverWith {
    case e =>
      encrypt(batch, vaultAddress, vaultNamespace, vaultToken, n + 1)
  }


}
