package com.redis.lock.miskiller;

import com.redis.lock.intf.impl.RedisBasedDistributedLock;
import com.redis.lock.util.RedisUtil;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

public class PessClientThread implements Runnable {
    String key = "prdNum";// 商品主键
    String clientList = "clientList";// // 抢购到商品的顾客列表主键
    String clientName;
    RedisBasedDistributedLock redisBasedDistributedLock;
    Jedis jedis = null;
    SocketAddress addr = new InetSocketAddress("localhost", 9999);

    public PessClientThread(int num) {
        clientName = "编号=" + num;
        init();
    }

    public void init() {
        jedis = RedisUtil.getInstance().getJedis();

        try {
            redisBasedDistributedLock = new RedisBasedDistributedLock(jedis, "lock.lock", 5 * 1000, addr);
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    public void run() {
        try {
            Thread.sleep((int) (Math.random() * 5000));// 随机睡眠一下
        } catch (InterruptedException e1) {
        }

        while (true) {
            //先判断缓存是否有商品
            if (Integer.valueOf(jedis.get(key)) <= 0) {
                break;
            }

            //缓存还有商品，取锁，商品数目减去1
            System.out.println("顾客:" + clientName + "开始抢商品");
            if (redisBasedDistributedLock.tryLock(3, TimeUnit.SECONDS)) { //等待3秒获取锁，否则返回false
                int prdNum = Integer.valueOf(jedis.get(key)); //再次取得商品缓存数目
                if (prdNum > 0) {
                    jedis.decr(key);//商品数减1
                    jedis.sadd(clientList, clientName);// 抢到商品记录一下
                    System.out.println("好高兴，顾客:" + clientName + "抢到商品");
                } else {
                    System.out.println("悲剧了，库存为0，顾客:" + clientName + "没有抢到商品");
                }
                redisBasedDistributedLock.unlock();
                break;
            }
        }
        //释放资源
        redisBasedDistributedLock = null;
        RedisUtil.returnResource(jedis);
    }

}
