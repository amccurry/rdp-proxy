package rdp.proxy.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import rdp.proxy.server.relay.RdpConnectionRelay;
import rdp.proxy.server.util.Utils;
import rdp.proxy.spi.RdpStore;
import rdp.proxy.spi.RdpSetting;
import spark.ResponseTransformer;
import spark.Route;
import spark.Service;

public class RdpProxy implements Closeable {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String LOADBALANCEINFO = "loadbalanceinfo";
  private static final String USERNAME = "username";
  private static final String FULL_ADDRESS = "full address";
  private static final ResponseTransformer JSON_TRANSFORMER = (ResponseTransformer) model -> OBJECT_MAPPER.writeValueAsString(
      model);

  public static void main(String[] args) throws Exception {
    RdpProxyConfig config = Utils.getConfig();
    try (RdpProxy proxy = new RdpProxy(config)) {
      proxy.init();
      proxy.join();
    }
  }

  private final Service _service;
  private final RdpStore _store;
  private final RdpProxyConfig _config;
  private final String _hostnameAdvertised;
  private final int _rdpPort;
  private final RdpConnectionRelay _relay;

  public RdpProxy(RdpProxyConfig config) throws Exception {
    _config = config;
    _hostnameAdvertised = config.getRdpHostname();
    _rdpPort = _config.getRdpPort();
    _service = Utils.igniteService(config);
    _store = Utils.getRdpMetaStore(config);
    _relay = new RdpConnectionRelay(_config, _store);
  }

  public void join() throws InterruptedException {
    _relay.exec();
  }

  public void init() throws IOException {
    List<RdpSetting> defaultSettings = Utils.getRdpDefaults();
    Route infoRoute = (Route) (request, response) -> {
      String user = request.params("user");
      if (!_store.isValidUser(user)) {
        _service.halt(404);
      }

      String loadBalanceInfo = _store.getLoadBalanceInfo(user);

      List<RdpSetting> rdpSettings = _store.getRdpSettings(defaultSettings, user, loadBalanceInfo, _hostnameAdvertised,
          _rdpPort);
      Map<String, RdpSetting> rdpSettingsMap = toMap(rdpSettings);
      RdpSetting fullAddress = rdpSettingsMap.get(FULL_ADDRESS);

      return RdpInfoResponse.builder()
                            .user(user)
                            .loadBalanceInfo(loadBalanceInfo)
                            .hostname(fullAddress.getStringValue())
                            .port(_rdpPort)
                            .build();
    };

    Route rdpFileRoute = (request, response) -> {
      String user = request.params("user");
      if (!_store.isValidUser(user)) {
        _service.halt(404);
      }
      String loadBalanceInfo = _store.getLoadBalanceInfo(user);

      List<RdpSetting> rdpSettings = _store.getRdpSettings(defaultSettings, user, loadBalanceInfo, _hostnameAdvertised,
          _rdpPort);
      Map<String, RdpSetting> rdpSettingsMap = toMap(rdpSettings);

      // full address:s:localhost:3389
      addIfMissing(rdpSettingsMap, RdpSetting.create(FULL_ADDRESS, _hostnameAdvertised + ":" + _rdpPort));

      // username:s:red
      addIfMissing(rdpSettingsMap, RdpSetting.create(USERNAME, user));

      // loadbalanceinfo:s:
      addIfMissing(rdpSettingsMap, RdpSetting.create(LOADBALANCEINFO, loadBalanceInfo));

      String filename = _store.getFilename(user);
      response.header(CONTENT_TYPE, "application/rdp; charset=utf-8");
      response.header(CONTENT_DISPOSITION, "attachment; filename=" + filename);
      HttpServletResponse servletResponse = response.raw();
      PrintWriter printWriter = servletResponse.getWriter();
      for (RdpSetting setting : rdpSettingsMap.values()) {
        printWriter.println(setting);
      }
      printWriter.flush();
      return null;
    };

    _service.get("/rdp-info/:user", infoRoute);
    _service.get("/rdp-json/:user", infoRoute, JSON_TRANSFORMER);
    _service.get("/rdp-file/:user", rdpFileRoute);
  }

  @Override
  public void close() throws IOException {
    _service.stop();
  }

  private void addIfMissing(Map<String, RdpSetting> rdpSettingsMap, RdpSetting setting) {
    if (!rdpSettingsMap.containsKey(setting)) {
      rdpSettingsMap.put(setting.getName(), setting);
    }
  }

  private Map<String, RdpSetting> toMap(List<RdpSetting> rdpSettings) {
    Map<String, RdpSetting> map = new HashMap<>();
    for (RdpSetting rdpSetting : rdpSettings) {
      map.put(rdpSetting.getName(), rdpSetting);
    }
    return map;
  }
}
