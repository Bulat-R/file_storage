package org.example.model.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import org.example.model.user.User;

import java.io.Serializable;

@Data
@Builder
public class FileDTO implements Serializable {
    private User owner;
    private String name;
    private String path;
    private Long size;
    private String md5;
    @ToString.Exclude
    private byte[] content;
}
