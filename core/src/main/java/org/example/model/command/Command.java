package org.example.model.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Map;

@AllArgsConstructor
@Getter
@ToString
public class Command implements Serializable {
    private final CommandType commandType;
    private final Map<ParameterType, Object> parameters;
}
