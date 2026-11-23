-- operator script: checks machine sensor temperature value against threshold
-- Variables 'temperature', 'max_temp'

-- Collect alerts
local alerts = {}

-- Check temperature
if temperature then
    if temperature > max_temp then
        alerts[#alerts + 1] = "WARNING: Temperature " .. temperature .. "C exceeds limit " .. max_temp .. "C"
    else
        print("Temperature OK: " .. temperature .. "C")
    end
else
    print("No temperature data available")
end

-- Report results
if #alerts > 0 then
    print("--- ALERTS ---")
    for i, alert in ipairs(alerts) do
        print(alert)
    end
    return "ALERT: " .. #alerts .. " threshold violations detected"
else
    return "ALL CLEAR: All sensors within normal range"
end
