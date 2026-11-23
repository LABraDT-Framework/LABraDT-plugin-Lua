local machine_id = machine_id or "machine-01"

print("[lua→docker-image] Probing pre-built image service for " .. machine_id)
print("[lua→docker-image] Sending trigger to docker-image-hello...")

local result = triggerNestedService("docker-image-hello")

if not result or result == "" then
    return "FAILED: no response from docker-image-hello"
end

-- Take only the first meaningful line (pre-built images can have verbose output)
local first_line = result:match("([^\n]+)") or result
print("[lua→docker-image] Image responded: " .. first_line)

return string.format("lua→docker-image OK | machine=%s image_output: %s", machine_id, first_line)
