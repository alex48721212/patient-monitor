package com.patientmonitor.realtime;

import com.patientmonitor.vital.VitalSign;

/** 서비스 계층이 발행하고 MonitorBroadcaster 가 커밋 이후 WebSocket 으로 내보내는 이벤트 */
public sealed interface MonitorEvent {

    /** 새 측정값 저장. 알람이 생겼으면 hasAlarms = true */
    record VitalRecorded(VitalSign vital, boolean hasAlarms) implements MonitorEvent {
    }

    /** 환자 등록/수정/퇴원/삭제 */
    record PatientsChanged() implements MonitorEvent {
    }

    /** 알람 확인 처리 */
    record AlarmsChanged() implements MonitorEvent {
    }
}
