-- Token bucket rate limiter (atomic).
--
-- KEYS[1] = rate limit key
-- ARGV[1] = capacity (max tokens)
-- ARGV[2] = refillRate (tokens per second; may be a decimal value)
-- ARGV[3] = permits
-- ARGV[4] = currentTimeMillis (caller-supplied)
--
-- Returns: {allowed (0/1), remaining (floor of token count), 0}

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local permits = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

local data = redis.call('HMGET', key, 'tokens', 'lastRefill')
local tokens = tonumber(data[1])
local lastRefill = tonumber(data[2])

if tokens == nil or lastRefill == nil then
    tokens = capacity
    lastRefill = now
end

local elapsed = now - lastRefill
if elapsed < 0 then
    elapsed = 0
end

local refilled = (elapsed / 1000.0) * rate
tokens = tokens + refilled
if tokens > capacity then
    tokens = capacity
end
lastRefill = now

local allowed = 0
if tokens >= permits then
    tokens = tokens - permits
    allowed = 1
end

redis.call('HSET', key, 'tokens', tostring(tokens), 'lastRefill', tostring(lastRefill))

-- Keep the bucket alive long enough for a full refill + safety margin.
local ttlSec = 1
if rate > 0 then
    ttlSec = math.ceil((capacity / rate) * 2)
end
if ttlSec < 1 then
    ttlSec = 1
end
redis.call('EXPIRE', key, ttlSec)

local remaining = math.floor(tokens)
return {allowed, remaining, 0}
