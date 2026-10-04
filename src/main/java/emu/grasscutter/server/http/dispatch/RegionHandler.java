package emu.grasscutter.server.http.dispatch;

import static emu.grasscutter.config.Configuration.*;

import com.google.gson.*;
import com.google.protobuf.ByteString;
import emu.grasscutter.*;
import emu.grasscutter.Grasscutter.ServerRunMode;
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
import io.javalin.config.RoutesConfig;
import io.javalin.http.Context;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Handles requests related to region queries. */
public final class RegionHandler implements Router {
    private static final Map<String, RegionData> regions = new ConcurrentHashMap<>();
    private static final Map<String, String> regionTitles = new ConcurrentHashMap<>();
    private static ByteString regionConfigEncrypted;
    private static ByteString regionConfigEncryptedCN;

    public RegionHandler() {
        try {
            this.initialize();
        } catch (Exception exception) {
            Grasscutter.getLogger().error("Failed to initialize region data.", exception);
        }
    }

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
        return scheme
                + "://"
                + HTTP_INFO.bindAddress
                + ":"
                + lr(HTTP_INFO.accessPort, HTTP_INFO.bindPort);
    }

    private void initialize() {
        regions.clear();
        regionTitles.clear();

        var configuredRegions = new ArrayList<>(DISPATCH_INFO.regions);
        if (Grasscutter.getRunMode() != ServerRunMode.HYBRID && configuredRegions.isEmpty()) {
            Grasscutter.getLogger()
                    .error(
                            "[Dispatch] There are no game servers available. Exiting due to unplayable state.");
            System.exit(1);
        } else if (configuredRegions.isEmpty()) {
            configuredRegions.add(
                    new Region(
                            "os_usa",
                            DISPATCH_INFO.defaultName,
                            lr(GAME_INFO.accessAddress, GAME_INFO.bindAddress),
                            lr(GAME_INFO.accessPort, GAME_INFO.bindPort)));
        }

        configuredRegions.forEach(
                region -> {
                    var regionInfo =
                            RegionInfo.newBuilder()
                                    .setGateserverIp(region.Ip)
                                    .setGateserverPort(region.Port)
                                    .build();
                    var updatedQuery =
                            QueryCurrRegionHttpRsp.newBuilder()
                                    .setRegionInfo(regionInfo)
                                    .setClientSecretKey(ByteString.copyFrom(Crypto.DISPATCH_SEED))
                                    .setRegionCustomConfigEncrypted(buildRegionCustomConfigEncrypted())
                                    .build();
                    regionTitles.put(
                            region.Name,
                            region.Title == null || region.Title.isEmpty() ? region.Name : region.Title);
                    regions.put(
                            region.Name,
                            new RegionData(
                                    updatedQuery,
                                    Utils.base64Encode(updatedQuery.toByteString().toByteArray())));
                });

        var hiddenIcons = new JsonArray();
        hiddenIcons.add(40);
        var codeSwitch = new JsonArray();
        codeSwitch.add(4334);

        var customConfig = new JsonObject();
        customConfig.addProperty("sdkenv", "2");
        customConfig.addProperty("checkdevice", "false");
        customConfig.addProperty("loadPatch", "false");
        customConfig.addProperty("showexception", String.valueOf(GameConstants.DEBUG));
        customConfig.addProperty("regionConfig", "pm|fk|add");
        customConfig.addProperty("downloadMode", "0");
        customConfig.add("codeSwitch", codeSwitch);
        customConfig.add("coverSwitch", hiddenIcons);

        var encodedConfig = JsonUtils.encode(customConfig).getBytes();
        Crypto.xor(encodedConfig, Crypto.DISPATCH_KEY);
        regionConfigEncrypted = ByteString.copyFrom(encodedConfig);

        customConfig.addProperty("sdkenv", "0");
        encodedConfig = JsonUtils.encode(customConfig).getBytes();
        Crypto.xor(encodedConfig, Crypto.DISPATCH_KEY);
        regionConfigEncryptedCN = ByteString.copyFrom(encodedConfig);
    }

    private static String buildRegionListResponse(Context ctx, boolean cn) {
        var dispatchDomain = getEffectiveDispatchDomain(ctx);
        var servers = new ArrayList<RegionSimpleInfo>();
        regions.keySet().stream()
                .sorted()
                .forEach(
                        name ->
                                servers.add(
                                        RegionSimpleInfo.newBuilder()
                                                .setName(name)
                                                .setTitle(regionTitles.getOrDefault(name, name))
                                                .setType("DEV_PUBLIC")
                                                .setDispatchUrl(dispatchDomain + "/query_cur_region/" + name)
                                                .build()));
        var updatedRegionList =
                QueryRegionListHttpRsp.newBuilder()
                        .addAllRegionList(servers)
                        .setClientSecretKey(ByteString.copyFrom(Crypto.DISPATCH_SEED))
                        .setClientCustomConfigEncrypted(
                                cn ? regionConfigEncryptedCN : regionConfigEncrypted)
                        .setEnableLoginPc(true)
                        .build();
        return Utils.base64Encode(updatedRegionList.toByteString().toByteArray());
    }

    @Override
    public void applyRoutes(RoutesConfig routes) {
        routes.get("/query_region_list", RegionHandler::queryRegionList);
        routes.get("/query_cur_region/{region}", RegionHandler::queryCurrentRegion);
        routes.get("/query_server_address", RegionHandler::queryServerAddress);
    }

    private static void queryRegionList(Context ctx) {
        String versionName = ctx.queryParam("version");
        boolean cn =
                versionName != null
                        && (versionName.startsWith("CNRELiOS")
                                || versionName.startsWith("CNRELWin")
                                || versionName.startsWith("CNRELAnd"));

        QueryAllRegionsEvent event = new QueryAllRegionsEvent(buildRegionListResponse(ctx, cn));
        event.call();
        ctx.result(event.getRegionList());

        Grasscutter.getLogger()
                .info(
                        String.format(
                                "[Dispatch] Client %s request: query_region_list",
                                Utils.address(ctx)));
    }

    private static void queryCurrentRegion(Context ctx) {
        String versionName = ctx.queryParam("version");
        String regionName = ctx.pathParam("region");
        var region = regions.get(regionName);
        var loadedHotfix = RegionVersionConfigLoader.load(versionName).orElse(null);

        if (loadedHotfix != null) {
            Grasscutter.getLogger()
                    .debug(
                            "Using region hotfix {} for client {}",
                            loadedHotfix.source(),
                            versionName);
        }

        if (!Grasscutter.getConfig().server.game.useXorEncryption) {
            var query =
                    buildRegionQuery(
                                    ctx,
                                    region,
                                    loadedHotfix == null ? null : loadedHotfix.regionInfo())
                            .toByteArray();
            QueryCurrentRegionEvent event =
                    new QueryCurrentRegionEvent(Utils.base64Encode(query));
            event.call();

            byte[] regionInfo = Utils.base64Decode(event.getRegionInfo());
            String keyId = ctx.queryParam("key_id");
            if (keyId != null && !keyId.isBlank()) {
                try {
                    ctx.json(Crypto.encryptAndSignRegionData(regionInfo, keyId));
                    logCurrentRegion(ctx, regionName);
                    return;
                } catch (Exception exception) {
                    Grasscutter.getLogger()
                            .warn("Failed to encrypt region data for key_id {}", keyId, exception);
                }
            }

            var rsp = new QueryCurRegionRspJson();
            rsp.content = event.getRegionInfo();
            rsp.sign = "TW9yZSBsb3ZlIGZvciBVQSBQYXRjaCBwbGF5ZXJz";
            ctx.json(rsp);
            logCurrentRegion(ctx, regionName);
            return;
        }

        String regionData = "CAESGE5vdCBGb3VuZCB2ZXJzaW9uIGNvbmZpZw==";
        if (!ctx.queryParamMap().values().isEmpty() && (region != null || loadedHotfix != null)) {
            var effectiveQuery =
                    buildRegionQuery(
                            ctx, region, loadedHotfix == null ? null : loadedHotfix.regionInfo());
            regionData = Utils.base64Encode(effectiveQuery.toByteString().toByteArray());
        }

        var clientVersion =
                versionName == null
                        ? ""
                        : versionName.replaceAll(Pattern.compile("[a-zA-Z]").pattern(), "");
        var versionCode = clientVersion.split("\\.");
        int versionMajor = 0, versionMinor = 0, versionFix = 0;
        try {
            versionMajor = Integer.parseInt(versionCode[0]);
            versionMinor = Integer.parseInt(versionCode[1]);
            versionFix = Integer.parseInt(versionCode[2]);
        } catch (RuntimeException ignored) {
            versionMajor = 0;
        }

        if (versionMajor >= 3
                || (versionMajor == 2 && versionMinor == 7 && versionFix >= 50)
                || (versionMajor == 2 && versionMinor == 8)) {
            try {
                QueryCurrentRegionEvent event = new QueryCurrentRegionEvent(regionData);
                event.call();

                String keyId = ctx.queryParam("key_id");

                if (versionMajor != GameConstants.VERSION_PARTS[0]
                        || versionMinor != GameConstants.VERSION_PARTS[1]) {
                    boolean updateClient = GameConstants.VERSION.compareTo(clientVersion) > 0;

                    QueryCurrRegionHttpRsp rsp =
                            QueryCurrRegionHttpRsp.newBuilder()
                                    .setRetcode(Retcode.RET_STOP_SERVER_VALUE)
                                    .setMsg("Connection Failed!")
                                    .setRegionInfo(RegionInfo.newBuilder())
                                    .setStopServer(
                                            StopServerInfo.newBuilder()
                                                    .setUrl("https://discord.gg/T5vZU6UyeG")
                                                    .setStopBeginTime((int) Instant.now().getEpochSecond())
                                                    .setStopEndTime(
                                                            (int) Instant.now().getEpochSecond() + 1)
                                                    .setContentMsg(
                                                            updateClient
                                                                    ? "\nVersion mismatch outdated client! \n\nServer version: %s\nClient version: %s"
                                                                            .formatted(
                                                                                    GameConstants.VERSION,
                                                                                    clientVersion)
                                                                    : "\nVersion mismatch outdated server! \n\nServer version: %s\nClient version: %s"
                                                                            .formatted(
                                                                                    GameConstants.VERSION,
                                                                                    clientVersion))
                                                    .build())
                                    .buildPartial();

                    Grasscutter.getLogger()
                            .debug(
                                    String.format(
                                            "Connection denied for %s due to %s.",
                                            Utils.address(ctx),
                                            updateClient ? "outdated client!" : "outdated server!"));

                    ctx.json(Crypto.encryptAndSignRegionData(rsp.toByteArray(), keyId));
                    return;
                }

                if (ctx.queryParam("dispatchSeed") == null) {
                    var rsp = new QueryCurRegionRspJson();
                    rsp.content = event.getRegionInfo();
                    rsp.sign = "TW9yZSBsb3ZlIGZvciBVQSBQYXRjaCBwbGF5ZXJz";
                    ctx.json(rsp);
                    return;
                }

                var regionInfo = Utils.base64Decode(event.getRegionInfo());
                ctx.json(Crypto.encryptAndSignRegionData(regionInfo, keyId));
            } catch (Exception e) {
                Grasscutter.getLogger()
                        .error("An error occurred while handling query_cur_region.", e);
            }
        } else {
            QueryCurrentRegionEvent event = new QueryCurrentRegionEvent(regionData);
            event.call();
            ctx.result(event.getRegionInfo());
        }

        logCurrentRegion(ctx, regionName);
    }

    private static void logCurrentRegion(Context ctx, String regionName) {
        Grasscutter.getLogger()
                .info(
                        String.format(
                                "Client %s request: query_cur_region/%s",
                                Utils.address(ctx), regionName));
    }

    private static JsonObject buildRegionCustomConfig() {
        var hiddenIcons = new JsonArray();
        hiddenIcons.add(40);
        hiddenIcons.add(41);
        hiddenIcons.add(42);

        var codeSwitch = new JsonArray();
        codeSwitch.add(4334);

        var customConfig = new JsonObject();
        customConfig.addProperty("sdkenv", "2");
        customConfig.addProperty("checkdevice", "false");
        customConfig.addProperty("loadPatch", "false");
        customConfig.addProperty("showexception", String.valueOf(GameConstants.DEBUG));
        customConfig.addProperty("regionConfig", "pm");
        customConfig.addProperty("downloadMode", "0");
        customConfig.add("codeSwitch", codeSwitch);
        customConfig.add("coverSwitch", hiddenIcons);
        return customConfig;
    }

    private static ByteString buildRegionCustomConfigEncrypted() {
        return encryptRegionCustomConfig(new Gson().toJson(buildRegionCustomConfig()));
    }

    private static ByteString encryptRegionCustomConfig(String json) {
        try {
            var key = Crypto.CUR_SIGNING_KEY;
            if (key == null) return ByteString.EMPTY;

            var keyFactory = java.security.KeyFactory.getInstance("RSA");
            var privKeySpec =
                    keyFactory.getKeySpec(key, java.security.spec.RSAPrivateCrtKeySpec.class);
            var pubKeySpec =
                    new java.security.spec.RSAPublicKeySpec(
                            privKeySpec.getModulus(), privKeySpec.getPublicExponent());
            var pubKey = keyFactory.generatePublic(pubKeySpec);

            var cipher = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, pubKey);

            byte[] data = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int keySize = ((java.security.interfaces.RSAKey) pubKey).getModulus().bitLength() / 8;
            int chunkSize = keySize - 11;

            var out = new java.io.ByteArrayOutputStream();
            for (int i = 0; i < data.length; i += chunkSize) {
                byte[] chunk =
                        Arrays.copyOfRange(data, i, Math.min(i + chunkSize, data.length));
                out.write(cipher.doFinal(chunk));
            }
            return ByteString.copyFrom(out.toByteArray());
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn("[Dispatch] Failed to encrypt RegionCustomConfig: {}", e.getMessage());
            return ByteString.EMPTY;
        }
    }

    private static QueryCurrRegionHttpRsp buildRegionQuery(
            Context ctx, RegionData configuredRegion, RegionInfo hotfixRegion) {
        QueryCurrRegionHttpRsp baseQuery =
                configuredRegion == null
                        ? QueryCurrRegionHttpRsp.newBuilder()
                                .setClientSecretKey(ByteString.copyFrom(Crypto.DISPATCH_SEED))
                                .setRegionCustomConfigEncrypted(buildRegionCustomConfigEncrypted())
                                .build()
                        : configuredRegion.getRegionQuery();
        RegionInfo.Builder regionInfo =
                (hotfixRegion == null ? baseQuery.getRegionInfo() : hotfixRegion).toBuilder();

        regionInfo.setGateserverIp(getEffectiveGameAddress(ctx));
        if (configuredRegion != null) {
            regionInfo.setGateserverPort(baseQuery.getRegionInfo().getGateserverPort());
        }

        return baseQuery.toBuilder().setRegionInfo(regionInfo).build();
    }

    public static void queryServerAddress(Context ctx) {
        var addr = Grasscutter.getConfig().server.game.accessAddress;
        var port =
                Grasscutter.getConfig().server.game.accessPort == 0
                        ? Grasscutter.getConfig().server.game.bindPort
                        : Grasscutter.getConfig().server.game.accessPort;
        ctx.result(addr + ":" + port);
    }

    /** Region data container. */
    public static class RegionData {
        private final QueryCurrRegionHttpRsp regionQuery;
        private final String base64;

        public RegionData(QueryCurrRegionHttpRsp prq, String b64) {
            this.regionQuery = prq;
            this.base64 = b64;
        }

        public QueryCurrRegionHttpRsp getRegionQuery() {
            return this.regionQuery;
        }

        public String getBase64() {
            return this.base64;
        }
    }

    public static QueryCurrRegionHttpRsp getCurrentRegion() {
        return Grasscutter.getRunMode() == ServerRunMode.HYBRID
                ? regions.get("os_usa").getRegionQuery()
                : null;
    }
}
