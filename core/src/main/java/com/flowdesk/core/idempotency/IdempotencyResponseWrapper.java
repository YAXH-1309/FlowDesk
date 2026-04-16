package com.flowdesk.core.idempotency;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.*;

/**
 * Captures the response body so it can be stored in the idempotency cache.
 */
public class IdempotencyResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintWriter writer = new PrintWriter(new OutputStreamWriter(buffer));

    public IdempotencyResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public PrintWriter getWriter() {
        return writer;
    }

    @Override
    public ServletOutputStream getOutputStream() {
        return new ServletOutputStream() {
            @Override public boolean isReady() { return true; }
            @Override public void setWriteListener(WriteListener l) {}
            @Override public void write(int b) { buffer.write(b); }
            @Override public void write(byte[] b, int off, int len) { buffer.write(b, off, len); }
        };
    }

    @Override
    public void flushBuffer() throws IOException {
        writer.flush();
    }

    public byte[] getCapturedBody() {
        writer.flush();
        return buffer.toByteArray();
    }

    public void copyBodyToResponse() throws IOException {
        writer.flush();
        byte[] body = buffer.toByteArray();
        getResponse().getOutputStream().write(body);
        getResponse().getOutputStream().flush();
    }
}
