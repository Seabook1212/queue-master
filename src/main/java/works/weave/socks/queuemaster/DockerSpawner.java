package works.weave.socks.queuemaster;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.command.PullImageResultCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import works.weave.socks.queuemaster.logging.FailureClassifier;
import works.weave.socks.queuemaster.logging.TraceExceptionTagger;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class DockerSpawner {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final int DOCKER_TASK_QUEUE_CAPACITY = 200;
    private final TraceExceptionTagger traceExceptionTagger;

	private DockerClient dc;
	private ThreadPoolExecutor dockerPool;

	private String imageName = "weaveworksdemos/worker";
	private String imageVersion = "latest";
	private String networkId = "weavedemo_backoffice";
	private int poolSize = 50;

    public DockerSpawner(TraceExceptionTagger traceExceptionTagger) {
        this.traceExceptionTagger = traceExceptionTagger;
    }

	public void init() {
		if (dc == null) {
            long startTime = System.currentTimeMillis();
			DockerClientConfig.DockerClientConfigBuilder builder = DockerClientConfig.createDefaultConfigBuilder();

            try {
                DockerClientConfig config = builder.build();
                dc = DockerClientBuilder.getInstance(config).build();

                dc.pullImageCmd(imageName).withTag(imageVersion).exec(new PullImageResultCallback()).awaitSuccess();
            } catch (Exception e) {
                traceExceptionTagger.tagException(e);
                logger.error(
                        "event=docker_client_init_failed dependency=docker operation=init image={} networkId={} elapsedMs={} errorType={} exceptionClass={}",
                        imageName + ":" + imageVersion,
                        networkId,
                        System.currentTimeMillis() - startTime,
                        FailureClassifier.classify(e),
                        e.getClass().getSimpleName(),
                        e);
                throw e;
            }
		}
		if (dockerPool == null) {
			dockerPool = new ThreadPoolExecutor(
					poolSize,
					poolSize,
					0L,
					TimeUnit.MILLISECONDS,
					new ArrayBlockingQueue<>(DOCKER_TASK_QUEUE_CAPACITY),
					new ThreadPoolExecutor.AbortPolicy());
		}
	}

	public void spawn() {
        try {
		    dockerPool.execute(new Runnable() {
		        public void run() {
                    long startTime = System.currentTimeMillis();
                    String operation = "create";
                    String containerId = null;

					logger.info("event=docker_worker_spawn_started dependency=docker operation=spawn image={} networkId={}",
                            imageName + ":" + imageVersion, networkId);
					try {
						CreateContainerResponse container = dc.createContainerCmd(imageName + ":" + imageVersion)
                                .withNetworkMode(networkId)
                                .withCmd("ping", "rabbitmq")
                                .exec();
						containerId = container.getId();
                        operation = "start";
						dc.startContainerCmd(containerId).exec();
						logger.info("event=docker_worker_started dependency=docker operation=start image={} networkId={} containerId={}",
                                imageName + ":" + imageVersion, networkId, containerId);
						// TODO instead of just sleeping, call await on the container and remove once it's completed.
						Thread.sleep(40000);
						try {
                            operation = "stop";
							dc.stopContainerCmd(containerId).exec();
						}
						catch (DockerException e) {
							logger.warn("event=docker_worker_stop_skipped dependency=docker operation=stop image={} networkId={} containerId={} errorType={} exceptionClass={}",
                                    imageName + ":" + imageVersion,
                                    networkId,
                                    containerId,
                                    FailureClassifier.classify(e),
                                    e.getClass().getSimpleName(),
                                    e);
						}
                        operation = "remove";
						dc.removeContainerCmd(containerId).exec();
						logger.info("event=docker_worker_removed dependency=docker operation=remove image={} networkId={} containerId={} elapsedMs={}",
                                imageName + ":" + imageVersion,
                                networkId,
                                containerId,
                                System.currentTimeMillis() - startTime);
						} catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        traceExceptionTagger.tagException(e);
                        logger.error(
                                "event=docker_worker_lifecycle_failed dependency=docker operation={} image={} networkId={} containerId={} elapsedMs={} errorType={} exceptionClass={}",
                                operation,
                                imageName + ":" + imageVersion,
                                networkId,
                                containerId,
                                System.currentTimeMillis() - startTime,
                                FailureClassifier.classify(e),
                                e.getClass().getSimpleName(),
                                e);
						} catch (Exception e) {
                            traceExceptionTagger.tagException(e);
							logger.error(
                                "event=docker_worker_lifecycle_failed dependency=docker operation={} image={} networkId={} containerId={} elapsedMs={} errorType={} exceptionClass={}",
                                operation,
                                imageName + ":" + imageVersion,
                                networkId,
                                containerId,
                                System.currentTimeMillis() - startTime,
                                FailureClassifier.classify(e),
                                e.getClass().getSimpleName(),
                                e);
					}
		        }
		    });
        } catch (RejectedExecutionException e) {
            traceExceptionTagger.tagException(e);
            logger.error(
                    "event=docker_task_rejected dependency=docker operation=spawn errorType={} exceptionClass={} activeThreads={} queuedTasks={} maxPoolSize={}",
                    FailureClassifier.classify(e),
                    e.getClass().getSimpleName(),
                    dockerPool.getActiveCount(),
                    dockerPool.getQueue().size(),
                    dockerPool.getMaximumPoolSize(),
                    e);
            throw e;
        }
	}
}
