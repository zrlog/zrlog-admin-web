./mvnw -PnodeBuild clean install
./mvnw -Pupdater-dev -pl zrlog-admin-web exec:java -Dexec.mainClass="com.zrlog.admin.Application"
