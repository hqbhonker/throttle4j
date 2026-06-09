-- Sliding window rate limiter using Redis Sorted Set (atomic).
--
-- KEYS[1] = rate limit key
-- ARGV[1] = limit
-- ARGV[2] = windowMillis
-- ARGV[3] = permits
-- ARGV[4] = currentTimeMillis (caller-supplied)
-- ARGV[5] = uniqueId (used to disambiguate sorted-set members)
--
-- Returns: {allowed (0/1), remaining, resetAtMillis}
--   resetAtMillis is the absolute epoch-ms when the oldest record exits the window.

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local permits = tonumber(ARGV[3])
local now = tonumber(ARGV[4])
local uniqueId = ARGV[5]

-- Drop entries that are out of the window.
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

local count = tonumber(redis.call('ZCARD', key))

if count + permits > limit then
    local remaining = limit - count
    if remaining < 0 then
        remaining = 0
    end
    local resetAt = now + window
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    if oldest and #oldest >= 2 then
        resetAt = tonumber(oldest[2]) + window
    end
    return {0, remaining, resetAt}
end

for i = 1, permits do
    redis.call('ZADD', key, now, uniqueId .. ':' .. i)
end
redis.call('PEXPIRE', key, window)

local remaining = limit - count - permits
if remaining < 0 then
    remaining = 0
end
local resetAt = now + window
return {1, remaining, resetAt}
