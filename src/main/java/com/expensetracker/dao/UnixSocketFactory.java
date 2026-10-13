package com.expensetracker.dao;

import com.mysql.cj.conf.PropertySet;
import com.mysql.cj.protocol.ServerSession;
import com.mysql.cj.protocol.SocketConnection;
import com.mysql.cj.protocol.SocketFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOptions;
import java.net.SocketTimeoutException;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

/**
 * Connector/J {@link SocketFactory} that reaches MySQL through a UNIX DOMAIN
 * SOCKET instead of TCP.
 *
 * Why this exists: some sandboxed / locked-down environments block raw TCP
 * connections from the JVM, while Unix-domain sockets keep working. A tiny
 * sidecar (see {@code tools/mysql-bridge.py}, not part of the app) forwards
 * the Unix socket to MySQL over TCP.
 *
 * Normal deployments do NOT need this: leave the {@code DB_SOCKET}
 * environment variable unset and {@link Db} uses plain TCP as usual.
 */
public class UnixSocketFactory implements SocketFactory {

    /** Unix socket path, from the DB_SOCKET environment variable. */
    public static String socketPath() {
        String path = System.getenv("DB_SOCKET");
        return (path == null || path.isBlank()) ? "/tmp/mysql-bridge.sock" : path.trim();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Closeable> T connect(String host, int port, PropertySet propSet,
                                           int loginTimeout) throws IOException {
        // Java 16+: a SocketChannel can open a Unix-domain connection directly.
        SocketChannel channel = SocketChannel.open(UnixDomainSocketAddress.of(socketPath()));
        channel.configureBlocking(false); // reads go through a Selector (timeouts)
        return (T) new UnixSocket(new UnixSocketImpl(channel, port));
    }

    @Override
    public <T extends Closeable> T performTlsHandshake(SocketConnection socketConnection,
                                                       ServerSession serverSession)
            throws IOException {
        throw new IOException("TLS is not supported over the Unix-socket bridge; "
                + "connect with useSSL=false.");
    }

    /**
     * A java.net.Socket whose traffic flows through a Unix-domain SocketChannel.
     * Reports itself as connected (the channel already is).
     */
    private static final class UnixSocket extends Socket {
        private volatile boolean closed = false;

        UnixSocket(SocketImpl impl) throws SocketException {
            super(impl);
        }

        @Override
        public boolean isConnected() {
            return !closed;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public synchronized void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    /**
     * SocketImpl adapter: every operation delegates to the Unix SocketChannel.
     * Only the methods Connector/J actually uses are meaningfully implemented
     * (streams, close, SO_TIMEOUT); the rest are harmless no-ops.
     */
    private static final class UnixSocketImpl extends SocketImpl {

        private final SocketChannel channel;
        private final InputStream in;
        private final OutputStream out;
        private Selector selector;
        private int soTimeout = 0; // 0 = infinite, like a normal socket

        UnixSocketImpl(SocketChannel channel, int port) {
            this.channel = channel;
            this.in = new ChannelInputStream();
            this.out = new ChannelOutputStream();
            this.port = port; // cosmetic: Socket.getPort() reports the MySQL port
        }

        // ---- connection lifecycle (already connected by the factory) ----
        @Override
        protected void create(boolean stream) {
        }

        @Override
        protected void connect(String host, int port) {
        }

        @Override
        protected void connect(InetAddress address, int port) {
        }

        @Override
        protected void connect(SocketAddress address, int timeout) {
        }

        @Override
        protected void bind(InetAddress host, int port) {
        }

        @Override
        protected void listen(int backlog) {
        }

        @Override
        protected void accept(SocketImpl s) throws IOException {
            throw new IOException("accept() not supported on a client socket");
        }

        @Override
        protected int available() {
            return 0;
        }

        @Override
        protected void close() throws IOException {
            channel.close();
            if (selector != null) {
                selector.close();
            }
        }

        @Override
        protected void sendUrgentData(int data) throws IOException {
            throw new IOException("urgent data not supported over Unix sockets");
        }

        @Override
        protected InputStream getInputStream() {
            return in;
        }

        @Override
        protected OutputStream getOutputStream() {
            return out;
        }

        // ---- socket options: only SO_TIMEOUT is used by the driver ----
        @Override
        public void setOption(int optID, Object value) throws SocketException {
            if (optID == SocketOptions.SO_TIMEOUT) {
                soTimeout = (Integer) value;
                return;
            }
            // ignore the rest (TCP_NODELAY etc. don't apply to Unix sockets)
        }

        @Override
        public Object getOption(int optID) throws SocketException {
            if (optID == SocketOptions.SO_TIMEOUT) {
                return soTimeout;
            }
            throw new SocketException("unsupported option: " + optID);
        }

        private synchronized Selector selector() throws IOException {
            if (selector == null || !selector.isOpen()) {
                selector = Selector.open();
                channel.register(selector, SelectionKey.OP_READ);
            }
            return selector;
        }

        /** InputStream with SO_TIMEOUT honored via a Selector. */
        private final class ChannelInputStream extends InputStream {
            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                int n = read(one, 0, 1);
                return n < 0 ? -1 : one[0] & 0xFF;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (len == 0) {
                    return 0;
                }
                Selector sel = selector();
                // select() with no timeout blocks forever (SO_TIMEOUT == 0).
                int ready = (soTimeout == 0) ? sel.select() : sel.select(soTimeout);
                if (ready == 0) {
                    throw new SocketTimeoutException("Read timed out");
                }
                sel.selectedKeys().clear();
                int n = channel.read(ByteBuffer.wrap(b, off, len));
                return n; // -1 signals EOF, like a normal socket
            }
        }

        /** OutputStream: loop until every byte is written (non-blocking channel). */
        private final class ChannelOutputStream extends OutputStream {
            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                ByteBuffer buf = ByteBuffer.wrap(b, off, len);
                while (buf.hasRemaining()) {
                    if (channel.write(buf) == 0) {
                        // Wait until the channel is writable (brief spin with yield).
                        Thread.yield();
                    }
                }
            }
        }
    }
}
