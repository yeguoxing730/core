package lock;

import com.redis.lock.intf.impl.RedisReentrantLock;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Created with IntelliJ IDEA.
 * User: uc203808
 * Date: 12/12/17
 * Time: 9:25 AM
 * To change this template use File | Settings | File Templates.
 */
public class ReentrantTest {
    @Test
    public void test() throws Exception {
        // 创建5个线程不停地去重入(随机次数n, 0 <= n <=5)获取锁
        List<Thread> threads = createThreads(5);
        //开始任务
        for (Thread t : threads) {
            t.start();
        }
        // 执行60秒
        Thread.sleep(60 * 1000);
        //停止所有线程
        Task.alive = false;
        // 等待所有线程终止
        for (Thread t : threads) {
            t.join();
        }

    }
    // 创建count个线程，每个线程都是不同的jedis连接以及不同的与时间服务器的连接
    private List<Thread> createThreads(int count) throws IOException {
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 0; i < count; i++) {
            Jedis jedis = new Jedis("localhost", 6379);
            RedisReentrantLock lock = new RedisReentrantLock(jedis);
            Task task = new Task(lock);
            Thread t = new Thread(task);
            threads.add(t);
        }
        return threads;
    }

    private static class Task implements Runnable {

        private RedisReentrantLock lock;

        private final int MAX_ENTRANT = 5;

        private final Random random = new Random();

        private static boolean alive = true;

        Task(RedisReentrantLock lock) {
            this.lock = lock;
        }

        public void run() {
            while (alive) {
                int times = random.nextInt(MAX_ENTRANT);
                doLock(times);
            }
        }

        private void doLock(int times) {
            if (lock.tryLock(5, TimeUnit.SECONDS)) {
                try {
                    if (times > 0) {
                        doLock(--times);
                    }
                } finally {
                    if (lock != null) {
                        lock.unlock();
                    }
                }
            }

        }



    }
}
