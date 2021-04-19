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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.util.StringUtils;
import org.apache.http.HttpHeaders;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AWS Request handler that logs in a text file the request latency + status code.
 */
public final class PrometheusExporterRequestHandler extends RequestHandler2 {

    private static final String KEY_SEPARATOR = "_";
    private static final String ATTEMPT = "attempt";
    private static final String LATENCY = "latency";
    private static final String CONTENT_LENGTH = "content_length";
    private static final String SYMBOL_REGEX = "[-.+]+";
    private static final String EMPTY_STRING = "";
    private static final String REGION = "region";
    private static final String STATUS = "status";
    private static final String SPACE = " ";

    /**
     * Prometheus client handler.
     */
    private final MeterRegistry meterRegistry;

    /**
     * Prefix used to create distinct metrics for each AWS client.
     */
    private final String metricPrefix;

    /**
     * Constructor.
     *
     * @param meterRegistry - Prometheus handler
     * @param metricPrefix  - string used to prefix metrics name
     */
    public PrometheusExporterRequestHandler(MeterRegistry meterRegistry, String metricPrefix) {
        super();
        this.meterRegistry = meterRegistry;
        this.metricPrefix = StringUtils.isNotBlank(metricPrefix) ? metricPrefix : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterAttempt(HandlerAfterAttemptContext context) {
        if (context.getException() != null && context.getException() instanceof AmazonServiceException) {
            String status = fetchStatus((AmazonServiceException) context.getException());
            String keyPrefix = buildKeyPrefix(context.getRequest());
            String region = context.getRequest().getHandlerContext(HandlerContextKey.SIGNING_REGION);
            meterRegistry.summary(merge(metricPrefix, keyPrefix, ATTEMPT), REGION, region, STATUS, status).record(1);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterResponse(Request<?> request, Response<?> response) {

        String prefix = buildKeyPrefix(request);
        String region = request.getHandlerContext(HandlerContextKey.SIGNING_REGION);
        String status = fetchStatus(response);

        fetchLatency(request).ifPresent(val -> meterRegistry.summary(merge(metricPrefix, prefix, LATENCY), REGION, region, STATUS, status).record(val));
        fetchContentLength(response).ifPresent(val -> meterRegistry.summary(merge(metricPrefix, prefix, CONTENT_LENGTH), REGION, region, STATUS, status).record(val));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterError(Request<?> request, Response<?> response, Exception exception) {


        String prefix = buildKeyPrefix(request);
        String region = request.getHandlerContext(HandlerContextKey.SIGNING_REGION);

        if (exception instanceof AmazonServiceException) {
            String status = fetchStatus((AmazonServiceException) exception);
            meterRegistry.summary(merge(metricPrefix, prefix), REGION, region, STATUS, status).record(1);
            fetchLatency(request).ifPresent(val -> meterRegistry.summary(merge(metricPrefix, prefix, LATENCY), REGION, region, STATUS, status).record(val));
            fetchContentLength(response).ifPresent(val -> meterRegistry.summary(merge(metricPrefix, prefix, CONTENT_LENGTH), REGION, region, STATUS, status).record(val));
        }


    }

    private String buildKeyPrefix(Request<?> request) {

        String serviceId = request.getHandlerContext(HandlerContextKey.SERVICE_ID);
        String apiName = request.getHandlerContext(HandlerContextKey.OPERATION_NAME);

        return sanitize(Joiner.on(KEY_SEPARATOR).join(serviceId, apiName));
    }

    private Optional<Integer> fetchContentLength(Response<?> response) {
        if (response != null && response.getHttpResponse() != null) {
            List<String> headerValue = response.getHttpResponse().getHeaderValues(HttpHeaders.CONTENT_LENGTH);
            if (Objects.nonNull(headerValue) && !headerValue.isEmpty()) {
                try {
                    return Optional.of(Integer.valueOf(headerValue.get(0)));
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private String fetchStatus(Response<?> response) {
        if (response != null && response.getHttpResponse() != null) {
            return HttpStatusCodeType.resolve(response.getHttpResponse().getStatusCode()).name().toLowerCase();
        }
        return EMPTY_STRING;
    }

    private String fetchStatus(AmazonServiceException exception) {
        if (exception != null) {
            return HttpStatusCodeType.resolve(exception.getStatusCode()).name().toLowerCase();
        }
        return EMPTY_STRING;
    }

    private String sanitize(String raw) {
        return raw.replaceAll(SPACE, KEY_SEPARATOR);
    }

    private String merge(String... parts) {
        return Joiner.on(KEY_SEPARATOR).skipNulls().join(parts).replaceAll(SYMBOL_REGEX, KEY_SEPARATOR);
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
