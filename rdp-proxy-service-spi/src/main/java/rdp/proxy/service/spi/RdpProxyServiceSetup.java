package rdp.proxy.service.spi;

import spark.Service;

public interface RdpProxyServiceSetup {

  public static RdpProxyServiceSetup INSTANCE = new RdpProxyServiceSetup() {
  };

  default void serviceSetup(Service service) {

  }

}
