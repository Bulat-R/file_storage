package org.example.model.command;

import lombok.ToString;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@ToString
public class Command implements Serializable {
    private final CommandType commandType;
    private final Map<ParameterType, Object> parameters;

    public Command(CommandType commandType) {
        this.commandType = commandType;
        parameters = new HashMap<>();
    }

    public CommandType getCommandType() {
        return commandType;
    }

    public Object getParameter(ParameterType type) {
        return parameters.getOrDefault(type, null);
    }

    public Command setParameter(ParameterType type, Object o) {
        parameters.put(type, o);
        return this;
    }

    public Command setAll(Map<ParameterType, Object> parameters) {
        this.parameters.putAll(parameters);
        return this;
    }
}
