package rdp.proxy.service.spi;

import spark.Service;

public interface RdpServiceSetup {

  public static RdpServiceSetup INSTANCE = new RdpServiceSetup() {

  };

  default void serviceSetup(Service service) {

  }

}
