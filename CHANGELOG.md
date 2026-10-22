# [3.0.0](https://github.com/gravitee-io/gravitee-service-discovery-consul/compare/2.0.0...3.0.0) (2026-10-22)


### Bug Fixes

* build against Vert.x 5 so that the plugin runs on APIM 4.12 onwards ([db2fa4d](https://github.com/gravitee-io/gravitee-service-discovery-consul/commit/db2fa4d9b56f11a660bcc4d61366e0b26e220d46))
* release closed consul clients by creating them as uniquely named shared clients ([7c4c7db](https://github.com/gravitee-io/gravitee-service-discovery-consul/commit/7c4c7db2a2b0786544f9780fe9621c4ce32ab014))


### BREAKING CHANGES

* the plugin is now built against Vert.x 5 and requires APIM 4.12 or later.

https://gravitee.atlassian.net/browse/APIM-15572

# [2.0.0](https://github.com/gravitee-io/gravitee-service-discovery-consul/compare/1.3.0...2.0.0) (2026-09-21)


* chore(deps)!: move to the gravitee orb 5 and parent 23 ([fcdbbb9](https://github.com/gravitee-io/gravitee-service-discovery-consul/commit/fcdbbb9856de9d68133f02cbfc17ee89e28334a9))


### BREAKING CHANGES

* the artifact is now compiled for Java 21, up from Java 11, and
requires a Java 21 runtime or later.

https://gravitee.atlassian.net/browse/BX-403
