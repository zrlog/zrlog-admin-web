./mvnw -PnodeBuild clean package
./mvnw exec:java -Dexec.mainClass="com.zrlog.admin.Application"