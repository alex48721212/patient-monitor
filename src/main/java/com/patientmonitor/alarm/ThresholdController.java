package com.patientmonitor.alarm;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 대시보드가 정상 범위를 서버 설정과 같게 표시하도록 노출 */
@RestController
public class ThresholdController {

    private final ThresholdProperties thresholds;

    public ThresholdController(ThresholdProperties thresholds) {
        this.thresholds = thresholds;
    }

    @GetMapping("/api/thresholds")
    public ThresholdProperties thresholds() {
        return thresholds;
    }
}
