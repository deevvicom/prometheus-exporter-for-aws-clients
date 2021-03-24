[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.deevvi/prometheus-exporter-for-aws-clients/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.deevvi/prometheus-exporter-for-aws-clients)

Prometheus Exporter for AWS clients

Why:

There are never enough metrics
Metrics provided by AWS (usually through CloudWatch) measure server-side latency.
What is:

a very simple solution to publish several basics metrics (latency, data transferred) while interacting with AWS
How to use it

The recommended way to use this library is through your build tool.

The prometheus-exporter-for-aws-clients artifact is published to Maven Central, using the group com.deevvi.

Latest stable version is 1.0.2.

Therefore,it can be added to your Gradle project by adding the dependencies:

compile "com.deevvi:prometheus-exporter-for-aws-clients:1.0.2"
and in Maven:

<dependency>
    <groupId>com.deevvi</groupId>
    <artifactId>prometheus-exporter-for-aws-clients</artifactId>
    <version>1.0.2</version>
</dependency>
Code example:

MeterRegistry meterRegistry = new SimpleMeterRegistry();
RequestHandler2 prometheusHandler = new PrometheusExporterRequestHandler(meterRegistry, "my-app-name");

AmazonSQSClientBuilder.standard()
        .withCredentials(new SystemPropertiesCredentialsProvider())
        .withRequestHandlers(prometheusHandler)
        .build();
Configuration parameters

metricPrefix: nullable prefix for metrics. Useful in case inside an application, there are multiple clients for the same service created and to distinguish between them.
publishPerRegionMetrics: flag to set if metrics should be published per region
publishGlobalMetrics: flag to set if metrics should be published regardless of region
Some observations

Setting both publishPerRegionMetrics and publishGlobalMetrics as false would result into no published metrics.
Setting both publishPerRegionMetrics and publishGlobalMetrics as true means the same metric will be published twice: once per region and once as global.