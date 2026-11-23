local machine_id = machine_id or "machine-01"

print("[lua→docker-file] Launching container diagnostic for " .. machine_id)
print("[lua→docker-file] Container will report host identity and wall-clock time")

local result = triggerNestedService("docker-file-hello")

if not result or result == "" then
    return "FAILED: no response from docker-file-hello"
end

print("[lua→docker-file] Container responded: " .. result)

-- Extract host and time if present
local host = result:match("host=(%S+)") or "unknown"
local time = result:match("time=(%S+)") or "unknown"

return string.format("lua→docker-file OK | machine=%s container_host=%s container_time=%s",
    machine_id, host, time)
