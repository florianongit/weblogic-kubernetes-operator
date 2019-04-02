// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.http.HttpClient;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.Scan;
import oracle.kubernetes.operator.rest.ScanCache;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import oracle.kubernetes.weblogic.domain.model.SubsystemHealth;
import org.joda.time.DateTime;

public class ReadHealthStep extends Step {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private ReadHealthStep(Step next) {
    super(next);
  }

  /**
   * Creates asynchronous {@link Step} to read health from a server instance.
   *
   * @param next Next processing step
   * @return asynchronous step
   */
  public static Step createReadHealthStep(Step next) {
    return new ReadHealthStep(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

    Domain dom = info.getDomain();
    V1ObjectMeta meta = dom.getMetadata();
    DomainSpec spec = dom.getSpec();
    String namespace = meta.getNamespace();

    String serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);

    String secretName =
        spec.getWebLogicCredentialsSecret() == null
            ? null
            : spec.getWebLogicCredentialsSecret().getName();

    Step getClient =
        HttpClient.createAuthenticatedClientForServer(
            namespace,
            secretName,
            new ReadHealthWithHttpClientStep(info.getServerService(serverName), getNext()));
    return doNext(getClient, packet);
  }

  private static String getRetrieveHealthSearchUrl() {
    return "/management/weblogic/latest/serverRuntime/search";
  }

  // overallHealthState, healthState

  private static String getRetrieveHealthSearchPayload() {
    return "{ fields: [ 'overallHealthState', 'activationTime' ], links: [] }";
  }

  static final class ReadHealthWithHttpClientStep extends Step {
    private final V1Service service;

    ReadHealthWithHttpClientStep(V1Service service, Step next) {
      super(next);
      this.service = service;
    }

    @Override
    public NextAction apply(Packet packet) {
      try {
        HttpClient httpClient = (HttpClient) packet.get(HttpClient.KEY);
        DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
        Scan scan = ScanCache.INSTANCE.lookupScan(info.getNamespace(), info.getDomainUID());
        WlsDomainConfig domainConfig = scan.getWlsDomainConfig();
        String serverName = (String) packet.get(ProcessingConstants.SERVER_NAME);
        WlsServerConfig serverConfig = domainConfig.getServerConfig(serverName);

        if (serverConfig == null) {
          // dynamic server
          String clusterName = service.getMetadata().getLabels().get(CLUSTERNAME_LABEL);
          WlsClusterConfig cluster = domainConfig.getClusterConfig(clusterName);
          serverConfig = cluster.getDynamicServersConfig().getServerConfig(serverName);
        }

        String serviceURL =
            HttpClient.getServiceURL(service, serverConfig.getAdminProtocolChannelName());
        if (serviceURL != null) {
          String jsonResult =
              httpClient
                  .executePostUrlOnServiceClusterIP(
                      getRetrieveHealthSearchUrl(),
                      serviceURL,
                      getRetrieveHealthSearchPayload(),
                      true)
                  .getResponse();

          ObjectMapper mapper = new ObjectMapper();
          JsonNode root = mapper.readTree(jsonResult);

          JsonNode state = null;
          JsonNode subsystemName = null;
          JsonNode symptoms = null;
          JsonNode overallHealthState = root.path("overallHealthState");
          if (overallHealthState != null) {
            state = overallHealthState.path("state");
            subsystemName = overallHealthState.path("subsystemName");
            symptoms = overallHealthState.path("symptoms");
          }
          JsonNode activationTime = root.path("activationTime");

          List<String> sym = new ArrayList<>();
          if (symptoms != null) {
            Iterator<JsonNode> it = symptoms.elements();
            while (it.hasNext()) {
              sym.add(it.next().asText());
            }
          }

          String subName = null;
          if (subsystemName != null) {
            String s = subsystemName.asText();
            if (s != null && !"null".equals(s)) {
              subName = s;
            }
          }

          ServerHealth health =
              new ServerHealth()
                  .withOverallHealth(state != null ? state.asText() : null)
                  .withActivationTime(
                      activationTime != null ? new DateTime(activationTime.asLong()) : null);
          if (subName != null) {
            health
                .getSubsystems()
                .add(new SubsystemHealth().withSubsystemName(subName).withSymptoms(sym));
          }

          @SuppressWarnings("unchecked")
          ConcurrentMap<String, ServerHealth> serverHealthMap =
              (ConcurrentMap<String, ServerHealth>)
                  packet.get(ProcessingConstants.SERVER_HEALTH_MAP);
          serverHealthMap.put((String) packet.get(ProcessingConstants.SERVER_NAME), health);
        }
        return doNext(packet);
      } catch (Throwable t) {
        // do not retry for health check
        LOGGER.fine(
            MessageKeys.WLS_HEALTH_READ_FAILED, packet.get(ProcessingConstants.SERVER_NAME));
        LOGGER.fine(MessageKeys.EXCEPTION, t);
        return doNext(packet);
      }
    }
  }
}
