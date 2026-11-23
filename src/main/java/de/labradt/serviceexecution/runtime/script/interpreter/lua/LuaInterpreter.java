package de.labradt.serviceexecution.runtime.script.interpreter.lua;

import de.labradt.serviceexecution.core.api.enums.TechnologyAdapter;
import de.labradt.serviceexecution.runtime.script.interpreter.IScriptInterpreter;
import lombok.extern.slf4j.Slf4j;
import org.luaj.vm2.*;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JseMathLib;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

/**
 * LuaInterpreter: Executes Lua scripts in a sandboxed environment using LuaJ.
 * Scripts get only the base, table, string and math libraries, os.clock / os.time
 * and the bridge's triggerNestedService. There is no luajava (no Java classes),
 * no io, no real os, no debug, no coroutine and no package / require.
 * Receives LuaScriptBridge via constructor injection from Spring
 */
@Slf4j
@Component
public class LuaInterpreter implements IScriptInterpreter {

    private final LuaScriptBridge luaScriptBridge;

    public LuaInterpreter(LuaScriptBridge luaScriptBridge) {
        this.luaScriptBridge = luaScriptBridge;
    }

    @Override
    public TechnologyAdapter getScriptType() {
        return TechnologyAdapter.LUA;
    }

    @Override
    public String execute(String scriptContent, Map<String, String> args) throws Exception {
        log.info("LuaInterpreter: Executing script ({} lines, {} args)",
                scriptContent.split("\n").length, args.size());

        // Create a fresh sandboxed Lua environment for each execution (no state leaks)
        Globals globals = createSandboxedGlobals();

        // Capture print() output into a buffer instead of System.out
        ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputBuffer);
        globals.STDOUT = printStream;

        // Inject arguments as Lua global variables
        injectArguments(globals, args);

        // Inject nested service trigger function from bridge
        luaScriptBridge.registerBridgeFunctions(globals);

        // Strip UTF-8 BOM if present (Windows editors often add it)
        if (scriptContent.startsWith("﻿")) {
            scriptContent = scriptContent.substring(1);
            log.debug("[LUA] Stripped UTF-8 BOM from script");
        }

        log.debug("[LUA] Script first 120 chars: {}",
                scriptContent.substring(0, Math.min(120, scriptContent.length())).replace("\n", "\\n"));

        // Compile the script - LuaJ returns nil on syntax error
        LuaValue chunk = globals.load(scriptContent, "script");
        if (chunk == null || chunk.isnil()) {
            throw new LuaError("Lua compile/syntax error - script could not be loaded (check script content)");
        }
        LuaValue result = chunk.call();
        
        printStream.flush();

        // Build the output: captured print() statements + return value (if distinct)
        String capturedOutput = outputBuffer.toString().trim();
        String returnValue = result != null && !result.isnil() ? result.tojstring() : "";

        String finalOutput;
        if (!capturedOutput.isEmpty() && !returnValue.isEmpty() && !capturedOutput.equals(returnValue)) {
            finalOutput = capturedOutput + "\n" + returnValue;
        } else if (!capturedOutput.isEmpty()) {
            finalOutput = capturedOutput;
        } else {
            finalOutput = returnValue;
        }

        return finalOutput;
    }

    /**
     * Creates a sandboxed Lua Globals with only safe libraries loaded (an allow list,
     * not JsePlatform.standardGlobals() minus a few names: that would keep luajava,
     * which lets a script use any Java class, e.g. java.lang.Runtime).
     */
    private Globals createSandboxedGlobals() {
        Globals globals = new Globals();
        globals.load(new BaseLib());      // print, pairs, ipairs, tostring, tonumber, pcall, error, ...
        globals.load(new PackageLib());   // needed while loading: the other libs register in package.loaded
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new JseMathLib());
        LoadState.install(globals);
        LuaC.install(globals);

        // Remove what can reach files or load other code
        globals.set("package", LuaValue.NIL);   // no module loading
        globals.set("require", LuaValue.NIL);
        globals.set("loadfile", LuaValue.NIL);  // no loading external Lua files
        globals.set("dofile", LuaValue.NIL);    // no executing external Lua files
        globals.get("string").set("dump", LuaValue.NIL);  // no bytecode dumps

        // Safe os functions that operators might need (no os.execute, os.remove, ...)
        LuaTable safeOs = new LuaTable();
        safeOs.set("clock", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(System.nanoTime() / 1_000_000_000.0);
            }
        });
        safeOs.set("time", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                return LuaValue.valueOf(System.currentTimeMillis() / 1000);
            }
        });
        globals.set("os", safeOs);

        return globals;
    }

    /**
     * Injects arguments as Lua global variables.
     */
    private void injectArguments(Globals globals, Map<String, String> args) {
        for (Map.Entry<String, String> entry : args.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Try to convert numeric strings to Lua numbers for natural comparisons
            try {
                double numericValue = Double.parseDouble(value);
                globals.set(key, LuaValue.valueOf(numericValue));
                log.debug("Injected arg as number: {} = {}", key, numericValue);
            } catch (NumberFormatException e) {
                globals.set(key, LuaValue.valueOf(value));
                log.debug("Injected arg as string: {} = {}", key, value);
            }
        }
    }
}
