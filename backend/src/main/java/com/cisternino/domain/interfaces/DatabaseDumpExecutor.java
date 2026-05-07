package com.cisternino.domain.interfaces;

import java.io.OutputStream;

/**
 * Executes a full database dump and writes the binary output to the given stream.
 * Implementation may invoke an external tool (e.g. pg_dump).
 */
public interface DatabaseDumpExecutor {
    /**
     * @param output stream to write the dump bytes to (e.g. HTTP response stream)
     * @return number of bytes written
     * @throws DumpException if the dump fails for any reason
     */
    long dump(OutputStream output) throws DumpException;

    class DumpException extends Exception {
        public DumpException(String msg) { super(msg); }
        public DumpException(String msg, Throwable cause) { super(msg, cause); }
    }
}
