package com.deevvi.prometheus.exporter;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.handlers.HandlerAfterAttemptContext;
import com.amazonaws.handlers.HandlerContextKey;
import com.amazonaws.http.HttpResponse;
import com.amazonaws.util.AWSRequestMetrics;
import com.amazonaws.util.TimingInfo;
import com.google.common.collect.ImmutableList;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PrometheusExporterRequestHandlerTest} class
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class PrometheusExporterRequestHandlerTest {

    private MeterRegistry meterRegistry = Mockito.mock(MeterRegistry.class);
    private PrometheusExporterRequestHandler handler = new PrometheusExporterRequestHandler(meterRegistry, "prefix", true, true);

    @DisplayName("No metric published when afterAttempt() call has no exception found")
    @Test
    public void testAfterAttemptWithoutException() {

        //setup
        HandlerAfterAttemptContext ctx = Mockito.mock(HandlerAfterAttemptContext.class);
        when(ctx.getException()).thenReturn(null);

        //call
        handler.afterAttempt(ctx);

        //verify
        verify(ctx, times(1)).getException();
        verifyZeroInteractions(meterRegistry);
    }

    @DisplayName("Metrics are published when afterAttempt() call has exception found")
    @Test
    public void testAfterAttemptWithException() {

        //setup
        AmazonServiceException exception = new AmazonServiceException("exception");
        exception.setStatusCode(300);
        Request request = Mockito.mock(Request.class);
        HandlerAfterAttemptContext ctx = Mockito.mock(HandlerAfterAttemptContext.class);
        when(ctx.getException()).thenReturn(exception);
        when(ctx.getRequest()).thenReturn(request);
        when(request.getHandlerContext(HandlerContextKey.SERVICE_ID)).thenReturn("service");
        when(request.getHandlerContext(HandlerContextKey.OPERATION_NAME)).thenReturn("operation");
        when(request.getHandlerContext(HandlerContextKey.SIGNING_REGION)).thenReturn("region");
        DistributionSummary summary = Mockito.mock(DistributionSummary.class);
        when(meterRegistry.summary(anyString(), anyIterable())).thenReturn(summary);

        //call
        handler.afterAttempt(ctx);

        //verify
        verify(ctx, times(3)).getException();
        verify(meterRegistry, times(2)).summary(anyString(), anyIterable());
        verify(summary, times(2)).record(1);
        verify(request, times(3)).getHandlerContext(any());
        verify(ctx, times(1)).getRequest();
        verifyNoMoreInteractions(ctx, meterRegistry, summary, request);
    }

    @DisplayName("Metrics are published after afterAttempt() call")
    @Test
    public void testAfterResponse() {

        //setup
        Request request = Mockito.mock(Request.class);
        Response response = Mockito.mock(Response.class);
        AWSRequestMetrics metrics = Mockito.mock(AWSRequestMetrics.class);

        when(request.getAWSRequestMetrics()).thenReturn(metrics);
        TimingInfo timingInfo = Mockito.mock(TimingInfo.class);
        when(metrics.getTimingInfo()).thenReturn(timingInfo);
        when(timingInfo.getStartEpochTimeMilliIfKnown()).thenReturn(1L);
        when(timingInfo.getEndEpochTimeMilliIfKnown()).thenReturn(2L);

        when(request.getHandlerContext(HandlerContextKey.SERVICE_ID)).thenReturn("service");
        when(request.getHandlerContext(HandlerContextKey.OPERATION_NAME)).thenReturn("operation");
        when(request.getHandlerContext(HandlerContextKey.SIGNING_REGION)).thenReturn("eu-west-1");

        HttpResponse httpResponse = Mockito.mock(HttpResponse.class);
        when(response.getHttpResponse()).thenReturn(httpResponse);
        when(httpResponse.getHeaderValues(anyString())).thenReturn(ImmutableList.of("1000"));
        when(httpResponse.getStatusCode()).thenReturn(200);
        DistributionSummary summary = Mockito.mock(DistributionSummary.class);
        when(meterRegistry.summary(anyString(), anyIterable())).thenReturn(summary);
        //call
        handler.afterResponse(request, response);

        //verify
        verify(request, times(1)).getAWSRequestMetrics();
        verify(request, times(3)).getHandlerContext(any());
        verify(metrics, times(1)).getTimingInfo();
        verify(timingInfo, times(2)).getStartEpochTimeMilliIfKnown();
        verify(timingInfo, times(2)).getEndEpochTimeMilliIfKnown();
        verify(response, times(4)).getHttpResponse();
        verify(httpResponse, times(1)).getHeaderValues("Content-Length");
        verify(httpResponse, times(1)).getStatusCode();

        verify(meterRegistry, times(6)).summary(anyString(), anyIterable());
        verify(summary, times(6)).record(anyDouble());

        verifyNoMoreInteractions(request, response, metrics, timingInfo, httpResponse, meterRegistry, summary);
    }

    @DisplayName("Metrics are published after afterError() call")
    @Test
    public void testAfterError() {

        //setup
        AmazonServiceException exception = new AmazonServiceException("exception");
        exception.setStatusCode(500);
        Request request = Mockito.mock(Request.class);
        Response response = Mockito.mock(Response.class);

        when(request.getAWSRequestMetrics()).thenReturn(null);

        when(request.getHandlerContext(HandlerContextKey.SERVICE_ID)).thenReturn("service");
        when(request.getHandlerContext(HandlerContextKey.OPERATION_NAME)).thenReturn("operation");
        when(request.getHandlerContext(HandlerContextKey.SIGNING_REGION)).thenReturn("eu-west-1");

        DistributionSummary summary = Mockito.mock(DistributionSummary.class);
        when(meterRegistry.summary(anyString(), anyIterable())).thenReturn(summary);

        //call
        handler.afterError(request, response, exception);

        //verify
        verify(request, times(1)).getAWSRequestMetrics();
        verify(request, times(3)).getHandlerContext(any());
        verify(response, times(1)).getHttpResponse();

        verify(meterRegistry, times(2)).summary(anyString(), anyIterable());
        verify(summary, times(2)).record(anyDouble());

        verifyNoMoreInteractions(request, response, meterRegistry, summary);
    }

}
