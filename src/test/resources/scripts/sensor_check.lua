-- operator script: checks machine sensor values against thresholds
-- Variables 'temperature', 'pressure', 'rpm' 

-- Define thresholds
local max_temp = 80
local max_pressure = 10
local max_rpm = 3000

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

-- Check pressure
if pressure then
    if pressure > max_pressure then
        alerts[#alerts + 1] = "WARNING: Pressure " .. pressure .. " bar exceeds limit " .. max_pressure .. " bar"
    else
        print("Pressure OK: " .. pressure .. " bar")
    end
else
    print("No pressure data available")
end

-- Check RPM
if rpm then
    if rpm > max_rpm then
        alerts[#alerts + 1] = "WARNING: RPM " .. rpm .. " exceeds limit " .. max_rpm
    else
        print("RPM OK: " .. rpm)
    end
else
    print("No RPM data available")
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
