### Overview

This module is used for the extended implementation of the ranger-obs project.


1.Extension of the ranger-obs-service module

(1)org.apache.ranger.obs.security.token.DistributedFileSystemSecretProvider:When the delegationToken manager is SimpleSecretManager,SecretKey provider for SimpleSecretManager,It implements the org.apache.ranger.obs.security.token.SecretProvider interface.

Add the following configuration item to the core-site.xml file of ranger-obs-service:
```
<property>
  <name>ranger.obs.service.dt.secret.provider</name>
  <value>org.apache.ranger.obs.security.token.DistributedFileSystemSecretProvider</value>
  <description>The default value is org.apache.ranger.obs.security.token.ShareFileSecretProvider</description>
</property>
```
