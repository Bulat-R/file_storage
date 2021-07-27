package org.example.controller;

import javafx.concurrent.Task;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.example.model.command.Command;
import org.example.model.command.CommandType;
import org.example.model.command.ParameterType;
import org.example.model.dto.FileDTO;
import org.example.netty.NettyNetwork;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class UploadTask extends Task<Long> {
    private final File file;
    private final String path;
    private final Semaphore semaphore;
    private final NettyNetwork network;
    private final AtomicBoolean isUploadError;

    public UploadTask(File file, String path, Semaphore semaphore, NettyNetwork network, AtomicBoolean isUploadError) {
        this.file = file;
        this.path = path;
        this.semaphore = semaphore;
        this.network = network;
        this.isUploadError = isUploadError;
    }

    @Override
    protected Long call() throws Exception {
        isUploadError.set(false);
        long size = file.length();
        FileDTO fileDTO = FileDTO.builder()
                .owner(Config.getUser())
                .name(file.getName())
                .path(path)
                .fullSize(size)
                .build();
        long readBytes = 0;
        try (FileInputStream is = new FileInputStream(file)) {
            while (!fileDTO.isEnd()) {
                semaphore.acquire();
                if (isUploadError.get()) {
                    break;
                }
                fileDTO.setStart(readBytes == 0);
                long l = (size - readBytes) > 30_000_000 ? 30_000_000 : size - readBytes;
                byte[] buffer = new byte[(int) l];
                readBytes += is.read(buffer, 0, (int) l);
                String md5 = DigestUtils.md5Hex(buffer);
                fileDTO.setMd5(md5);
                fileDTO.setContent(buffer);
                fileDTO.setPart(fileDTO.getPart() + 1);
                fileDTO.setEnd(readBytes == size);
                network.writeMessage(new Command(CommandType.FILE_UPLOAD).setParameter(ParameterType.FILE_DTO, fileDTO));
                updateProgress(readBytes, size);
            }
        }
        return readBytes;
    }
}
