package com.ibm.pross.server.app.http;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.ibm.pross.server.app.avpss.ApvssShareholder;
import com.ibm.pross.server.app.http.handlers.ExponentiateHandler;
import com.ibm.pross.server.app.http.handlers.GenerateHandler;
import com.ibm.pross.server.app.http.handlers.InfoHandler;
import com.ibm.pross.server.app.http.handlers.ReadHandler;
import com.ibm.pross.server.app.http.handlers.RootHandler;
import com.ibm.pross.server.configuration.permissions.AccessEnforcement;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import bftsmart.reconfiguration.util.sharedconfig.ServerConfiguration;

@SuppressWarnings("restriction")
public class HttpRequestProcessor {

	public static final String TLS_VERSION = "TLSv1.2";

	public static final int BASE_HTTP_PORT = 8080;

	public static int SHUTDOWN_DELAY_SECONDS = 5;
	public static int NUM_PROCESSING_THREADS = 15;

	private final HttpsServer server;

	public HttpRequestProcessor(final int serverIndex, final ServerConfiguration serverConfig,
			final AccessEnforcement accessEnforcement, final ConcurrentMap<String, ApvssShareholder> shareholders,
			final X509Certificate caCert, final X509Certificate hostCert, final PrivateKey privateKey)
			throws IOException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException,
			UnrecoverableKeyException, CertificateException {

		final int httpListenPort = BASE_HTTP_PORT + serverIndex;
		this.server = HttpsServer.create(new InetSocketAddress(httpListenPort), 0);

		setupTls(caCert, hostCert, privateKey);

		System.out.println("HTTP server listening on port: " + httpListenPort);

		addHandlers(serverIndex, serverConfig, accessEnforcement, shareholders);

		// this.server.setExecutor(Executors.newFixedThreadPool(NUM_PROCESSING_THREADS));
	}

	public void addHandlers(final int serverIndex, final ServerConfiguration serverConfig,
			final AccessEnforcement accessEnforcement, final ConcurrentMap<String, ApvssShareholder> shareholders) {
		// Returns basic information about this server:
		// quorum information, other servers)
		this.server.createContext("/", new RootHandler(serverIndex, serverConfig, shareholders));

		// Define request handlers for the supported client operations
		this.server.createContext("/generate", new GenerateHandler(accessEnforcement, shareholders));
		this.server.createContext("/info", new InfoHandler(accessEnforcement, serverConfig, shareholders));

		// Handlers for reading or storing shares
		this.server.createContext("/read", new ReadHandler(accessEnforcement, serverConfig, shareholders));
		// this.server.createContext("/store", new InfoHandler(accessEnforcement,
		// serverConfig, shareholders));
		// implement as DKG with default value given to each shareholder (must use
		// interpolation style DKG!)

		// Handlers for deleting or recovering shares
		this.server.createContext("/delete", new InfoHandler(accessEnforcement, serverConfig, shareholders));
		this.server.createContext("/recover", new InfoHandler(accessEnforcement, serverConfig, shareholders));

		// Handlers for enabling and disabling shares
		this.server.createContext("/enable", new InfoHandler(accessEnforcement, serverConfig, shareholders));
		this.server.createContext("/disable", new InfoHandler(accessEnforcement, serverConfig, shareholders));

		// Handlers for using the shares to perform functions
		this.server.createContext("/exponentiate", new ExponentiateHandler(accessEnforcement, shareholders));
		this.server.createContext("/rsa_sign", new InfoHandler(accessEnforcement, serverConfig, shareholders));

		// Define server to server requests
		this.server.createContext("/get_partial", new InfoHandler(accessEnforcement, serverConfig, shareholders));
	}

	public void setupTls(final X509Certificate caCert, final X509Certificate hostCert, final PrivateKey hostKey)
			throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, CertificateException,
			IOException, UnrecoverableKeyException {

		// Configure SSL context
		final SSLContext sslContext = SSLContext.getInstance(TLS_VERSION);

		// Create in-memory key store
		final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
		final char[] password = "password".toCharArray();
		keyStore.load(null, password);

		// Add the CA certificate
		keyStore.setCertificateEntry("ca", caCert);
		
		// Add certificate and private key for the server
		keyStore.setKeyEntry("host", hostKey, password, new X509Certificate[] { hostCert, caCert });

		// Make Key Manager Factory
		final KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(keyStore, password);

		// setup the trust manager factory
		final TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		tmf.init(keyStore);

		sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

		this.server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
			public void configure(final HttpsParameters params) {
				try {
					// Configure context
					final SSLEngine engine = sslContext.createSSLEngine();
					params.setWantClientAuth(true);
					params.setNeedClientAuth(false);
					params.setCipherSuites(engine.getEnabledCipherSuites());
				} catch (Exception ex) {
					throw new RuntimeException("Failed to create HTTPS server");
				}
			}
		});
	}

	public void start() {
		this.server.start();
	}

	public void stop() {
		this.server.stop(SHUTDOWN_DELAY_SECONDS);
	}

	/**
	 * From:
	 * https://stackoverflow.com/questions/13592236/parse-a-uri-string-into-name-value-collection
	 * 
	 * @param url
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public static Map<String, List<String>> parseQueryString(final String queryString)
			throws UnsupportedEncodingException {

		final Map<String, List<String>> queryPairs = new LinkedHashMap<String, List<String>>();
		final String[] pairs = queryString.split("&");
		for (String pair : pairs) {
			final int idx = pair.indexOf("=");
			final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
			if (!queryPairs.containsKey(key)) {
				queryPairs.put(key, new LinkedList<String>());
			}
			final String value = idx > 0 && pair.length() > idx + 1
					? URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
					: null;
			queryPairs.get(key).add(value);
		}
		return queryPairs;
	}

}