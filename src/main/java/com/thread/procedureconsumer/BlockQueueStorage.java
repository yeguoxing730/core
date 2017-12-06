package com.thread.procedureconsumer;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created with IntelliJ IDEA.
 * User: uc203808
 * Date: 10/19/17
 * Time: 2:24 PM
 * To change this template use File | Settings | File Templates.
 */
public class BlockQueueStorage implements Storage {
    // 仓库最大存储量
    private final int MAX_SIZE = 100;

    // 仓库存储的载体
    private LinkedBlockingQueue<Object> list = new LinkedBlockingQueue<Object>(
            100);
    // 生产num个产品
    @Override
    public void produce(int num) {
        // 如果仓库剩余容量为0
        if (list.size() == MAX_SIZE)
        {
            System.out.println("【库存量】:" + MAX_SIZE + "/t暂时不能执行生产任务!");
        }

        // 生产条件满足情况下，生产num个产品
        for (int i = 1; i <= num; ++i)
        {
            try
            {
                // 放入产品，自动阻塞
                list.put(new Object());
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            System.out.println("【现仓储量为】:" + list.size());
        }
    }
    // 消费num个产品
    @Override
    public void consume(int num) {
        // 如果仓库存储量不足
        if (list.size() == 0)
        {
            System.out.println("【库存量】:0/t暂时不能执行生产任务!");
        }

        // 消费条件满足情况下，消费num个产品
        for (int i = 1; i <= num; ++i)
        {
            try
            {
                // 消费产品，自动阻塞
                list.take();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        System.out.println("【现仓储量为】:" + list.size());
    }

    @Override
    public int getMAX_SIZE() {
        return MAX_SIZE;
    }
}
