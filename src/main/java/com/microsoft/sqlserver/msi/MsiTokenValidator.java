package com.microsoft.sqlserver.msi;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.Validator;

public class MsiTokenValidator implements Validator {
    protected static final Logger logger = LogManager.getLogger(MsiTokenValidator.class);
    private volatile long lastValidated = System.currentTimeMillis();

    @Override
    public boolean validate( Connection connection,  int validateAction) {
        logger.info("Validation invoked: " + validateAction);
        logger.info("Connection:" + connection.getClass().getName());
        final ConnectionPool pool = MsiTokenCache.getPool();
        MsiAuthToken.refreshToken(pool);
       
        return validateStatement(connection, validateAction);
    }

    protected boolean validateStatement( Connection connection,  int validateAction) {
        logger.info("Validation statement: " + validateAction);
        String query = null;
        long now = System.currentTimeMillis();
        
        PoolConfiguration poolProperties = MsiTokenCache.getPool().getPoolProperties();

        if (validateAction == 4 && poolProperties.getInitSQL() != null) {
            query = poolProperties.getInitSQL();
        }

        if (query == null) {
            query = poolProperties.getValidationQuery();
        }

        if (query == null) {
            int validationQueryTimeout = poolProperties.getValidationQueryTimeout();
            if (validationQueryTimeout < 0)
                validationQueryTimeout = 0;
            try {
                if (connection.isValid(validationQueryTimeout)) {
                    this.lastValidated = now;
                    return true;
                } else {
                    if (poolProperties.getLogValidationErrors()) {
                        logger.error("isValid() returned false.");
                    }
                    return false;
                }
            } catch (final SQLException e) {
                if (poolProperties.getLogValidationErrors()) {
                    logger.error("isValid() failed.", e);
                } else if (logger.isDebugEnabled()) {
                    logger.debug("isValid() failed.", e);
                }
                return false;
            }
        }

        Statement stmt = null;
        try {
            stmt = connection.createStatement();

            final int validationQueryTimeout = poolProperties.getValidationQueryTimeout();
            if (validationQueryTimeout > 0) {
                stmt.setQueryTimeout(validationQueryTimeout);
            }

            stmt.execute(query);
            stmt.close();
            this.lastValidated = now;

            logger.debug("Validated query:" + query);
            return true;
        
        } catch (final Exception ex) {
            if (poolProperties.getLogValidationErrors()) {
                logger.error("SQL Validation error", ex);
            } else if (logger.isDebugEnabled()) {
                logger.debug("Unable to validate object:", ex);
            }
            if (stmt != null)
                try {
                    stmt.close();
                } catch (final Exception ignore2) {
                    /* NOOP */}

            try {
                if (!connection.getAutoCommit()) {
                    connection.rollback();
                }
            } catch (final SQLException e) {
                // do nothing
            }
        } finally {
            try {
                if (!connection.getAutoCommit()) {
                    connection.commit();
                }
            } catch (final SQLException e) {
                // do nothing
            }
        }
        return false;
    }
   
}