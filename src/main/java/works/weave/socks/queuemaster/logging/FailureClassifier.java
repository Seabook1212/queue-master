package works.weave.socks.queuemaster.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import jakarta.validation.ConstraintViolationException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

public final class FailureClassifier {

    private FailureClassifier() {
    }

    public static String classify(Throwable throwable) {
        Throwable root = NestedExceptionUtils.getMostSpecificCause(throwable);

        if (root instanceof SocketTimeoutException || root instanceof TimeoutException) {
            return "timeout";
        }
        if (root instanceof UnknownHostException) {
            return "dns_failure";
        }
        if (root instanceof ConnectException) {
            return "connection_refused";
        }
        if (root instanceof AmqpConnectException) {
            return "connection_error";
        }
        if (root instanceof MessageConversionException
                || root instanceof JsonProcessingException
                || root instanceof HttpMessageNotReadableException) {
            return "serialization_error";
        }
        if (root instanceof MethodArgumentNotValidException
                || root instanceof ConstraintViolationException) {
            return "validation_error";
        }
        if (root instanceof RejectedExecutionException) {
            return "thread_pool_saturated";
        }
        return "internal_error";
    }
}
