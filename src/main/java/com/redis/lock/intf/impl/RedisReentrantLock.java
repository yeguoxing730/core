package com.redis.lock.intf.impl;

import com.redis.lock.intf.abstact.AbstractLock;
import com.redis.lock.util.LockInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;

/**
 * Created with IntelliJ IDEA.
 * User: uc203808
 * Date: 12/12/17
 * Time: 9:06 AM
 * To change this template use File | Settings | File Templates.
 */
public class RedisReentrantLock extends AbstractLock {
    private static final String DEFAULT_LOCK_KEY = "lock.lock";
    private static final long DEFAULT_LOCK_EXPIRES = 3000L;


    private Jedis jedis;

    protected String lockKey;

    // 锁的有效时长(毫秒)
    protected long lockExpires;

    private static final Logger logger = LoggerFactory.getLogger(RedisReentrantLock.class);

    public RedisReentrantLock(Jedis jedis) {
        this(jedis, DEFAULT_LOCK_KEY, DEFAULT_LOCK_EXPIRES);
    }

    public RedisReentrantLock(Jedis jedis, String lockKey, long lockExpires) {
        this.jedis = jedis;
        this.lockKey = lockKey;
        this.lockExpires = lockExpires;
    }

    // 阻塞式获取锁的实现
    protected boolean lock(boolean useTimeout, long time, TimeUnit unit, boolean interrupt) throws InterruptedException {
        if (interrupt) {
            checkInterruption();
        }

        // 超时控制 的时间可以从本地获取, 因为这个和锁超时没有关系, 只是一段时间区间的控制
        long start = localTimeMillis();
        long timeout = unit.toMillis(time); // if !useTimeout, then it's useless

        // walkthrough
        // 1. lockKey未关联value, 直接设置lockKey, 成功获取到锁, return true
        // 2. lock 已过期, 用getset设置lockKey, 判断返回的旧的LockInfo
        // 2.1 若仍是超时的, 则成功获取到锁, return true
        // 2.2 若不是超时的, 则进入下一次循环重新开始 步骤1
        // 3. lock没过期, 判断是否是当前线程持有
        // 3.1 是, 则计数加 1, return true
        // 3.2 否, 则进入下一次循环重新开始 步骤1
        // note: 每次进入循环都检查 : 1.是否超时, 若是则return false; 2.是否检查中断(interrupt)被中断,
        // 若需检查中断且被中断, 则抛InterruptedException
        while (useTimeout ? !isTimeout(start, timeout) : true) {
            if (interrupt) {
                checkInterruption();
            }

            long lockExpireTime = serverTimeMillis() + lockExpires + 1;// 锁超时时间
            String newLockInfoJson = LockInfo.newForCurrThread(lockExpireTime).toString();
            if (jedis.setnx(lockKey, newLockInfoJson) == 1) { // 条件能成立的唯一情况就是redis中lockKey还未关联value
                // 成功获取到锁, 设置相关标识
                logger.debug("{} get lock(new), lockInfo: {}", Thread.currentThread().getName(), newLockInfoJson);
                locked = true;
                return true;
            }

            // value已有值, 但不能说明锁被持有, 因为锁可能expired了
            String currLockInfoJson = jedis.get(lockKey);
            // 若这瞬间锁被delete了
            if (currLockInfoJson == null) {
                continue;
            }

            LockInfo currLockInfo = LockInfo.fromString(currLockInfoJson);
            // 竞争条件只可能出现在锁超时的情况, 因为如果没有超时, 线程发现锁并不是被自己持有, 线程就不会去动value
            if (isTimeExpired(currLockInfo.getExpires())) {
                // 锁超时了
                LockInfo oldLockInfo = LockInfo.fromString(jedis.getSet(lockKey, newLockInfoJson));
                if (oldLockInfo != null && isTimeExpired(oldLockInfo.getExpires())) {
                    // 成功获取到锁, 设置相关标识
                    logger.debug("{} get lock(new), lockInfo: {}", Thread.currentThread().getName(), newLockInfoJson);
                    locked = true;
                    return true;
                }
            } else {
                // 锁未超时, 不会有竞争情况
                if (isHeldByCurrentThread(currLockInfo)) { // 当前线程持有
                    // 成功获取到锁, 设置相关标识
                    currLockInfo.setExpires(serverTimeMillis() + lockExpires + 1); // 设置新的锁超时时间
                    currLockInfo.incCount();
                    jedis.set(lockKey, currLockInfo.toString());
                    logger.debug("{} get lock(inc), lockInfo: {}", Thread.currentThread().getName(), currLockInfo);
                    locked = true;
                    return true;
                }
            }
        }
        locked = false;
        return false;
    }

    public boolean tryLock() {
        long lockExpireTime = serverTimeMillis() + lockExpires + 1;
        String newLockInfo = LockInfo.newForCurrThread(lockExpireTime).toString();

        if (jedis.setnx(lockKey, newLockInfo) == 1) {
            locked = true;
            return true;
        }

        String currLockInfoJson = jedis.get(lockKey);
        if (currLockInfoJson == null) {
            // 再一次尝试获取
            if (jedis.setnx(lockKey, newLockInfo) == 1) {
                locked = true;
                return true;
            } else {
                locked = false;
                return false;
            }
        }

        LockInfo currLockInfo = LockInfo.fromString(currLockInfoJson);

        if (isTimeExpired(currLockInfo.getExpires())) {
            LockInfo oldLockInfo = LockInfo.fromString(jedis.getSet(lockKey, newLockInfo));
            if (oldLockInfo != null && isTimeExpired(oldLockInfo.getExpires())) {
                locked = true;
                return true;
            }
        } else {
            if (isHeldByCurrentThread(currLockInfo)) {
                currLockInfo.setExpires(serverTimeMillis() + lockExpires + 1);
                currLockInfo.incCount();
                jedis.set(lockKey, currLockInfo.toString());
                locked = true;
                return true;
            }
        }
        locked = false;
        return false;
    }

    @Override
    public Condition newCondition() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * Queries if this lock is held by any thread.
     *
     * @return {@code true} if any thread holds this lock and {@code false}
     * otherwise
     */
    public boolean isLocked() {
        // walkthrough
        // 1. lockKey未关联value, return false
        // 2. 若 lock 已过期, return false, 否则 return true
        if (!locked) { // 本地locked为false, 肯定没加锁
            return false;
        }
        String json = jedis.get(lockKey);
        if (json == null) {
            return false;
        }
        if (isTimeExpired(LockInfo.fromString(json).getExpires())) {
            return false;
        }
        return true;
    }

    @Override
    protected void unlock0() {
        // walkthrough
        // 1. 若锁过期, return
        // 2. 判断自己是否是锁的owner
        // 2.1 是, 若 count = 1, 则删除lockKey; 若 count > 1, 则计数减 1, return
        // 2.2 否, 则抛异常 IllegalMonitorStateException, reutrn
        // done, return
        LockInfo currLockInfo = LockInfo.fromString(jedis.get(lockKey));
        if (isTimeExpired(currLockInfo.getExpires())) {
            return;
        }

        if (isHeldByCurrentThread(currLockInfo)) {
            if (currLockInfo.getCount() == 1) {
                jedis.del(lockKey);
                logger.debug("{} unlock(del), lockInfo: null", Thread.currentThread().getName());
            } else {
                currLockInfo.decCount(); // 持有锁计数减1
                String json = currLockInfo.toString();
                jedis.set(lockKey, json);
                logger.debug("{} unlock(dec), lockInfo: {}", Thread.currentThread().getName(), json);
            }
        } else {
            throw new IllegalMonitorStateException(String.format("current thread[%s] does not holds the lock", Thread.currentThread().toString()));
        }

    }

    public void release() {
        jedis.close();
    }

    public boolean isHeldByCurrentThread() {
        return isHeldByCurrentThread(LockInfo.fromString(jedis.get(lockKey)));
    }

    // ------------------- utility methods ------------------------

    private boolean isHeldByCurrentThread(LockInfo lockInfo) {
        return lockInfo.isCurrentThread();
    }

    private void checkInterruption() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }
    }

    private boolean isTimeExpired(long time) {
        return time < serverTimeMillis();
    }

    private boolean isTimeout(long start, long timeout) {
        return start + timeout < System.currentTimeMillis();
    }

    private long serverTimeMillis() {
        return Long.parseLong(jedis.time().get(0));
    }

    private long localTimeMillis() {
        return System.currentTimeMillis();
    }
}
