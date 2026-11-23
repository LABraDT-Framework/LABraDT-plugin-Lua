local machine_id = machine_id or "machine-01"
local warn_temp   = tonumber(warn_threshold) or 75.0

-- Simulate reading current sensor value (real system: injected via defaultArgs)
local current_temp = 68.3

local status
if current_temp >= warn_temp then
    status = "WARNING"
else
    status = "ONLINE"
end

print(string.format("[lua-hello] machine=%s temp=%.1f threshold=%.1f → %s",
    machine_id, current_temp, warn_temp, status))

return string.format("machine=%s temp=%.1f status=%s ts=%d",
    machine_id, current_temp, status, os.time())
