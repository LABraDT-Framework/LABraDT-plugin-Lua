package de.labradt.serviceexecution.runtime.script.interpreter;

import de.labradt.serviceexecution.runtime.script.interpreter.lua.LuaInterpreter;
import de.labradt.serviceexecution.runtime.script.interpreter.lua.LuaScriptBridge;
import de.labradt.serviceexecution.core.api.dtos.ExecutionRequestDTO;
import de.labradt.serviceexecution.core.api.dtos.ExecutionResponseDTO;
import de.labradt.serviceexecution.core.api.dtos.ServiceTriggerCommand;
import de.labradt.serviceexecution.core.api.interfaces.IServiceOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the LuaInterpreter using real Lua script execution via LuaJ.
 */
class LuaInterpreterTest {

    private LuaInterpreter interpreter;
    private LuaScriptBridge mockBridge;

    @BeforeEach
    void setUp() {
        // Create a mock LuaScriptBridge for basic tests that don't use nested services
        mockBridge = mock(LuaScriptBridge.class);
        interpreter = new LuaInterpreter(mockBridge);
    }

    @Test
    @DisplayName("Execute simple arithmetic and return result")
    void shouldExecuteSimpleArithmetic() throws Exception {
        String script = "return 2 + 3";
        String result = interpreter.execute(script, Map.of());
        assertEquals("5", result);
    }

    @Test
    @DisplayName("Capture print() output")
    void shouldCapturePrintOutput() throws Exception {
        String script = "print('Hello from Lua')";
        String result = interpreter.execute(script, Map.of());
        assertEquals("Hello from Lua", result);
    }

    @Test
    @DisplayName("Capture both print output and return value")
    void shouldCapturePrintAndReturnValue() throws Exception {
        String script = "print(\"Computing...\")\nreturn 42";
        String result = interpreter.execute(script, Map.of());
        assertTrue(result.contains("Computing..."));
        assertTrue(result.contains("42"));
    }

    @Test
    @DisplayName("Inject string arguments as Lua globals")
    void shouldInjectStringArguments() throws Exception {
        String script = "return 'Machine: ' .. machine_name";
        Map<String, String> args = Map.of("machine_name", "OSACA-Mill-01");
        String result = interpreter.execute(script, args);
        assertEquals("Machine: OSACA-Mill-01", result);
    }

    @Test
    @DisplayName("Inject numeric arguments and use them in comparisons")
    void shouldInjectNumericArguments() throws Exception {
        String script = "if temperature > 80 then\n    return \"OVERHEAT\"\nelse\n    return \"OK\"\nend";
        String resultOk = interpreter.execute(script, Map.of("temperature", "50"));
        assertEquals("OK", resultOk);

        String resultOverheat = interpreter.execute(script, Map.of("temperature", "95"));
        assertEquals("OVERHEAT", resultOverheat);
    }

    @Test
    @DisplayName("Handle multiple arguments simultaneously")
    void shouldHandleMultipleArguments() throws Exception {
        String script = "return temperature + pressure + rpm";
        Map<String, String> args = Map.of(
                "temperature", "25",
                "pressure", "5",
                "rpm", "1000"
        );
        String result = interpreter.execute(script, args);
        assertEquals("1030", result);
    }

    @Test
    @DisplayName("Each execution is isolated - no state leaks between runs")
    void shouldIsolateExecutions() throws Exception {
        interpreter.execute("", Map.of("my_secret", "password123"));
        String script = "if my_secret == \"password123\" then return \"LEAKED: \" .. my_secret else return \"ISOLATED\" end";
        String result = interpreter.execute(script, Map.of());
        assertEquals("ISOLATED", result);
    }

    @Nested
    @DisplayName("Sandbox enforcement")
    class SandboxTests {
        @Test
        @DisplayName("Block os.execute (prevent system command execution)")
        void shouldBlockOsExecute() {
            String script = "os.execute('echo hacked')";
            assertThrows(Exception.class, () -> interpreter.execute(script, Map.of()));
        }

        @Test
        @DisplayName("Block io operations (prevent file system access)")
        void shouldBlockIoOperations() {
            String script = "local f = io.open('/etc/passwd', 'r')";
            assertThrows(Exception.class, () -> interpreter.execute(script, Map.of()));
        }

        @Test
        @DisplayName("Block luajava (no access to Java classes such as Runtime)")
        void shouldBlockLuajava() {
            String script = "local rt = luajava.bindClass('java.lang.Runtime')\n"
                    + "return rt:getRuntime():exec('echo hacked')";
            assertThrows(Exception.class, () -> interpreter.execute(script, Map.of()));
        }

        @Test
        @DisplayName("No debug, coroutine, package, require, dofile, loadfile or string.dump")
        void shouldNotExposeUnsafeLibraries() throws Exception {
            String script = "return tostring(luajava) .. tostring(debug) .. tostring(coroutine) .. "
                    + "tostring(package) .. tostring(require) .. tostring(dofile) .. "
                    + "tostring(loadfile) .. tostring(string.dump) .. tostring(os.execute)";
            assertEquals("nil".repeat(9), interpreter.execute(script, Map.of()));
        }

        @Test
        @DisplayName("Base, table, string and math libraries still work")
        void shouldProvideSafeLibraries() throws Exception {
            String script = "local t = {}\n"
                    + "for i, v in ipairs({3, 1, 2}) do table.insert(t, v) end\n"
                    + "table.sort(t)\n"
                    + "return table.concat(t, ',') .. ' ' .. string.upper('ok') .. ' ' .. "
                    + "math.floor(2.7) .. ' ' .. ('%d'):format(tonumber('7'))";
            assertEquals("1,2,3 OK 2 7", interpreter.execute(script, Map.of()));
        }

        @Test
        @DisplayName("Allow safe os.clock and os.time")
        void shouldAllowSafeOsFunctions() throws Exception {
            String script = "local t = os.time()\nlocal c = os.clock()\nreturn \"time=\" .. t .. \" clock=\" .. string.format(\"%.2f\", c)";
            String result = interpreter.execute(script, Map.of());
            assertTrue(result.contains("time="));
            assertTrue(result.contains("clock="));
        }
    }

    @Nested
    @DisplayName("Real Script File Execution")
    class RealScriptFileExecutionTests {

        @Test
        @DisplayName("Execute sensor_check.lua - all sensors OK")
        void shouldExecuteSensorCheck_AllOk() throws Exception {
            String script = loadScript("scripts/sensor_check.lua");
            Map<String, String> args = Map.of(
                    "temperature", "50",
                    "pressure", "5",
                    "rpm", "1500"
            );
            String result = interpreter.execute(script, args);
            assertTrue(result.contains("Temperature OK: 50C"));
            assertTrue(result.contains("Pressure OK: 5 bar"));
            assertTrue(result.contains("RPM OK: 1500"));
            assertTrue(result.contains("ALL CLEAR"));
        }

        @Test
        @DisplayName("Execute sensor_check.lua - thresholds exceeded")
        void shouldExecuteSensorCheck_ThresholdExceeded() throws Exception {
            String script = loadScript("scripts/sensor_check.lua");
            Map<String, String> args = Map.of(
                    "temperature", "90",
                    "pressure", "20",
                    "rpm", "1500"
            );
            String result = interpreter.execute(script, args);
            assertTrue(result.contains("WARNING: Temperature 90C exceeds limit 80C"));
            assertTrue(result.contains("WARNING: Pressure 20 bar exceeds limit 10 bar"));
            assertTrue(result.contains("RPM OK: 1500"));
            assertTrue(result.contains("ALERT: 2 threshold violations detected"));
        }

        @Test
        @DisplayName("Execute sensor_check.lua - missing sensor data")
        void shouldExecuteSensorCheck_MissingSensors() throws Exception {
            String script = loadScript("scripts/sensor_check.lua");
            Map<String, String> args = new HashMap<>();
            args.put("temperature", "50");

            String result = interpreter.execute(script, args);
            assertTrue(result.contains("Temperature OK: 50C"));
            assertTrue(result.contains("No pressure data available"));
            assertTrue(result.contains("No RPM data available"));
            assertTrue(result.contains("ALL CLEAR"));
        }
    }

    @Nested
    @DisplayName("Nested Service Calls")
    class NestedServiceCallTests {

        private IServiceOrchestrator mockOrchestrator;
        private LuaScriptBridge luaScriptBridge;

        @BeforeEach
        void setUpNested() {
            mockOrchestrator = mock(IServiceOrchestrator.class);
            luaScriptBridge = new LuaScriptBridge(mockOrchestrator);
            interpreter = new LuaInterpreter(luaScriptBridge);
        }

        @Test
        @DisplayName("Script can call triggerNestedService() function")
        void shouldCallTriggerNestedService() throws Exception {
            // Setup mock to return a successful response
            ExecutionResponseDTO response = ExecutionResponseDTO.success(
                    "cooling-service",
                    "Alert fired: severity=HIGH",
                    100L
            );
            when(mockOrchestrator.resolveAndExecuteNested(any(ServiceTriggerCommand.class)))
                    .thenReturn(response);

            // Script that triggers a nested service
            String script = "local result = triggerNestedService('cooling-service', {severity = 'HIGH', value = '95'})\n" +
                    "return result";

            String result = interpreter.execute(script, Map.of());
            assertEquals("Alert fired: severity=HIGH", result);

            // Verify the nested service was called
            verify(mockOrchestrator, times(1)).resolveAndExecuteNested(any(ServiceTriggerCommand.class));
        }

        @Test
        @DisplayName("Nested service decision logic: temperature exceeds threshold")
        void shouldDecideToTriggerNestedService_TemperatureHigh() throws Exception {
            // Setup mock to return success
            ExecutionResponseDTO response = ExecutionResponseDTO.success(
                    "cooling-service",
                    "Critical alert sent",
                    50L
            );
            when(mockOrchestrator.resolveAndExecuteNested(any(ServiceTriggerCommand.class)))
                    .thenReturn(response);

            // Analysis script: decides whether to trigger alert
            String script = "local temp = tonumber(temperature)\n" +
                    "local threshold = tonumber(threshold)\n" +
                    "if temp > threshold then\n" +
                    "    local result = triggerNestedService('cooling-service', {severity = 'HIGH', temp = temperature})\n" +
                    "    return 'Alert fired: ' .. result\n" +
                    "else\n" +
                    "    return 'Temperature OK: ' .. temperature\n" +
                    "end";

            Map<String, String> args = Map.of(
                    "temperature", "95",
                    "threshold", "80"
            );

            String result = interpreter.execute(script, args);
            assertTrue(result.contains("Alert fired"));
            assertTrue(result.contains("Critical alert sent"));
        }

        @Test
        @DisplayName("Nested service decision logic: temperature within threshold (no nested call)")
        void shouldDecideNotToTriggerNestedService() throws Exception {
            // Setup mock should not be called
            String script = "local temp = tonumber(temperature)\n" +
                    "local threshold = tonumber(threshold)\n" +
                    "if temp > threshold then\n" +
                    "    local result = triggerNestedService('cooling-service', {severity = 'HIGH'})\n" +
                    "    return 'Alert fired: ' .. result\n" +
                    "else\n" +
                    "    return 'Temperature OK: ' .. temperature\n" +
                    "end";

            Map<String, String> args = Map.of(
                    "temperature", "50",
                    "threshold", "80"
            );

            String result = interpreter.execute(script, args);
            assertEquals("Temperature OK: 50", result);

            // Verify orchestrator was NOT called
            verify(mockOrchestrator, never()).resolveAndExecuteNested(any());
        }

        @Test
        @DisplayName("Pass multiple arguments to nested service")
        void shouldPassMultipleArgsToNestedService() throws Exception {
            ExecutionResponseDTO response = ExecutionResponseDTO.success(
                    "energy-monitoring-service",
                    "Analysis complete",
                    75L
            );
            when(mockOrchestrator.resolveAndExecuteNested(any(ServiceTriggerCommand.class)))
                    .thenReturn(response);

            String script = "local result = triggerNestedService('energy-monitoring-service', {\n" +
                    "    machine_id = 'M001',\n" +
                    "    temperature = '75',\n" +
                    "    pressure = '10',\n" +
                    "    rpm = '1500'\n" +
                    "})\n" +
                    "return result";

            String result = interpreter.execute(script, Map.of());
            assertEquals("Analysis complete", result);

            // Verify all arguments were passed
            verify(mockOrchestrator, times(1)).resolveAndExecuteNested(argThat(cmd ->
                    cmd.getDynamicArgs().get("machine_id").equals("M001") &&
                            cmd.getDynamicArgs().get("temperature").equals("75") &&
                            cmd.getDynamicArgs().get("pressure").equals("10") &&
                            cmd.getDynamicArgs().get("rpm").equals("1500")
            ));
        }

        @Test
        @DisplayName("Handle failure response from nested service")
        void shouldHandleNestedServiceFailure() throws Exception {
            ExecutionResponseDTO failureResponse = ExecutionResponseDTO.failure(
                    "cooling-service",
                    "Service timeout",
                    0L
            );
            when(mockOrchestrator.resolveAndExecuteNested(any(ServiceTriggerCommand.class)))
                    .thenReturn(failureResponse);

            String script = "local result = triggerNestedService('cooling-service', {severity = 'HIGH'})\n" +
                    "if result == '' then return 'Service failed' else return result end";

            String result = interpreter.execute(script, Map.of());
            assertEquals("Service failed", result);
        }

        @Test
        @DisplayName("Nested service without bridge still executes basic scripts")
        void shouldExecuteBasicScriptWithoutNestedService() throws Exception {
            // Create interpreter with a mock bridge that doesn't register any function
            LuaScriptBridge mockBridge = mock(LuaScriptBridge.class);
            LuaInterpreter basicInterpreter = new LuaInterpreter(mockBridge);

            String script = "return 'Script executed'";
            String result = basicInterpreter.execute(script, Map.of());
            assertEquals("Script executed", result);

            // Verify the bridge was asked to register functions
            verify(mockBridge, times(1)).registerBridgeFunctions(any());
        }

        @Test
        @DisplayName("Chain two nested service calls - second depends on first result")
        void shouldChainTwoNestedServiceCalls() throws Exception {
            // Setup mock to handle first nested service call
            ExecutionResponseDTO firstResponse = ExecutionResponseDTO.success(
                    "geometry-correction-service",
                    "VALIDATION_PASSED",
                    50L
            );
            
            // Setup mock to handle second nested service call
            ExecutionResponseDTO secondResponse = ExecutionResponseDTO.success(
                    "cooling-service",
                    "Alert sent based on validation",
                    100L
            );
            
            when(mockOrchestrator.resolveAndExecuteNested(any(ServiceTriggerCommand.class)))
                    .thenReturn(firstResponse)
                    .thenReturn(secondResponse);

            String script = "local firstResult = triggerNestedService('geometry-correction-service', {data = 'test_data'})\n" +
                    "\n" +
                    "if firstResult == 'VALIDATION_PASSED' then\n" +
                    "    local secondResult = triggerNestedService('cooling-service', {validation_result = firstResult, severity = 'HIGH'})\n" +
                    "    return 'Both services executed: ' .. secondResult\n" +
                    "else\n" +
                    "    return 'Validation failed: ' .. firstResult\n" +
                    "end";

            String result = interpreter.execute(script, Map.of());
            assertEquals("Both services executed: Alert sent based on validation", result);

            // Verify both nested services were called in sequence
            verify(mockOrchestrator, times(2)).resolveAndExecuteNested(any(ServiceTriggerCommand.class));
        }

    }

    private String loadScript(String resourcePath) throws Exception {
        URI uri = getClass().getClassLoader().getResource(resourcePath).toURI();
        return Files.readString(Path.of(uri));
    }
}
