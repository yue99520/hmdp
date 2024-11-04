package com.hmdp.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
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

import io.lettuce.core.Range;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Ernie Lee
 */
@Slf4j
@Service
public class VoucherOrderServiceRedisAsyncImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final String VOUCHER_ORDER_STREAM_NAME = "stream.orders";
    private static final ExecutorService SECKILL_VOUCHER_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final DefaultRedisScript<Long> SECKILL_PREORDER_SCRIPT;

    static {
        SECKILL_PREORDER_SCRIPT = new DefaultRedisScript<>();
        SECKILL_PREORDER_SCRIPT.setLocation(new ClassPathResource("seckill_preorder2.lua"));
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

    @Autowired
    private IVoucherOrderService proxy;

    @PostConstruct
    public void init() {
        SECKILL_VOUCHER_ORDER_EXECUTOR.execute(() -> {
            while (isRunning) {
                List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(VOUCHER_ORDER_STREAM_NAME, ReadOffset.from("0")));
                if (list == null || list.isEmpty()) {
                    break;
                }
                handleMessage(list.get(0));
            }
            while (isRunning) {
                List<MapRecord<String, Object, Object>> list = redisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(VOUCHER_ORDER_STREAM_NAME, ReadOffset.lastConsumed()));
                if (list == null || list.isEmpty()) {
                    continue;
                }
                handleMessage(list.get(0));
            }
        });
    }

    private void handleMessage(MapRecord<String, Object, Object> record) {
        try {
            VoucherOrder voucherOrder = toVoucherOrder(record);
            handleVoucherOrder(voucherOrder);
        } catch (Throwable e) {
            log.error("failed to process voucher order", e);
        } finally {
            redisTemplate.opsForStream().acknowledge(VOUCHER_ORDER_STREAM_NAME, "g1", record.getId());
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        log.info("voucher {}", voucherOrder.getVoucherId());
        SeckillVoucher voucher = seckillVoucherService.getById(voucherOrder.getVoucherId());

        Result validateVoucherResult = validateVoucher(voucher);
        if (!validateVoucherResult.getSuccess()) {
            log.error("invalid voucher: voucherId={}, msg={}", voucher.getVoucherId(), validateVoucherResult.getErrorMsg());
            throw new IllegalArgumentException("invalid voucher");
        }

        Result result = validateOneVoucherPerUser(voucherOrder.getUserId(), voucherOrder.getVoucherId());
        if (!result.getSuccess()) {
            log.error("invalid user: userId={}, voucherId={}, msg={}", voucherOrder.getUserId(), voucher.getVoucherId(), result.getErrorMsg());
            throw new IllegalArgumentException("invalid voucher");
        }

        proxy.createVoucherOrder(voucherOrder);
        log.info("new voucher order is created: userId={}, voucherId={}", voucherOrder.getUserId(), voucherOrder.getVoucherId());
    }

    private VoucherOrder toVoucherOrder(MapRecord<String, Object, Object> record) {
        Map<Object, Object> recordValues = record.getValue();
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(Optional
                .ofNullable(recordValues.get("id"))
                .map(Object::toString)
                .map(Long::parseLong)
                .orElseThrow(() -> new IllegalStateException("invalid voucher order id")));
        voucherOrder.setUserId(Optional
                .ofNullable(recordValues.get("userId"))
                .map(Object::toString)
                .map(Long::parseLong)
                .orElseThrow(() -> new IllegalStateException("invalid user id")));
        voucherOrder.setVoucherId(Optional
                .ofNullable(recordValues.get("voucherId"))
                .map(Object::toString)
                .map(Long::parseLong)
                .orElseThrow(() -> new IllegalStateException("invalid voucher id")));
        return voucherOrder;
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

    @Override
    public Result seckillVoucher(Long voucherId) {
//        initializeProxyService();
        Long userId = UserHolder.getUser().getId();
        long voucherOrderId = globalIDGenerator.nextId("voucher_order");

        Result result = preorderVoucher(userId, voucherId, voucherOrderId);
        if (!result.getSuccess()) {
            return result;
        }
        return Result.ok(voucherOrderId);
    }

    private void initializeProxyService() {
        if (proxy == null) {
            proxy = (IVoucherOrderService) AopContext.currentProxy();
        }
    }

    private Result preorderVoucher(Long userId, Long voucherId, Long orderId) {
        Long preorderResult = redisTemplate.execute(SECKILL_PREORDER_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString(), orderId.toString());
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
