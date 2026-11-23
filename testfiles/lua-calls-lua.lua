local machine_id = machine_id or "machine-01"

if not machine_id or machine_id == "" then
    return "ABORT: missing machine_id argument"
end

print("[lua→lua] Starting health-check pipeline for " .. machine_id)
print("[lua→lua] Delegating to lua-hello status service...")

local result = triggerNestedService("lua-hello")

if not result or result == "" then
    return "FAILED: no response from lua-hello"
end

-- Parse the status out of "machine=X temp=Y status=Z ts=N"
local status = result:match("status=(%S+)") or "UNKNOWN"
local temp   = result:match("temp=([%d%.]+)") or "?"

print(string.format("[lua→lua] Received: %s", result))
print(string.format("[lua→lua] Parsed: status=%s temp=%s", status, temp))

if status == "ONLINE" then
    return string.format("lua→lua OK | machine=%s status=ONLINE temp=%s°C", machine_id, temp)
elseif status == "WARNING" then
    return string.format("lua→lua WARN | machine=%s temp=%s°C exceeds threshold", machine_id, temp)
else
    return string.format("lua→lua UNKNOWN | raw=%s", result)
end
