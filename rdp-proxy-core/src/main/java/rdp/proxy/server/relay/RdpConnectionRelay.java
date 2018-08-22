package rdp.proxy.server.relay;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Set;
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

  private final ServerSocket _ss;
  private final AtomicBoolean _running = new AtomicBoolean(true);
  private final ExecutorService _service;
  private final RdpStore _store;
  private final int _bufferSize;
  private final long _relayCheckTime = TimeUnit.SECONDS.toMillis(1);
  private final int _remoteRdpTcpTimeout;
  private final int _soTimeout;

  public RdpConnectionRelay(RdpProxyConfig config, RdpStore store) throws IOException {
    _bufferSize = config.getRdpRelayBufferSize();
    _remoteRdpTcpTimeout = config.getRdpRemoteTcpTimeout();
    _soTimeout = config.getRdpSoTimeout();
    _store = store;

    String bindAddress = config.getRdpBindAddress();
    int port = config.getRdpPort();
    int backlog = config.getRdpBacklog();
    InetAddress bindAddr = InetAddress.getByName(bindAddress);
    _ss = new ServerSocket(port, backlog, bindAddr);
    _service = Executors.newCachedThreadPool();
  }

  public void exec() {
    while (_running.get()) {
      Socket socket;
      try {
        socket = _ss.accept();
      } catch (Throwable t) {
        LOGGER.error("Unknown error", t);
        continue;
      }
      _service.submit(() -> {
        try {
          handleNewConnection(socket);
          LOGGER.debug("Socket {} closed", socket);
        } catch (Throwable t) {
          LOGGER.error("Unknown error, during new connection setup", t);
        }
      });
    }
  }

  private void handleNewConnection(Socket s) throws Exception {
    try (Socket socket = s) {
      LOGGER.debug("Socket {} new connection", socket);
      socket.setTcpNoDelay(true);
      socket.setSoTimeout(_soTimeout);
      socket.setKeepAlive(true);
      try (InputStream rcInput = socket.getInputStream(); OutputStream rcOutput = socket.getOutputStream()) {
        LOGGER.debug("Socket {} read first message", socket);
        byte[] message = readFirstMessage(rcInput);
        if (message == null) {
          return;
        }
        LOGGER.info("Socket {} find cookie", socket);
        String cookie = findCookie(message);
        Set<ConnectionInfo> connectionInfoSet = _store.getConnectionInfoWithCookie(cookie);
        LOGGER.info("Socket {} connectionInfo {} with cookie {} found", socket, connectionInfoSet, cookie);

        if (connectionInfoSet == null || connectionInfoSet.isEmpty()) {
          LOGGER.info("Socket {} connection info for cookie {} did not find a remote connection, hang up", socket,
              cookie);
          return;
        }

        LOGGER.info("Connection info {} for cookie {} for remote socket", connectionInfoSet, cookie, socket);

        try (Socket rdpServer = createConnection(connectionInfoSet)) {
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
              Thread.sleep(_relayCheckTime);
            }
          }
        }
      }
    }
  }

  private Socket createConnection(Set<ConnectionInfo> connectionInfoSet) throws IOException {
    for (ConnectionInfo connectionInfo : connectionInfoSet) {
      Socket rdpServer = new Socket(connectionInfo.getProxy());
      rdpServer.setTcpNoDelay(true);
      rdpServer.setSoTimeout(_soTimeout);
      rdpServer.setKeepAlive(true);
      SocketAddress _endpoint = new InetSocketAddress(connectionInfo.getAddress(), connectionInfo.getPort());
      try {
        rdpServer.connect(_endpoint, _remoteRdpTcpTimeout);
        return rdpServer;
      } catch (Exception e) {
        LOGGER.error("Could not connect to {}", connectionInfo);
        LOGGER.error("Connection execption", e);
      }
    }
    throw new IOException("None of the connectionInfos " + connectionInfoSet + " successfully connected");
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
    DataInputStream dataInput = new DataInputStream(input);
    int tpktVersion = dataInput.read();
    if (tpktVersion == -1) {
      return null;
    }
    if (tpktVersion != 3) {
      LOGGER.error("Unknown client, hang up");
      return null;
    }
    
    // read reserve
    int reserve = dataInput.readUnsignedByte();
    
    int len = dataInput.readUnsignedShort();
    byte[] buf = new byte[len - 4];
    dataInput.readFully(buf);

    if (buf.length + 4 != len) {
      LOGGER.error("Length len {} does not match buffer length {}, hang up", len, buf.length + 4);
      return null;
    }

    byte[] result = new byte[len];
    result[0] = (byte) tpktVersion;
    result[1] = (byte) reserve;
    putShort(result, 2, (short) len);
    System.arraycopy(buf, 0, result, 4, buf.length);
    return result;
  }

  public static short getShort(byte[] b, int off) {
    return (short) ((b[off + 1] & 0xFF) + (b[off] << 8));
  }

  public static void putShort(byte[] b, int off, short val) {
    b[off + 1] = (byte) (val);
    b[off] = (byte) (val >>> 8);
  }

}
