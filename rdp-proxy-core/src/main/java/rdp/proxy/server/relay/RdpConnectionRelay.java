package rdp.proxy.server.relay;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import com.google.common.collect.ImmutableMap;

import rdp.proxy.server.RdpProxyConfig;
import rdp.proxy.server.util.Utils;
import rdp.proxy.spi.ConnectionInfo;
import rdp.proxy.spi.RdpStore;

public class RdpConnectionRelay implements Closeable {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdpConnectionRelay.class);

  public static final String RDP_CONNECTIONS_COUNTER = "rdp.connections.counter";
  public static final String RDP_CONNECTION_TIMER_SERVER_TO_CLIENT = "rdp.connection.timer.server.to.client";
  public static final String RDP_CONNECTION_TIMER_CLIENT_TO_SERVER = "rdp.connection.timer.client.to.server";
  public static final String BANDWIDTH_METER_CLIENT_TO_SERVER = ".bandwidth.meter.client.to.server";
  public static final String BANDWIDTH_METER_SERVER_TO_CLIENT = ".bandwidth.meter.server.to.client";
  public static final String RDP_CONNECTION_METER_SERVER_TO_CLIENT = "rdp.connection.meter.server.to.client";
  public static final String RDP_CONNECTION_METER_CLIENT_TO_SERVER = "rdp.connection.meter.client.to.server";

  private final static char[] HES_ARRAY = "0123456789ABCDEF".toCharArray();

  private final AtomicReference<Future<Void>> _futureListener = new AtomicReference<>();
  private final AtomicReference<ServerSocket> _ss = new AtomicReference<>();
  private final AtomicBoolean _running = new AtomicBoolean(true);
  private final AtomicBoolean _listening = new AtomicBoolean();
  private final ExecutorService _service;
  private final RdpStore _store;
  private final int _bufferSize;
  private final int _remoteRdpTcpTimeout;
  private final int _soTimeout;
  private final Counter _connectionCounter;
  private final Timer _connectionTimerClientToServer;
  private final Timer _connectionTimerServerToClient;
  private final Map<String, ConnectionProxyInstance> _connectionMap = new ConcurrentHashMap<>();
  private final MetricRegistry _metrics;
  private final Meter _connectionMeterServerToClient;
  private final Meter _connectionMeterClientToServer;
  private final RdpProxyConfig _config;
  private final long _relayCheckTime = TimeUnit.SECONDS.toMillis(1);
  private final long _waitTimeBetweenAttempts;
  private final int _maxConnectionAttempts;

  public RdpConnectionRelay(RdpProxyConfig config, RdpStore store, MetricRegistry metrics) throws IOException {
    _metrics = metrics;
    _bufferSize = config.getRdpRelayBufferSize();
    _remoteRdpTcpTimeout = config.getRdpRemoteTcpTimeout();
    _soTimeout = config.getRdpSoTimeout();
    _store = store;
    _waitTimeBetweenAttempts = config.getWaitTimeBetweenAttempts();
    _maxConnectionAttempts = config.getMaxConnectionAttempts();
    _config = config;

    _service = Executors.newCachedThreadPool();
    _connectionCounter = metrics.counter(RDP_CONNECTIONS_COUNTER);
    _connectionTimerClientToServer = metrics.timer(RDP_CONNECTION_TIMER_CLIENT_TO_SERVER);
    _connectionTimerServerToClient = metrics.timer(RDP_CONNECTION_TIMER_SERVER_TO_CLIENT);
    _connectionMeterClientToServer = metrics.meter(RDP_CONNECTION_METER_CLIENT_TO_SERVER);
    _connectionMeterServerToClient = metrics.meter(RDP_CONNECTION_METER_SERVER_TO_CLIENT);
  }

  public boolean isListening() {
    return _listening.get();
  }

  @Override
  public void close() throws IOException {
    _running.set(false);
    _listening.set(false);
    Utils.closeQuietly(_ss.get());
    _service.shutdownNow();
  }

  public Map<String, ConnectionProxyInstance> getConnectionMap() {
    return ImmutableMap.copyOf(_connectionMap);
  }

  public void kill(String id) {
    ConnectionProxyInstance connectionProxyInstance = _connectionMap.get(id);
    if (connectionProxyInstance != null) {
      LOGGER.info("Killing connection {}", id, connectionProxyInstance);
      connectionProxyInstance.getAlive()
                             .set(false);
    } else {
      LOGGER.info("Connection not found {}", id);
    }
  }

  public void startListening() {
    Future<Void> future = _futureListener.get();
    if (future == null || future.isDone()) {
      _listening.set(true);
      _futureListener.set(_service.submit(getCallable()));
    }
  }

  public void stopListening() {
    Future<Void> future = _futureListener.get();
    if (future != null) {
      _listening.set(false);
      Utils.closeQuietly(_ss.get());
      future.cancel(true);
    }
  }

  private Callable<Void> getCallable() {
    return new Callable<Void>() {
      @Override
      public Void call() throws Exception {
        String bindAddress = _config.getRdpBindAddress();
        int port = _config.getRdpPort();
        int backlog = _config.getRdpBacklog();
        InetAddress bindAddr = InetAddress.getByName(bindAddress);
        ServerSocket serverSocket = new ServerSocket(port, backlog, bindAddr);
        _ss.set(serverSocket);
        while (_listening.get()) {
          Socket socket;
          try {
            socket = serverSocket.accept();
          } catch (Throwable t1) {
            LOGGER.error("Unknown error", t1);
            continue;
          }
          _service.submit(() -> {
            try {
              handleNewConnection(socket);
              LOGGER.debug("Socket {} closed", socket);
            } catch (Throwable t2) {
              LOGGER.error("Unknown error, during new connection setup", t2);
            }
          });
        }
        return null;
      }
    };
  }

  private void handleNewConnection(Socket s) throws Exception {
    _connectionCounter.inc();
    try (Socket socket = s) {
      SocketInfo clientConnection = SocketInfo.create(socket);
      LOGGER.debug("Socket {} new connection", socket);
      socket.setTcpNoDelay(true);
      socket.setSoTimeout(_soTimeout);
      socket.setKeepAlive(true);
      BytesRef bytesRef = new BytesRef();
      try (InputStream rcInput = socket.getInputStream(); OutputStream rcOutput = socket.getOutputStream()) {
        LOGGER.debug("Socket {} read first message", socket);
        BytesRef message = readFirstMessage(rcInput, bytesRef);
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
          AtomicBoolean alive = new AtomicBoolean(true);
          String id = UUID.randomUUID()
                          .toString();
          SocketInfo serverConnection = SocketInfo.create(rdpServer);
          _connectionMap.put(id, ConnectionProxyInstance.builder()
                                                        .client(clientConnection)
                                                        .server(serverConnection)
                                                        .id(id)
                                                        .alive(alive)
                                                        .build());
          Meter bandwidthClientToServer = _metrics.meter(getClientToServerBandwidthName(id));
          Meter bandwidthServerToClient = _metrics.meter(getServerToClientBandwidthName(id));
          try (InputStream rsInput = rdpServer.getInputStream(); OutputStream rsOutput = rdpServer.getOutputStream()) {
            rsOutput.write(message.buffer, 0, message.length);
            rsOutput.flush();

            Future<Void> f1 = startRelayReadMessages(alive, rcInput, rsOutput, _connectionTimerClientToServer,
                bandwidthClientToServer, _connectionMeterClientToServer, bytesRef);
            Future<Void> f2 = startRelay(alive, rsInput, rcOutput, _connectionTimerServerToClient,
                bandwidthServerToClient, _connectionMeterServerToClient);
            while (_running.get()) {
              if (!alive.get() || shouldCloseConnection(f1, f2, rdpServer, socket)) {
                alive.set(false);
                f1.cancel(true);
                f2.cancel(true);
                return;
              }
              Thread.sleep(_relayCheckTime);
            }
          } finally {
            _metrics.remove(getClientToServerBandwidthName(id));
            _metrics.remove(getServerToClientBandwidthName(id));
            _connectionMap.remove(id);
          }
        }
      }
    } finally {
      _connectionCounter.dec();
    }
  }

  public static String getServerToClientBandwidthName(String id) {
    return id + BANDWIDTH_METER_SERVER_TO_CLIENT;
  }

  public static String getClientToServerBandwidthName(String id) {
    return id + BANDWIDTH_METER_CLIENT_TO_SERVER;
  }

  private Socket createConnection(Set<ConnectionInfo> connectionInfoSet) throws IOException, InterruptedException {
    for (int attempt = 0; attempt < _maxConnectionAttempts; attempt++) {
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
      Thread.sleep(_waitTimeBetweenAttempts);
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

  private Future<Void> startRelayReadMessages(AtomicBoolean alive, InputStream input, OutputStream output, Timer timer,
      Meter bandwidth, Meter overallBandwidth, BytesRef bytesRef) {
    return _service.submit(() -> {
      while (alive.get()) {
        BytesRef message = readMessage(input, bytesRef);
        if (message == null) {
          return null;
        }
        try (Context context = timer.time()) {
          output.write(message.buffer, 0, message.length);
          output.flush();
          bandwidth.mark(message.length);
          overallBandwidth.mark(message.length);
        }
      }
      return null;
    });
  }

  private Future<Void> startRelay(AtomicBoolean alive, InputStream input, OutputStream output, Timer timer,
      Meter bandwidth, Meter overallBandwidth) {
    return _service.submit(() -> {
      byte[] buf = new byte[_bufferSize];
      int read;
      while (alive.get()) {
        if ((read = input.read(buf, 0, buf.length)) == -1) {
          return null;
        }
        try (Context context = timer.time()) {
          output.write(buf, 0, read);
          output.flush();
          bandwidth.mark(read);
          overallBandwidth.mark(read);
        }
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

  private static BytesRef readMessage(InputStream input, BytesRef bytesRef) throws IOException {
    int b1 = input.read();
    switch (b1) {
    case -1:
      // socket closed
      return null;

    case 3:
      return readMessageType1(input, b1, bytesRef);

    case 20:
    case 21:
    case 22:
    case 23:
      return readMessageType2(input, b1, bytesRef);

    case 96:
    case 132:
    case 176:
    case 184:
      return readMessageType3(input, b1, bytesRef);

    default:
      break;
    }

    int available = input.available();
    byte[] buf = new byte[available + 1];
    buf[0] = (byte) b1;
    readFully(input, buf, 1, available);
    LOGGER.error("Unknown message, available {} msg {}", available, bytesToHex(buf));

    return null;
  }

  private static BytesRef readMessageType3(InputStream input, int b1, BytesRef bytesRef) throws IOException {
    int len = input.read();
    if (!isValidLength(len)) {
      return null;
    }
    bytesRef.growSetLength(len);
    readFully(input, bytesRef.buffer, 2, len - 2);
    bytesRef.buffer[0] = (byte) b1;
    bytesRef.buffer[1] = (byte) len;
    return bytesRef;
  }

  private static boolean isValidLength(int len) {
    return len >= 0;
  }

  private static BytesRef readMessageType2(InputStream input, int b1, BytesRef bytesRef) throws IOException {
    int r1 = input.read();
    int r2 = input.read();
    int len = readUnsignedShort(input);
    if (!isValidLength(len)) {
      return null;
    }

    bytesRef.growSetLength(len + 5);
    readFully(input, bytesRef.buffer, 5, len);
    bytesRef.buffer[0] = (byte) b1;
    bytesRef.buffer[1] = (byte) r1;
    bytesRef.buffer[2] = (byte) r2;
    putShort(bytesRef.buffer, 3, (short) len);
    return bytesRef;
  }

  private static BytesRef readMessageType1(InputStream input, int b1, BytesRef bytesRef) throws IOException {
    int r1 = input.read();
    int len = readUnsignedShort(input);
    if (!isValidLength(len)) {
      return null;
    }

    bytesRef.growSetLength(len);
    readFully(input, bytesRef.buffer, 4, len - 4);
    bytesRef.buffer[0] = (byte) b1;
    bytesRef.buffer[1] = (byte) r1;
    putShort(bytesRef.buffer, 2, (short) len);
    return bytesRef;
  }

  private BytesRef readFirstMessage(InputStream input, BytesRef bytesRef) throws IOException {
    int tpktVersion = input.read();
    if (tpktVersion == -1) {
      return null;
    }
    if (tpktVersion != 3) {
      LOGGER.error("Unknown client, hang up");
      return null;
    }
    return readMessageType1(input, tpktVersion, bytesRef);
  }

  public static short getShort(byte[] b, int off) {
    return (short) ((b[off + 1] & 0xFF) + (b[off] << 8));
  }

  public static void putShort(byte[] b, int off, short val) {
    b[off + 1] = (byte) (val);
    b[off] = (byte) (val >>> 8);
  }

  public static void readFully(InputStream input, byte[] buf, int off, int len) throws IOException {
    if (len < 0) {
      throw new IndexOutOfBoundsException();
    }
    int n = 0;
    while (n < len) {
      int count = input.read(buf, off + n, len - n);
      if (count < 0) {
        throw new EOFException();
      }
      n += count;
    }
  }

  public static int readUnsignedShort(InputStream input) throws IOException {
    int ch1 = input.read();
    int ch2 = input.read();
    if ((ch1 | ch2) < 0)
      throw new EOFException();
    return (ch1 << 8) + (ch2 << 0);
  }

  public static String bytesToHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder();
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      builder.append((char) HES_ARRAY[v >>> 4]);
      builder.append((char) HES_ARRAY[v & 0x0F]);
      builder.append(' ');
    }
    return builder.toString();
  }
}
