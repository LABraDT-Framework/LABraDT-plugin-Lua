local machine_id = machine_id or "machine-01"

print("[lua→external] Preparing outbound API call for " .. machine_id)
print("[lua→external] Target: external-httpbin (POST echo service)")
print("[lua→external] This validates external network reachability from the runtime")

local result = triggerNestedService("external-httpbin")

if not result or result == "" then
    return "FAILED: no response from external-httpbin"
end

local bytes = string.len(result)
print(string.format("[lua→external] Received %d bytes", bytes))

-- httpbin echoes back the request; a valid response contains 'url'
if result:find('"url"') then
    return string.format("lua→external OK | machine=%s httpbin reachable bytes=%d", machine_id, bytes)
else
    return string.format("lua→external WARN | machine=%s unexpected response bytes=%d", machine_id, bytes)
end
