package rdp.proxy.server.relay;

import java.io.Closeable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
import rdp.proxy.server.util.ConfigUtils;
import rdp.proxy.server.util.Utils;
import rdp.proxy.spi.RdpGatewayApi;

@Slf4j
public class RdpConnectionRelayV2 implements Closeable {

  private final AtomicReference<Future<Void>> _futureListener = new AtomicReference<>();
  private final AtomicReference<ServerSocket> _ss = new AtomicReference<>();
  private final AtomicBoolean _running = new AtomicBoolean(true);
  private final AtomicBoolean _listening = new AtomicBoolean();
  private final ExecutorService _service;
  private final RdpGatewayApi _rdpGatewayApi;

  public static RdpConnectionRelayV2 create(RdpGatewayApi rdpGatewayApi) {
    return new RdpConnectionRelayV2(rdpGatewayApi);
  }

  private RdpConnectionRelayV2(RdpGatewayApi rdpGatewayApi) {
    _rdpGatewayApi = rdpGatewayApi;
    _service = Executors.newCachedThreadPool();
  }

  public boolean isListening() {
    return _listening.get();
  }

  @Override
  public void close() {
    _running.set(false);
    _listening.set(false);
    Utils.closeQuietly(_ss.get());
    _service.shutdownNow();
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
        String bindAddress = ConfigUtils.getRdpGatewayBindAddress();
        int port = ConfigUtils.getRdpGatewayPort();
        int backlog = ConfigUtils.getRdpGatewayBacklog();
        InetAddress bindAddr = InetAddress.getByName(bindAddress);
        ServerSocket serverSocket = new ServerSocket(port, backlog, bindAddr);
        _ss.set(serverSocket);
        while (_listening.get()) {
          Socket socket;
          try {
            socket = serverSocket.accept();
          } catch (Throwable t1) {
            log.error("Unknown error", t1);
            continue;
          }
          _service.submit(RdpConnectionHandler.create()
                                              .clientSocket(socket)
                                              .rdpGatewayApi(_rdpGatewayApi)
                                              .service(_service)
                                              .build());
        }
        return null;
      }
    };
  }
}
