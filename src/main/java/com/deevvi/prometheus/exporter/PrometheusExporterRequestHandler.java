package com.deevvi.prometheus.exporter;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerAfterAttemptContext;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.util.AWSRequestMetrics;
import com.amazonaws.util.TimingInfo;
import com.deevvi.httpstatuscode.HttpStatusCodeType;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.util.StringUtils;
import org.apache.http.HttpHeaders;

import java.util.List;
import java.util.Optional;

/**
 * AWS Request handler that logs in a text file the request latency + status code.
 */
public final class PrometheusExporterRequestHandler extends RequestHandler2 {

    private static final String KEY_SEPARATOR = "_";
    private static final String ATTEMPT = "attempt";
    private static final String LATENCY = "latency";
    private static final String CONTENT_LENGTH = "content_length";
    /**
     * Prometheus client handler.
     */
    private final MeterRegistry meterRegistry;

    /**
     * Prefix used to create distinct metrics for each AWS client.
     */
    private final String metricPrefix;

    /**
     * Flag to set if request handler should publish metrics per region or not.
     */
    private final boolean publishPerRegionMetrics;

    /**
     * Flag to set if request handler should publish global metrics.
     */
    private final boolean publishGlobalMetrics;

    /**
     * Constructor.
     *
     * @param meterRegistry           - Prometheus handler
     * @param metricPrefix            - string used to prefix metrics name
     * @param publishPerRegionMetrics - flag to set if request handler should publish metrics per region or not
     * @param publishGlobalMetrics    - flag to set if request handler should publish global metrics
     */
    public PrometheusExporterRequestHandler(MeterRegistry meterRegistry, String metricPrefix, boolean publishPerRegionMetrics, boolean publishGlobalMetrics) {
        super();
        this.meterRegistry = meterRegistry;
        this.metricPrefix = StringUtils.isNotBlank(metricPrefix) ? metricPrefix : null;
        this.publishPerRegionMetrics = publishPerRegionMetrics;
        this.publishGlobalMetrics = publishGlobalMetrics;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterAttempt(HandlerAfterAttemptContext context) {
        if (context.getException() != null && context.getException() instanceof AmazonServiceException) {
            fetchStatus((AmazonServiceException) context.getException())
                    .ifPresent(val -> buildKeyPrefix(context.getRequest())
                            .forEach(keyPrefix -> meterRegistry.summary(merge(metricPrefix, keyPrefix, ATTEMPT, val.name()), Lists.newArrayList()).record(1)));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterResponse(Request<?> request, Response<?> response) {

        List<String> prefixes = buildKeyPrefix(request);

        fetchLatency(request).ifPresent(val -> prefixes.forEach(prefix -> meterRegistry.summary(merge(metricPrefix, prefix, LATENCY), Lists.newArrayList()).record(val)));
        fetchContentLength(response).ifPresent(val -> prefixes.forEach(prefix -> meterRegistry.summary(merge(metricPrefix, prefix, CONTENT_LENGTH), Lists.newArrayList()).record(val)));
        fetchStatus(response).ifPresent(val -> prefixes.forEach(prefix -> meterRegistry.summary(merge(metricPrefix, prefix, val.name()), Lists.newArrayList()).record(1)));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterError(Request<?> request, Response<?> response, Exception exception) {

        List<String> prefixes = buildKeyPrefix(request);

        if (exception instanceof AmazonServiceException) {
            fetchStatus((AmazonServiceException) exception)
                    .ifPresent(val -> prefixes.forEach(prefix -> meterRegistry.summary(merge(metricPrefix, prefix, val.name()), Lists.newArrayList()).record(1)));
        }

        fetchLatency(request)
                .ifPresent(val -> prefixes.forEach(prefix -> meterRegistry.summary(merge(metricPrefix, prefix, LATENCY), Lists.newArrayList()).record(val)));
        fetchContentLength(response)
                .ifPresent(val -> prefixes.forEach(prefix -> meterRegistry.summary(merge(metricPrefix, prefix, CONTENT_LENGTH), Lists.newArrayList()).record(val)));
    }

    private List<String> buildKeyPrefix(Request<?> request) {
        List<String> prefixes = Lists.newArrayList();
        if (publishGlobalMetrics || publishPerRegionMetrics) {
            String serviceId = request.getHandlerContext(HandlerContextKey.SERVICE_ID);
            String apiName = request.getHandlerContext(HandlerContextKey.OPERATION_NAME);

            if (publishGlobalMetrics) {
                prefixes.add(sanitize(Joiner.on(KEY_SEPARATOR).join(serviceId, apiName)));
            }
            if (publishPerRegionMetrics) {
                String region = request.getHandlerContext(HandlerContextKey.SIGNING_REGION);
                prefixes.add(sanitize(Joiner.on(KEY_SEPARATOR).join(serviceId, apiName, region)));
            }

        }
        return prefixes;
    }

    private Optional<Integer> fetchContentLength(Response<?> response) {
        if (response != null && response.getHttpResponse() != null) {
            List<String> headerValue = response.getHttpResponse().getHeaderValues(HttpHeaders.CONTENT_LENGTH);
            if (!headerValue.isEmpty()) {
                return Optional.of(Integer.valueOf(headerValue.get(0)));
            }
        }
        return Optional.empty();
    }

    private Optional<HttpStatusCodeType> fetchStatus(Response<?> response) {
        if (response != null && response.getHttpResponse() != null) {
            return Optional.of(HttpStatusCodeType.resolve(response.getHttpResponse().getStatusCode()));
        }
        return Optional.empty();
    }

    private Optional<HttpStatusCodeType> fetchStatus(AmazonServiceException exception) {
        if (exception != null) {
            return Optional.of(HttpStatusCodeType.resolve(exception.getStatusCode()));
        }
        return Optional.empty();
    }

    private String sanitize(String raw) {
        return raw.replaceAll(" ", KEY_SEPARATOR);
    }

    private String merge(String... parts) {
        return Joiner.on(KEY_SEPARATOR).skipNulls().join(parts);
    }

    private Optional<Long> fetchLatency(Request<?> request) {
        AWSRequestMetrics metrics = request.getAWSRequestMetrics();
        if (metrics != null) {
            TimingInfo timingInfo = metrics.getTimingInfo();
            if (timingInfo != null && timingInfo.getStartEpochTimeMilliIfKnown() != null && timingInfo.getEndEpochTimeMilliIfKnown() != null) {
                return Optional.of(timingInfo.getEndEpochTimeMilliIfKnown() - timingInfo.getStartEpochTimeMilliIfKnown());
            }
        }
        return Optional.empty();
    }
}
