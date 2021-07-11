package org.example.model;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

@Data
public class Message implements Serializable {

    private Date sendAt;
    private String content;

    public Message(String content) {
        this.content = content;
        sendAt = new Date();
    }
}