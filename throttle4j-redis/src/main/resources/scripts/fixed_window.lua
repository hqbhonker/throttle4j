-- Fixed window rate limiter (atomic).
--
-- KEYS[1] = rate limit key
-- ARGV[1] = limit (max permits in window)
-- ARGV[2] = windowMillis (window length in ms)
-- ARGV[3] = permits (permits to acquire)
--
-- Returns: {allowed (0/1), remaining, ttlMillis}
--   ttlMillis is the relative time until the window resets.

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local permits = tonumber(ARGV[3])

local current = tonumber(redis.call('GET', key))
if current == nil then
    current = 0
end

local ttl = redis.call('PTTL', key)
if ttl == nil or ttl < 0 then
    ttl = window
end

if current + permits > limit then
    local remaining = limit - current
    if remaining < 0 then
        remaining = 0
    end
    return {0, remaining, ttl}
end

if current == 0 then
    redis.call('SET', key, permits, 'PX', window)
    ttl = window
else
    redis.call('INCRBY', key, permits)
end

local remaining = limit - current - permits
if remaining < 0 then
    remaining = 0
end
return {1, remaining, ttl}
