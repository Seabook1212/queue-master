package works.weave.socks.queuemaster.logging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class TraceExceptionTagger {

    private final ObjectProvider<Tracer> tracerProvider;

    public TraceExceptionTagger(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    public void tagException(Throwable throwable) {
        if (throwable == null) {
            return;
        }

        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return;
        }

        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return;
        }

        Throwable rootCause = rootCauseOf(throwable);
        String exceptionType = rootCause.getClass().getName();
        String exceptionMessage = rootCause.getMessage() != null ? rootCause.getMessage() : "";

        currentSpan.tag("exception.type", exceptionType);
        currentSpan.tag("exception.message", exceptionMessage);
        currentSpan.tag("error.type", exceptionType);
        currentSpan.tag("error.message", exceptionMessage);
        currentSpan.error(throwable);
    }

    private Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
