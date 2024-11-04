package com.hmdp.service.impl;

import java.time.LocalDateTime;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.GlobalIDGenerator;
import com.hmdp.utils.UserHolder;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static com.hmdp.utils.RedisConstants.LOCK_VOUCHER_ORDER_KEY;

/**
 * @author Ernie Lee
 */
//@Service
public class VoucherOrderServiceSynchronizedImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private GlobalIDGenerator globalIDGenerator;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        Result validateVoucherResult = validateVoucher(voucher);
        if (!validateVoucherResult.getSuccess()) return validateVoucherResult;

        Long userId = UserHolder.getUser().getId();

        RLock lock = redissonClient.getLock(LOCK_VOUCHER_ORDER_KEY + userId);
        boolean locked = lock.tryLock();
        if (!locked) {
            return Result.fail("voucher order is processing");
        }

        Result result = validateOneVoucherPerUser(userId, voucherId);
        if (!result.getSuccess()) return result;

        try {
            // Get the proxy object of the transactional method
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(userId, voucherId);
        } finally {
            lock.unlock();
        }
    }

    private Result validateOneVoucherPerUser(Long userId, Long voucherId) {
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("user has already ordered this voucher");
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
}
