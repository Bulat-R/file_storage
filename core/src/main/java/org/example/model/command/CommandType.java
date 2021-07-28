package org.example.model.command;

public enum CommandType {
    AUTH_REQUEST,
    AUTH_OK,
    AUTH_NO,
    CONTENT_REQUEST,
    CONTENT_RESPONSE,
    FILE_UPLOAD,
    ERROR,
    FILE_DOWNLOAD,
    CREATE_DIR,
    NEXT_PART,
    UPLOAD_ERROR,
    DOWNLOAD_ERROR
}
