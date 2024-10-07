package com.hmdp.utils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import lombok.extern.slf4j.Slf4j;

import static org.junit.Assert.*;

@SpringBootTest
@Slf4j
public class GlobalIDGeneratorTest {

    @Autowired
    GlobalIDGenerator globalIDGenerator;

    private ExecutorService executorService = Executors.newFixedThreadPool(300);

    @Test
    void testIdGenerator() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable runnable = () -> {
            for (int i = 0; i < 100; i++) {
                long id = globalIDGenerator.nextId("test");
                String idString = Long.toBinaryString(id);
                String increment = idString.substring(idString.length() - 32, idString.length() - 1);
                String timestamp = idString.substring(0, idString.length() - 32);
                System.out.println(timestamp + " " + increment);
            }
            countDownLatch.countDown();
        };
        System.out.println("start");
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(runnable);
        }
        long end = System.currentTimeMillis();
        countDownLatch.await();
        System.out.println("time：" + (end - start) + "ms");
    }

    @Test
    void testIdGenerator2() throws InterruptedException {
        testIdGenerator();
        System.out.println("testIdGenerator2");
        testIdGenerator();
    }

    @AfterEach
    void tearDown() {
        executorService.shutdown();
    }
}