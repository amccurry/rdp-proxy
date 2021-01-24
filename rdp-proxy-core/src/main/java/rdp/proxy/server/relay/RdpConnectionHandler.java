package rdp.proxy.server.relay;

import static rdp.proxy.server.relay.MessageUtil.readFirstMessage;
import static rdp.proxy.server.relay.MessageUtil.readMessage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
import rdp.proxy.server.util.ConfigUtils;
import rdp.proxy.spi.ConnectionInfo;
import rdp.proxy.spi.RdpStore;

@Slf4j
public class RdpConnectionHandler implements Callable<Boolean> {

  private final AtomicBoolean _running = new AtomicBoolean(true);
  private final long _waitTimeBetweenAttemptsInSeconds;
  private final int _maxConnectionAttempts;
  private final ExecutorService _service;
  private final int _bufferSize = 10000;
  private final long _relayCheckTimeInSeconds;
  private final int _soTimeout;
  private final int _remoteRdpTcpTimeout;

  private RdpStore _store;

  public RdpConnectionHandler(ExecutorService service) {
    _service = service;
    _waitTimeBetweenAttemptsInSeconds = ConfigUtils.getRdpGatewayWaitTimeBetweenAttemptsToConnectInSeconds();
    _maxConnectionAttempts = ConfigUtils.getRdpGatewayMaxConnectionAttempts();
    _relayCheckTimeInSeconds = ConfigUtils.getRdpGatewayRelayCheckTimeInSeconds();
    _soTimeout = ConfigUtils.getRdpGatewaySoTimeoutInMilliSeconds();
    _remoteRdpTcpTimeout = ConfigUtils.getRdpGatewayRemoteRdpTcpTimeout();
  }

  @Override
  public Boolean call() throws Exception {
    return null;
  }

  private void handleNewConnection(Socket s) throws Exception {
    try (Socket socket = s) {
      log.debug("Socket {} new connection", socket);
      configureSocket(socket);
      BytesRef bytesRef = new BytesRef();
      try (InputStream rcInput = socket.getInputStream(); OutputStream rcOutput = socket.getOutputStream()) {
        log.debug("Socket {} read first message", socket);
        BytesRef message = readFirstMessage(rcInput, bytesRef);
        if (message == null) {
          return;
        }
        log.info("Socket {} find cookie", socket);
        String cookie = findCookie(message);
        Set<ConnectionInfo> connectionInfoSet = _store.getConnectionInfoWithCookie(cookie);
        log.info("Socket {} connectionInfo {} with cookie {} found", socket, connectionInfoSet, cookie);

        if (connectionInfoSet == null || connectionInfoSet.isEmpty()) {
          log.info("Socket {} connection info for cookie {} did not find a remote connection, hang up", socket, cookie);
          return;
        }

        log.info("Connection info {} for cookie {} for remote socket", connectionInfoSet, cookie, socket);
        try (Closeable session = _store.createSession(cookie)) {
          try (Socket rdpServer = createConnection(connectionInfoSet)) {
            AtomicBoolean alive = new AtomicBoolean(true);
            try (InputStream rsInput = rdpServer.getInputStream();
                OutputStream rsOutput = rdpServer.getOutputStream()) {
              rsOutput.write(message.buffer, 0, message.length);
              rsOutput.flush();
              Future<Void> f1 = startRelayReadMessages(alive, rcInput, rsOutput, bytesRef);
              Future<Void> f2 = startRelay(alive, rsInput, rcOutput);
              while (_running.get()) {
                if (!alive.get() || shouldCloseConnection(f1, f2, rdpServer, socket)) {
                  alive.set(false);
                  f1.cancel(true);
                  f2.cancel(true);
                  return;
                }
                Thread.sleep(TimeUnit.SECONDS.toMillis(_relayCheckTimeInSeconds));
              }
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

  private Socket createConnection(Set<ConnectionInfo> connectionInfoSet) throws IOException, InterruptedException {
    for (int attempt = 0; attempt < _maxConnectionAttempts; attempt++) {
      for (ConnectionInfo connectionInfo : connectionInfoSet) {
        Socket rdpServer = new Socket(connectionInfo.getProxy());
        configureSocket(rdpServer);
        SocketAddress _endpoint = new InetSocketAddress(connectionInfo.getAddress(), connectionInfo.getPort());
        try {
          rdpServer.connect(_endpoint, _remoteRdpTcpTimeout);
          return rdpServer;
        } catch (Exception e) {
          log.error("Could not connect to {}", connectionInfo);
          log.error("Connection execption", e);
        }
      }
      Thread.sleep(TimeUnit.SECONDS.toMillis(_waitTimeBetweenAttemptsInSeconds));
    }
    throw new IOException("None of the connectionInfos " + connectionInfoSet + " successfully connected");
  }

  private void configureSocket(Socket rdpServer) throws SocketException {
    rdpServer.setTcpNoDelay(true);
    rdpServer.setSoTimeout(_soTimeout);
    rdpServer.setKeepAlive(true);
  }

  private Future<Void> startRelayReadMessages(AtomicBoolean alive, InputStream input, OutputStream output,
      BytesRef bytesRef) {
    return _service.submit(() -> {
      while (alive.get()) {
        BytesRef message = readMessage(input, bytesRef);
        if (message == null) {
          return null;
        }
        output.write(message.buffer, 0, message.length);
        output.flush();
      }
      return null;
    });
  }

  private Future<Void> startRelay(AtomicBoolean alive, InputStream input, OutputStream output) {
    return _service.submit(() -> {
      byte[] buf = new byte[_bufferSize];
      int read;
      while (alive.get()) {
        if ((read = input.read(buf, 0, buf.length)) == -1) {
          return null;
        }
        output.write(buf, 0, read);
        output.flush();
      }
      return null;
    });
  }

  private String findCookie(BytesRef message) {
    StringBuilder builder = new StringBuilder();
    for (int i = 11; i < message.length; i++) {
      if (message.buffer[i] == 13) {
        break;
      }
      builder.append((char) message.buffer[i]);
    }
    return builder.toString();
  }

}
