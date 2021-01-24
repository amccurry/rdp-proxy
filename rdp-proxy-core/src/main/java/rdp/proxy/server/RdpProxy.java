package rdp.proxy.server;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import rdp.proxy.server.relay.RdpConnectionRelayV2;
import rdp.proxy.server.util.ConfigUtils;
import rdp.proxy.server.util.Utils;
import rdp.proxy.spi.RdpGatewayApi;
import rdp.proxy.spi.RdpSetting;
import spark.ResponseTransformer;
import spark.Route;
import spark.Service;

public class RdpProxy implements Closeable {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String RDP_FILE_USER = "/rdp-file/:user";
  private static final String RDP_JSON_USER = "/rdp-json/:user";
  private static final String RDP_INFO_USER = "/rdp-info/:user";
  private static final String CONTENT_DISPOSITION = "Content-Disposition";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String LOADBALANCEINFO = "loadbalanceinfo";
  private static final String USERNAME = "username";
  private static final String FULL_ADDRESS = "full address";
  private static final ResponseTransformer JSON_TRANSFORMER = (ResponseTransformer) model -> OBJECT_MAPPER.writeValueAsString(
      model);

  public static void main(String[] args) throws Exception {
    Object lock = new Object();
    synchronized (lock) {
      try (RdpProxy proxy = new RdpProxy()) {
        proxy.initGateway();
        proxy.initAdmin();
        proxy.start();
        lock.wait();
      }
    }
  }

  private final Service _gatewayService;
  private final Service _adminService;
  private final RdpGatewayApi _rdpGatewayApi;
  private final String _hostnameAdvertised;
  private final RdpConnectionRelayV2 _relay;
  private final int _rdpPortAdvertised;

  public RdpProxy() {
    _hostnameAdvertised = ConfigUtils.getRdpGatewayAdvertisedHostname();
    _rdpPortAdvertised = ConfigUtils.getRdpGatewayAdvertisedPort();
    _gatewayService = ConfigUtils.createGatewayService();
    _adminService = ConfigUtils.createAdminService();
    _rdpGatewayApi = ConfigUtils.createRdpGatewayApi();
    _relay = RdpConnectionRelayV2.create(_rdpGatewayApi);
  }

  public boolean isListening() {
    return _relay.isListening();
  }

  public void start() {
    _relay.startListening();
  }

  public void initAdmin() {

  }

  public void initGateway() throws IOException {
    List<RdpSetting> defaultSettings = ConfigUtils.getRdpDefaults();
    Route infoRoute = (Route) (request, response) -> {
      String user = request.params("user");
      if (!_rdpGatewayApi.isValidUser(user)) {
        _gatewayService.halt(404);
      }

      String loadBalanceInfo = _rdpGatewayApi.getLoadBalanceInfo(user);

      List<RdpSetting> rdpSettings = _rdpGatewayApi.getRdpSettings(defaultSettings, user, loadBalanceInfo,
          _hostnameAdvertised, _rdpPortAdvertised);
      Map<String, RdpSetting> rdpSettingsMap = toMap(rdpSettings);

      addIfMissing(rdpSettingsMap, RdpSetting.create(FULL_ADDRESS, _hostnameAdvertised + ":" + _rdpPortAdvertised));

      RdpSetting fullAddress = rdpSettingsMap.get(FULL_ADDRESS);

      return RdpInfoResponse.builder()
                            .user(user)
                            .loadBalanceInfo(loadBalanceInfo)
                            .hostname(fullAddress.getStringValue())
                            .port(_rdpPortAdvertised)
                            .build();
    };

    Route rdpFileRoute = (request, response) -> {
      String user = request.params("user");
      if (!_rdpGatewayApi.isValidUser(user)) {
        _gatewayService.halt(404);
      }
      String loadBalanceInfo = _rdpGatewayApi.getLoadBalanceInfo(user);

      List<RdpSetting> rdpSettings = _rdpGatewayApi.getRdpSettings(defaultSettings, user, loadBalanceInfo,
          _hostnameAdvertised, _rdpPortAdvertised);
      Map<String, RdpSetting> rdpSettingsMap = toMap(rdpSettings);

      addIfMissing(rdpSettingsMap, RdpSetting.create(FULL_ADDRESS, _hostnameAdvertised + ":" + _rdpPortAdvertised));
      addIfMissing(rdpSettingsMap, RdpSetting.create(USERNAME, user));
      addIfMissing(rdpSettingsMap, RdpSetting.create(LOADBALANCEINFO, loadBalanceInfo));

      String filename = _rdpGatewayApi.getFilename(user);
      response.header(CONTENT_TYPE, "application/rdp; charset=utf-8");
      response.header(CONTENT_DISPOSITION, "attachment; filename=" + filename);
      HttpServletResponse servletResponse = response.raw();
      PrintWriter printWriter = servletResponse.getWriter();
      for (RdpSetting setting : rdpSettingsMap.values()) {
        printWriter.println(setting + "\r");
      }
      printWriter.flush();
      return "";
    };

    _gatewayService.get(RDP_INFO_USER, infoRoute);
    _gatewayService.get(RDP_JSON_USER, infoRoute, JSON_TRANSFORMER);
    _gatewayService.get(RDP_FILE_USER, rdpFileRoute);
  }

  @Override
  public void close() throws IOException {
    Utils.closeQuietly(() -> _gatewayService.stop());
    Utils.closeQuietly(() -> _adminService.stop());
    Utils.closeQuietly(_relay);
  }

  private void addIfMissing(Map<String, RdpSetting> rdpSettingsMap, RdpSetting setting) {
    if (!rdpSettingsMap.containsKey(setting.getName())) {
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
