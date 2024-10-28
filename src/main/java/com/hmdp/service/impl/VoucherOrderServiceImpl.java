package com.hmdp.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.GlobalIDGenerator;
import com.hmdp.utils.UserHolder;

import lombok.extern.slf4j.Slf4j;

import static com.hmdp.utils.RedisConstants.LOCK_VOUCHER_ORDER_KEY;

/**
 * @author Ernie Lee
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final BlockingQueue<VoucherOrder> VOUCHER_ORDER_QUEUE = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_VOUCHER_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final DefaultRedisScript<Long> SECKILL_PREORDER_SCRIPT;

    static {
        SECKILL_PREORDER_SCRIPT = new DefaultRedisScript<>();
        SECKILL_PREORDER_SCRIPT.setLocation(new ClassPathResource("seckill_preorder.lua"));
        SECKILL_PREORDER_SCRIPT.setResultType(Long.class);
    }

    private boolean isRunning = true;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private GlobalIDGenerator globalIDGenerator;

    private IVoucherOrderService proxy;

    @PostConstruct
    public void init() {
        SECKILL_VOUCHER_ORDER_EXECUTOR.execute(() -> {
            while (isRunning) {
                try {
                    VoucherOrder voucherOrder = VOUCHER_ORDER_QUEUE.take();
                    SeckillVoucher voucher = seckillVoucherService.getById(voucherOrder.getVoucherId());

                    Result validateVoucherResult = validateVoucher(voucher);
                    if (!validateVoucherResult.getSuccess()) {
                        log.error("failed validate voucher: voucherId=" + voucher.getVoucherId() + ", msg=" + validateVoucherResult.getErrorMsg());
                        continue;
                    }

                    RLock lock = redissonClient.getLock(LOCK_VOUCHER_ORDER_KEY + voucherOrder.getUserId());
                    boolean locked = lock.tryLock();
                    if (!locked) {
                        continue;
                    }

                    Result result = validateOneVoucherPerUser(voucherOrder.getUserId(), voucherOrder.getVoucherId());
                    if (!result.getSuccess()) {
                        log.error("failed validate user: userId=" + voucherOrder.getUserId() + ", voucherId=" + voucher.getVoucherId() + ", msg=" + result.getErrorMsg());
                        continue;
                    }

                    try {
                        log.debug("creating voucher order: voucherId=" + voucherOrder.getVoucherId() + ", userId=" + voucherOrder.getUserId());
                        proxy.createVoucherOrder(voucherOrder);
                        log.debug("created voucher order: voucherId=" + voucherOrder.getVoucherId() + ", userId=" + voucherOrder.getUserId());
                    } finally {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    log.error("failed to process voucher order", e);
                }
            }
        });
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Result result = preorderVoucher(userId, voucherId);
        if (!result.getSuccess()) {
            return result;
        }

        long voucherOrderId = globalIDGenerator.nextId("voucher_order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(globalIDGenerator.nextId("voucher_order"));
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);

        assignVoucherOrder(voucherOrder);
        return Result.ok(voucherOrderId);
    }

    private void assignVoucherOrder(VoucherOrder voucherOrder) {
        if (proxy == null) {
            proxy = (IVoucherOrderService) AopContext.currentProxy();
        }
        VOUCHER_ORDER_QUEUE.add(voucherOrder);
    }

    private Result preorderVoucher(Long userId, Long voucherId) {
        Long preorderResult = redisTemplate.execute(SECKILL_PREORDER_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        if (preorderResult == null) {
            return Result.fail("preorder failed");
        }
        if (preorderResult == -1L) {
            return Result.fail("voucher not found");
        }
        if (preorderResult == -2L) {
            return Result.fail("voucher is out of stock");
        }
        if (preorderResult == -3L) {
            return Result.fail("user has already ordered this voucher");
        }
        if (preorderResult != 0L) {
            return Result.fail("unknown error");
        }
        return Result.ok();
    }

    private static Result validateVoucher(SeckillVoucher voucher) {
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("voucher is not available yet");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("voucher is expired");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("voucher is out of stock");
        }
        return Result.ok();
    }

    private Result validateOneVoucherPerUser(Long userId, Long voucherId) {
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("user has already ordered this voucher");
        }
        return Result.ok();
    }

    @Transactional
    @Override
    public Result createVoucherOrder(Long userId, Long voucherId) {
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(globalIDGenerator.nextId("voucher_order"));
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        return createVoucherOrder(voucherOrder);
    }

    @Transactional
    @Override
    public Result createVoucherOrder(VoucherOrder voucherOrder) {
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("order failed");
        }
        save(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }

    @PreDestroy
    public void destroy() {
        isRunning = false;
        SECKILL_VOUCHER_ORDER_EXECUTOR.shutdown();
    }
}
