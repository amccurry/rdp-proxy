package rdp.proxy.server.setup;

import spark.Service;

public interface RdpProxySetup {

  public static RdpProxySetup INSTANCE = new RdpProxySetup() {
  };

  default void serviceSetup(Service service) {

  }

}
