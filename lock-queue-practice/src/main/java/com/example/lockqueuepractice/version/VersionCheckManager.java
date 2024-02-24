package com.example.lockqueuepractice.version;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class VersionCheckManager {
    private Map<String, Set<String>> forceUpdateMap = Map.of("android", Set.of("4.81.1", "4.82.0", "4.82.1"), "ios", Set.of());
    private String androidMinVersion = "5.0.0";
    private String iosMinVersion = "5.0.0";

    public Boolean needForceUpdate(String os, String osVer, String appVer) {
        return os == "android" && VersionUtils.compare(appVer, androidMinVersion) < 0
                || os == "ios" && VersionUtils.compare(appVer, iosMinVersion) < 0
                || checkForceUpdateSpecificVersion(os, appVer);
    }

    private Boolean checkForceUpdateSpecificVersion(String os, String appVer) {
        if (!forceUpdateMap.containsKey(os)) {
            return false;
        }

        if (forceUpdateMap.get(os).contains(appVer)) {
            return false;
        }
    }

    public Boolean needUpdate(String os, String appVer) {
        return os == "android" && VersionUtils.compare(appVer, androidMinVersion) < 0
                || os == "ios" && VersionUtils.compare(appVer, iosMinVersion) < 0;
    }
}
