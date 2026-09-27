./mvnw -PnodeBuild clean install
./mvnw -pl zrlog-admin-web exec:java -Dexec.mainClass="com.zrlog.admin.Application"