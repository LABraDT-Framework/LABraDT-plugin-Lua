package de.labradt.serviceexecution.runtime.script.interpreter.lua;

import de.labradt.serviceexecution.core.api.dtos.ExecutionResponseDTO;
import de.labradt.serviceexecution.core.api.dtos.ServiceTriggerCommand;
import de.labradt.serviceexecution.core.api.enums.TechnologyAdapter;
import de.labradt.serviceexecution.core.api.interfaces.IServiceOrchestrator;
import de.labradt.serviceexecution.runtime.script.interpreter.IScriptBridge;
import lombok.extern.slf4j.Slf4j;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.TwoArgFunction;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LuaScriptBridge: Bridges Lua scripts to Java backend for nested service calls.
 * 
 * When a Lua script calls triggerNestedService(), this bridge:
 * 1. Converts Lua values to Java ExecutionRequestDTO
 * 2. Calls back up to the IServiceOrchestrator (ServiceManager)
 * 3. Waits synchronously for the result
 * 4. Converts the response back to Lua values for the script to use
 *
 * This implements the loopback pattern:
 * Lua Script → triggerNestedService() → LuaScriptBridge → IServiceOrchestrator.ExecuteNestedServiceSync → Runtime → Result
 */
@Slf4j
@Component
public class LuaScriptBridge implements IScriptBridge<Globals> {

    private final IServiceOrchestrator orchestrator;

    /**
     * @Lazy breaks the circular dependency:
     * ServiceManager → ScriptRuntime → LuaInterpreter → LuaScriptBridge → IServiceOrchestrator (= ServiceManager)
     */
    public LuaScriptBridge(@Lazy IServiceOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public TechnologyAdapter getScriptType() {
        return TechnologyAdapter.LUA;
    }

    @Override
    public void registerBridgeFunctions(Globals globals) {
        globals.set("triggerNestedService", createNestedServiceTrigger());
        log.debug("LuaScriptBridge: Injected triggerNestedService() into Lua globals");
    }

    /**
     * Creates a Lua function that scripts can call to trigger nested services.
     *
     * Simple form (looks up service definition from registry):
     *   local result = triggerNestedService("my-service-id")
     *   local result = triggerNestedService("my-service-id", {key = "value"})
     */
    public TwoArgFunction createNestedServiceTrigger() {
        return new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue serviceIdArg, LuaValue dynamicArgsArg) {
                try {
                    // The dynamic-args table is optional; luaTableToMap treats a NIL
                    // second argument (or anything that isn't a table) as an empty map.
                    String serviceId = serviceIdArg.checkjstring();
                    Map<String, String> dynamicArgs = luaTableToMap(dynamicArgsArg);
                    log.info("LuaScriptBridge: Triggering nested service by ID: {} with {} dynamic arg(s)",
                            serviceId, dynamicArgs.size());
                    ServiceTriggerCommand cmd = new ServiceTriggerCommand();
                    cmd.setServiceIds(List.of(serviceId));
                    cmd.setDynamicArgs(dynamicArgs);
                    ExecutionResponseDTO response = orchestrator.resolveAndExecuteNested(cmd);

                    if (response.isSuccess()) {
                        log.info("LuaScriptBridge: Nested service completed successfully");
                    } else {
                        log.error("LuaScriptBridge: Nested service failed: {}", response.getErrorMessage());
                    }

                    String output = response.getOutputData();
                    return LuaValue.valueOf(output != null ? output : "");

                } catch (Exception e) {
                    log.error("LuaScriptBridge: Error triggering nested service", e);
                    return LuaValue.valueOf("ERROR: " + e.getMessage());
                }
            }
        };
    }

    /**
     * Converts a Lua table to a Java Map<String, String> for argument passing
     */
    public static Map<String, String> luaTableToMap(LuaValue luaTable) {
        Map<String, String> result = new HashMap<>();

        // Handle nil 
        if (luaTable == null || luaTable.isnil()) {
            log.debug("LuaScriptBridge: Args table is nil, using empty map");
            return result;
        }

        // Handle non-table 
        if (!luaTable.istable()) {
            log.debug("LuaScriptBridge: Args is not a table");
            return result;
        }

        // Convert luaTable from LuaValue to LuaTable
        LuaTable table = luaTable.checktable();
        LuaValue key = LuaValue.NIL;

        while (true) {
            key = table.next(key).arg1();
            if (key.isnil()) {
                break;  // End of table
            }

            LuaValue value = table.get(key);
            String keyStr = key.tojstring();

            // Convert value to string
            String valueStr = value.tojstring();

            result.put(keyStr, valueStr);
            log.debug("LuaScriptBridge: Converted arg: {} = {}", keyStr, valueStr);
        }

        return result;
    }
}
