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
import rdp.proxy.service.spi.RdpProxyServiceSetup;
import rdp.proxy.spi.BaseRdpStore;
import rdp.proxy.spi.RdpSetting;
import rdp.proxy.spi.RdpStore;
import rdp.proxy.spi.config.ConfigUtil;
import spark.Service;

@SuppressWarnings("unchecked")
public class Utils {

  public static final String RDP_SO_TIMEOUT = "RDP_SO_TIMEOUT";
  public static final String RDP_REMOTE_TCP_TIMEOUT = "RDP_REMOTE_TCP_TIMEOUT";
  public static final String RDP_RELAY_BUFFER_SIZE = "RDP_RELAY_BUFFER_SIZE";
  public static final String RDP_BACKLOG = "RDP_BACKLOG";
  public static final String RDP_PROXY_SETUP_CLASSNAME = "RDP_PROXY_SETUP_CLASSNAME";
  public static final String RDP_META_STORE_CLASSNAME = "RDP_META_STORE_CLASSNAME";
  public static final String RDP_HTTP_BIND_ADDRESS = "RDP_HTTP_BIND_ADDRESS";
  public static final String RDP_HOSTNAME_ADVERTISED = "RDP_HOSTNAME_ADVERTISED";
  public static final String RDP_BIND_ADDRESS = "RDP_BIND_ADDRESS";
  public static final String RDP_HTTP_PORT = "RDP_HTTP_PORT";
  public static final String RDP_PORT = "RDP_PORT";

  private static final String DEFAULT_RDP = "/default.rdp";

  public static RdpProxyServiceSetup getRdpProxySetup(RdpProxyConfig config) throws Exception {
    String rdpProxySetupClassname = config.getRdpProxySetupClassname();
    if (rdpProxySetupClassname == null) {
      return RdpProxyServiceSetup.INSTANCE;
    }
    Class<? extends RdpProxyServiceSetup> clazz = (Class<? extends RdpProxyServiceSetup>) Class.forName(
        rdpProxySetupClassname);
    return clazz.newInstance();
  }

  public static RdpStore getRdpMetaStore(RdpProxyConfig config) throws Exception {
    String rdpMetaStoreClassname = config.getRdpMetaStoreClassname();
    if (rdpMetaStoreClassname == null) {
      return BaseRdpStore.INSTANCE;
    }
    Class<? extends RdpStore> clazz = (Class<? extends RdpStore>) Class.forName(rdpMetaStoreClassname);
    return clazz.newInstance();
  }

  public static RdpProxyConfig getConfig() {
    RdpProxyConfigBuilder builder = RdpProxyConfig.builder();
    builder.rdpPort(ConfigUtil.loadProperty(RDP_PORT, value -> Integer.parseInt(value)));
    builder.rdpHttpPort(ConfigUtil.loadProperty(RDP_HTTP_PORT, value -> Integer.parseInt(value)));
    builder.rdpBindAddress(ConfigUtil.loadProperty(RDP_BIND_ADDRESS, value -> value));
    builder.rdpHttpBindAddress(ConfigUtil.loadProperty(RDP_HTTP_BIND_ADDRESS, value -> value));
    builder.rdpHostnameAdvertised(ConfigUtil.loadProperty(RDP_HOSTNAME_ADVERTISED, value -> value));

    builder.rdpProxySetupClassname(ConfigUtil.loadProperty(RDP_PROXY_SETUP_CLASSNAME, value -> value));
    builder.rdpMetaStoreClassname(ConfigUtil.loadProperty(RDP_META_STORE_CLASSNAME, value -> value));
    builder.rdpBacklog(ConfigUtil.loadProperty(RDP_BACKLOG, value -> Integer.parseInt(value)));
    builder.rdpRelayBufferSize(ConfigUtil.loadProperty(RDP_RELAY_BUFFER_SIZE, value -> Integer.parseInt(value)));
    builder.rdpRemoteTcpTimeout(ConfigUtil.loadProperty(RDP_REMOTE_TCP_TIMEOUT, value -> Integer.parseInt(value)));
    builder.rdpSoTimeout(ConfigUtil.loadProperty(RDP_SO_TIMEOUT, value -> Integer.parseInt(value)));

    return builder.build();
  }

  public static Service igniteService(RdpProxyConfig config) throws Exception {
    RdpProxyServiceSetup setup = getRdpProxySetup(config);
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

}
