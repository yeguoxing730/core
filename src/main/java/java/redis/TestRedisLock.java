package java.redis;

import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisShardInfo;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * Created with IntelliJ IDEA.
 * User: uc203808
 * Date: 12/4/17
 * Time: 4:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestRedisLock {
    Logger logger = Logger.getLogger(getClass());
    public static JedisPool pool;
    public static Jedis jedis;
    static RedisConnection connection;
    public static final Object LOCK = new Object();
    @BeforeClass
    public static void before(){
        if (pool == null) {
            try {
                JedisPoolConfig config = new JedisPoolConfig();
                //控制一个pool最多有多少个状态为idle(空闲的)的jedis实例。
                config.setMaxIdle(300);
                config.setMaxTotal(1000);
                //表示当borrow(引入)一个jedis实例时，最大的等待时间，如果超过等待时间，则直接抛出JedisConnectionException；
                config.setMaxWaitMillis(1000);
                //在borrow一个jedis实例时，是否提前进行validate操作；如果为true，则得到的jedis实例均是可用的；
                config.setTestOnBorrow(true);
                pool = new JedisPool(config, "127.0.0.1", 2189);

                jedis = pool.getResource();
                JedisConnectionFactory jcf = new JedisConnectionFactory(config);
                JedisShardInfo shardInfo = new JedisShardInfo("127.0.0.1", 2189);
                jcf.setShardInfo(shardInfo);
                jcf.setUsePool(true);
                jcf.setHostName("127.0.0.1");
                jcf.setPort(2189);
                connection = jcf.getConnection();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    @Test
    public void testAdd(){
        Jedis jedis = pool.getResource();
        long count = jedis.setnx("test", String.valueOf(System.currentTimeMillis()));
        long time = jedis.ttl("test");
        System.out.println("过期时间：" + time);
        logger.debug(count);
        jedis.close();

    }

    @Test
    public void testTryLock()	{
        RedisLockUtil util = new RedisLockUtil();
        for(int i = 0; i < 10; i++){
            RedisLockThread redisLockThread = new RedisLockThread(util);
            Thread thread = new Thread(redisLockThread, "LOCK_THREAD_" + i);
            thread.start();
        }
        //监控redis连接池使用状态
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    System.out.println("当前存活的线程:" + pool.getNumActive());
                    System.out.println("当前空闲的线程:" + pool.getNumIdle());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
        try {
            Thread.sleep(1000 * 3600);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static AtomicInteger count = new AtomicInteger(0);
    public static synchronized Jedis getJedis(){
        System.out.println("获得jedis连接" + count.incrementAndGet());
        return pool.getResource();
    }
}