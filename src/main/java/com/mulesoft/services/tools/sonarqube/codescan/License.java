package com.mulesoft.services.tools.sonarqube.codescan;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.sonar.api.ExtensionPoint;
import org.sonar.api.batch.ScannerSide;
import org.sonar.api.config.Configuration;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonarsource.api.sonarlint.SonarLintSide;

@ScannerSide
@ExtensionPoint
@SonarLintSide
public class License {

    private final Configuration settings;

    private static final Logger logger = Loggers.get(License.class);

    public static final String PROXY_URL = "codescan.license_proxy";

    public License(Configuration settings) {
        this.settings = settings;
    }

    public AddonStatus isMulesoftEnabled() {
        AddonStatus addonStatus = new AddonStatus();
        addonStatus.setAddon("mulesoft");
        addonStatus.setEnabled(false);
        try {
            Optional<String> urlString = settings.get("sonar.host.url");
            Optional<String> organization = settings.get("sonar.organization");

            if (urlString.isPresent() && organization.isPresent()) {
                addonStatus.setOrganization(organization.get());
                URL url = new URL(urlString.get());
                String host = url.getHost();
                if (host.endsWith(".codescan.io") || host.endsWith(".autorabit.com")) {
                    HttpGet licenseCheckRequest = new HttpGet(
                            url.getProtocol() + "://" + host + "/_codescan/billing/organization/" + organization.get()
                                    + "/addons/mulesoft");
                    String login = StringUtils.stripToEmpty(settings.get("sonar.login").orElse(""));
                    if (!login.isEmpty()) {
                        String authorization =
                                login + ":" + StringUtils.stripToEmpty(settings.get("sonar.password").orElse(""));
                        licenseCheckRequest.addHeader(HttpHeaders.AUTHORIZATION,
                                "Basic " + Base64.encodeBase64String(authorization.getBytes(StandardCharsets.UTF_8)));
                    }

                    HttpResponse response = getRequest(licenseCheckRequest);

                    if (response.getStatusLine().getStatusCode() != 200) {
                        logger.debug("Failed response from CodeScan");
                        addonStatus.setMessage("MuleSoft : Failed response from CodeScan License Check");
                        return addonStatus;
                    } else {
                        ObjectMapper objectMapper = new ObjectMapper();
                        addonStatus = objectMapper.readValue(response.getEntity().getContent(),
                                AddonStatus.class);
                        return addonStatus;
                    }
                } else {
                    addonStatus.setMessage("MuleSoft : This feature is enabled for SAAS customers");
                    return addonStatus;
                }
            } else {
                addonStatus.setMessage("MuleSoft : Host or Organization is missing");
                return addonStatus;
            }
        } catch (Exception e) {
            addonStatus.setMessage("MuleSoft : Something Went Wrong!!!");
            return addonStatus;
        }
    }

    private HttpResponse getRequest(HttpRequestBase httpRequest) throws IOException {
        final int timeout = 60;

        setLoggerThreshold("org.apache.http.wire", org.sonar.api.utils.log.LoggerLevel.INFO);
        setLoggerThreshold("org.apache.http.headers", org.sonar.api.utils.log.LoggerLevel.INFO);
        HttpClientBuilder httpBuilder = HttpClientBuilder
                .create();

        boolean hasSetProxy = false;
        if (!StringUtils.isEmpty(System.getProperty("http.proxyHost"))) {
            Integer proxyPort = NumberUtils.createInteger(System.getProperty("http.proxyPort"));
            if (proxyPort != null) {
                logger.debug(
                        "Setting proxy from env to " + System.getProperty("http.proxyHost") + ":" + proxyPort + " (u/p "
                                + System.getProperty("http.proxyUser") + ":" + (
                                StringUtils.isBlank(System.getProperty("http.proxyPassword")) ? "" : "****") + ")");
                hasSetProxy = true;
                httpBuilder.useSystemProperties();
            }
        }

        if (!hasSetProxy) {
            Optional<String> proxyUrl = settings.get(License.PROXY_URL);
            if (proxyUrl.isPresent() && !"".equals(proxyUrl.get())) {
                URL url = new URL(proxyUrl.get());
                HttpHost httpHost = new HttpHost(url.getHost(), url.getPort());
                httpBuilder.setProxy(httpHost);
                String username;
                String password;
                if (url.getUserInfo() != null) {
                    final String usernamePassword = url.getUserInfo();
                    final int atColon = usernamePassword.indexOf(':');
                    if (atColon >= 0) {
                        username = usernamePassword.substring(0, atColon);
                        password = usernamePassword.substring(atColon + 1);
                    } else {
                        username = usernamePassword;
                        password = null;
                    }
                    BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
                    credsProvider.setCredentials(new AuthScope(httpHost),
                            new UsernamePasswordCredentials(username, password));
                    httpBuilder
                            .setDefaultCredentialsProvider(credsProvider)
                            .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
                    logger.debug("Setting proxy from config to " + url.getHost() + ":" + url.getPort() + " (u/p " + (
                            username.length() <= 10 ? StringUtils.left(username, 10) + "****" : username) + ":" + (
                            StringUtils.isBlank(password) ? "" : "****") + ")");
                } else {
                    logger.debug("Setting proxy from config to " + url.getHost() + ":" + url.getPort());
                }


            }
        }

        CloseableHttpClient httpclient = httpBuilder.build();

        httpRequest.setConfig(RequestConfig.custom()
                .setConnectTimeout(timeout * 1000)
                .setConnectionRequestTimeout(timeout * 1000)
                .setSocketTimeout(timeout * 1000)
                .build());
        return httpclient.execute(httpRequest);
    }

    private static void setLoggerThreshold(String name, org.sonar.api.utils.log.LoggerLevel level) {
        try {
            Object o = Loggers.get(name);
            Class<?> c = o.getClass();
            Method setMethod = c.getMethod("setLevel", org.sonar.api.utils.log.LoggerLevel.class);
            setMethod.setAccessible(true);
            setMethod.invoke(o, level);
        } catch (Exception e) {
            logger.debug("Couldn't set log threshold: " + e.getMessage());
        }
    }
}
