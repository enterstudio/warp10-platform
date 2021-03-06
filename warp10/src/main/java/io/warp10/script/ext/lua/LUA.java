//
//   Copyright 2016  Cityzen Data
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.script.ext.lua;

import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.functions.SCRIPTENGINE;

import javax.script.ScriptEngine;

import org.luaj.vm2.script.LuaScriptEngineFactory;

public class LUA extends SCRIPTENGINE {
  
  public LUA(String name) {
    super(name, "lua");
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object ret = super.apply(stack);
    stack.pop();
    return ret;
  }

  @Override
  protected ScriptEngine getEngine() {
    ScriptEngine se = new LuaScriptEngineFactory().getScriptEngine();
    return se;
  }
}
