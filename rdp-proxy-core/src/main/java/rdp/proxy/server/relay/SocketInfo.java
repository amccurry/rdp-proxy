package rdp.proxy.server.relay;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

@Value
@AllArgsConstructor
@Builder
public class SocketInfo {

  public static void main(String args[]) throws IOException {
    new Thread(() -> {
      try (ServerSocket ss = new ServerSocket(5555)) {
        try (Socket socket = ss.accept()) {
          System.out.println("server " + SocketInfo.create(socket));
          Thread.sleep(5000);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }).start();
    try (Socket sock = new Socket("127.0.0.01", 5555)) {
      System.out.println("client " + SocketInfo.create(sock));
    }
  }

  InetAddress inetAddress;
  int port;
  long start = System.nanoTime();

  public static SocketInfo create(Socket socket) {
    return SocketInfo.builder()
                     .inetAddress(socket.getInetAddress())
                     .port(socket.getPort())
                     .build();
  }

  public String getInfo() {
    return toString() + " lifetime " + (System.nanoTime() - start) / 1_000_000_000L + " sec";
  }

  @Override
  public String toString() {
    return inetAddress + ":" + port;
  }

}
