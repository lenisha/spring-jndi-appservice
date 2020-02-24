package com.microsoft.sqlserver.msi;


import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tomcat.jdbc.pool.ConnectionPool;

public class MsiTokenCache {

    private static String KEY_EXPIRE = "expiresOn";
    private static String KEY_TOKEN = "accessToken";
    private static String KEY_POOL = "pool";

    private static ConcurrentHashMap<String,Object> cache = null;

    protected static ConcurrentHashMap<String,Object> getCache() {
        if (cache == null)
           cache = new ConcurrentHashMap<String, Object>();
        return cache;
    }

    public static long getExpiration() {
        cache = getCache();
        Long expiration = (Long)cache.get(KEY_EXPIRE);

        if ( expiration != null )
            return expiration.longValue();
        else
            return 0;
    }
    public static String getToken() {
        cache = getCache();
        return (String)cache.get(KEY_TOKEN);
    }
    public static ConnectionPool getPool() {
        cache = getCache();
        return (ConnectionPool)cache.get(KEY_POOL);
    }

    public static void saveExpiration(String expiration) throws Exception {
        try {
            Date dateFmt = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a X").parse(expiration);
            long expLong= dateFmt.getTime() / 1000;

            saveExpiration(expLong);
        } catch (ParseException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static void saveExpiration(long expiration) {
        cache = getCache();
        cache.put(KEY_EXPIRE,new Long(expiration));
    }

    public static void saveToken(String token) {
        cache = getCache();
        cache.put(KEY_TOKEN,token);
    }
    public static void savePool(ConnectionPool pool) {
        cache = getCache();
        cache.put(KEY_POOL,pool);
    }

}
