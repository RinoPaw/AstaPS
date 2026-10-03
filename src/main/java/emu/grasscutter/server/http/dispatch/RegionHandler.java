package emu.grasscutter.server.http.dispatch;

import static emu.grasscutter.config.Configuration.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;
import emu.grasscutter.*;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.config.ConfigContainer.Region;
import emu.grasscutter.net.proto.QueryCurrRegionHttpRspOuterClass.QueryCurrRegionHttpRsp;
import emu.grasscutter.net.proto.QueryRegionListHttpRspOuterClass.QueryRegionListHttpRsp;
import emu.grasscutter.net.proto.RegionInfoOuterClass.RegionInfo;
import emu.grasscutter.net.proto.RegionSimpleInfoOuterClass.RegionSimpleInfo;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.StopServerInfoOuterClass.StopServerInfo;
import emu.grasscutter.server.event.dispatch.*;
import emu.grasscutter.server.http.Router;
import emu.grasscutter.server.http.objects.QueryCurRegionRspJson;
import emu.grasscutter.utils.*;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;

/** Handles requests related to region queries. */
public final class RegionHandler implements Router {
    private static final Map<String, RegionData> regions = new ConcurrentHashMap<>();
    private static String regionListResponse;
    private static String regionListResponseCN;
    private static com.google.protobuf.ByteString regionConfigEncrypted;
    private static com.google.protobuf.ByteString regionConfigEncryptedCN;

    public RegionHandler() {
        try { // Read and initialize region data.
            this.initialize();
        } catch (Exception exception) {
            Grasscutter.getLogger().error("Failed to initialize region data.", exception);
        }
    }

    /**
     * Determines the effective game-server address for a request.
     * Priority: configured accessAddress -^ request host -^ bindAddress.
     * This lets clients on localhost / LAN / public IP / domain all join.
     */
    private static String getEffectiveGameAddress(Context ctx) {
        var configured = GAME_INFO.accessAddress;
        if (configured != null && !configured.isEmpty() && !configured.equals("0.0.0.0")) {
            return configured;
        }
        var host = ctx.host();
        if (host != null && !host.isEmpty()) {
            var h = host.contains(":") ? host.substring(0, host.lastIndexOf(':')) : host;
            if (!h.equals("127.0.0.1") && !h.equalsIgnoreCase("localhost") && !h.isEmpty()) {
                return h;
            }
        }
        return GAME_INFO.bindAddress;
    }

    /** Determines the effective dispatch domain for a request (config -^ request host -^ bind). */
    private static String getEffectiveDispatchDomain(Context ctx) {
        var scheme = "http" + (HTTP_ENCRYPTION.useInRouting ? "s" : "");
        var configured = HTTP_INFO.accessAddress;
        if (configured != null && !configured.isEmpty() && !configured.equals("0.0.0.0")) {
            return scheme + "://" + configured + ":" + lr(HTTP_INFO.accessPort, HTTP_INFO.bindPort);
        }
        var host = ctx.host();
        if (host != null && !host.isEmpty()) {
            var h = host.contains(":") ? host.substring(0, host.lastIndexOf(':')) : host;
            if (!h.equals("127.0.0.1") && !h.equalsIgnoreCase("localhost") && !h.isEmpty()) {
                return scheme + "://" + h + ":" + ctx.port();
            }
        }
        return scheme + "://" + HTTP_INFO.bindAddress + ":" + lr(HTTP_INFO.accessPort, HTTP_INFO.bindPort);
    }

    /** Configures region data according to configuration. */
    private void initialize() {
        // Create regions.
        var configuredRegions = new ArrayList<>(DISPATCH_INFO.regions);
        if (Grasscutter.getRunMode() != ServerRunMode.HYBRID && configuredRegions.size() == 0) {
            Grasscutter.getLogger()
                    .error(
                            "[Dispatch] There are no game servers available. Exiting due to unplayable state.");
            System.exit(1);
        } else if (configuredRegions.size() == 0)
            configuredRegions.add(
                    new Region(
                            "os_usa",
                            DISPATCH_INFO.defaultName,
                            lr(GAME_INFO.accessAddress, GAME_INFO.bindAddress),
                            lr(GAME_INFO.accessPort, GAME_INFO.bindPort)));

        configuredRegions.forEach(
                region -> {
                    // Create a region info object.
                    var regionInfo =
                            RegionInfo.newBuilder()
                                    .setGateserverIp(region.Ip)
                                    .setGateserverPort(region.Port)
                                    .build();
                    // Create an updated region query.
                    var updatedQuery =
                            QueryCurrRegionHttpRsp.newBuilder()
                                    .setRegionInfo(regionInfo)
                                    .setClientSecretKey(ByteString.copyFrom(Crypto.DISPATCH_SEED))
                                    .setRegionCustomConfigEncrypted(buildRegionCustomConfigEncrypted())
                                    .build();
                    regions.put(
                            region.Name,
                            new RegionData(
                                    updatedQuery, Utils.base64Encode(updatedQuery.toByteString().toByteArray())));
                });

        // Determine config settings.
        var hiddenIcons = new JsonArray();
        hiddenIcons.add(40);
        var codeSwitch = new JsonArray();
        codeSwitch.add(4334);

        // Create a config object.
        var customConfig = new JsonObject();
        customConfig.addProperty("sdkenv", "2");
        customConfig.addProperty("checkdevice", "false");
        customConfig.addProperty("loadPatch", "false");
        customConfig.addProperty("showexception", String.valueOf(GameConstants.DEBUG));
        customConfig.addProperty("regionConfig", "pm|fk|add");
        customConfig.add("codeSwitch", codeSwitch);
        customConfig.add("coverSwitch", new JsonArray());
        customConfig.add("perf_report_config", new JsonObject());
        customConfig.add("perf_report_record_config", new JsonObject());
        customConfig.add("perf_report_host_config", new JsonObject());
        customConfig.addProperty("url_check", "false");
        customConfig.addProperty("account_url", "");
        customConfig.addProperty("account_url_bak", "");
        customConfig.addProperty("pay_callback_url", "");
        customConfig.addProperty("resource_url", "");
        customConfig.addProperty("resource_url_bak", "");
        customConfig.addProperty("data_url", "");
        customConfig.addProperty("data_url_bak", "");
        customConfig.addProperty("feedback_url", "");
        customConfig.addProperty("feedback_url_bak", "");
        customConfig.addProperty("disable_mmt", "false");
        customConfig.addProperty("stop_server", "false");
        customConfig.addProperty("enable_console", "true");
        customConfig.addProperty("log_level", "INFO");
        customConfig.addProperty("sdkenv", "2");
        customConfig.addProperty("checkdevice", "false");
        customConfig.addProperty("loadPatch", "false");
        customConfig.addProperty("showexception", String.valueOf(GameConstants.DEBUG));
        customConfig.addProperty("regionConfig", "pm|fk|add");
        customConfig.add("codeSwitch", codeSwitch);
        customConfig.add("coverSwitch", new JsonArray());
        customConfig.add("perf_report_config", new JsonObject());
        customConfig.add("perf_report_record_config", new JsonObject());
        customConfig.add("perf_report_host_config", new JsonObject());
        customConfig.addProperty("url_check", "false");
        customConfig.addProperty("account_url", "");
        customConfig.addProperty("account_url_bak", "");
        customConfig.addProperty("pay_callback_url", "");
        customConfig.addProperty("resource_url", "");
        customConfig.addProperty("resource_url_bak", "");
        customConfig.addProperty("data_url", "");
        customConfig.addProperty("data_url_bak", "");
        customConfig.addProperty("feedback_url", "");
        customConfig.addProperty("feedback_url_bak", "");
        customConfig.addProperty("disable_mmt", "false");
        customConfig.addProperty("stop_server", "false");
        customConfig.addProperty("enable_console", "true");
        customConfig.addProperty("log_level", "INFO");
        customConfig.add("hidden_icon", hiddenIcons);

        regionConfigEncrypted =
                ByteString.copyFrom(Crypto.xor(DISPATCH_INFO.encryptionKey, customConfig.toString().getBytes()));
        regionConfigEncryptedCN = regionConfigEncrypted;

        regionListResponse = this.buildRegionList(false);
        regionListResponseCN = this.buildRegionList(true);
    }

    private String buildRegionList(boolean isCn) {
        var builder = QueryRegionListHttpRsp.newBuilder();
        builder.setRetcode(Retcode.RET_SUCC_VALUE);
        builder.setClientSecretKey(ByteString.copyFrom(Crypto.DISPATCH_SEED));

        regions.forEach(
                (name, data) -> {
                    var info =
                            RegionSimpleInfo.newBuilder()
                                    .setName(name)
                                    .setTitle(DISPATCH_INFO.defaultName)
                                    .setType("DEV_PUBLIC")
                                    .setDispatchUrl(getDispatchAddress() + "/query_cur_region/" + name)
                                    .build();
                    builder.addRegionList(info);
                });

        builder.setClientDataVersion(GameConstants.VERSION);
        builder.setClientSilenceDataVersion(GameConstants.VERSION);
        builder.setEnableLoginPc(true);
        builder.setRegionConfigEncrypted(isCn ? regionConfigEncryptedCN : regionConfigEncrypted);
        return Utils.base64Encode(builder.build().toByteString().toByteArray());
    }

    private static String getDispatchAddress() {
        String address = HTTP_INFO.accessAddress;
        if (address == null || address.isBlank()) address = HTTP_INFO.bindAddress;
        return "http"
                + (HTTP_ENCRYPTION.useInRouting ? "s" : "")
                + "://"
                + address
                + ":"
                + lr(HTTP_INFO.accessPort, HTTP_INFO.bindPort);
    }

    @Override
    public void applyRoutes(Javalin javalin) {
        javalin.get("/query_region_list", this::queryRegionList);
        javalin.get("/query_cur_region/{region}", this::queryCurrentRegion);
        javalin.get("/query_cur_region", this::queryCurrentRegion);
        javalin.get("/query_cur_region/{region}/", this::queryCurrentRegion);
        javalin.get("/query_cur_region/", this::queryCurrentRegion);
    }

    private void queryRegionList(Context ctx) {
        ctx.result(Utils.serialize(ctx.queryParam("version"), regionListResponse, regionListResponseCN));
    }

    private void queryCurrentRegion(Context ctx) {
        var name = ctx.pathParamMap().getOrDefault("region", "os_usa");
        var data = regions.getOrDefault(name, regions.values().stream().findFirst().orElse(null));
        if (data == null) {
            ctx.status(404);
            return;
        }

        // If accessAddress is blank/0.0.0.0, adapt the gateserver IP to the request host.
        var query = data.query;
        var effective = getEffectiveGameAddress(ctx);
        if (!effective.equals(query.getRegionInfo().getGateserverIp())) {
            query =
                    query.toBuilder()
                            .setRegionInfo(query.getRegionInfo().toBuilder().setGateserverIp(effective).build())
                            .build();
        }

        var event = new QueryCurrentRegionEvent(ctx, query);
        event.call();
        if (event.isCanceled()) {
            ctx.status(403);
            return;
        }

        ctx.result(
                Utils.serialize(
                        ctx.queryParam("version"),
                        Utils.base64Encode(event.getRegionInfo().toByteArray()),
                        Utils.base64Encode(event.getRegionInfo().toByteArray())));
    }

    private static final class RegionData {
        private final QueryCurrRegionHttpRsp query;
        private final String encoded;

        private RegionData(QueryCurrRegionHttpRsp query, String encoded) {
            this.query = query;
            this.encoded = encoded;
        }
    }
}
