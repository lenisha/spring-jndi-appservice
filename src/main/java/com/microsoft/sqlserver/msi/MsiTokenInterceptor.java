package com.microsoft.sqlserver.msi;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;

import java.lang.reflect.Method;
import java.sql.SQLException;

public class MsiTokenInterceptor extends JdbcInterceptor {
    protected static final Logger logger = LogManager.getLogger(MsiTokenInterceptor.class);

    @Override
    public void reset(ConnectionPool parent, PooledConnection con) {
        logger.debug("Reset connection pool started");
        if (parent == null)
            return;
        logger.debug("pool has active: " + parent.getActive() + " idle: " + parent.getIdle());

        // refresh logic
        MsiAuthToken.refreshToken(parent);

        // need to refresh connection with new token
        if (con != null)
            try {
                con.connect();
            } catch (SQLException e) {
                logger.error("Exception caught on reconnect:" + e.getMessage());
               throw new RuntimeException(e.getCause());
            }
    }
   
    @Override
    public void disconnected(ConnectionPool parent, PooledConnection con, boolean finalizing) {
        logger.debug("Disconnect started ");
        super.disconnected(parent, con, finalizing);
       
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        logger.debug("Invoke started METHOD: " + method.getName());

         return super.invoke(proxy, method, args);
    }

    @Override
    public void poolStarted(ConnectionPool pool) {
        logger.debug("Init connection pool start");
        super.poolStarted(pool);
  
        try {
            if ( pool != null &&  MsiAuthToken.isMsiEnabled(pool.getPoolProperties().getUrl()) ) {
                String accessToken = MsiAuthToken.aquireMsiToken("https://database.windows.net/");
                pool.getPoolProperties().getDbProperties().setProperty("accessToken", accessToken);
               
                MsiAuthToken.cacheToken(accessToken);
                MsiTokenCache.savePool(pool);
            }
        } catch (Throwable e) {
            logger.error("Exception caught during initialization:" + e.getMessage());
            throw new RuntimeException(e.getCause());
        }
    }
}