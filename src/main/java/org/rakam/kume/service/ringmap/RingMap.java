package org.rakam.kume.service.ringmap;

import io.netty.util.concurrent.EventExecutor;
import org.rakam.kume.Cluster;
import org.rakam.kume.Member;
import org.rakam.kume.transport.OperationContext;
import org.rakam.kume.transport.Request;
import org.rakam.kume.util.ConsistentHashRing;
import org.rakam.kume.util.FutureUtil;
import org.rakam.kume.util.ThrowableNioEventLoopGroup;
import org.rakam.kume.util.ThrowableNioEventLoopGroup;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.rakam.kume.util.ConsistentHashRing.hash;

public class RingMap<K, V> extends AbstractRingMap<RingMap, Map, K, V> {
    public RingMap(Cluster.ServiceContext<RingMap> serviceContext, Supplier<Map> mapSupplier, MapMergePolicy<V> mergePolicy, int replicationFactor) {
        super(serviceContext, mapSupplier, mergePolicy, replicationFactor);
    }

    public RingMap(Cluster.ServiceContext<RingMap> serviceContext, MapMergePolicy<V> mergePolicy, int replicationFactor) {
        super(serviceContext, ConcurrentHashMap::new, mergePolicy, replicationFactor);
    }

    public CompletableFuture<Void> merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        int bucketId = getRing().findBucketIdFromToken(hash(key));
        ConsistentHashRing.Bucket bucket = getRing().getBucket(bucketId);

        FutureUtil.MultipleFutureListener listener = new FutureUtil.MultipleFutureListener((bucket.members.size() / 2) + 1);
        for (Member next : bucket.members) {
            if (next.equals(localMember)) {
                mergeLocal(key, value, remappingFunction);
                listener.increment();
            } else {
                listener.listen(getContext().ask(next, new MergeMapOperation(key, value, remappingFunction)));
            }
        }

        return listener.get();
    }

    protected void mergeLocal(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Map<K, V> partition = getPartition(getRing().findBucketId(key));
        V oldValue = partition.get(key);
        V newValue = (oldValue == null) ? value : remappingFunction.apply(oldValue, value);
        if (newValue == null) {
            partition.remove(key);
        } else {
            partition.put(key, newValue);
        }
    }

    /**
     * Since we use ConcurrentHashMap, most of the operation may run concurrently.
     * However
     *
     * @param executor
     * @param ctx
     * @param request
     */
    @Override
    public void handle(ThrowableNioEventLoopGroup executor, OperationContext ctx, Request request) {
        if (request instanceof PartitionRestrictedMapRequest) {
            int id = ((PartitionRestrictedMapRequest) request).getPartition(this) % executor.executorCount();
            EventExecutor child = executor.getChild(id);
            if (child.inEventLoop()) {
                try {
                    request.run(this, ctx);
                } catch (Exception e) {
                    LOGGER.error("error while running throwable code block", e);
                }
            } else {
                child.execute(() -> request.run(this, ctx));
            }
        } else {
            executor.execute(() -> request.run(this, ctx));
        }
    }


    public static class MergeMapOperation implements PartitionRestrictedMapRequest<RingMap, Void> {
        private final BiFunction remappingFunction;
        Object key;
        Object value;

        public MergeMapOperation(Object key, Object value, BiFunction remappingFunction) {
            this.key = key;
            this.value = value;
            this.remappingFunction = remappingFunction;
        }

        @Override
        public void run(RingMap service, OperationContext ctx) {
            service.mergeLocal(key, value, remappingFunction);
        }

        @Override
        public int getPartition(AbstractRingMap service) {
            return service.getPartitionId(service.getRing().findBucketId(key));
        }
    }
}