- come inviare i log applicati di flink, anche le metriche e tutto il resto. SOprattuto i log applicativi (perchè devo calcolare delle metriche di quanto ci ho messo ad elaborare)
manda queste informazione a Splunk. ogni microservizio fa un pezzo di computazione, voglio sapere quanto dura questo pezzo.
- parallelizzare su più taskManager. prima provare su diversi task slot (stesso taskManger), poi su diversi taskmanager.
si fa sempre specificando il parallelismo
- apache flink su gke (kubernetes di google, google kubernetes environment). antos

# 1. log applicativi
Flink si appoggia a log4j e ha come best practice l'uso di slf4j. i dati di log quindi si possono inviare a splunk come viene fatto con tutti gli altri microservizi.
Ho provato a farlo funzionare e c'è tantissimo setup da fare. chiedere se abbiamo roba già setuppata (tipo un docker compose con splunk ed un micrtoservizio spring che invia log a splunk) in modo da poterlo copiare.

Il logging tramite log4j è configurabile tramite un file di configurazione log4j-console.properties. ATTENZIONE: se sto runnando su docker devo fare in modo che il file di configurazione sia copiato nel container, altrimenti non funziona.
(path /opt/flink/conf/log4j-console.properties del jobmanager e del taskmanager)
ci sono altri file di configurazione li dentro, per più info vedere [qui](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/deployment/advanced/logging/#configuring-log4j-2)

Updates: Michele mi ha detto di non fossilizzarmi su Splunk, di quello se ne occupano altri, io devo solo usare log4j e slf4j e stampare i log su file. mi ha fornito un documento tecnico a riguardo.
il file di configurazione è log4j-console.properties, che va messo nella cartella /opt/flink/conf/ del jobmanager e del taskmanager. si può cambiare l'impostazione di default tramite i seguenti argomenti da passare alla JVM:-Dlog.file=/opt/flink/log/flink--standalonesession-0-c40902733971.log
- -Dlog.file=/opt/flink/log/flink--standalonesession-0-c40902733971.log
- -Dlog4j.configuration=file:/opt/flink/conf/log4j-console.properties
- -Dlog4j.configurationFile=file:/opt/flink/conf/log4j-console.properties
- -Dlogback.configurationFile=file:/opt/flink/conf/logback-console.xml

Inoltre ATTENZIONE: si può usare logback, log4j2 e log4j, non confondere le sintassi.
(vedi [la documentazione di log4j2](https://logging.apache.org/log4j/2.x/manual/configuration.html)
ho provato logback (gli altri progetti usano quello) ma dopo una giornata persa sono tornato indietro al log4j2, riproverò logback.
# 2. parallelismo
In teoria l'ho fatto.
Il parallelismo esplicito può essere espresso in fase di submit del job, a livello globale tramite la variabile d'ambiente parallelism.default nel docker compose o a livello di singolo operatore tramite il metodo setParallelism.
Adesso, tramite setMaxParallelism, o tramite qualche configurazione (quindi sul compose) che non ricordo, si può specificare il numero massimo di parallelism che un operatore può avere.
Questo è ciò che mi ha chiesto Massimo ma al momento sto avendo il problema opposto: se imposto solo il max parallelism, il parallelism di default è 1 e quindi non si parallelizza niente.
se lo forzo a 2 o a 3 funziona, ma non ha senso perchè sto tenendo impegnati dei task slot che non fanno un cazzo.
Mi piacerebbe capire come fare il cosiddetto [Elastic Scaling](https://nightlies.apache.org/flink/flink-docs-release-1.18/docs/deployment/elastic_scaling/#adaptive-scheduler) ed in teoria l'ho fatto, ma non funziona.
mi correggo, funziona, ma non come mi aspettavo.
The Adaptive Scheduler can adjust the parallelism of a job based on available slots. It will automatically reduce the parallelism if not enough slots are available to run the job with the originally configured parallelism;
In pratice lui cerca di ottenere il parallelismo che gli ho chiesto, ma se non ci riesce, si accontenta di quello che c'è. NON è quello che voglio, non so se è possibile fare quello che voglio.
quel che forse si può fare invece è l' Adaptive Batch Scheduler che ha una proprietà _avg-data-volume-per-task_ che indica dopo quanto "traffico" scalare. però funziona solo per i batch, non per i job in streaming.


In compenso ho visto che se il numero di task slot è minore del parallelismo, il job parte comunque e se aggiungo un nuovo task slot, il job scala automaticamente ( fino al parallelismo desiderato)
Da quanto ho capito usando k8s o yarn dovrebbe essere possibile fare in modo che se non ci sono abbastanza task slot, ne vengano creati di nuovi.
With Flink 1.5.0 when running on Yarn or Mesos, you only need to decide on the parallelism of your job and the system will make sure that it starts enough TaskManagers with enough slots to execute your job. This happens completely dynamically and you can even change the parallelism of your job at runtime.
[source](https://stackoverflow.com/questions/50719147/apache-flink-guideliness-for-setting-parallelism)
In ultimo l'lastic Scaling dovrebbe anche permettere di continuare il lavoro in caso di failure di uno o più worker, ci ho provato ma si blocca il job, probabilmente bisogna settare il checkpointing.