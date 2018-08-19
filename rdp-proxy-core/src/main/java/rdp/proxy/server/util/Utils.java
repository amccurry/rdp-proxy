package rdp.proxy.server.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import rdp.proxy.server.RdpProxyConfig;
import rdp.proxy.server.RdpProxyConfig.RdpProxyConfigBuilder;
import rdp.proxy.server.setup.RdpProxySetup;
import rdp.proxy.spi.RdpStore;
import rdp.proxy.spi.RdpSetting;
import spark.Service;

@SuppressWarnings("unchecked")
public class Utils {

  private static final String RDP_HTTP_BIND_ADDRESS = "RDP_HTTP_BIND_ADDRESS";
  private static final String DEFAULT_RDP = "/default.rdp";
  private static final String RDP_HOSTNAME_ADVERTISED = "RDP_HOSTNAME_ADVERTISED";
  private static final String RDP_BIND_ADDRESS = "RDP_BIND_ADDRESS";
  private static final String RDP_HTTP_PORT = "RDP_HTTP_PORT";
  private static final String RDP_PORT = "RDP_PORT";

  public static RdpProxySetup getRdpProxySetup(RdpProxyConfig config) throws Exception {
    String rdpProxySetupClassname = config.getRdpProxySetupClassname();
    if (rdpProxySetupClassname == null) {
      return RdpProxySetup.INSTANCE;
    }
    Class<? extends RdpProxySetup> clazz = (Class<? extends RdpProxySetup>) Class.forName(rdpProxySetupClassname);
    return clazz.newInstance();
  }

  public static RdpStore getRdpMetaStore(RdpProxyConfig config) throws Exception {
    String rdpMetaStoreClassname = config.getRdpMetaStoreClassname();
    if (rdpMetaStoreClassname == null) {
      return RdpStore.INSTANCE;
    }
    Class<? extends RdpStore> clazz = (Class<? extends RdpStore>) Class.forName(rdpMetaStoreClassname);
    return clazz.newInstance();
  }

  public static RdpProxyConfig getConfig() {
    RdpProxyConfigBuilder builder = RdpProxyConfig.builder();
    loadProperty(RDP_PORT, value -> builder.rdpPort(Integer.parseInt(value)));
    loadProperty(RDP_HTTP_PORT, value -> builder.rdpHttpPort(Integer.parseInt(value)));
    loadProperty(RDP_BIND_ADDRESS, value -> builder.rdpBindAddress(value));
    loadProperty(RDP_HTTP_BIND_ADDRESS, value -> builder.rdpHttpBindAddress(value));
    loadProperty(RDP_HOSTNAME_ADVERTISED, value -> builder.rdpHostnameAdvertised(value));
    return builder.build();
  }

  public static Service igniteService(RdpProxyConfig config) throws Exception {
    RdpProxySetup setup = getRdpProxySetup(config);
    Service service = Service.ignite();
    service.port(config.getRdpHttpPort());
    service.ipAddress(config.getRdpHttpBindAddress());
    setup.serviceSetup(service);
    return service;
  }

  public static List<RdpSetting> getRdpDefaults() throws IOException {
    InputStream inputStream = Utils.class.getResourceAsStream(DEFAULT_RDP);
    Builder<RdpSetting> builder = ImmutableList.builder();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trim = line.trim();
        if (!trim.isEmpty()) {
          builder.add(RdpSetting.parse(trim));
        }
      }
    }
    return builder.build();
  }

  private static void loadProperty(String name, Loader loader) {
    String value = getProperty(name);
    if (value != null) {
      loader.load(value);
    }
  }

  private static String getProperty(String name) {
    String property = System.getProperty(name);
    if (property != null) {
      return property;
    }
    return System.getenv(name);
  }

  private static interface Loader {
    void load(String value);
  }
}
