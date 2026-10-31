package com.patientmonitor.realtime;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.patientmonitor.alarm.Alarm;
import com.patientmonitor.alarm.AlarmRepository;

/**
 * 트랜잭션이 커밋된 뒤에만 전송해서, 롤백된 데이터가 화면에 나타나지 않게 한다.
 * (트랜잭션 밖에서 발행된 이벤트도 fallbackExecution 으로 바로 전송)
 */
@Component
public class MonitorBroadcaster {

    private final MonitorWebSocketHandler handler;
    private final AlarmRepository alarmRepository;

    public MonitorBroadcaster(MonitorWebSocketHandler handler, AlarmRepository alarmRepository) {
        this.handler = handler;
        this.alarmRepository = alarmRepository;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void on(MonitorEvent event) {
        switch (event) {
            case MonitorEvent.VitalRecorded e -> {
                handler.broadcast("vital", e.vital());
                if (e.hasAlarms()) {
                    List<Alarm> alarms = alarmRepository.findByVitalSignId(e.vital().id());
                    alarms.forEach(alarm -> handler.broadcast("alarm", alarm));
                }
            }
            case MonitorEvent.PatientsChanged e -> handler.broadcast("patients-changed", "");
            case MonitorEvent.AlarmsChanged e -> handler.broadcast("alarms-changed", "");
        }
    }
}
