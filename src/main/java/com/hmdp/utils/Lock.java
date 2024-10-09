package com.hmdp.utils;

public interface Lock {

    boolean tryLock(long timeoutSeconds);

    void unlock();
}
