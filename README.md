## Create a Spark Conf from the template
1. Copy the template
```
cd scala
cp spark.conf.template spark.conf
```

2. Set the values accordingly
```
spark.yarn.appMasterEnv.BATCH_FILE=data/dialogue-10.csv
spark.yarn.appMasterEnv.OUTPUT_FILE=data/encrypted.csv
spark.executorEnv.VAULT_ADDRESS=http://localhost:8200
spark.executorEnv.VAULT_NAMESPACE=""
spark.executorEnv.VAULT_TOKEN=token
spark.executorEnv.TRANSIT_BATCH_SIZE=20000
spark.executorEnv.MAX_HTTP_THREADS=5
```

## Build
3. Create fat JAR
```
sbt assembly
```

## Run
This happens in two stages due to the Vault provider depending on the token and address from step 1.

4. Create DataProc Cluster and HCP Vault Cluster
```
cd terraform/step-1
terraform init
terraform apply
```

5. Create Transit Engine
```
cd terraform/step-2
terraform init
terraform apply
```

6. Deploy Waypoint Job
```
cd waypoint
waypoint up
```

## Results

```
Vault Medium - 60,000 Batch Size
——————————————————
Encrypted
Expected Record Count: 26839031
Final Record Count: 26839031
Elapsed Time: 561016ms
Records per second: 47841

Vault High - 5,000 Batch Size
———————————————
Encrypted Async
Executor Count: 8
Expected Record Count: 26839031
Final Record Count: 26839031
Elapsed Time: 250835ms
Records per second: 107356

Vault High - 10,000 Batch Size
———————————————-
EncryptedAsync
Executor Count: 8
Expected Record Count: 26839031
Final Record Count: 26839031
Elapsed Time: 234892ms
Records per second: 114696

Vault High - 15,000 Batch Size
———————————————
EncryptedAsync
Executor Count: 10
Expected Record Count: 26839031
Final Record Count: 26839031
Elapsed Time: 183120ms
Records per second: 146661

Vault High - 25,000 Batch Size
———————————————-
EncryptedAsync
Executor Count: 8
Expected Record Count: 26839031
Final Record Count: 26839031
Elapsed Time: 259966ms
Records per second: 103625
```