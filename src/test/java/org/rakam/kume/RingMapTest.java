package org.rakam.kume;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.junit.Test;
import org.rakam.kume.service.ringmap.RingMap;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by buremba <Burak Emre Kabakcı> on 23/11/14 19:07.
 */
public class RingMapTest extends KumeTest {

    @Test
    public void test() {
        HazelcastInstance hz = Hazelcast.newHazelcastInstance(new Config());
        HazelcastInstance hz1 = Hazelcast.newHazelcastInstance(new Config());

        long l = System.currentTimeMillis();
        IMap<String, Integer> test = hz.getMap("test");
        for (int i = 0; i < 100000; i++) {
            test.put("test"+i, 3);
        }
        System.out.println(System.currentTimeMillis() - l);
    }

    @Test
    public void testMap() throws InterruptedException, TimeoutException, ExecutionException {
        ServiceInitializer services = new ServiceInitializer()
            .add(bus -> new RingMap(bus, 2));

        Cluster cluster0 = new ClusterBuilder().services(services).start();
        Cluster cluster1 = new ClusterBuilder().services(services).start();

        waitForDiscovery(cluster0, 1);

        RingMap ringMap0 = cluster0.getService(RingMap.class);
        RingMap ringMap1 = cluster1.getService(RingMap.class);

        long l = System.currentTimeMillis();
        for (int i = 0; i < 100000; i++) {
            ringMap0.put("test"+i, 5).get();
        }

        System.out.println(System.currentTimeMillis() - l);
        System.out.println(ringMap1.getLocalSize());
        System.out.println(ringMap0.getLocalSize());

//        Object test = ringMap1.get("test").get().getData();
//        assertEquals(test, 5);
    }

    @Test
    public void testMapNewNode() throws InterruptedException, TimeoutException, ExecutionException {
        ServiceInitializer services = new ServiceInitializer()
            .add(bus -> new RingMap(bus, 2));

        Cluster cluster0 = new ClusterBuilder().services(services).start();

        RingMap ringMap0 = cluster0.getService(RingMap.class);

        for (int i = 0; i < 1000; i++) {
            ringMap0.put("test"+i, 5).get();
        }

        Cluster cluster1 = new ClusterBuilder().services(services).start();

        RingMap ringMap1 = cluster1.getService(RingMap.class);
        waitForMigrationEnd(ringMap1);

        Integer size = ringMap1.size().get().values().stream().reduce((x, y) -> x + y).get();
        assertEquals(size.intValue(), 2000);
    }

    @Test
    public void testMapNodeFailure() throws InterruptedException, TimeoutException, ExecutionException {
        ServiceInitializer services = new ServiceInitializer()
            .add(bus -> new RingMap(bus, 2));

        Cluster cluster0 = new ClusterBuilder().services(services).start();
        Cluster cluster1 = new ClusterBuilder().services(services).start();
        Cluster cluster2 = new ClusterBuilder().services(services).start();

        waitForDiscovery(cluster0, 3);
        waitForDiscovery(cluster1, 3);
        waitForDiscovery(cluster2, 3);

        RingMap ringMap0 = cluster0.getService(RingMap.class);

        for (int i = 0; i < 1000; i++) {
            ringMap0.put("test"+i, 5).get();
        }

        cluster2.close();
        waitForMigrationEnd(ringMap0);

        Optional<Integer> test = ringMap0.size().get().values().stream().reduce((i0, i1) -> i0 + i1);
        assertTrue(test.isPresent());
        assertEquals(test.get().intValue(), 200);
    }

    private void waitForMigrationEnd(RingMap ringMap0) throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        System.out.println("adding listener");
        ringMap0.listenMigrations(new MigrationListener() {

            @Override
            public void migrationStart(Member removedMember) {

            }

            @Override
            public void migrationEnd(Member removedMember) {
                countDownLatch.countDown();
            }
        });
        countDownLatch.await();
    }

    @Test
    public void testMapMultipleThreads() throws InterruptedException, TimeoutException, ExecutionException {
        ServiceInitializer services = new ServiceInitializer()
            .add(bus -> new RingMap(bus, 2));

        Cluster cluster0 = new ClusterBuilder().services(services).start();
        new ClusterBuilder().services(services).start();

        waitForDiscovery(cluster0, 1);

        RingMap ringMap0 = cluster0.getService(RingMap.class);
        RingMap ringMap1 = cluster0.getService(RingMap.class);

        CountDownLatch countDownLatch = new CountDownLatch(2);

        for (int i = 0; i < 10; i++) {
            final int finalI = i;
            new Thread(() -> {
                try {
                    for (int i1 = 0; i1 < 100; i1++) {
                        ringMap0.put("s0"+ finalI+i1, finalI+i1).get();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                countDownLatch.countDown();
            }).run();
            new Thread(() -> {
                try {
                    for (int i1 = 0; i1 < 100; i1++) {
                        ringMap1.put("s1"+ finalI+i1, finalI+i1).get();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                countDownLatch.countDown();
            }).run();
        }

        countDownLatch.await();
        Optional<Integer> test = ringMap1.size().get().values().stream().reduce((i0, i1) -> i0 + i1);
        assertTrue(test.isPresent());
        assertEquals(test.get().intValue(), 2000*2);
    }
}
