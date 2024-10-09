package com.hmdp.service.impl;

import java.time.LocalDateTime;
import java.util.UUID;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.GlobalIDGenerator;
import com.hmdp.utils.Lock;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;

import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.hmdp.utils.RedisConstants.LOCK_ORDER_KEY;

/**
 * @author Ernie Lee
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private GlobalIDGenerator globalIDGenerator;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("voucher is not available yet");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("voucher is expired");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("voucher is out of stock");
        }

        Long userId = UserHolder.getUser().getId();
        Lock lock = new SimpleRedisLock(LOCK_ORDER_KEY + ":" + userId, redisTemplate);

        boolean locked = lock.tryLock( 15);
        if (!locked) {
            return Result.fail("voucher order is processing");
        }
        try {
            // Get the proxy object of the transactional method
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(userId, voucherId);
        } finally {
            lock.unlock();
        }
    }

    @Transactional(timeout = 10)
    public Result createVoucherOrder(Long userId, Long voucherId) {
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("user has already ordered this voucher");
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("order failed");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(globalIDGenerator.nextId("voucher_order"));
        voucherOrder.setUserId(UserHolder.getUser().getId());
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }
}
