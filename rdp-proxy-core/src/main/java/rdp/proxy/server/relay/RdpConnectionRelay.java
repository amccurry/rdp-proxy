package rdp.proxy.server.relay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rdp.proxy.server.RdpProxyConfig;
import rdp.proxy.spi.ConnectionInfo;
import rdp.proxy.spi.RdpStore;

public class RdpConnectionRelay {
  private static final Logger LOGGER = LoggerFactory.getLogger(RdpConnectionRelay.class);

  private static final String MSTSHASH = "mstshash";

  private final ServerSocket _ss;
  private final AtomicBoolean _running = new AtomicBoolean(true);
  private final ExecutorService _service;
  private final RdpStore _store;
  private final int _bufferSize = 10000;
  private final long _checkTime = 1000;
  private final int _remoteRdpTimeout = (int) TimeUnit.MINUTES.toMillis(1);
  private final int _soTimeout = (int) TimeUnit.MINUTES.toMillis(5);

  public RdpConnectionRelay(RdpProxyConfig config, RdpStore store) throws IOException {
    String bindAddress = config.getRdpBindAddress();
    int port = config.getRdpPort();
    int backlog = config.getRdpBacklog();
    _store = store;
    InetAddress bindAddr = InetAddress.getByName(bindAddress);
    _ss = new ServerSocket(port, backlog, bindAddr);
    _service = Executors.newCachedThreadPool();
  }

  public void exec() {
    while (_running.get()) {
      Socket socket;
      try {
        socket = _ss.accept();
        _service.submit(() -> {
          try {
            handleNewConnection(socket);
            LOGGER.info("Socket {} closed", socket);
          } catch (Throwable t) {
            LOGGER.error("Unknown error, during new connection setup", t);
          }
        });
      } catch (Throwable t) {
        LOGGER.error("Unknown error", t);
      }
    }
  }

  private void handleNewConnection(Socket s) throws Exception {
    try (Socket socket = s) {
      LOGGER.info("Socket {} new connection", socket);
      socket.setTcpNoDelay(true);
      socket.setSoTimeout(_soTimeout);
      socket.setKeepAlive(true);
      try (InputStream rcInput = socket.getInputStream(); OutputStream rcOutput = socket.getOutputStream()) {
        LOGGER.info("Socket {} read first message", socket);
        byte[] message = readFirstMessage(rcInput);
        if (message == null) {
          return;
        }
        ConnectionInfo connectionInfo;
        LOGGER.info("Socket {} find cookie", socket);
        String cookie = findCookie(message);
        if (isCookie(cookie)) {
          LOGGER.info("Socket {} cookie found", socket);
          connectionInfo = _store.startRdpSessionIfMissingWithCookie(cookie);
        } else {
          LOGGER.info("Socket {} loadbalanceinfo found", socket);
          connectionInfo = _store.startRdpSessionIfMissingWithId(cookie);
        }

        if (connectionInfo == null) {
          LOGGER.info("Socket {} connection info for cookie {} did not find a remote connection, hang up", socket,
              cookie);
          return;
        }

        LOGGER.info("Connection info {} for cookie {} for remote socket", connectionInfo, cookie, socket);

        try (Socket rdpServer = new Socket(connectionInfo.getProxy())) {
          rdpServer.setTcpNoDelay(true);
          SocketAddress _endpoint = new InetSocketAddress(connectionInfo.getAddress(), connectionInfo.getPort());
          rdpServer.connect(_endpoint, _remoteRdpTimeout);
          rdpServer.setSoTimeout(_soTimeout);
          rdpServer.setKeepAlive(true);
          try (InputStream rsInput = rdpServer.getInputStream(); OutputStream rsOutput = rdpServer.getOutputStream()) {
            rsOutput.write(message);
            rsOutput.flush();
            AtomicBoolean alive = new AtomicBoolean(true);
            Future<Void> f1 = startRelay(alive, rcInput, rsOutput);
            Future<Void> f2 = startRelay(alive, rsInput, rcOutput);
            while (_running.get()) {
              if (shouldCloseConnection(f1, f2, rdpServer, socket)) {
                alive.set(false);
                f1.cancel(true);
                f2.cancel(true);
                return;
              }
              Thread.sleep(_checkTime);
            }
          }
        }
      }
    }
  }

  private boolean shouldCloseConnection(Future<Void> f1, Future<Void> f2, Socket rdpServerSocket,
      Socket remoteClientTocket) {
    if (f1.isDone()) {
      return true;
    }
    if (f2.isDone()) {
      return true;
    }
    if (rdpServerSocket.isClosed() || !rdpServerSocket.isConnected()) {
      return true;
    }
    if (remoteClientTocket.isClosed() || !remoteClientTocket.isConnected()) {
      return true;
    }
    return false;
  }

  private boolean isCookie(String cookie) {
    return cookie.contains(MSTSHASH);
  }

  private Future<Void> startRelay(AtomicBoolean alive, InputStream input, OutputStream output) {
    return _service.submit(() -> {
      byte[] buf = new byte[_bufferSize];
      int read;
      while (alive.get() && (read = input.read(buf, 0, buf.length)) != -1) {
        output.write(buf, 0, read);
        output.flush();
      }
      return null;
    });
  }

  private String findCookie(byte[] message) {
    StringBuilder builder = new StringBuilder();
    for (int i = 11; i < message.length; i++) {
      if (message[i] == 13) {
        break;
      }
      builder.append((char) message[i]);
    }
    return builder.toString();
  }

  private byte[] readFirstMessage(InputStream input) throws IOException {
    int available = input.available();
    byte[] buf = new byte[available];
    input.read(buf, 0, available);

    if (buf.length == 0) {
      LOGGER.error("Unknown client, hang up");
      return null;
    }
    int tpktVersion = buf[0];
    if (tpktVersion != 3) {
      LOGGER.error("Unknown client, hang up");
      return null;
    }
    short len = getShort(buf, 2);
    if (len == buf.length) {
      return buf;
    }
    LOGGER.error("Length len {} does not match buffer length {}", len, buf.length);
    return null;
  }

  public static short getShort(byte[] b, int off) {
    return (short) ((b[off + 1] & 0xFF) + (b[off] << 8));
  }

}
