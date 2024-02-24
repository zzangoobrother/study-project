package com.example.lockqueuepractice.version;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/version")
public class VersionController {

    private final VersionCheckManager versionCheckManager;

    public VersionController(VersionCheckManager versionCheckManager) {
        this.versionCheckManager = versionCheckManager;
    }

    @GetMapping("/check")
    public VersionResult check(@RequestParam String os, @RequestParam String osVer, @RequestParam String appVer) {
        VersionResult result = VersionResult.OK;

        if (versionCheckManager.needForceUpdate(os, osVer, appVer)) {
            // Check force update
            result = VersionResult.FORCE_UPDATE;
        } else if (versionCheckManager.needUpdate(os, appVer)) {
            // Check ask update
            result = VersionResult.UPDATE;
        }

        if (needForceUpdateForAndroidUnder_4_89_0(os, osVer, appVer)) {
            result = VersionResult.FORCE_UPDATE;
        }

        if ("ios" == os && appVer == "5.42.2") {
            result = VersionResult.FORCE_UPDATE;
        }

        return result;
    }

    private Boolean needForceUpdateForAndroidUnder_4_89_0(String os, String osVer, String appVer) {
        return os == "android" && VersionUtils.compare(appVer, "4.89.0") < 0 && VersionUtils.getMajorVersion(osVer) == 11;
    }
}
