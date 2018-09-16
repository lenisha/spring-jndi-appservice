# Spring/Hibernate App on Azure AppService (Tomcat) using JNDI to access SQL Server on Azure  

## Spring Hibernate JNDI Example

This Spring example is based on tutorial [Hibernate, JPA & Spring MVC - Part 2] (https://www.alexecollins.com/tutorial-hibernate-jpa-spring-mvc-part-2/) by Alex Collins

Spring application uses hibernate to connect to SQL Server. Hibernate is using container managed JNDI Data Source.

There are few options to configure JNDI for the web application running undet Tomcat (Tomcat JNDI)[https://www.journaldev.com/2513/tomcat-datasource-jndi-example-java] :

- Application context.xml - located in app `META-INF/context.xml` - define Resource element in the context file and container will take care of loading and configuring it.
- Server server.xml - Global, shared by applications, defined in `tomcat/conf/server.xml`
- server.xml and context.xml - defining Datasource globally and including ResourceLink in application context.xml

When deploying to Java application on Azure AppService, you can customize  out of the box managed Tomcat server.xml, but is not recommended as it will create snowflake deployement. That's why we will define JNDI Datasource on the **Application level**

# Create Azure AppService and database

In Azure Portal create `Web App + SQL` and configure settings for the App to use Tomcat
![Azure App Service config](https://github.com/lenisha/spring-jndi-appservice/raw/master/img/AppService.PNG  "App Service Config")

Copy the Connection String from Azure SQL database
![Azure SQL Connection](https://github.com/lenisha/spring-jndi-appservice/raw/master/img/ConnString.PNG "Azure  SQL Server")

Add the Connection String from Azure SQL database to **App Service / Application Settings**   settings

** DO NOT INCLUDE USERNAME/PASSWORD **

![Azure SQL Connection](https://github.com/lenisha/spring-jndi-appservice/raw/master/img/ConnectionString.PNG "Azure App Service Settings")

DB connection url for Azure SQL is usually in thins format `jdbc:sqlserver://server.database.windows.net:1433;database=db;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;`

Adding Setting `JAVA_OPTS` with value `-D<connection name>=<jdbc url>`  would create environment variable `connection name` available to the Java application.
 In our example above - `SQLDB_URL`


# Managed Service Identity
Add MSI to AppService and grant it permissions to SQL database

```
az webapp identity assign --resource-group <resource_group> --name <app_service_name>
az ad sp show --id <id_created_above>

az sql server ad-admin create --resource-group <resource_group> --server-name <sql_server_name>  --display-name <admin_account_name> --object-id <id_created_above>
```

Example:

```
az webapp identity assign --resource-group jnditest --name testjndi
az ad sp show --id 82cc5f96-226a-4721-xxxxx-xxxxxxx

az sql server ad-admin create --resource-group jnditest --server-name jnditestsrv  --display-name admin-msi --object-id 82cc5f96-226a-4721-902c-xxxxxx
```

# Define DataSource


To define JNDI Datasource for Tomact Application, add file `META-INF/context.xml` to the application.
In this example it's added to `main/webapp/META-INF/context.xml` anc contains the following datasource definition

```
<Context>
    <Resource auth="Container" 
	    driverClassName="com.microsoft.sqlserver.jdbc.SQLServerDriver"
	    maxActive="8" maxIdle="4"
	    name="jdbc/tutorialDS" type="javax.sql.DataSource"
		url="${SQLDB_URL}"
	    factory="com.microsoft.sqlserver.msi.MsiDataSourceFactory" />
</Context>
```
where
- `factory` overrides default Tomcat `BasicDataSourceFactory` and is MSI aware (included in `msi-mssql-jdbc` library)
- `url` points to url, in the example above provided by environment variable set by `JAVA_OPTS=-DSQLDB_URL=jdbc:sqlserver://...'

## Enable MSI for the JDBC Connection Factory

There are currently 3 ways to enable MSI for datasource connection Factory

- Environment variable: `JDBC_MSI_ENABLE=true`, set it in ApplicationSettings for Azure WebApp

- jdbcURL flag: to set it add in jdbc connection string `msiEnable=true`. E.g `jdbc:sqlserver://server.database.windows.net:1433;database=db;msiEnable=true;...`

- `msiEnable` flag in context.xml . E.g
```
<Context>
    <Resource auth="Container"
	   ....
		msiEnable="true"
		factory="com.microsoft.sqlserver.msi.MsiDataSourceFactory" />

</Context>
```

## Use SQL Server Hibernate Dialect

in `resources\META-INF\persistence.xml`

```
<property name="hibernate.dialect" value="org.hibernate.dialect.SQLServerDialect" />
```

## Update Application dependencies
JDBC driver for SQL server `sqljdbc42.jar` installed in Tomcat in Azure App Service by default is older version that does not support token authentication,
Include newer version that supports token based Authentication along with the app.

```

        <dependency>
            <groupId>com.microsoft.sqlserver.msi</groupId>
            <artifactId>msi-mssql-jdbc</artifactId>
	        <version>1.1.0</version>
        </dependency>

        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.8.0</version>
        </dependency>

        <dependency>
            <groupId>com.microsoft.sqlserver</groupId>
            <artifactId>mssql-jdbc</artifactId>
            <version>6.4.0.jre7</version>
        </dependency>
```

The `msi-mssql-jdbc` library is available on Maven central - sources: [msi-mssql-jdbc](https://github.com/lenisha/msi-mssql-jdbc/releases/tag/v1.0)




## To test locally:
`mvn clean  package`
and copy resulting war file from target directory to Tomcat webapps directory

Navigate to `localhost:8080/spring-jndi-appservice/create-user.html`

## To debug locally
set env variables
```
set JAVA_OPTS=-DSQLDB_URL=jdbc:sqlserver://server.database.windows.net:1433;database=database;msiEnable=true;encrypt=true;trustServerCerti
ficate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;

set JPDA_SUSPEND=y

catalina.bat jpda start
```


## Deploy on Azure AppService

To test on Azure AppService deploy using Azure deployment options, the simplest is to use FTP:

### FTP
  - Get the FTP hostname and credentials from the App Service blade
  - Upload war file to `d:\home\site\wwwroot\webapps`  
  - Upload web.config file to `d:\home\site\wwwroot`
  - Restart App Service
  - Navigate to `https://appname.azurewebsites.net/tutorial-hibernate-jpa/create-user.html`

### Maven plugin
- setup authentication in .m2/settings.xml as described in https://docs.microsoft.com/en-us/java/azure/spring-framework/deploy-spring-boot-java-app-with-maven-plugin

- Add plugin definition in pom.xml
```
      <plugin>
            <groupId>com.microsoft.azure</groupId>
            <artifactId>azure-webapp-maven-plugin</artifactId>
            <version>0.2.0</version>
            <configuration>
                <authentication>
                    <serverId>azure-auth</serverId>
                </authentication>
                 <!-- Web App information -->
               <resourceGroup>testjavajndi</resourceGroup>
               <appName>testjavajndi</appName>
               <!-- <region> and <pricingTier> are optional. They will be used to create new Web App if the specified Web App doesn't exist -->
               <region>canadacentral</region>
               <pricingTier>S1</pricingTier>
               
               <!-- Java Runtime Stack for Web App on Windows-->
               <javaVersion>1.7.0_51</javaVersion>
               <javaWebContainer>tomcat 7.0.50</javaWebContainer>
               
               <!-- WAR deployment -->
              <deploymentType>war</deploymentType>

               <!-- Specify the war file location, optional if the war file location is: ${project.build.directory}/${project.build.finalName}.war -->
               <warFile>${project.build.directory}/${project.build.finalName}.war </warFile>

               <!-- Specify context path, optional if you want to deploy to ROOT -->
               <path>/tutorial-hibernate-jpa</path>

            </configuration>
        </plugin>
	
```
- run `mvn azure-webapp:deploy`

Example output:
```
[INFO] --- azure-webapp-maven-plugin:0.2.0:deploy (default-cli) @ tutorial-hibernate-jpa ---
AI: INFO 25-03-2018 19:31, 1: Configuration file has been successfully found as resource
AI: INFO 25-03-2018 19:31, 1: Configuration file has been successfully found as resource
[INFO] Start deploying to Web App testjavajndi...
[INFO] Authenticate with ServerId: azure-auth
[INFO] [Correlation ID: 462a8a7f-c2ec-40c8-a43b-80be705225b8] Instance discovery was successful
[INFO] Updating target Web App...
[INFO] Successfully updated Web App.
[INFO] Using 'UTF-8' encoding to copy filtered resources.
[INFO] Copying 1 resource to C:\projects\tutorial-hibernate-jpa\target\azure-webapps\testjavajndi\webapps
[INFO] Starting uploading files to FTP server: waws-prod-yt1-005.ftp.azurewebsites.windows.net
[INFO] Starting uploading directory: C:\projects\tutorial-hibernate-jpa\target\azure-webapps\testjavajndi --> /site/wwwroot
[INFO] [DIR] C:\projects\tutorial-hibernate-jpa\target\azure-webapps\testjavajndi --> /site/wwwroot
[INFO] ..[DIR] C:\projects\tutorial-hibernate-jpa\target\azure-webapps\testjavajndi\webapps --> /site/wwwroot/webapps
[INFO] ....[FILE] C:\projects\tutorial-hibernate-jpa\target\azure-webapps\testjavajndi\webapps\tutorial-hibernate-jpa.war --> /site/wwwroot/webapps
[INFO] ...........Reply Message : 226 Transfer complete.

[INFO] Finished uploading directory: C:\projects\tutorial-hibernate-jpa\target\azure-webapps\testjavajndi --> /site/wwwroot
[INFO] Successfully uploaded files to FTP server: waws-prod-yt1-005.ftp.azurewebsites.windows.net
[INFO] Successfully deployed Web App at https://testjavajndi.azurewebsites.net
``` 


## Azure App Insights

Enable Azure App Insights for monitoring and application log aggregation. The process describe on Azure docs web site:
[App Insights for Java](https://docs.microsoft.com/en-us/azure/application-insights/app-insights-java-get-started)
Summary of steps below:

- update `pom.xml` to include library dependencies
```
<dependency>
    <groupId>com.microsoft.azure</groupId>
    <artifactId>applicationinsights-web</artifactId>
    <!-- or applicationinsights-core for bare API -->
    <version>[2.1,)</version>
</dependency>
<dependency>
    <groupId>com.microsoft.azure</groupId>
    <artifactId>applicationinsights-logging-log4j1_2</artifactId>
    <version>[2.1,)</version>
</dependency>
```

- Add `ApplicationInsights.xml` to resources folder that contains definition of telemetry filters and JMX Counters

- Add App Insights Filter in `web.xml`
 ```
  <filter>
        <filter-name>ApplicationInsightsWebFilter</filter-name>
        <filter-class>
            com.microsoft.applicationinsights.web.internal.WebRequestTrackingFilter
        </filter-class>
    </filter>
    <filter-mapping>
        <filter-name>ApplicationInsightsWebFilter</filter-name>
        <url-pattern>/*</url-pattern>
    </filter-mapping>
  ```
- For Spring MVC interceptors in `mvx-dispatcher-servlet.xml`

```
   <context:component-scan base-package="tutorial, com.microsoft.applicationinsights.web.spring"/>

   <!-- This factory method causes ApplicationInsights.xml to get loaded -->
    <bean name="appinsightTelemetryConfiguration"
                 class="com.microsoft.applicationinsights.TelemetryConfiguration"
                 factory-method="getActive">
    </bean>

    <mvc:interceptors>
        <mvc:interceptor>
            <mvc:mapping path="/**"/>
            <bean class="com.microsoft.applicationinsights.web.spring.RequestNameHandlerInterceptorAdapter" />
        </mvc:interceptor>
    </mvc:interceptors>
```

- Update `log4j.prooperties` to include app insights appender, it would stream all app logs to log analytics

```
log4j.appender.aiAppender=com.microsoft.applicationinsights.log4j.v1_2.ApplicationInsightsAppender

log4j.logger.tutorial=INFO, file, aiAppender
log4j.logger.com.microsoft=INFO, file, aiAppender
```

- Add Instrumentation key in Azure App Service environments variable `APPLICATION_INSIGHTS_IKEY`

### App Insights Views
After performing these steps following views will be available in App Insights:

** Live Stream **
![Live Stream](https://github.com/lenisha/spring-jndi-appservice/raw/master/img/AI-LiveStream.PNG "LS")

** Metrics **
![Metrics](https://github.com/lenisha/spring-jndi-appservice/raw/master/img/AI-Metrics.png "Metrics")

** Performance **
![Performance](https://github.com/lenisha/spring-jndi-appservice/raw/master/img/AI-Performace.PNG "Performance")

** Logs **
![Logs](https://github.com/lenisha/spring-jndi-appservice/raw/master/img/AI-Logs.png "Logs")


### App Insights Agent
To enable dependency tracking and end to end transaction view include app insights agent with the java application

- Copy agent jar and Create `AI-Agent.xml` in the same directory that has agent jar (in `/appinsights` directory in this project).
  This file will enables standard dependencies tracking such as SQL, Redis, HTTP
- Optional: define Java functions to track (it will measure and include these functions in Application Map)
  In this project we defined UserController.createUser as a method to track
```
 <Class name="tutorial.UsersController">
            <Method name="createUser"
                    reportCaughtExceptions="true"
                    reportExecutionTime="true"
            />
 </Class>
```

- Deploy Agent jar and xml file in the directory in App service /site/wwwroot/appinsights
  Using plugin in `pom.xml`
```
<resource>
    <!-- Where your artifacts are stored -->
    <directory>${project.basedir}/appinsights</directory>
    <!-- Relative path to /site/wwwroot/ -->
    <targetPath>appinsights</targetPath>
    <includes>
        <include>*</include>
    </includes>
</resource>
```

- Include java agent in JVM startup. Set `-javaagent:<path to agent>` in Azure App Service env variable `JAVA_OPTS`
```
 <appSettings>
     <property>
          <name>JAVA_OPTS</name>
          <value>-javaagent:D:\home\site\wwwroot\appinsights\applicationinsights-agent-2.1.2.jar -DSQLDB_URL=jdbc:sqlserver://jnditestsrv.database.windows.net:1433;database=jnditestsql;encrypt=true;trustServerCertificate=false;hostNameInCertificate=*.database.windows.net;loginTimeout=30;</value>
      </property>
       <property>
           <name>JDBC_MSI_ENABLE</name>
           <value>true</value>
       </property>
        <!-- Add APPLICATION_INSIGHTS_IKEY - Instrumentation key-->
 </appSettings>
```

The resulting settings:
![Azure App Sttings](https://github.com/lenisha/spring-jndi-appservice/raw/master/img/AppSettings.PNG "Azure App Service Settings")

** Application Map ** - shows App Service usage of SQL and specifically marked `createUser` method
![Application Map](https://github.com/lenisha/spring-jndi-appservice/raw/master/img/AI-AppMap.PNG "Azure App Map")

** End to End transaction View ** - shows dependencies time distribution
![End to End Transaction](https://github.com/lenisha/spring-jndi-appservice/raw/master/img/AI-EndToEnd.PNG "E2E")

## Additional notes on environment indirection

JDBC driver for SQL server `sqljdbc4jar` is installed in Tomcat in Azure App Service by default is old version, need to include most recent version supporting AzureAD in `pom.xml`
To define JNDI Datsource for Tomact Application, add file `META-INF/context.xml` to the application.
In this example it's added to `main/webapp/META-INF/context.xml` anc contains the following datasource definition

```
<Context>
    <Resource auth="Container"
	    driverClassName="com.microsoft.sqlserver.jdbc.SQLServerDriver"
	    maxActive="8" maxIdle="4"
	    name="jdbc/tutorialDS" type="javax.sql.DataSource"
	    url="${SQLAZURE_UsersDB}" />

</Context>
```

Notice that the URL for the database uses environment variable that should be available and processed by Tomcat startup.
Unfortunately directly reading App Settings Connection string environment varibale does not work, due to the way Tomcat is started by Azure App Service lifecycle.
So we will use **indirection** or what was called in C++ world a pointer to a variable.

The way to enforce Tomcat to read environment variables and make them available to application is to define them in `JAVA_OPTS` paramaters during Tomcat startup.

#### Define SQL Connection env varible for JAVA_OPTS
As discussed in [How to set env in Java app in Azure App Service](https://blogs.msdn.microsoft.com/azureossds/2015/10/09/setting-environment-variable-and-accessing-it-in-java-program-on-azure-webapp/)
To set environment variable that uses another variable definition (not direct value) is to override it in `web.config`

And here are our configuration where we are passing in `JAVA_OPTS` to tomcat  `SQLAZURE_UsersDB`variable in that is used in our DataSource definition above. The value of it relies on the fact that we have set in Application Settings Database Connection string called `UsersDB`, as discussed previously.

```
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <system.webServer>
    <handlers>
      <remove name="httpPlatformHandlerMain" />
      <add name="httpPlatformHandlerMain" path="*" verb="*" modules="httpPlatformHandler" resourceType="Unspecified"/>
    </handlers>
    <httpPlatform processPath="%AZURE_TOMCAT7_HOME%\bin\startup.bat">
        <environmentVariables>
            <environmentVariable name="JAVA_OPTS" value="-DSQLAZURE_UsersDB=%SQLAZURECONNSTR_UsersDB%"/>
        </environmentVariables>
      </httpPlatform>
  </system.webServer>
</configuration>
```

This file `web.config` should be copied to `D:\home\site\wwwroot` on the AppService to override Tomcat loading.