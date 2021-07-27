package org.example.model.command;

public enum CommandType {
    AUTH_REQUEST,
    AUTH_OK, AUTH_NO,
    CONTENT_REQUEST,
    CONTENT_RESPONSE,
    FILE_UPLOAD,
    ERROR,
    FILE_UPLOAD_OK,
    FILE_DOWNLOAD,
    DELETE_OK,
    RENAME_OK,
    CREATE_DIR,
    CREATE_OK,
    NEXT_PART,
    UPLOAD_ERROR
}
