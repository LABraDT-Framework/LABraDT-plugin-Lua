# lua: module reference

The Lua script interpreter for the LABraDT Runtime. The text below was moved here from the runtime
project's `CODE_GUIDE.md` (section "runtime-script") together with the code; the script host it
plugs into (`ScriptRuntime`, `IScriptInterpreter`, `IScriptBridge`) is described there.

---

## `LuaInterpreter` (implements `IScriptInterpreter`, type `LUA`)

The Lua interpreter carries the two decisions most likely to be asked about. First, it runs each
script in a freshly sandboxed environment built from an allow list: only the base, table, string
and math libraries are loaded, plus `os.clock`/`os.time` as safe helpers and the bridge's
`triggerNestedService`. There is no `luajava` (which would let a script use any Java class), no
`io`, no real `os`, no `debug`, no `coroutine`, and no `package`/`require`/`dofile`/`loadfile`, so
an operator's script can't touch the filesystem or escape the JVM. A new environment per run also
means no state leaks between executions.

```java
Globals globals = new Globals();
globals.load(new BaseLib());
globals.load(new PackageLib());   // needed while loading the libraries, removed below
globals.load(new TableLib());
globals.load(new StringLib());
globals.load(new JseMathLib());
LoadState.install(globals);
LuaC.install(globals);
globals.set("package", LuaValue.NIL);
globals.set("require", LuaValue.NIL);
globals.set("loadfile", LuaValue.NIL);
globals.set("dofile", LuaValue.NIL);
globals.get("string").set("dump", LuaValue.NIL);
// ... then a small safeOs with only clock()/time() is put back
```

Second, arguments are injected as global variables and numeric strings are auto-converted to Lua
numbers, so a threshold like `"80"` compares as a number, not a string:

```java
try {
    double numericValue = Double.parseDouble(value);
    globals.set(key, LuaValue.valueOf(numericValue));   // "80"  -> 80  (number)
} catch (NumberFormatException e) {
    globals.set(key, LuaValue.valueOf(value));          // "machine-01" stays a string
}
```

## `LuaScriptBridge` (implements `IScriptBridge<Globals>`)

Injects `triggerNestedService(serviceId [, argsTable])` into the script's globals; a call converts
the Lua arguments to Java and calls `IServiceOrchestrator.resolveAndExecuteNested(...)` in the same
JVM, then returns the nested service's output to the script. The runtime project's `CODE_GUIDE.md`
("Nested service calls") compares this with the HTTP path containers use.

## Tests

`LuaInterpreterTest` (plain JUnit + Mockito, no Spring context) covers the sandbox, argument
injection, output capture, nested calls through a mocked orchestrator and the `sensor_check.lua`
resource script.
