# Lua

Lua script interpreter for the LABraDT Runtime (artifact `de.labradt:runtime-lua`). It runs
`SCRIPT` / `LUA` services in a sandboxed LuaJ environment and lets a script call other services
(`triggerNestedService`).

## What is in here

| Path | What it is |
|---|---|
| `src/main/java/.../interpreter/lua/LuaInterpreter.java` | `IScriptInterpreter` for `LUA`: sandbox from an allow list, args as globals, captured output |
| `src/main/java/.../interpreter/lua/LuaScriptBridge.java` | `IScriptBridge`: the `triggerNestedService` function for nested calls |
| `src/test/` | `LuaInterpreterTest` and the `sensor_check.lua` resource script |
| [MODULES.md](MODULES.md) | How the sandbox and argument handling work |

The packages are unchanged (`de.labradt.serviceexecution.runtime.script.interpreter.lua`), so the
runtime app finds the interpreter through its normal component scan once the jar is on its classpath.

## Build

The module builds against the runtime core (`runtime-script`, `core-api` and the runtime parent pom)
from the local Maven repository. There is no package registry yet, so install the runtime first:

```bash
# in the runtime project
mvn install -DskipTests

# then here
mvn install
```

`mvn test` runs `LuaInterpreterTest`.

## Use it with the runtime

Add the plugin to the dependencies of `service-manager-app/pom.xml` in the runtime project and rebuild
it:

```xml
<dependency>
    <groupId>de.labradt</groupId>
    <artifactId>runtime-lua</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

At startup the script host logs `Auto-registered interpreter: LUA → LuaInterpreter`. With the plugin
present, the runtime also seeds its three default Lua services for the engine's shipped rules, and the
runtime tests tagged `plugins` that need Lua run instead of being skipped.
