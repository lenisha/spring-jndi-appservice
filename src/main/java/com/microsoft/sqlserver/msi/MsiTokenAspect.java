package com.microsoft.sqlserver.msi;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.tomcat.dbcp.dbcp.BasicDataSource;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;



@Aspect
public class MsiTokenAspect {
    protected static final Logger logger = LogManager.getLogger(MsiTokenAspect.class);

    @Before(value = "execution (* org.apache.tomcat.dbcp.dbcp.BasicDataSource.getConnection())")
    public void onNewConnection(final JoinPoint pjp) throws Throwable {

        Object target = pjp.getTarget();
        if ( !(target instanceof BasicDataSource) )
            return;

        BasicDataSource ds = (BasicDataSource)target;
        if (!MsiAuthToken.isMsiEnabled(ds.getUrl()))
            return;

        long now = System.currentTimeMillis() / 1000;

        logger.debug("MSI Token validation time now:" + now + " token expiration at:" + MsiTokenCache.getExpiration());

        if ( MsiTokenCache.getExpiration() > now  + MsiAuthToken.SKEW) {
            // Token is still valid
            logger.debug("Token is still valid");
            ds.addConnectionProperty("accessToken", MsiTokenCache.getToken() );
            return;
        }
        else {
            // token expired or was not obtained yet
            logger.debug("Getting new token");
            String accessToken = MsiAuthToken.aquireMsiToken("https://database.windows.net/");
            ds.addConnectionProperty("accessToken", accessToken);
            MsiAuthToken.cacheToken(accessToken);
        }
    }



}