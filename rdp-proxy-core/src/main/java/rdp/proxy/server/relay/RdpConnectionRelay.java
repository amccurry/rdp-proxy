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
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rdp.proxy.server.RdpProxyConfig;
import rdp.proxy.spi.ConnectionInfo;
import rdp.proxy.spi.RdpStore;

public class RdpConnectionRelay {

  private static final String MSTSHASH = "mstshash";

  private static final Logger LOGGER = LoggerFactory.getLogger(RdpConnectionRelay.class);

  private final ServerSocket _ss;
  private final AtomicBoolean _running = new AtomicBoolean(true);
  private final ExecutorService _service;
  private final RdpStore _store;
  private final int _bufferSize = 10000;
  private final long _checkTime = 1000;

  private int _remoteRdpTimeout;

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
      socket.setTcpNoDelay(true);
      try (InputStream rcInput = socket.getInputStream(); OutputStream rcOutput = socket.getOutputStream()) {
        byte[] message = readFirstMessage(rcInput);
        ConnectionInfo connectionInfo;
        String cookie = findCookie(message);
        if (isCookie(cookie)) {
          connectionInfo = _store.startRdpSessionIfMissingWithCookie(cookie);
        } else {
          connectionInfo = _store.startRdpSessionIfMissingWithId(cookie);
        }

        if (connectionInfo == null) {
          LOGGER.info("Connection info for cookie {} did not find a remote connection, hang up on {}", cookie, socket);
          return;
        }

        LOGGER.info("Connection info {} for cookie {} for remote socket", connectionInfo, cookie, socket);

        try (Socket rdpServer = new Socket(connectionInfo.getProxy())) {
          rdpServer.setTcpNoDelay(true);
          SocketAddress _endpoint = new InetSocketAddress(connectionInfo.getAddress(), connectionInfo.getPort());
          rdpServer.connect(_endpoint, _remoteRdpTimeout);
          try (InputStream rsInput = rdpServer.getInputStream(); OutputStream rsOutput = rdpServer.getOutputStream()) {
            rsOutput.write(message);
            rsOutput.flush();
            AtomicBoolean alive = new AtomicBoolean(true);
            Future<Void> f1 = startRelay(alive, rcInput, rsOutput);
            Future<Void> f2 = startRelay(alive, rsInput, rcOutput);
            while (_running.get()) {
              if (f1.isDone() || f2.isDone()) {
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

    int tpktVersion = buf[0];
    if (tpktVersion != 3) {
      throw new IOException("Unknown client");
    }
    short len = getShort(buf, 2);
    if (len == buf.length) {
      return buf;
    }
    throw new IOException("Length len " + len + " does not match buffer length " + buf.length);
  }

  public static short getShort(byte[] b, int off) {
    return (short) ((b[off + 1] & 0xFF) + (b[off] << 8));
  }

}
