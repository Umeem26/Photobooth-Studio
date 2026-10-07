package hardware;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

class CameraManagerTest {

    @Test
    void getInstanceReturnsSameObjectAcrossThreads() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Set<CameraManager> seen = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    start.await();
                    seen.add(CameraManager.getInstance());
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) f.get();
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, seen.size());
    }

    @Test
    void constructorIsPrivateAndClassIsFinal() {
        Constructor<?>[] ctors = CameraManager.class.getDeclaredConstructors();
        assertEquals(1, ctors.length);
        assertTrue(Modifier.isPrivate(ctors[0].getModifiers()));
        assertTrue(Modifier.isFinal(CameraManager.class.getModifiers()));
    }

    @Test
    void closeWithoutOpeningDoesNotTouchHardware() {
        // Instance lazy: close sebelum kamera dipakai harus aman (no-op)
        assertDoesNotThrow(() -> CameraManager.getInstance().close());
    }
}
