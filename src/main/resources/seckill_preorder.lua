---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by liyue.
--- DateTime: 2024/10/24 下午12:42
---

local voucherId = ARGV[1]
local userId = ARGV[2]

local voucherStockKey = "seckill:stock:" .. voucherId
local voucherStock = redis.call("get", voucherStockKey)
if voucherStock == nil then
    return -1
end

if tonumber(voucherStock) <= 0 then
    return -2
end

local userOrderSetKey = "seckill:order:" .. voucherId
local userWasOrdered = redis.call("sismember", userOrderSetKey, userId)
if userWasOrdered == 1 then
    return -3
end

redis.call("incrby", voucherStockKey, -1)
redis.call("sadd", userOrderSetKey, userId)

return 0

